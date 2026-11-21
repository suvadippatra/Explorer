package com.troikoss.continuum_explorer.server.omnios

import android.content.Context
import android.util.Log
import com.troikoss.continuum_explorer.server.ServerManager
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class OmniOSQrRole {
    HOST,
    RECEIVER
}

data class OmniOSQrPayload(
    val role: OmniOSQrRole,
    val ip: String,
    val port: Int,
    val deviceName: String,
    val castUrl: String
)

enum class ReceiverInviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}

/**
 * Incoming App-to-App cast request awaiting permission on the Receiver device.
 */
data class IncomingCastInvite(
    val inviteId: String,
    val hostIp: String,
    val hostPort: Int,
    val hostDeviceName: String,
    val castUrl: String,
    val initiatedLocally: Boolean = false,
    val receivedAtMs: Long = System.currentTimeMillis(),
    val status: ReceiverInviteStatus = ReceiverInviteStatus.PENDING
)

/**
 * Approved App-to-App Receiver session rendering the Host's OmniOS desktop fullscreen.
 */
data class ActiveReceiverSession(
    val hostIp: String,
    val hostPort: Int,
    val hostDeviceName: String,
    val castUrl: String,
    val startedAtMs: Long = System.currentTimeMillis()
)

sealed class OutboundInviteState {
    data object Idle : OutboundInviteState()
    data class Sending(val targetName: String, val targetIp: String) : OutboundInviteState()
    data class WaitingForReceiver(val inviteId: String, val targetName: String, val targetIp: String) : OutboundInviteState()
    data class Accepted(val targetName: String) : OutboundInviteState()
    data class Declined(val targetName: String) : OutboundInviteState()
    data class Failed(val message: String) : OutboundInviteState()
}

/**
 * Two-Way QR Pairing & Receiver-Permission Manager for OmniOS App-to-App Casting.
 *
 * Permission Policy:
 * - Built-In Display Cast: No permission needed.
 * - Wired Cast (USB-C / HDMI): No permission needed.
 * - Wireless Display (Miracast): No approval needed.
 * - Web Cast (LAN Browser): No approval needed.
 * - App-to-App Cast: Always asks permission of the **Receiver** before opening the
 *   fullscreen OmniOS display surface.
 */
object OmniOSPairingManager {
    private const val TAG = "OmniOSPairing"
    const val DEFAULT_RECEIVER_LISTENER_PORT = 8091

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("OmniOSPairing"))
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var receiverEngine: ApplicationEngine? = null

    @Volatile
    private var outboundPollJob: Job? = null

    private val inviteHistory = ConcurrentHashMap<String, IncomingCastInvite>()

    private val _receiverListenerPort = MutableStateFlow(DEFAULT_RECEIVER_LISTENER_PORT)
    val receiverListenerPort: StateFlow<Int> = _receiverListenerPort.asStateFlow()

    private val _isReceiverListening = MutableStateFlow(false)
    val isReceiverListening: StateFlow<Boolean> = _isReceiverListening.asStateFlow()

    private val _pendingReceiverInvites = MutableStateFlow<List<IncomingCastInvite>>(emptyList())
    val pendingReceiverInvites: StateFlow<List<IncomingCastInvite>> = _pendingReceiverInvites.asStateFlow()

    private val _activeReceiverSession = MutableStateFlow<ActiveReceiverSession?>(null)
    val activeReceiverSession: StateFlow<ActiveReceiverSession?> = _activeReceiverSession.asStateFlow()

    private val _outboundInviteState = MutableStateFlow<OutboundInviteState>(OutboundInviteState.Idle)
    val outboundInviteState: StateFlow<OutboundInviteState> = _outboundInviteState.asStateFlow()

    fun init() {
        // Web Cast, Built-in Screen, Wired Display, and Wireless Display connect immediately
        // without a host approval barrier; App-to-App Cast asks permission on the Receiver side.
        OmniOSHostServer.sessionAuthValidator = { _, _, _ ->
            CastingSessionAuthResult(CastingSessionAuthStatus.APPROVED)
        }
        OmniOSHostServer.pairingRequestHandler = { _, _ ->
            JSONObject().apply {
                put("status", "APPROVED")
                put("sessionToken", "omnios-${UUID.randomUUID().toString().take(8)}")
            }
        }
        OmniOSHostServer.pairingStatusHandler = { _ ->
            JSONObject().put("status", "APPROVED")
        }
    }

    // --- 1. TWO-WAY QR PAYLOAD CODEC ---

    fun buildHostQrPayload(port: Int = OmniOSHostServer.activePort): String {
        val ip = ServerManager.resolveLocalIpAddress()
        val deviceName = OmniOSDiscoveryManager.getLocalDeviceLabel()
        return JSONObject().apply {
            put("app", "omnios")
            put("role", "HOST")
            put("ip", ip)
            put("port", port)
            put("name", deviceName)
            put("url", "http://$ip:$port")
        }.toString()
    }

    fun buildReceiverQrPayload(): String {
        val ip = ServerManager.resolveLocalIpAddress()
        val port = _receiverListenerPort.value
        val deviceName = OmniOSDiscoveryManager.getLocalDeviceLabel()
        return JSONObject().apply {
            put("app", "omnios")
            put("role", "RECEIVER")
            put("ip", ip)
            put("port", port)
            put("name", deviceName)
        }.toString()
    }

    fun parseQrOrManualAddress(
        rawInput: String,
        defaultRole: OmniOSQrRole = OmniOSQrRole.HOST,
        defaultPort: Int = ServerManager.DEFAULT_CASTING_PORT
    ): OmniOSQrPayload? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Case A: Compact OmniOS JSON payload
        if (trimmed.startsWith("{")) {
            return try {
                val obj = JSONObject(trimmed)
                val ip = obj.optString("ip").trim()
                if (ip.isBlank()) return null
                val roleStr = obj.optString("role", defaultRole.name).uppercase()
                val role = if (roleStr == "RECEIVER") OmniOSQrRole.RECEIVER else OmniOSQrRole.HOST
                val fallbackPort = if (role == OmniOSQrRole.RECEIVER) DEFAULT_RECEIVER_LISTENER_PORT else defaultPort
                val port = obj.optInt("port", fallbackPort).takeIf { it in 1..65535 } ?: fallbackPort
                val name = obj.optString("name", ip).ifBlank { ip }
                val url = obj.optString("url").ifBlank { "http://$ip:$port" }
                OmniOSQrPayload(role = role, ip = ip, port = port, deviceName = name, castUrl = url)
            } catch (_: Exception) {
                null
            }
        }

        // Case B: Full HTTP/HTTPS URL (e.g., http://192.168.1.42:8080)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return try {
                val uri = URI(trimmed)
                val host = uri.host?.trim().orEmpty()
                if (host.isBlank()) return null
                val port = if (uri.port in 1..65535) uri.port else defaultPort
                OmniOSQrPayload(
                    role = defaultRole,
                    ip = host,
                    port = port,
                    deviceName = host,
                    castUrl = "http://$host:$port"
                )
            } catch (_: Exception) {
                null
            }
        }

        // Case C: Bare IP or IP:PORT
        val parts = trimmed.split(":")
        val host = parts.firstOrNull()?.trim().orEmpty()
        if (host.isBlank() || host.contains(" ")) return null
        val fallbackPort = if (defaultRole == OmniOSQrRole.RECEIVER) DEFAULT_RECEIVER_LISTENER_PORT else defaultPort
        val port = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: fallbackPort
        return OmniOSQrPayload(
            role = defaultRole,
            ip = host,
            port = port,
            deviceName = host,
            castUrl = "http://$host:$port"
        )
    }

    // --- 2. RECEIVER-SIDE INVITE LISTENER & PERMISSION PROMPT ---

    fun startReceiverListener(context: Context) {
        if (receiverEngine != null) {
            _isReceiverListening.value = true
            return
        }
        val appContext = context.applicationContext
        scope.launch {
            if (receiverEngine != null) return@launch
            var boundEngine: ApplicationEngine? = null
            var boundPort = DEFAULT_RECEIVER_LISTENER_PORT

            for (candidatePort in DEFAULT_RECEIVER_LISTENER_PORT..(DEFAULT_RECEIVER_LISTENER_PORT + 8)) {
                try {
                    val server = embeddedServer(Netty, port = candidatePort, host = "0.0.0.0") {
                        install(CORS) {
                            anyHost()
                            allowMethod(HttpMethod.Get)
                            allowMethod(HttpMethod.Post)
                            allowMethod(HttpMethod.Options)
                        }
                        routing {
                            get("/api/receiver/ping") {
                                val res = JSONObject().apply {
                                    put("ready", true)
                                    put("role", "RECEIVER")
                                    put("deviceName", OmniOSDiscoveryManager.getLocalDeviceLabel())
                                    put("ip", ServerManager.resolveLocalIpAddress())
                                    put("port", candidatePort)
                                }
                                call.respondText(res.toString(), ContentType.Application.Json)
                            }

                            post("/api/receiver/invite") {
                                val remoteIp = call.request.origin.remoteHost
                                val body = try { JSONObject(call.receiveText()) } catch (_: Exception) { JSONObject() }
                                val inviteId = body.optString("inviteId").ifBlank { UUID.randomUUID().toString() }
                                val hostIp = body.optString("hostIp").ifBlank { remoteIp }
                                val hostPort = body.optInt("hostPort", ServerManager.DEFAULT_CASTING_PORT)
                                val hostName = body.optString("hostDeviceName").ifBlank { hostIp }
                                val castUrl = body.optString("castUrl").ifBlank { "http://$hostIp:$hostPort/?mode=app_receiver" }

                                val invite = IncomingCastInvite(
                                    inviteId = inviteId,
                                    hostIp = hostIp,
                                    hostPort = hostPort,
                                    hostDeviceName = hostName,
                                    castUrl = castUrl,
                                    initiatedLocally = false,
                                    status = ReceiverInviteStatus.PENDING
                                )
                                enqueueReceiverPermissionRequest(invite)
                                val res = JSONObject().apply {
                                    put("success", true)
                                    put("inviteId", inviteId)
                                    put("status", ReceiverInviteStatus.PENDING.name)
                                }
                                call.respondText(res.toString(), ContentType.Application.Json)
                            }

                            get("/api/receiver/invite/{inviteId}") {
                                val id = call.parameters["inviteId"].orEmpty()
                                val item = inviteHistory[id]
                                if (item == null) {
                                    call.respondText(
                                        JSONObject().put("status", ReceiverInviteStatus.EXPIRED.name).toString(),
                                        ContentType.Application.Json,
                                        HttpStatusCode.NotFound
                                    )
                                } else {
                                    val res = JSONObject().apply {
                                        put("inviteId", item.inviteId)
                                        put("status", item.status.name)
                                    }
                                    call.respondText(res.toString(), ContentType.Application.Json)
                                }
                            }
                        }
                    }
                    server.start(wait = false)
                    boundEngine = server
                    boundPort = candidatePort
                    break
                } catch (e: Throwable) {
                    Log.w(TAG, "Receiver listener port $candidatePort busy: ${e.message}")
                }
            }

            if (boundEngine != null) {
                receiverEngine = boundEngine
                _receiverListenerPort.value = boundPort
                _isReceiverListening.value = true
                OmniOSDiscoveryManager.registerReceiverService(appContext, boundPort)
                Log.i(TAG, "Receiver Invite Listener active on :$boundPort")
            }
        }
    }

    fun stopReceiverListener() {
        OmniOSDiscoveryManager.unregisterReceiverService()
        val current = receiverEngine
        receiverEngine = null
        _isReceiverListening.value = false
        scope.launch {
            try {
                current?.stop(gracePeriodMillis = 150L, timeoutMillis = 400L)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Triggers the Receiver permission confirmation dialog when the Receiver user taps
     * a discovered Host, scans a Host QR code, or enters a Host IP manually.
     */
    fun requestLocalReceiverPermission(
        hostIp: String,
        hostPort: Int,
        hostDeviceName: String
    ) {
        val cleanIp = hostIp.trim()
        if (cleanIp.isEmpty()) return
        val invite = IncomingCastInvite(
            inviteId = "local-${UUID.randomUUID().toString().take(8)}",
            hostIp = cleanIp,
            hostPort = hostPort,
            hostDeviceName = hostDeviceName.ifBlank { cleanIp },
            castUrl = "http://$cleanIp:$hostPort/?mode=app_receiver",
            initiatedLocally = true,
            status = ReceiverInviteStatus.PENDING
        )
        enqueueReceiverPermissionRequest(invite)
    }

    private fun enqueueReceiverPermissionRequest(invite: IncomingCastInvite) {
        inviteHistory[invite.inviteId] = invite
        _pendingReceiverInvites.update { list ->
            list.filterNot { it.hostIp == invite.hostIp && it.hostPort == invite.hostPort } + invite
        }
    }

    /**
     * Called when the Receiver user taps "Allow" on the permission prompt.
     * Opens the fullscreen DeX-style OmniOS Receiver display surface.
     */
    fun acceptReceiverInvite(inviteId: String) {
        val existing = inviteHistory[inviteId] ?: _pendingReceiverInvites.value.firstOrNull { it.inviteId == inviteId }
            ?: return
        val accepted = existing.copy(status = ReceiverInviteStatus.ACCEPTED)
        inviteHistory[inviteId] = accepted
        _pendingReceiverInvites.update { list -> list.filterNot { it.inviteId == inviteId } }
        _activeReceiverSession.value = ActiveReceiverSession(
            hostIp = accepted.hostIp,
            hostPort = accepted.hostPort,
            hostDeviceName = accepted.hostDeviceName,
            castUrl = accepted.castUrl
        )
    }

    /**
     * Called when the Receiver user taps "Decline" on the permission prompt.
     */
    fun declineReceiverInvite(inviteId: String) {
        val existing = inviteHistory[inviteId]
        if (existing != null) {
            inviteHistory[inviteId] = existing.copy(status = ReceiverInviteStatus.DECLINED)
        }
        _pendingReceiverInvites.update { list -> list.filterNot { it.inviteId == inviteId } }
    }

    fun disconnectReceiverSession() {
        _activeReceiverSession.value = null
    }

    // --- 3. HOST-SIDE OUTBOUND APP-TO-APP CAST INVITE ---

    /**
     * Sends an App-to-App cast request from this Host to a Receiver device on the same LAN.
     * The Receiver device will prompt its user for permission (Allow / Decline).
     */
    fun sendCastInviteToReceiver(
        receiverIp: String,
        receiverPort: Int = DEFAULT_RECEIVER_LISTENER_PORT,
        receiverName: String = receiverIp,
        hostPort: Int = OmniOSHostServer.activePort
    ) {
        outboundPollJob?.cancel()
        val targetIp = receiverIp.trim()
        if (targetIp.isEmpty()) {
            _outboundInviteState.value = OutboundInviteState.Failed("Invalid receiver IP")
            return
        }

        outboundPollJob = scope.launch {
            _outboundInviteState.value = OutboundInviteState.Sending(receiverName, targetIp)
            val inviteId = UUID.randomUUID().toString()
            val myIp = ServerManager.resolveLocalIpAddress()
            val myName = OmniOSDiscoveryManager.getLocalDeviceLabel()
            val payload = JSONObject().apply {
                put("inviteId", inviteId)
                put("hostIp", myIp)
                put("hostPort", hostPort)
                put("hostDeviceName", myName)
                put("castUrl", "http://$myIp:$hostPort/?mode=app_receiver")
            }.toString()

            try {
                val postReq = Request.Builder()
                    .url("http://$targetIp:$receiverPort/api/receiver/invite")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                val postOk = httpClient.newCall(postReq).execute().use { resp -> resp.isSuccessful }
                if (!postOk) {
                    _outboundInviteState.value = OutboundInviteState.Failed("Receiver refused request")
                    return@launch
                }

                _outboundInviteState.value = OutboundInviteState.WaitingForReceiver(
                    inviteId = inviteId,
                    targetName = receiverName,
                    targetIp = targetIp
                )

                val deadlineMs = System.currentTimeMillis() + 60_000L
                while (isActive && System.currentTimeMillis() < deadlineMs) {
                    delay(750L)
                    val pollReq = Request.Builder()
                        .url("http://$targetIp:$receiverPort/api/receiver/invite/$inviteId")
                        .get()
                        .build()

                    val status = try {
                        httpClient.newCall(pollReq).execute().use { resp ->
                            val raw = resp.body?.string().orEmpty()
                            JSONObject(raw).optString("status", "PENDING")
                        }
                    } catch (_: Exception) {
                        "PENDING"
                    }

                    when (status) {
                        ReceiverInviteStatus.ACCEPTED.name -> {
                            _outboundInviteState.value = OutboundInviteState.Accepted(receiverName)
                            return@launch
                        }
                        ReceiverInviteStatus.DECLINED.name -> {
                            _outboundInviteState.value = OutboundInviteState.Declined(receiverName)
                            return@launch
                        }
                        ReceiverInviteStatus.EXPIRED.name -> {
                            _outboundInviteState.value = OutboundInviteState.Failed("Request expired")
                            return@launch
                        }
                    }
                }

                _outboundInviteState.value = OutboundInviteState.Failed("Receiver did not respond")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reach receiver $targetIp:$receiverPort: ${e.message}")
                _outboundInviteState.value = OutboundInviteState.Failed(
                    e.message?.take(64) ?: "Unable to reach receiver"
                )
            }
        }
    }

    fun resetOutboundInviteState() {
        outboundPollJob?.cancel()
        outboundPollJob = null
        _outboundInviteState.value = OutboundInviteState.Idle
    }
}
