package com.troikoss.continuum_explorer.server.omnios

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.server.ContinuumHubService
import com.troikoss.continuum_explorer.server.OmniOSCastingBridge
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.ui.converter.FileConverterDispatcher
import com.troikoss.continuum_explorer.ui.converter.OutputDestinationMode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class CastingSessionAuthStatus {
    APPROVED,
    PENDING,
    DENIED
}

data class CastingSessionAuthResult(
    val status: CastingSessionAuthStatus,
    val message: String = "",
    val requestId: String? = null
)

/**
 * Embedded Ktor Netty Server powering OmniOS Casting mode.
 *
 * Capabilities:
 * - Serves bundled OmniOS SDUI web client assets from `assets/omnios/`
 * - WebSocket `/ws/sdui` for authoritative state deltas, 10-byte binary touch input, and live Admin push
 * - WebSocket `/ws/syncplay` + `/api/syncplay/...` sharing the exact same [SyncPlayManager] engine
 * - Sandboxed shared file browser & download API (`/api/files/...`) for host-selected files/folders
 * - Live My Suite Mini-App host endpoints (`/api/suite/...`) for Markdown, Code, Table, Photo, and File Converter
 * - Pluggable Host Approval gate invoked before any remote client receives OmniOS desktop state
 */
object OmniOSHostServer : OmniOSCastingBridge {
    private const val TAG = "OmniOSHostServer"

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("OmniOSHostServer"))

    @Volatile
    private var engine: ApplicationEngine? = null

    @Volatile
    private var appContextRef: Context? = null

    @Volatile
    private var metricsJob: Job? = null

    private data class ClientWsRecord(
        val sessionId: String,
        val clientIp: String,
        val token: String?,
        val session: WebSocketSession,
        @Volatile var approved: Boolean
    )

    private val activeSessions = ConcurrentHashMap<String, ClientWsRecord>()

    /**
     * Pluggable approval validator hooked by Phase 3's OmniOSPairingManager.
     * Localhost/loopback sessions (Built-In Screen) are always immediately approved.
     */
    @Volatile
    var sessionAuthValidator: ((token: String?, clientIp: String, deviceName: String) -> CastingSessionAuthResult)? = null

    /**
     * Pluggable pairing REST handler hooked by Phase 3's OmniOSPairingManager.
     */
    @Volatile
    var pairingRequestHandler: ((bodyJson: JSONObject, clientIp: String) -> JSONObject)? = null

    @Volatile
    var pairingStatusHandler: ((requestId: String) -> JSONObject)? = null

    @Volatile
    var activePort: Int = ServerManager.DEFAULT_CASTING_PORT
        private set

    override val isRunning: Boolean
        get() = engine != null

    override fun getActiveConnectionsCount(): Int = activeSessions.size

    init {
        OmniOSAdminConfigStore.onConfigChangedListener = { updated ->
            OmniOSStateEngine.handleAdminConfigUpdated(updated)
            OmniOSAudioRoutingManager.syncRouteFromAdmin(updated.audioRoute)
        }
        OmniOSStateEngine.onDeltaBroadcast = { deltaArr ->
            broadcastToApproved(
                JSONObject().apply {
                    put("op", 0x20) // STATE_DELTA
                    put("delta", deltaArr)
                }.toString()
            )
        }
        OmniOSStateEngine.onAdminConfigBroadcast = { configJson ->
            broadcastToApproved(
                JSONObject().apply {
                    put("op", 0x30) // ADMIN_CONFIG
                    put("config", configJson)
                }.toString()
            )
        }
    }

    /**
     * Called by PairingManager when Host approves a pending session token so any waiting
     * WebSocket unlocks immediately without requiring a manual page reload.
     */
    fun notifySessionApproved(token: String) {
        serverScope.launch {
            val fullSync = JSONObject().apply {
                put("op", 0x21)
                put("state", OmniOSStateEngine.buildFullSyncJson())
            }.toString()
            for (rec in activeSessions.values) {
                if (!rec.approved && rec.token == token) {
                    rec.approved = true
                    try {
                        rec.session.send(Frame.Text(fullSync))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Called by PairingManager when Host denies a pending session token.
     */
    fun notifySessionDenied(token: String) {
        serverScope.launch {
            val denyPayload = JSONObject().apply {
                put("op", 0x50) // DISCONNECT_BARRIER
                put("reason", "denied")
                put("message", "Connection denied by Host.")
            }.toString()
            for (rec in activeSessions.values) {
                if (rec.token == token) {
                    rec.approved = false
                    try {
                        rec.session.send(Frame.Text(denyPayload))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override suspend fun startOrThrow(context: Context, port: Int) {
        if (engine != null) return
        val appContext = context.applicationContext
        appContextRef = appContext
        activePort = port
        OmniOSAdminConfigStore.init(appContext)
        OmniOSStateEngine.resetSessionState()
        OmniOSStateEngine.refreshSystemMetrics(appContext)

        val server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(12)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }

            routing {
                // --- 1. STATIC OMNIOS SDUI CLIENT ASSETS ---
                get("/") {
                    serveBundledAsset(call, appContext, "index.html", "text/html; charset=utf-8")
                }
                get("/css/{path...}") {
                    val rel = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    serveBundledAsset(call, appContext, "css/$rel", "text/css; charset=utf-8")
                }
                get("/js/{path...}") {
                    val rel = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    serveBundledAsset(call, appContext, "js/$rel", "application/javascript; charset=utf-8")
                }
                get("/assets/{path...}") {
                    val rel = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    serveBundledAsset(call, appContext, "assets/$rel", guessMimeType(rel))
                }

                // --- 2. HOST INFO & PAIRING ENDPOINTS ---
                get("/api/suite/host-info") {
                    val admin = OmniOSAdminConfigStore.settings.value
                    val info = JSONObject().apply {
                        put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                        put("ip", ServerManager.resolveLocalIpAddress())
                        put("port", activePort)
                        put("requireApproval", admin.requireHostApproval)
                        put("theme", admin.theme)
                        put("allowedApps", JSONArray(admin.allowedApps.toList()))
                    }
                    call.respondText(info.toString(), ContentType.Application.Json)
                }

                post("/api/pair/request") {
                    val clientIp = call.request.origin.remoteHost
                    val body = try { JSONObject(call.receiveText()) } catch (_: Exception) { JSONObject() }
                    val handler = pairingRequestHandler
                    if (handler != null) {
                        val res = handler(body, clientIp)
                        call.respondText(res.toString(), ContentType.Application.Json)
                    } else {
                        val token = "auto-${System.currentTimeMillis()}"
                        val res = JSONObject().apply {
                            put("status", "APPROVED")
                            put("sessionToken", token)
                        }
                        call.respondText(res.toString(), ContentType.Application.Json)
                    }
                }

                get("/api/pair/status/{requestId}") {
                    val reqId = call.parameters["requestId"].orEmpty()
                    val handler = pairingStatusHandler
                    if (handler != null) {
                        call.respondText(handler(reqId).toString(), ContentType.Application.Json)
                    } else {
                        call.respondText("""{"status":"APPROVED"}""", ContentType.Application.Json)
                    }
                }

                // --- 3. HOST ADMIN GOVERNANCE API ---
                get("/api/admin/config") {
                    call.respondText(
                        OmniOSAdminConfigStore.settings.value.toJson().toString(),
                        ContentType.Application.Json
                    )
                }

                post("/api/admin/config") {
                    try {
                        val json = JSONObject(call.receiveText())
                        if (json.has("touchInputEnabled")) {
                            OmniOSAdminConfigStore.setTouchInputEnabled(json.getBoolean("touchInputEnabled"))
                        }
                        if (json.has("theme")) {
                            OmniOSAdminConfigStore.setTheme(json.getString("theme"))
                        }
                        if (json.has("darkMode")) {
                            OmniOSAdminConfigStore.setDarkMode(json.getBoolean("darkMode"))
                        }
                        if (json.has("brightness")) {
                            OmniOSAdminConfigStore.setBrightness(json.getInt("brightness"))
                        }
                        if (json.has("audioRoute")) {
                            OmniOSAdminConfigStore.setAudioRoute(json.getString("audioRoute"))
                        }
                        if (json.has("animationDuration")) {
                            OmniOSAdminConfigStore.setAnimationDuration(json.getInt("animationDuration"))
                        }
                        if (json.has("allowedApps")) {
                            val arr = json.getJSONArray("allowedApps")
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) set.add(arr.getString(i))
                            OmniOSAdminConfigStore.setAllowedApps(set)
                        }
                        call.respondText("""{"success":true}""", ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(
                            JSONObject().put("success", false).put("error", e.message).toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                post("/api/admin/icons") {
                    try {
                        val json = JSONObject(call.receiveText())
                        val iconsObj = json.optJSONObject("icons")
                        if (iconsObj != null) {
                            val keys = iconsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                OmniOSAdminConfigStore.setCustomIcon(k, iconsObj.optString(k))
                            }
                        }
                        call.respondText("""{"success":true}""", ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(
                            JSONObject().put("success", false).put("error", e.message).toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                // --- 4. SANDBOXED SHARED FILES BROWSER & DOWNLOADS (FTP-LIKE CAPABILITY IN CASTING) ---
                get("/api/files/list") {
                    val requestedPath = call.request.queryParameters["path"]?.trim().orEmpty()
                    val result = buildSharedFilesListingJson(requestedPath)
                    call.respondText(result.toString(), ContentType.Application.Json)
                }

                get("/api/files/download") {
                    val path = call.request.queryParameters["path"]?.trim().orEmpty()
                    val file = File(path)
                    if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                        call.respond(HttpStatusCode.Forbidden, "File is not in host-shared paths")
                        return@get
                    }
                    ContinuumHubService.recordLog(
                        call.request.origin.remoteHost,
                        "CAST",
                        "DOWNLOAD",
                        file.name,
                        file.length()
                    )
                    call.respondFileWithRangeSupport(file, inline = false)
                }

                get("/api/files/raw") {
                    val path = call.request.queryParameters["path"]?.trim().orEmpty()
                    val file = File(path)
                    if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                        call.respond(HttpStatusCode.Forbidden, "File is not in host-shared paths")
                        return@get
                    }
                    call.respondFileWithRangeSupport(file, inline = true)
                }

                // --- 5. MY SUITE MINI-APPS LIVE HOST BRIDGE ---
                get("/api/suite/read") {
                    val path = call.request.queryParameters["path"]?.trim().orEmpty()
                    val file = File(path)
                    if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                        call.respondText("""{"success":false,"error":"Access denied"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@get
                    }
                    if (file.length() > 4 * 1024 * 1024) {
                        call.respondText("""{"success":false,"error":"File exceeds 4MB editor limit"}""", ContentType.Application.Json, HttpStatusCode.PayloadTooLarge)
                        return@get
                    }
                    val content = try { file.readText() } catch (e: Exception) {
                        call.respondText(JSONObject().put("success", false).put("error", e.message).toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        return@get
                    }
                    val res = JSONObject().apply {
                        put("success", true)
                        put("fileName", file.name)
                        put("filePath", file.absolutePath)
                        put("content", content)
                    }
                    call.respondText(res.toString(), ContentType.Application.Json)
                }

                post("/api/suite/save") {
                    try {
                        val body = JSONObject(call.receiveText())
                        val requestedPath = body.optString("filePath", "").trim()
                        val fileName = body.optString("fileName", "untitled.txt").trim()
                            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                            .ifBlank { "untitled.txt" }
                        val content = body.optString("content", "")
                        val toolId = body.optString("tool", "markdown_studio")

                        val targetFile: File = if (requestedPath.isNotBlank()) {
                            val candidate = File(requestedPath)
                            if (!OmniOSAdminConfigStore.isFilePermittedForCasting(candidate)) {
                                call.respondText("""{"success":false,"error":"Target file is outside host-shared paths"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                                return@post
                            }
                            candidate
                        } else {
                            val suiteDir = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                                "ContinuumSuite/OmniOS"
                            ).apply { if (!exists()) mkdirs() }
                            File(suiteDir, fileName).also {
                                OmniOSAdminConfigStore.addSharedPath(suiteDir.absolutePath)
                            }
                        }

                        targetFile.writeText(content)
                        try {
                            val (resolvedToolId, resolvedToolName) = when (toolId) {
                                "code_studio" -> "code_studio" to "Code Studio"
                                "table_studio" -> "table_studio" to "Table Studio"
                                "photo_studio" -> "photo_studio" to "Photo Studio"
                                else -> "markdown_studio" to "Markdown Studio"
                            }
                            SuiteRecentDatabase.recordFileActivity(
                                context = appContext,
                                file = targetFile,
                                editorToolId = resolvedToolId,
                                editorToolName = resolvedToolName,
                                activityType = if (requestedPath.isBlank()) SuiteActivityType.CREATED else SuiteActivityType.EDITED
                            )
                        } catch (_: Exception) {}

                        ContinuumHubService.recordLog(call.request.origin.remoteHost, "CAST", "SAVE", targetFile.name, content.length.toLong())
                        val res = JSONObject().apply {
                            put("success", true)
                            put("filePath", targetFile.absolutePath)
                            put("fileName", targetFile.name)
                        }
                        call.respondText(res.toString(), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(
                            JSONObject().put("success", false).put("error", e.message ?: "Save failed").toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }

                get("/api/suite/converter/targets") {
                    val path = call.request.queryParameters["path"]?.trim().orEmpty()
                    val file = File(path)
                    if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                        call.respondText("""{"success":false,"targets":[]}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@get
                    }
                    val item = FileConverterDispatcher.createQueueItem(file)
                    val res = JSONObject().apply {
                        put("success", item != null)
                        put("sourceExtension", item?.sourceExtension ?: file.extension)
                        put("targets", JSONArray(item?.availableTargets ?: emptyList<String>()))
                        put("defaultTarget", item?.targetFormat ?: "")
                    }
                    call.respondText(res.toString(), ContentType.Application.Json)
                }

                post("/api/suite/converter/run") {
                    try {
                        val body = JSONObject(call.receiveText())
                        val path = body.optString("path", "").trim()
                        val targetFormat = body.optString("targetFormat", "").trim().lowercase(Locale.ROOT)
                        val file = File(path)
                        if (!OmniOSAdminConfigStore.isFilePermittedForCasting(file) || !file.isFile) {
                            call.respondText("""{"success":false,"error":"Source file is not shared"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                            return@post
                        }
                        val queueItem = FileConverterDispatcher.createQueueItem(file)
                        if (queueItem == null || targetFormat !in queueItem.availableTargets) {
                            call.respondText("""{"success":false,"error":"Unsupported conversion format"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val prepared = queueItem.copy(targetFormat = targetFormat)
                        val convertedResult = FileConverterDispatcher.convertSingleItem(
                            context = appContext,
                            item = prepared,
                            destinationMode = OutputDestinationMode.CONVERTED_SUBFOLDER,
                            customFolder = null,
                            onProgress = { _, _ -> }
                        )
                        if (convertedResult.isSuccess) {
                            val outFile = convertedResult.getOrThrow()
                            outFile.parentFile?.let { OmniOSAdminConfigStore.addSharedPath(it.absolutePath) }
                            val encodedOut = URLEncoder.encode(outFile.absolutePath, "UTF-8")
                            val res = JSONObject().apply {
                                put("success", true)
                                put("outputName", outFile.name)
                                put("outputPath", outFile.absolutePath)
                                put("downloadUrl", "/api/files/download?path=$encodedOut")
                            }
                            call.respondText(res.toString(), ContentType.Application.Json)
                        } else {
                            call.respondText(
                                JSONObject().put("success", false).put("error", convertedResult.exceptionOrNull()?.message ?: "Conversion failed").toString(),
                                ContentType.Application.Json,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    } catch (e: Exception) {
                        call.respondText(
                            JSONObject().put("success", false).put("error", e.message ?: "Conversion error").toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }

                // --- 6. SYNCPLAY ENGINE BRIDGE (SAME ENGINE AS FTP HOSTING) ---
                webSocket("/ws/syncplay") {
                    val clientIp = call.request.origin.remoteHost
                    val peerId = "omnios-${System.currentTimeMillis()}-${(1000..9999).random()}"
                    val initialName = call.request.queryParameters["name"]?.take(28) ?: "OmniOS-${clientIp.substringAfterLast('.')}"
                    SyncPlayManager.handleWebSocketSession(
                        peerId = peerId,
                        clientIp = clientIp,
                        initialName = initialName,
                        session = this,
                        appContext = appContext
                    )
                }

                get("/api/syncplay/state") {
                    call.respondText(SyncPlayManager.buildStateJson("STATE").toString(), ContentType.Application.Json)
                }

                get("/api/syncplay/stream") {
                    val state = SyncPlayManager.roomState.value
                    val file = if (state.isActive && state.mediaFilePath.isNotBlank()) File(state.mediaFilePath) else null
                    if (file == null || !file.exists() || !file.isFile) {
                        call.respondText("No active SyncPlay media", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondFileWithRangeSupport(file, inline = true)
                }

                post("/api/syncplay/command") {
                    val clientIp = call.request.origin.remoteHost
                    try {
                        val body = JSONObject(call.receiveText())
                        val action = body.optString("action", "PLAY").uppercase(Locale.ROOT)
                        val actorName = body.optString("name", "OmniOS-${clientIp.substringAfterLast('.')}").take(28)
                        val passcode = body.optString("passcode", "").trim()
                        val posMs = if (body.has("positionMs")) body.optLong("positionMs") else null
                        val speed = if (body.has("speed")) body.optDouble("speed", 1.0).toFloat() else null

                        val ok = SyncPlayManager.triggerScheduledCommand(
                            context = appContext,
                            action = action,
                            targetPositionMs = posMs,
                            speed = speed,
                            actorName = actorName,
                            providedPasscode = passcode
                        )
                        if (!ok) {
                            call.respondText(
                                JSONObject().put("success", false).put("message", "Rejected: check passcode or guest permissions.").toString(),
                                ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                        } else {
                            call.respondText(
                                JSONObject().put("success", true).put("state", SyncPlayManager.buildStateJson("COMMAND")).toString(),
                                ContentType.Application.Json
                            )
                        }
                    } catch (e: Exception) {
                        call.respondText(
                            JSONObject().put("success", false).put("message", e.message ?: "Error").toString(),
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest
                        )
                    }
                }

                // --- 7. AUTHORITATIVE SDUI & 10-BYTE BINARY TOUCH WEBSOCKET ---
                webSocket("/ws/sdui") {
                    val clientIp = call.request.origin.remoteHost
                    val token = call.request.queryParameters["token"]
                    val deviceName = call.request.queryParameters["name"] ?: "Display ($clientIp)"

                    // Web Cast, Built-in Screen, Wired Cast, Wireless Display require no host approval;
                    // App-to-App Cast asks permission on the Receiver side before opening this surface.
                    val authResult = sessionAuthValidator?.invoke(token, clientIp, deviceName)
                        ?: CastingSessionAuthResult(CastingSessionAuthStatus.APPROVED)

                    val sessionId = "${System.currentTimeMillis()}_${(1000..9999).random()}"
                    val record = ClientWsRecord(
                        sessionId = sessionId,
                        clientIp = clientIp,
                        token = token,
                        session = this,
                        approved = authResult.status == CastingSessionAuthStatus.APPROVED
                    )
                    activeSessions[sessionId] = record
                    ContinuumHubService.recordLog(clientIp, "CAST", "WS_OPEN", "OmniOS client connected ($deviceName)")

                    try {
                        when (authResult.status) {
                            CastingSessionAuthStatus.APPROVED -> {
                                sendFullSync(this)
                            }
                            CastingSessionAuthStatus.PENDING -> {
                                val barrier = JSONObject().apply {
                                    put("op", 0x50)
                                    put("reason", "pending_approval")
                                    put("requestId", authResult.requestId ?: "")
                                    put("message", authResult.message.ifBlank { "Waiting for Host Approval..." })
                                }
                                send(Frame.Text(barrier.toString()))
                            }
                            CastingSessionAuthStatus.DENIED -> {
                                val barrier = JSONObject().apply {
                                    put("op", 0x50)
                                    put("reason", "denied")
                                    put("message", authResult.message.ifBlank { "Connection denied by Host." })
                                }
                                send(Frame.Text(barrier.toString()))
                            }
                        }

                        for (frame in incoming) {
                            if (!record.approved) {
                                // Only answer heartbeats while waiting for host approval
                                if (frame is Frame.Text) {
                                    val obj = try { JSONObject(frame.readText()) } catch (_: Exception) { null }
                                    if (obj?.optInt("op") == 0x40) {
                                        val ack = JSONObject().apply {
                                            put("op", 0x41)
                                            put("ts", obj.optLong("ts"))
                                            put("serverTs", System.currentTimeMillis())
                                        }
                                        send(Frame.Text(ack.toString()))
                                    }
                                }
                                continue
                            }

                            when (frame) {
                                is Frame.Binary -> handleBinaryTouchFrame(frame.readBytes())
                                is Frame.Text -> handleSduiJsonFrame(this, frame.readText())
                                else -> Unit
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        Log.i(TAG, "OmniOS WS closed cleanly: $sessionId")
                    } catch (e: Throwable) {
                        Log.w(TAG, "OmniOS WS error ($sessionId): ${e.message}")
                    } finally {
                        activeSessions.remove(sessionId)
                    }
                }
            }
        }

        server.start(wait = false)
        engine = server
        startMetricsLoop(appContext)
        OmniOSDiscoveryManager.registerHostService(appContext, port)
        Log.i(TAG, "OmniOS Host Server started on :$port")
    }

    override suspend fun stop(graceful: Boolean) {
        OmniOSDiscoveryManager.unregisterHostService()
        OmniOSDisplayProjectionManager.stopWiredProjection()
        metricsJob?.cancel()
        metricsJob = null
        val current = engine
        engine = null
        activeSessions.clear()
        try {
            if (graceful) {
                current?.stop(gracePeriodMillis = 600L, timeoutMillis = 1500L)
            } else {
                current?.stop(gracePeriodMillis = 100L, timeoutMillis = 300L)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping OmniOS Host Server: ${e.message}")
        }
    }

    private fun startMetricsLoop(appContext: Context) {
        metricsJob?.cancel()
        metricsJob = serverScope.launch {
            while (isActive && engine != null) {
                delay(4000L)
                if (activeSessions.isNotEmpty()) {
                    OmniOSStateEngine.refreshSystemMetrics(appContext)
                }
            }
        }
    }

    private suspend fun sendFullSync(session: WebSocketSession) {
        val fullSync = JSONObject().apply {
            put("op", 0x21) // FULL_SYNC
            put("state", OmniOSStateEngine.buildFullSyncJson())
        }
        session.send(Frame.Text(fullSync.toString()))
    }

    private fun handleBinaryTouchFrame(bytes: ByteArray) {
        if (bytes.size < 10) return
        val buffer = ByteBuffer.wrap(bytes)
        val opcode = buffer.get().toInt() and 0xFF
        val pointerId = buffer.get().toInt() and 0xFF
        val rawX = buffer.short.toInt() and 0xFFFF
        val rawY = buffer.short.toInt() and 0xFFFF
        val normX = rawX / 65535.0f
        val normY = rawY / 65535.0f
        OmniOSStateEngine.recordTouchPacket(opcode, pointerId, normX, normY)
    }

    private suspend fun handleSduiJsonFrame(session: WebSocketSession, text: String) {
        try {
            val json = JSONObject(text)
            when (json.optInt("op", -1)) {
                0x22 -> sendFullSync(session) // SYNC_REQUEST

                0x40 -> { // HEARTBEAT
                    val ack = JSONObject().apply {
                        put("op", 0x41)
                        put("ts", json.optLong("ts"))
                        put("serverTs", System.currentTimeMillis())
                    }
                    session.send(Frame.Text(ack.toString()))
                }

                0x10 -> OmniOSStateEngine.handleWindowAction(json) // WINDOW_ACTION

                0x11 -> OmniOSStateEngine.handleAppAction(json) // APP_ACTION

                0x31 -> handleAdminCommandFromClient(json) // ADMIN_COMMAND
            }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed SDUI JSON: ${e.message}")
        }
    }

    private fun handleAdminCommandFromClient(json: JSONObject) {
        val vis = OmniOSAdminConfigStore.settings.value.settingsVisibility
        when (json.optString("cmd")) {
            "set_theme" -> if (vis["appearance"] == true) OmniOSAdminConfigStore.setTheme(json.optString("theme", "dex"))
            "set_dark_mode" -> if (vis["appearance"] == true) OmniOSAdminConfigStore.setDarkMode(json.optBoolean("darkMode", true))
            "set_brightness" -> if (vis["appearance"] == true) OmniOSAdminConfigStore.setBrightness(json.optInt("brightness", 100))
            "set_animation_duration" -> if (vis["animation"] == true) OmniOSAdminConfigStore.setAnimationDuration(json.optInt("duration", 300))
            "set_audio_route" -> if (vis["sound"] == true) OmniOSAdminConfigStore.setAudioRoute(json.optString("route", "receiver"))
            "set_custom_icon" -> if (vis["appearance"] == true) {
                OmniOSAdminConfigStore.setCustomIcon(json.optString("iconId"), json.optString("svg"))
            }
        }
    }

    private fun broadcastToApproved(payload: String) {
        serverScope.launch {
            for (rec in activeSessions.values) {
                if (!rec.approved) continue
                try {
                    rec.session.send(Frame.Text(payload))
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildSharedFilesListingJson(requestedPath: String): JSONObject {
        val filesArr = JSONArray()
        var currentDisplayPath = "Shared Files"
        var parentPath: String? = null

        if (requestedPath.isBlank() || requestedPath == "/") {
            val shared = OmniOSAdminConfigStore.settings.value.sharedCastPaths
            for (p in shared) {
                val f = File(p)
                if (!f.exists()) continue
                filesArr.put(fileToListingJson(f))
            }
        } else {
            val dir = File(requestedPath)
            if (OmniOSAdminConfigStore.isFilePermittedForCasting(dir) && dir.isDirectory) {
                currentDisplayPath = dir.name
                val parent = dir.parentFile
                parentPath = if (parent != null && OmniOSAdminConfigStore.isFilePermittedForCasting(parent)) {
                    parent.absolutePath
                } else {
                    ""
                }
                val children = dir.listFiles()
                    ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
                    .orEmpty()
                for (child in children) {
                    filesArr.put(fileToListingJson(child))
                }
            }
        }

        return JSONObject().apply {
            put("currentPath", requestedPath)
            put("displayPath", currentDisplayPath)
            put("parentPath", parentPath ?: "")
            put("files", filesArr)
        }
    }

    private fun fileToListingJson(file: File): JSONObject {
        val encoded = URLEncoder.encode(file.absolutePath, "UTF-8")
        return JSONObject().apply {
            put("name", file.name)
            put("path", file.absolutePath)
            put("type", if (file.isDirectory) "folder" else "file")
            put("ext", if (file.isDirectory) "" else file.extension.lowercase(Locale.ROOT))
            put("sizeBytes", if (file.isDirectory) 0L else file.length())
            put("size", if (file.isDirectory) "${file.listFiles()?.size ?: 0} items" else formatFileSize(file.length()))
            if (file.isFile) {
                put("downloadUrl", "/api/files/download?path=$encoded")
                put("rawUrl", "/api/files/raw?path=$encoded")
            }
        }
    }

    private suspend fun serveBundledAsset(
        call: ApplicationCall,
        appContext: Context,
        relativePath: String,
        mimeType: String
    ) {
        val safePath = relativePath.replace("..", "").trimStart('/')
        try {
            appContext.assets.open("omnios/$safePath").use { input ->
                call.respondBytes(input.readBytes(), ContentType.parse(mimeType))
            }
        } catch (_: Exception) {
            call.respond(HttpStatusCode.NotFound, "OmniOS asset not found: $safePath")
        }
    }

    private suspend fun ApplicationCall.respondFileWithRangeSupport(file: File, inline: Boolean) {
        val totalLen = file.length()
        val mime = guessMimeType(file.name)
        val dispType = if (inline) "inline" else "attachment"
        val safeName = file.name.replace("\"", "")
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.ContentDisposition, "$dispType; filename=\"$safeName\"")

        val rangeHeader = request.headers[HttpHeaders.Range]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val spec = rangeHeader.removePrefix("bytes=").substringBefore(",")
            val parts = spec.split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val end = (parts.getOrNull(1)?.toLongOrNull() ?: (totalLen - 1)).coerceAtMost(totalLen - 1)
            if (start in 0..end && end < totalLen) {
                val chunkLen = end - start + 1
                response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLen")
                response.header(HttpHeaders.ContentLength, chunkLen.toString())
                respondOutputStream(ContentType.parse(mime), HttpStatusCode.PartialContent) {
                    RandomAccessFile(file, "r").use { raf ->
                        raf.seek(start)
                        val buf = ByteArray(64 * 1024)
                        var remaining = chunkLen
                        while (remaining > 0) {
                            val read = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (read <= 0) break
                            write(buf, 0, read)
                            remaining -= read
                        }
                    }
                }
                return
            }
        }

        response.header(HttpHeaders.ContentLength, totalLen.toString())
        respondOutputStream(ContentType.parse(mime), HttpStatusCode.OK) {
            file.inputStream().buffered(64 * 1024).use { it.copyTo(this) }
        }
    }

    private fun guessMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "woff2" -> "font/woff2"
        "otf" -> "font/otf"
        "ttf" -> "font/ttf"
        "txt", "md", "csv" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
