package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.ui.audio.AudioPlayerManager
import com.troikoss.continuum_explorer.utils.toUniversal
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToLong

data class SyncPlayPeerInfo(
    val peerId: String,
    val displayName: String,
    val clientIp: String,
    val rttMs: Long = 0L,
    val clockOffsetMs: Long = 0L,
    val isBufferedReady: Boolean = false,
    val isAuthenticated: Boolean = false,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

data class SyncPlayRoomState(
    val isActive: Boolean = false,
    val mediaFilePath: String = "",
    val mediaFileName: String = "",
    val mediaSizeBytes: Long = 0L,
    val isVideo: Boolean = true,
    val isPlaying: Boolean = false,
    val basePositionMs: Long = 0L,
    val stateUpdatedAtServerEpochMs: Long = System.currentTimeMillis(),
    val playbackSpeed: Float = 1.0f,
    val lastAction: String = "IDLE",
    val lastActor: String = "Host",
    val executeAtServerEpochMs: Long = 0L,
    val scheduleLeadTimeMs: Long = 140L,
    val passcode: String = SyncPlayManager.generatePin(),
    val allowGuestControl: Boolean = false,
    val syncHostAudioPlayer: Boolean = true,
    val connectedPeers: List<SyncPlayPeerInfo> = emptyList(),
    val recentEvents: List<String> = emptyList()
) {
    val requiresPasscode: Boolean
        get() = true

    /**
     * Computes the live interpolated playback position at [nowEpochMs] taking into account
     * any scheduled future execution timestamp and playback speed.
     */
    fun currentPositionMs(nowEpochMs: Long = System.currentTimeMillis()): Long {
        if (!isPlaying) return basePositionMs.coerceAtLeast(0L)
        val anchorTime = if (executeAtServerEpochMs > stateUpdatedAtServerEpochMs) {
            executeAtServerEpochMs
        } else {
            stateUpdatedAtServerEpochMs
        }
        val elapsedMs = (nowEpochMs - anchorTime).coerceAtLeast(0L)
        return (basePositionMs + (elapsedMs * playbackSpeed).roundToLong()).coerceAtLeast(0L)
    }
}

/**
 * Beatsync-inspired real-time Group Audio & Video Synchronized Playback Engine.
 *
 * Key Mechanisms:
 * 1. Pre-Buffered Media Delivery: Video/Audio is streamed via HTTP 206 Byte-Range requests
 *    (`/api/syncplay/stream`) and cached ahead of time in each peer's buffer.
 * 2. NTP-style WebSocket Clock Sync: Peers send `PING` with `clientSendMs`; server replies immediately
 *    with `serverRecvMs` and `serverSendMs` so every client computes its exact clock offset and RTT.
 * 3. Scheduled Future Execution (`executeAtServerEpochMs`): When Host or an authorized Peer triggers
 *    `PLAY`, `PAUSE`, `SEEK`, or `SPEED`, the server schedules execution at `serverNow + leadTimeMs`
 *    (e.g. 80–160ms ahead, or adaptive to peer RTT). All devices pre-seek during the lead window and
 *    fire playback at the exact same synchronized millisecond.
 * 4. Mandatory Passcode Authentication & Host-Controlled Space (with optional Guest Co-Control).
 */
object SyncPlayManager {
    private const val TAG = "SyncPlayManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SyncPlayEngine"))
    private val mainHandler = Handler(Looper.getMainLooper())

    fun generatePin(): String {
        val code = (100000..999999).random()
        return code.toString()
    }

    private val _roomState = MutableStateFlow(SyncPlayRoomState())
    val roomState: StateFlow<SyncPlayRoomState> = _roomState.asStateFlow()

    private data class ConnectedWsPeer(
        val peerId: String,
        var displayName: String,
        val clientIp: String,
        val session: WebSocketSession,
        var rttMs: Long = 6L,
        var clockOffsetMs: Long = 0L,
        var isBufferedReady: Boolean = false,
        var isAuthenticated: Boolean = false,
        var lastSeenEpochMs: Long = System.currentTimeMillis()
    )

    private val wsPeers = ConcurrentHashMap<String, ConnectedWsPeer>()

    /**
     * Optional callback for an in-app Host Video/Audio Player to receive scheduled commands
     * with exact millisecond delay.
     */
    @Volatile
    var hostPlayerCommandListener: ((action: String, positionMs: Long, speed: Float, delayMs: Long) -> Unit)? = null

    /**
     * Optional observer notified whenever the SyncPlay room activates, updates, or stops
     * (used by OmniOSStateEngine to inject/update/remove the locked fullscreen SyncPlay window).
     */
    @Volatile
    var onRoomStateChangedListener: ((SyncPlayRoomState) -> Unit)? = null

    init {
        scope.launch {
            while (isActive) {
                delay(3000L)
                val now = System.currentTimeMillis()
                var changed = false
                val iter = wsPeers.entries.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    if (now - entry.value.lastSeenEpochMs > 45_000L) {
                        iter.remove()
                        changed = true
                    }
                }
                if (changed) {
                    syncPeersToState()
                }
                if (_roomState.value.isActive && wsPeers.isNotEmpty()) {
                    broadcastStateFrame("HEARTBEAT")
                }
            }
        }
    }

    fun isVideoFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")
    }

    fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus")
    }

    fun isSupportedMedia(file: File): Boolean = file.isFile && (isVideoFile(file) || isAudioFile(file))

    /**
     * Starts or updates a SyncPlay session with the selected audio or video file.
     * Enforces a mandatory room password (generating a 6-digit PIN if blank) so no guest can join without a password.
     */
    fun loadMediaFile(
        context: Context,
        file: File,
        passcode: String = _roomState.value.passcode,
        allowGuestControl: Boolean = _roomState.value.allowGuestControl,
        autoStartServer: Boolean = true
    ) {
        if (!file.exists() || !file.isFile) return
        val isVid = isVideoFile(file)
        val now = System.currentTimeMillis()
        val effectivePasscode = passcode.trim().ifBlank { generatePin() }

        // Ensure file is accessible in FtpBucketManager if in BATCH_FILES mode
        try {
            FtpBucketManager.initialize(context)
            FtpBucketManager.addItem(context, file)
        } catch (_: Exception) {}

        val serverSnapshot = ServerManager.state.value
        if (autoStartServer && !serverSnapshot.isCastingRunning) {
            if (!serverSnapshot.isFtpRunning || !serverSnapshot.isHttpTransportRunning) {
                ServerManager.startCoordinated(context, startHttp = true, startFtp = true)
            }
        }

        val eventMsg = "${timeStamp()} Host loaded \"${file.name}\" (${formatBytes(file.length())}) • PIN Protected"
        _roomState.update { current ->
            current.copy(
                isActive = true,
                mediaFilePath = file.absolutePath,
                mediaFileName = file.name,
                mediaSizeBytes = file.length(),
                isVideo = isVid,
                isPlaying = false,
                basePositionMs = 0L,
                stateUpdatedAtServerEpochMs = now,
                playbackSpeed = 1.0f,
                lastAction = "LOAD",
                lastActor = "Host",
                executeAtServerEpochMs = now + current.scheduleLeadTimeMs,
                passcode = effectivePasscode,
                allowGuestControl = allowGuestControl,
                recentEvents = (listOf(eventMsg) + current.recentEvents).take(25)
            )
        }

        // If audio file and host audio sync is enabled, prepare it in AudioPlayerManager
        if (!isVid && _roomState.value.syncHostAudioPlayer) {
            mainHandler.post {
                try {
                    val univ = file.toUniversal()
                    AudioPlayerManager.playTrack(context, univ, listOf(univ))
                    if (AudioPlayerManager.isPlaying) {
                        AudioPlayerManager.togglePlayPause()
                    }
                    AudioPlayerManager.seekTo(0L)
                } catch (e: Exception) {
                    Log.w(TAG, "Host audio prep warning: ${e.message}")
                }
            }
        }

        onRoomStateChangedListener?.invoke(_roomState.value)
        scope.launch {
            broadcastStateFrame("LOAD")
        }
    }

    /**
     * Stops the active SyncPlay session and cleanly shuts down both the FTP and HTTP servers.
     */
    fun stopSyncPlayAndServer(context: Context) {
        stopSession(clearMediaHistory = true)
        ServerManager.stopCoordinated(context, graceful = true)
    }

    fun updateRoomSecurity(
        passcode: String,
        allowGuestControl: Boolean,
        scheduleLeadTimeMs: Long = _roomState.value.scheduleLeadTimeMs,
        syncHostAudioPlayer: Boolean = _roomState.value.syncHostAudioPlayer
    ) {
        val cleanPass = passcode.trim().ifBlank {
            _roomState.value.passcode.ifBlank { generatePin() }
        }
        val passChanged = cleanPass != _roomState.value.passcode
        if (passChanged) {
            wsPeers.values.forEach { peer ->
                peer.isAuthenticated = false
            }
        }
        _roomState.update { current ->
            current.copy(
                passcode = cleanPass,
                allowGuestControl = allowGuestControl,
                scheduleLeadTimeMs = scheduleLeadTimeMs.coerceIn(10L, 1000L),
                syncHostAudioPlayer = syncHostAudioPlayer
            )
        }
        syncPeersToState()
        scope.launch {
            broadcastStateFrame("CONFIG")
        }
    }

    /**
     * Stops the active SyncPlay session and completely removes the previously loaded media file
     * from SyncPlay state/history so the user must select a file fresh on next launch.
     */
    fun stopSession(clearMediaHistory: Boolean = true) {
        _roomState.update { current ->
            if (clearMediaHistory) {
                current.copy(
                    isActive = false,
                    mediaFilePath = "",
                    mediaFileName = "",
                    mediaSizeBytes = 0L,
                    isPlaying = false,
                    basePositionMs = 0L,
                    lastAction = "IDLE",
                    lastActor = "Host",
                    executeAtServerEpochMs = 0L,
                    recentEvents = emptyList()
                )
            } else {
                current.copy(
                    isActive = false,
                    isPlaying = false,
                    lastAction = "STOP",
                    recentEvents = (listOf("${timeStamp()} Host ended SyncPlay session") + current.recentEvents).take(25)
                )
            }
        }
        onRoomStateChangedListener?.invoke(_roomState.value)
        scope.launch {
            broadcastStateFrame("STOP")
        }
    }

    /**
     * Triggers a synchronized transport command (`PLAY`, `PAUSE`, `SEEK`, `SPEED`) scheduled
     * at `now + effectiveLeadMs` so Host and all remote browsers/apps execute simultaneously.
     */
    fun triggerScheduledCommand(
        context: Context?,
        action: String,
        targetPositionMs: Long? = null,
        speed: Float? = null,
        actorName: String = "Host",
        providedPasscode: String? = null
    ): Boolean {
        val snapshot = _roomState.value
        if (!snapshot.isActive) return false

        val isHost = actorName.equals("Host", ignoreCase = true)
        if (!isHost) {
            if (!snapshot.allowGuestControl) return false
            if (snapshot.requiresPasscode && providedPasscode != snapshot.passcode) return false
        }

        val now = System.currentTimeMillis()
        val maxHalfRtt = wsPeers.values.maxOfOrNull { (it.rttMs / 2L) + 25L } ?: 40L
        val effectiveLeadMs = max(snapshot.scheduleLeadTimeMs, maxHalfRtt).coerceIn(10L, 600L)
        val executeAt = now + effectiveLeadMs

        val resolvedSpeed = (speed ?: snapshot.playbackSpeed).coerceIn(0.25f, 3.0f)
        val resolvedPos = (targetPositionMs ?: snapshot.currentPositionMs(now)).coerceAtLeast(0L)
        val resolvedPlaying = when (action.uppercase(Locale.ROOT)) {
            "PLAY" -> true
            "PAUSE" -> false
            else -> snapshot.isPlaying
        }

        val eventSummary = when (action.uppercase(Locale.ROOT)) {
            "PLAY" -> "${timeStamp()} $actorName ▶ PLAY @ ${formatTimestamp(resolvedPos)} (sync +${effectiveLeadMs}ms)"
            "PAUSE" -> "${timeStamp()} $actorName ⏸ PAUSE @ ${formatTimestamp(resolvedPos)}"
            "SEEK" -> "${timeStamp()} $actorName ⏩ SEEK → ${formatTimestamp(resolvedPos)}"
            "SPEED" -> "${timeStamp()} $actorName ⚡ SPEED → ${resolvedSpeed}x"
            else -> "${timeStamp()} $actorName $action"
        }

        _roomState.update { current ->
            current.copy(
                isPlaying = resolvedPlaying,
                basePositionMs = resolvedPos,
                stateUpdatedAtServerEpochMs = now,
                playbackSpeed = resolvedSpeed,
                lastAction = action.uppercase(Locale.ROOT),
                lastActor = actorName,
                executeAtServerEpochMs = executeAt,
                recentEvents = (listOf(eventSummary) + current.recentEvents).take(25)
            )
        }

        scheduleHostLocalPlayback(
            context = context,
            action = action.uppercase(Locale.ROOT),
            positionMs = resolvedPos,
            speed = resolvedSpeed,
            delayMs = effectiveLeadMs
        )

        scope.launch {
            broadcastStateFrame("COMMAND")
        }
        return true
    }

    private fun scheduleHostLocalPlayback(
        context: Context?,
        action: String,
        positionMs: Long,
        speed: Float,
        delayMs: Long
    ) {
        val state = _roomState.value
        hostPlayerCommandListener?.invoke(action, positionMs, speed, delayMs)

        if (!state.isVideo && state.syncHostAudioPlayer) {
            mainHandler.post {
                try {
                    val file = File(state.mediaFilePath)
                    if (AudioPlayerManager.currentTrack?.absolutePath != file.absolutePath && context != null && file.exists()) {
                        val univ = file.toUniversal()
                        AudioPlayerManager.playTrack(context, univ, listOf(univ))
                        if (AudioPlayerManager.isPlaying) {
                            AudioPlayerManager.togglePlayPause()
                        }
                    }
                    AudioPlayerManager.setSpeed(speed)
                    AudioPlayerManager.seekTo(positionMs)
                } catch (_: Exception) {}
            }
            mainHandler.postDelayed({
                try {
                    when (action) {
                        "PLAY" -> {
                            if (!AudioPlayerManager.isPlaying) {
                                AudioPlayerManager.togglePlayPause()
                            }
                        }
                        "PAUSE" -> {
                            if (AudioPlayerManager.isPlaying) {
                                AudioPlayerManager.togglePlayPause()
                            }
                            AudioPlayerManager.seekTo(positionMs)
                        }
                        "SEEK" -> {
                            AudioPlayerManager.seekTo(positionMs)
                        }
                        "SPEED" -> {
                            AudioPlayerManager.setSpeed(speed)
                        }
                    }
                } catch (_: Exception) {}
            }, delayMs.coerceAtLeast(2L))
        }
    }

    suspend fun handleWebSocketSession(
        peerId: String,
        clientIp: String,
        initialName: String,
        session: WebSocketSession,
        appContext: Context
    ) {
        val currentPass = _roomState.value.passcode
        val peer = ConnectedWsPeer(
            peerId = peerId,
            displayName = initialName.ifBlank { "Peer-${peerId.takeLast(4)}" },
            clientIp = clientIp,
            session = session,
            isAuthenticated = currentPass.isEmpty()
        )
        wsPeers[peerId] = peer
        addEvent("${timeStamp()} ${peer.displayName} ($clientIp) joined SyncPlay room")
        syncPeersToState()

        try {
            session.send(Frame.Text(buildStateJsonInternal("WELCOME", peer).toString()))
        } catch (_: Exception) {}

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleIncomingWsMessage(peer, text, appContext)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SyncPlay WS closed for ${peer.displayName}: ${e.message}")
        } finally {
            wsPeers.remove(peerId)
            addEvent("${timeStamp()} ${peer.displayName} left SyncPlay room")
            syncPeersToState()
        }
    }

    private suspend fun handleIncomingWsMessage(
        peer: ConnectedWsPeer,
        rawJson: String,
        appContext: Context
    ) {
        val serverRecvMs = System.currentTimeMillis()
        peer.lastSeenEpochMs = serverRecvMs
        try {
            val obj = JSONObject(rawJson)
            when (obj.optString("type").uppercase(Locale.ROOT)) {
                "PING" -> {
                    val clientSendMs = obj.optLong("clientSendMs", serverRecvMs)
                    val reportedRtt = when {
                        obj.has("lastRttMs") -> obj.optLong("lastRttMs", peer.rttMs)
                        obj.has("rttMs") -> obj.optLong("rttMs", peer.rttMs)
                        else -> peer.rttMs
                    }
                    val reportedOffset = when {
                        obj.has("lastOffsetMs") -> obj.optLong("lastOffsetMs", peer.clockOffsetMs)
                        obj.has("clockOffsetMs") -> obj.optLong("clockOffsetMs", peer.clockOffsetMs)
                        else -> peer.clockOffsetMs
                    }
                    val bufferReady = obj.optBoolean("bufferReady", peer.isBufferedReady)

                    peer.rttMs = reportedRtt.coerceIn(0L, 10_000L)
                    peer.clockOffsetMs = reportedOffset
                    peer.isBufferedReady = bufferReady
                    syncPeersToState()

                    val pong = JSONObject().apply {
                        put("type", "PONG")
                        put("clientSendMs", clientSendMs)
                        put("serverRecvMs", serverRecvMs)
                        put("serverSendMs", System.currentTimeMillis())
                    }
                    peer.session.send(Frame.Text(pong.toString()))
                }

                "AUTH" -> {
                    val code = obj.optString("passcode", "").trim()
                    val name = obj.optString("name", "").trim()
                    if (name.isNotBlank()) {
                        peer.displayName = name.take(28)
                    }
                    val roomPass = _roomState.value.passcode
                    val ok = roomPass.isEmpty() || code == roomPass
                    peer.isAuthenticated = ok
                    syncPeersToState()

                    val reply = JSONObject().apply {
                        put("type", "AUTH_RESULT")
                        put("authenticated", ok)
                        put("message", if (ok) "Passcode verified! Sync controls unlocked." else "Invalid passcode.")
                    }
                    peer.session.send(Frame.Text(reply.toString()))
                    if (ok) {
                        peer.session.send(Frame.Text(buildStateJsonInternal("SYNC", peer).toString()))
                    }
                }

                "BUFFER_STATUS" -> {
                    peer.isBufferedReady = obj.optBoolean("ready", true)
                    val name = obj.optString("name", "").trim()
                    if (name.isNotBlank()) peer.displayName = name.take(28)
                    syncPeersToState()
                    broadcastStateFrame("PEERS")
                }

                "COMMAND" -> {
                    val action = obj.optString("action", "PLAY").uppercase(Locale.ROOT)
                    val targetPos = if (obj.has("positionMs")) obj.optLong("positionMs") else null
                    val speed = if (obj.has("speed")) obj.optDouble("speed", 1.0).toFloat() else null
                    val pass = obj.optString("passcode", "").trim()

                    val roomPass = _roomState.value.passcode
                    if (roomPass.isNotEmpty() && pass == roomPass) {
                        peer.isAuthenticated = true
                    }

                    if (!_roomState.value.allowGuestControl) {
                        val err = JSONObject().apply {
                            put("type", "ERROR")
                            put("message", "Host has disabled guest playback control.")
                        }
                        peer.session.send(Frame.Text(err.toString()))
                        return
                    }

                    if (roomPass.isNotEmpty() && !peer.isAuthenticated) {
                        val err = JSONObject().apply {
                            put("type", "ERROR")
                            put("message", "Enter the room passcode first to control group playback.")
                        }
                        peer.session.send(Frame.Text(err.toString()))
                        return
                    }

                    triggerScheduledCommand(
                        context = appContext,
                        action = action,
                        targetPositionMs = targetPos,
                        speed = speed,
                        actorName = peer.displayName,
                        providedPasscode = if (peer.isAuthenticated) roomPass else pass
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Invalid WS payload from ${peer.clientIp}: ${e.message}")
        }
    }

    private suspend fun broadcastStateFrame(eventType: String) {
        for (peer in wsPeers.values) {
            try {
                val payload = buildStateJsonInternal(eventType, peer).toString()
                peer.session.send(Frame.Text(payload))
            } catch (_: Exception) {}
        }
    }

    fun buildStateJson(eventType: String = "STATE"): JSONObject = buildStateJsonInternal(eventType, null)

    private fun buildStateJsonInternal(eventType: String = "STATE", forPeer: ConnectedWsPeer? = null): JSONObject {
        val s = _roomState.value
        val now = System.currentTimeMillis()
        val isPeerAuthed = forPeer == null || forPeer.isAuthenticated
        return JSONObject().apply {
            put("type", eventType)
            put("serverEpochMs", now)
            put("isActive", s.isActive)
            put("mediaFileName", if (isPeerAuthed) s.mediaFileName else "🔒 Locked SyncPlay Space (Enter Passcode to Join)")
            put("mediaSizeBytes", if (isPeerAuthed) s.mediaSizeBytes else 0L)
            put("isVideo", s.isVideo)
            put(
                "streamUrl",
                if (isPeerAuthed && s.isActive && s.mediaFilePath.isNotBlank()) {
                    "/api/syncplay/stream?t=${s.mediaSizeBytes}&pin=${s.passcode.hashCode()}"
                } else {
                    ""
                }
            )
            put("isPlaying", if (isPeerAuthed) s.isPlaying else false)
            put("basePositionMs", if (isPeerAuthed) s.basePositionMs else 0L)
            put("livePositionMs", if (isPeerAuthed) s.currentPositionMs(now) else 0L)
            put("stateUpdatedAtServerEpochMs", s.stateUpdatedAtServerEpochMs)
            put("playbackSpeed", s.playbackSpeed.toDouble())
            put("lastAction", s.lastAction)
            put("lastActor", s.lastActor)
            put("executeAtServerEpochMs", s.executeAtServerEpochMs)
            put("scheduleLeadTimeMs", s.scheduleLeadTimeMs)
            put("requiresPasscode", true)
            put("allowGuestControl", s.allowGuestControl)
            if (forPeer != null) {
                put("authenticated", forPeer.isAuthenticated)
                put("peerId", forPeer.peerId)
            }

            val peersArr = JSONArray()
            s.connectedPeers.forEach { p ->
                peersArr.put(JSONObject().apply {
                    put("peerId", p.peerId)
                    put("name", p.displayName)
                    put("ip", p.clientIp)
                    put("rttMs", p.rttMs)
                    put("clockOffsetMs", p.clockOffsetMs)
                    put("ready", p.isBufferedReady)
                    put("authenticated", p.isAuthenticated)
                })
            }
            put("peers", peersArr)

            val eventsArr = JSONArray()
            s.recentEvents.take(12).forEach { eventsArr.put(it) }
            put("events", eventsArr)
        }
    }

    private fun syncPeersToState() {
        val list = wsPeers.values.map {
            SyncPlayPeerInfo(
                peerId = it.peerId,
                displayName = it.displayName,
                clientIp = it.clientIp,
                rttMs = it.rttMs,
                clockOffsetMs = it.clockOffsetMs,
                isBufferedReady = it.isBufferedReady,
                isAuthenticated = it.isAuthenticated,
                lastSeenEpochMs = it.lastSeenEpochMs
            )
        }.sortedBy { it.displayName }

        _roomState.update { it.copy(connectedPeers = list) }
    }

    private fun addEvent(msg: String) {
        _roomState.update { current ->
            current.copy(recentEvents = (listOf(msg) + current.recentEvents).take(25))
        }
    }

    private fun timeStamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSecs = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
