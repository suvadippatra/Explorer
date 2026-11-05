package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.Environment
import android.util.Log
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.html.*
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/**
 * KtorEmbeddedServer
 *
 * Lightweight, Netty-backed Ktor HTTP, Static Website & WebSocket server operating on port 8080.
 * - In Self-Hosted Website Mode (FtpHostedMode.CUSTOM_FOLDER): Serves the selected HTML file or
 *   static web repository (e.g. cloned/downloaded from GitHub) with full relative CSS/JS/media/font
 *   asset resolution and SPA routing fallback.
 * - In FTP & File Share Mode: Serves a minimalist, responsive Web Portal with configurable
 *   Desktop (split table), Mobile (touch cards), and Screen Mirroring (live WebSocket theatre) layouts.
 */
class KtorEmbeddedServer(
    private val context: Context,
    private val port: Int = 8080,
    private val rootDirectory: File = Environment.getExternalStorageDirectory(),
    private val hostedMode: FtpHostedMode = SettingsManager.ftpHostedMode.value,
    private val entryHtmlFile: File? = null,
    private val getScreenCastManager: () -> ScreenCastManager?
) {
    companion object {
        private const val TAG = "KtorEmbeddedServer"
    }

    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return

        val effectiveRootDir = when {
            rootDirectory.exists() && rootDirectory.isDirectory -> rootDirectory
            rootDirectory.exists() && rootDirectory.isFile -> rootDirectory.parentFile ?: Environment.getExternalStorageDirectory()
            else -> Environment.getExternalStorageDirectory()
        }

        val resolvedEntryHtml = entryHtmlFile ?: if (rootDirectory.exists() && rootDirectory.isFile) {
            rootDirectory
        } else {
            File(effectiveRootDir, "index.html").takeIf { it.exists() && it.isFile }
                ?: File(effectiveRootDir, "index.htm").takeIf { it.exists() && it.isFile }
        }

        try {
            engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(15)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Authorization)
                }

                routing {
                    // 1. Root Route: Self-Hosted Website OR Continuum Web Portal
                    get("/") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val forceExplorer = call.parameters["explorer"] == "true"
                        val requestedPath = call.parameters["path"]?.let {
                            URLDecoder.decode(it, "UTF-8")
                        }

                        // If in Self-Hosted Website mode and not explicitly opening ?explorer=true or ?path=...
                        if (hostedMode == FtpHostedMode.CUSTOM_FOLDER && !forceExplorer && requestedPath.isNullOrEmpty()) {
                            val htmlTarget = resolvedEntryHtml
                                ?: effectiveRootDir.listFiles()?.firstOrNull {
                                    it.isFile && (it.name.endsWith(".html", ignoreCase = true) || it.name.endsWith(".htm", ignoreCase = true))
                                }
                            if (htmlTarget != null && htmlTarget.exists() && htmlTarget.isFile) {
                                ContinuumHubService.addLog(
                                    clientIp,
                                    "Website",
                                    "Served ${htmlTarget.name}",
                                    effectiveRootDir.name,
                                    bytes = htmlTarget.length()
                                )
                                call.respondFile(htmlTarget)
                                return@get
                            }
                        }

                        // Otherwise render the responsive Web Portal / File Browser
                        val currentDir = if (!requestedPath.isNullOrEmpty()) {
                            val candidate = File(requestedPath)
                            if (candidate.exists() && candidate.isDirectory && isAllowedPath(candidate, effectiveRootDir)) {
                                candidate
                            } else {
                                effectiveRootDir
                            }
                        } else {
                            effectiveRootDir
                        }

                        val files: List<File> = if (
                            hostedMode == FtpHostedMode.BATCH_FILES &&
                            requestedPath.isNullOrEmpty() &&
                            FtpBucketManager.bucketItems.isNotEmpty()
                        ) {
                            FtpBucketManager.bucketItems.filter { it.exists() }.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            )
                        } else {
                            currentDir.listFiles()?.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            ) ?: emptyList()
                        }

                        ContinuumHubService.addLog(clientIp, "HTTP", "Browsed portal", currentDir.name)

                        call.respondHtml(HttpStatusCode.OK) {
                            renderDashboard(
                                currentDir = currentDir,
                                rootDir = effectiveRootDir,
                                files = files,
                                isRootView = requestedPath.isNullOrEmpty(),
                                hasWebsiteEntry = resolvedEntryHtml?.exists() == true
                            )
                        }
                    }

                    // 2. Direct File Download / Inline Preview Route
                    get("/download") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val filePath = call.parameters["file"]?.let {
                            URLDecoder.decode(it, "UTF-8")
                        }
                        val inlinePreview = call.parameters["inline"] == "true"

                        if (filePath.isNullOrEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, "Missing file parameter")
                            return@get
                        }

                        val file = File(filePath)
                        if (file.exists() && file.isFile && isAllowedPath(file, effectiveRootDir)) {
                            ContinuumHubService.addLog(
                                clientIp,
                                "HTTP",
                                if (inlinePreview) "Previewed ${file.name}" else "Downloaded ${file.name}",
                                "${file.length() / 1024} KB",
                                bytes = file.length()
                            )
                            if (!inlinePreview) {
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        file.name
                                    ).toString()
                                )
                            }
                            call.respondFile(file)
                        } else {
                            ContinuumHubService.addLog(clientIp, "HTTP", "404 Not Found", filePath)
                            call.respond(HttpStatusCode.NotFound, "File not found or access restricted")
                        }
                    }

                    // 3. Live Screen Sharing WebSocket Route
                    webSocket("/screencast") {
                        val screenCast = getScreenCastManager()
                        if (screenCast == null) {
                            send(Frame.Text("INACTIVE"))
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "MediaProjection inactive"))
                            return@webSocket
                        }

                        val channel = kotlinx.coroutines.channels.Channel<ByteArray>(
                            capacity = kotlinx.coroutines.channels.Channel.CONFLATED
                        )

                        val frameListener: (ByteArray) -> Unit = { bytes ->
                            channel.trySend(bytes)
                        }

                        screenCast.addFrameListener(frameListener)
                        try {
                            for (frameBytes in channel) {
                                if (!isActive) break
                                send(Frame.Binary(true, frameBytes))
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "WebSocket client disconnected: ${e.message}")
                        } finally {
                            screenCast.removeFrameListener(frameListener)
                            channel.close()
                        }
                    }

                    // 4. API Status Route
                    get("/api/status") {
                        val hasScreencast = getScreenCastManager() != null
                        val activeFtpPort = ContinuumHubService.activeFtpPort
                        val modeName = hostedMode.name
                        call.respondText(
                            """{"status":"online","httpPort":$port,"ftpPort":$activeFtpPort,"mode":"$modeName","screencast":$hasScreencast}""",
                            ContentType.Application.Json
                        )
                    }

                    // 5. Security Password-Gated Multipart File Upload Route
                    post("/upload") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val expectedPass = SettingsManager.ftpUploadPassword.value
                        val isUploadAllowed = SettingsManager.ftpAllowUpload.value

                        if (!isUploadAllowed) {
                            call.respondText(
                                """{"success":false,"message":"Web uploads are disabled on the host device"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                            return@post
                        }

                        var providedPass = call.request.headers["X-Upload-Password"] ?: ""
                        var targetFolderPath = if (hostedMode == FtpHostedMode.BATCH_FILES) {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        } else {
                            effectiveRootDir.absolutePath
                        }
                        val multipart = call.receiveMultipart()
                        var savedFilesCount = 0

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "password") {
                                        providedPass = part.value
                                    } else if (part.name == "targetDir" && part.value.isNotBlank()) {
                                        val f = File(part.value)
                                        if (f.exists() && f.isDirectory && !f.absolutePath.contains("ftp_hosted_bucket")) {
                                            targetFolderPath = f.absolutePath
                                        }
                                    }
                                }
                                is PartData.FileItem -> {
                                    val passValid = expectedPass.isBlank() || providedPass == expectedPass
                                    if (passValid) {
                                        val originalName = part.originalFileName ?: "uploaded_${System.currentTimeMillis()}"
                                        val destFolder = File(targetFolderPath).apply { if (!exists()) mkdirs() }
                                        val saveFile = File(destFolder, originalName)
                                        part.streamProvider().use { input ->
                                            saveFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        savedFilesCount++
                                        ContinuumHubService.addLog(
                                            clientIp,
                                            "Upload",
                                            "Uploaded ${saveFile.name}",
                                            "${saveFile.length() / 1024} KB",
                                            bytes = saveFile.length()
                                        )
                                    }
                                }
                                else -> {}
                            }
                            part.dispose()
                        }

                        val passValid = expectedPass.isBlank() || providedPass == expectedPass
                        if (!passValid) {
                            call.respondText(
                                """{"success":false,"message":"Incorrect upload security password"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Unauthorized
                            )
                        } else {
                            call.respondText(
                                """{"success":true,"message":"Uploaded $savedFilesCount file(s)"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.OK
                            )
                        }
                    }

                    // 6. Catch-All Static Website Asset Route (CSS, JS, Images, Fonts, Sub-pages & SPA fallback)
                    get("/{staticPath...}") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val segments = call.parameters.getAll("staticPath")?.filter { it.isNotBlank() } ?: emptyList()
                        if (segments.isEmpty()) {
                            call.respondRedirect("/")
                            return@get
                        }

                        val relPath = segments.joinToString(File.separator)
                        val candidate = File(effectiveRootDir, relPath)

                        if (candidate.exists() && isAllowedPath(candidate, effectiveRootDir)) {
                            if (candidate.isFile) {
                                ContinuumHubService.addLog(
                                    clientIp,
                                    "Asset",
                                    candidate.name,
                                    "${candidate.length() / 1024} KB",
                                    bytes = candidate.length()
                                )
                                call.respondFile(candidate)
                                return@get
                            } else if (candidate.isDirectory) {
                                val subIndex = File(candidate, "index.html").takeIf { it.exists() && it.isFile }
                                    ?: File(candidate, "index.htm").takeIf { it.exists() && it.isFile }
                                if (subIndex != null) {
                                    ContinuumHubService.addLog(
                                        clientIp,
                                        "Website",
                                        "Served ${subIndex.name}",
                                        candidate.name,
                                        bytes = subIndex.length()
                                    )
                                    call.respondFile(subIndex)
                                    return@get
                                }
                            }
                        }

                        // SPA Client-Side Routing Fallback in Self-Hosted Website mode
                        val acceptHeader = call.request.header(HttpHeaders.Accept).orEmpty()
                        if (
                            hostedMode == FtpHostedMode.CUSTOM_FOLDER &&
                            acceptHeader.contains("text/html", ignoreCase = true) &&
                            resolvedEntryHtml != null &&
                            resolvedEntryHtml.exists()
                        ) {
                            call.respondFile(resolvedEntryHtml)
                            return@get
                        }

                        call.respond(HttpStatusCode.NotFound, "Asset not found: /$relPath")
                    }
                }
            }.start(wait = false)

            Log.i(TAG, "Ktor Server started on port $port (mode=$hostedMode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ktor Server", e)
        }
    }

    private fun isAllowedPath(file: File, effectiveRootDir: File): Boolean {
        return try {
            val canonical = file.canonicalPath
            val rootCanonical = effectiveRootDir.canonicalPath
            val extCanonical = Environment.getExternalStorageDirectory().canonicalPath
            canonical.startsWith(rootCanonical) ||
                canonical.startsWith(extCanonical) ||
                canonical.startsWith("/storage/") ||
                FtpBucketManager.bucketItems.any { item ->
                    item.exists() && canonical.startsWith(item.canonicalPath)
                }
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            engine?.stop(500, 1000)
            engine = null
            Log.i(TAG, "Ktor Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ktor Server", e)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun HTML.renderDashboard(
        currentDir: File,
        rootDir: File,
        files: List<File>,
        isRootView: Boolean,
        hasWebsiteEntry: Boolean
    ) {
        val hasScreencast = getScreenCastManager() != null
        val allowUploads = SettingsManager.ftpAllowUpload.value
        val requireUploadPass = SettingsManager.ftpUploadPassword.value.isNotBlank()
        val ftpPort = ContinuumHubService.activeFtpPort
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

        head {
            title("Continuum Server Portal")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            style {
                unsafe {
                    raw("""
                        :root {
                            --bg: #0f1115;
                            --surface: #171a21;
                            --surface-elevated: #1f242d;
                            --surface-hover: #262c36;
                            --primary: #6366f1;
                            --primary-hover: #4f46e5;
                            --accent: #10b981;
                            --text: #f3f4f6;
                            --text-muted: #9ca3af;
                            --border: #2a303c;
                            --danger: #ef4444;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            background: var(--bg);
                            color: var(--text);
                            font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, sans-serif;
                            font-size: 13.5px;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                        }
                        header {
                            background: var(--surface);
                            border-bottom: 1px solid var(--border);
                            padding: 12px 20px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            flex-wrap: wrap;
                            gap: 12px;
                            position: sticky;
                            top: 0;
                            z-index: 50;
                        }
                        .brand {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                        }
                        .brand-title {
                            font-size: 1rem;
                            font-weight: 700;
                            letter-spacing: -0.01em;
                            color: #fff;
                        }
                        .status-pill {
                            background: rgba(16, 185, 129, 0.14);
                            color: var(--accent);
                            border: 1px solid rgba(16, 185, 129, 0.3);
                            font-size: 0.72rem;
                            font-weight: 600;
                            padding: 3px 8px;
                            border-radius: 999px;
                        }
                        .toolbar {
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            flex-wrap: wrap;
                        }
                        .segmented {
                            display: inline-flex;
                            background: var(--bg);
                            border: 1px solid var(--border);
                            border-radius: 8px;
                            padding: 2px;
                            gap: 2px;
                        }
                        .seg-btn {
                            background: transparent;
                            color: var(--text-muted);
                            border: none;
                            padding: 5px 10px;
                            font-size: 0.78rem;
                            font-weight: 600;
                            border-radius: 6px;
                            cursor: pointer;
                            transition: all 0.15s;
                        }
                        .seg-btn.active {
                            background: var(--primary);
                            color: #fff;
                        }
                        .btn {
                            background: var(--surface-elevated);
                            color: var(--text);
                            border: 1px solid var(--border);
                            padding: 6px 12px;
                            border-radius: 8px;
                            font-size: 0.8rem;
                            font-weight: 600;
                            cursor: pointer;
                            text-decoration: none;
                            display: inline-flex;
                            align-items: center;
                            gap: 6px;
                            transition: background 0.15s, border-color 0.15s;
                        }
                        .btn:hover { background: var(--surface-hover); }
                        .btn-primary {
                            background: var(--primary);
                            border-color: var(--primary);
                            color: #fff;
                        }
                        .btn-primary:hover { background: var(--primary-hover); }

                        /* Workspace container adapts to selected layout mode */
                        .workspace {
                            flex: 1;
                            padding: 16px 20px;
                            display: grid;
                            gap: 16px;
                            max-width: 1600px;
                            width: 100%;
                            margin: 0 auto;
                        }
                        body[data-layout="desktop"] .workspace.with-cast {
                            grid-template-columns: minmax(340px, 44%) 1fr;
                            align-items: start;
                        }
                        body[data-layout="mobile"] .workspace {
                            grid-template-columns: 1fr;
                            max-width: 640px;
                            padding: 10px 12px;
                        }
                        body[data-layout="mirror"] .workspace {
                            grid-template-columns: 1fr;
                        }
                        .panel {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            overflow: hidden;
                            display: flex;
                            flex-direction: column;
                        }
                        .panel-header {
                            padding: 12px 16px;
                            background: var(--surface-elevated);
                            border-bottom: 1px solid var(--border);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 10px;
                            flex-wrap: wrap;
                        }
                        .panel-title {
                            font-weight: 600;
                            font-size: 0.88rem;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }
                        .subbar {
                            padding: 10px 16px;
                            background: #13161c;
                            border-bottom: 1px solid var(--border);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 12px;
                            flex-wrap: wrap;
                        }
                        .path-text {
                            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                            font-size: 0.78rem;
                            color: var(--text-muted);
                            word-break: break-all;
                        }
                        .search-input {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            color: var(--text);
                            padding: 6px 10px;
                            border-radius: 7px;
                            font-size: 0.8rem;
                            min-width: 200px;
                        }

                        /* Desktop Table View */
                        .file-table {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        .file-table th {
                            text-align: left;
                            padding: 10px 16px;
                            font-size: 0.74rem;
                            text-transform: uppercase;
                            letter-spacing: 0.04em;
                            color: var(--text-muted);
                            border-bottom: 1px solid var(--border);
                            background: #14171d;
                        }
                        .file-table td {
                            padding: 10px 16px;
                            border-bottom: 1px solid #1e232c;
                            vertical-align: middle;
                        }
                        .file-table tr:hover td {
                            background: var(--surface-hover);
                        }
                        .item-link {
                            color: var(--text);
                            text-decoration: none;
                            font-weight: 500;
                            display: inline-flex;
                            align-items: center;
                            gap: 10px;
                            word-break: break-word;
                        }
                        .item-link:hover { color: #818cf8; }

                        /* Mobile Card View (Activated when data-layout="mobile") */
                        body[data-layout="mobile"] .file-table thead {
                            display: none;
                        }
                        body[data-layout="mobile"] .file-table,
                        body[data-layout="mobile"] .file-table tbody {
                            display: block;
                            width: 100%;
                        }
                        body[data-layout="mobile"] .file-table tr {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            padding: 10px 14px;
                            border-bottom: 1px solid var(--border);
                            gap: 10px;
                        }
                        body[data-layout="mobile"] .file-table td {
                            padding: 0;
                            border: none;
                        }
                        body[data-layout="mobile"] .col-type,
                        body[data-layout="mobile"] .col-date {
                            display: none;
                        }

                        /* Screen Mirroring Canvas Area */
                        .cast-stage {
                            background: #050608;
                            min-height: 360px;
                            max-height: 82vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            position: relative;
                            padding: 12px;
                            overflow: hidden;
                        }
                        body[data-layout="mirror"] .cast-stage {
                            min-height: 74vh;
                        }
                        #screenCanvas {
                            max-width: 100%;
                            max-height: 76vh;
                            width: auto;
                            height: auto;
                            border-radius: 8px;
                            box-shadow: 0 8px 30px rgba(0,0,0,0.65);
                            transition: transform 0.2s ease;
                        }
                        .hidden { display: none !important; }

                        /* Upload Modal */
                        .modal-backdrop {
                            position: fixed;
                            inset: 0;
                            background: rgba(0, 0, 0, 0.72);
                            display: none;
                            align-items: center;
                            justify-content: center;
                            z-index: 200;
                            padding: 16px;
                        }
                        .modal {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 14px;
                            padding: 20px;
                            width: 100%;
                            max-width: 420px;
                            display: flex;
                            flex-direction: column;
                            gap: 14px;
                        }
                        .modal input[type="password"],
                        .modal input[type="file"] {
                            background: var(--bg);
                            border: 1px solid var(--border);
                            color: var(--text);
                            padding: 10px;
                            border-radius: 8px;
                            width: 100%;
                        }
                    """)
                }
            }
        }
        body {
            attributes["data-layout"] = "desktop"

            header {
                div("brand") {
                    span("brand-title") { +"Continuum Hub" }
                    span("status-pill") { +"FTP :$ftpPort • HTTP :$port" }
                    if (hasWebsiteEntry) {
                        a(href = "/", classes = "btn") {
                            +"🌐 View Hosted Site"
                        }
                    }
                }

                div("toolbar") {
                    // Layout Mode Segmented Switcher (Desktop / Mobile / Mirror Theatre)
                    div("segmented") {
                        button(classes = "seg-btn active") {
                            id = "btnLayoutDesktop"
                            onClick = "setPortalLayout('desktop')"
                            +"💻 Desktop"
                        }
                        button(classes = "seg-btn") {
                            id = "btnLayoutMobile"
                            onClick = "setPortalLayout('mobile')"
                            +"📱 Mobile"
                        }
                        button(classes = "seg-btn") {
                            id = "btnLayoutMirror"
                            onClick = "setPortalLayout('mirror')"
                            +"📺 Mirror View"
                        }
                    }

                    if (allowUploads) {
                        button(classes = "btn btn-primary") {
                            onClick = "openUploadModal()"
                            +"⬆ Upload"
                        }
                    }
                }
            }

            // Main Workspace
            div(classes = if (hasScreencast) "workspace with-cast" else "workspace") {
                id = "mainWorkspace"

                // Live Screen Mirroring Panel
                div(classes = if (hasScreencast) "panel" else "panel hidden") {
                    id = "castPanel"
                    div("panel-header") {
                        div("panel-title") {
                            +"📺 Live Device Screen Mirror"
                            span("status-pill") {
                                id = "castStatusBadge"
                                +if (hasScreencast) "CONNECTING" else "STANDBY"
                            }
                        }
                        div("toolbar") {
                            button(classes = "btn") {
                                onClick = "rotateClientCanvas()"
                                +"↻ Rotate"
                            }
                            button(classes = "btn") {
                                onClick = "fullscreenCanvas()"
                                +"⛶ Fullscreen"
                            }
                        }
                    }
                    div("cast-stage") {
                        id = "castStage"
                        canvas {
                            id = "screenCanvas"
                            width = "1280"
                            height = "720"
                        }
                    }
                }

                // File Explorer & Download Panel
                div("panel") {
                    id = "explorerPanel"
                    div("panel-header") {
                        div("panel-title") {
                            +when (hostedMode) {
                                FtpHostedMode.BATCH_FILES -> "Shared Files & Folders Bucket (${files.size})"
                                FtpHostedMode.CUSTOM_FOLDER -> "Website Assets (${currentDir.name})"
                                FtpHostedMode.FULL_STORAGE -> "Device Storage (${currentDir.name})"
                            }
                        }
                        div("toolbar") {
                            val parentPath = currentDir.parentFile?.absolutePath
                            if (!isRootView && !parentPath.isNullOrEmpty()) {
                                val parentEncoded = URLEncoder.encode(parentPath, "UTF-8")
                                a(href = "/?explorer=true&path=$parentEncoded", classes = "btn") {
                                    +"↑ Up Folder"
                                }
                            }
                            if (!isRootView) {
                                a(href = "/?explorer=true", classes = "btn") {
                                    +"⌂ Root"
                                }
                            }
                        }
                    }

                    div("subbar") {
                        span("path-text") {
                            +if (hostedMode == FtpHostedMode.BATCH_FILES && isRootView && FtpBucketManager.bucketItems.isNotEmpty()) {
                                "Virtual Bucket (${FtpBucketManager.bucketItems.size} selected items)"
                            } else {
                                currentDir.absolutePath
                            }
                        }
                        input {
                            id = "fileSearchInput"
                            classes = setOf("search-input")
                            type = InputType.search
                            placeholder = "Filter files in folder..."
                            onInput = "filterPortalFiles(this.value)"
                        }
                    }

                    table("file-table") {
                        thead {
                            tr {
                                th { +"Name" }
                                th(classes = "col-type") { +"Type" }
                                th { +"Size" }
                                th(classes = "col-date") { +"Modified" }
                                th {
                                    style = "text-align: right;"
                                    +"Actions"
                                }
                            }
                        }
                        tbody {
                            id = "fileTableBody"
                            if (files.isEmpty()) {
                                tr {
                                    td {
                                        colSpan = "5"
                                        style = "text-align: center; padding: 32px; color: var(--text-muted);"
                                        +"This directory is empty."
                                    }
                                }
                            } else {
                                for (file in files) {
                                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                                    val isDir = file.isDirectory
                                    val ext = file.extension.uppercase(Locale.US).ifBlank { "FILE" }
                                    tr(classes = "file-row") {
                                        attributes["data-name"] = file.name.lowercase(Locale.US)
                                        td {
                                            if (isDir) {
                                                a(classes = "item-link", href = "/?explorer=true&path=$encodedPath") {
                                                    +"📁 ${file.name}"
                                                }
                                            } else {
                                                a(classes = "item-link", href = "/download?file=$encodedPath&inline=true", target = "_blank") {
                                                    +"📄 ${file.name}"
                                                }
                                            }
                                        }
                                        td(classes = "col-type") {
                                            style = "color: var(--text-muted); font-size: 0.78rem;"
                                            +if (isDir) "Folder" else ext
                                        }
                                        td {
                                            style = "color: var(--text-muted); font-size: 0.8rem;"
                                            +if (isDir) {
                                                val count = try { file.list()?.size ?: 0 } catch (_: Exception) { 0 }
                                                "$count items"
                                            } else {
                                                formatBytes(file.length())
                                            }
                                        }
                                        td(classes = "col-date") {
                                            style = "color: var(--text-muted); font-size: 0.78rem;"
                                            +dateFormat.format(Date(file.lastModified()))
                                        }
                                        td {
                                            style = "text-align: right;"
                                            if (isDir) {
                                                a(classes = "btn", href = "/?explorer=true&path=$encodedPath") {
                                                    +"Open"
                                                }
                                            } else {
                                                a(classes = "btn", href = "/download?file=$encodedPath") {
                                                    +"⬇ Download"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Upload Modal Dialog
            div("modal-backdrop") {
                id = "uploadModal"
                div("modal") {
                    h3 { +"Upload Files to Host" }
                    p {
                        style = "font-size: 0.8rem; color: var(--text-muted);"
                        +"Destination: ${currentDir.name}"
                    }
                    if (requireUploadPass) {
                        input {
                            id = "uploadPasswordInput"
                            type = InputType.password
                            placeholder = "Upload Security Password"
                        }
                    } else {
                        input {
                            id = "uploadPasswordInput"
                            type = InputType.hidden
                            value = ""
                        }
                    }
                    input {
                        id = "uploadFilesInput"
                        type = InputType.file
                        multiple = true
                    }
                    input {
                        id = "currentTargetDir"
                        type = InputType.hidden
                        value = currentDir.absolutePath
                    }
                    div {
                        style = "display: flex; justify-content: flex-end; gap: 8px;"
                        button(classes = "btn") {
                            onClick = "closeUploadModal()"
                            +"Cancel"
                        }
                        button(classes = "btn btn-primary") {
                            onClick = "submitFileUpload()"
                            +"Upload"
                        }
                    }
                    span {
                        id = "uploadStatusNotice"
                        style = "font-size: 0.8rem;"
                    }
                }
            }

            script {
                unsafe {
                    raw("""
                        let canvasRotation = 0;

                        function setPortalLayout(mode) {
                            document.body.setAttribute('data-layout', mode);
                            document.getElementById('btnLayoutDesktop').classList.toggle('active', mode === 'desktop');
                            document.getElementById('btnLayoutMobile').classList.toggle('active', mode === 'mobile');
                            document.getElementById('btnLayoutMirror').classList.toggle('active', mode === 'mirror');

                            const castPanel = document.getElementById('castPanel');
                            const ws = document.getElementById('mainWorkspace');
                            if (mode === 'mirror') {
                                castPanel.classList.remove('hidden');
                                ws.classList.remove('with-cast');
                            }
                        }

                        // Auto-select Mobile layout on narrow viewports initially
                        if (window.innerWidth < 768) {
                            setPortalLayout('mobile');
                        }

                        function filterPortalFiles(query) {
                            const q = (query || '').toLowerCase().trim();
                            const rows = document.querySelectorAll('.file-row');
                            rows.forEach(row => {
                                const name = row.getAttribute('data-name') || '';
                                row.style.display = (!q || name.includes(q)) ? '' : 'none';
                            });
                        }

                        function rotateClientCanvas() {
                            canvasRotation = (canvasRotation + 90) % 360;
                            const canvas = document.getElementById('screenCanvas');
                            if (canvas) {
                                canvas.style.transform = 'rotate(' + canvasRotation + 'deg)';
                            }
                        }

                        function fullscreenCanvas() {
                            const stage = document.getElementById('castStage');
                            if (stage && stage.requestFullscreen) {
                                stage.requestFullscreen();
                            }
                        }

                        function openUploadModal() {
                            document.getElementById('uploadModal').style.display = 'flex';
                        }
                        function closeUploadModal() {
                            document.getElementById('uploadModal').style.display = 'none';
                            document.getElementById('uploadStatusNotice').innerText = '';
                        }

                        async function submitFileUpload() {
                            const passEl = document.getElementById('uploadPasswordInput');
                            const pass = passEl ? passEl.value : '';
                            const filesInput = document.getElementById('uploadFilesInput');
                            const targetDir = document.getElementById('currentTargetDir').value;
                            const notice = document.getElementById('uploadStatusNotice');

                            if (!filesInput.files || filesInput.files.length === 0) {
                                alert('Select at least one file to upload.');
                                return;
                            }

                            const formData = new FormData();
                            formData.append('password', pass);
                            formData.append('targetDir', targetDir);
                            for (let i = 0; i < filesInput.files.length; i++) {
                                formData.append('file_' + i, filesInput.files[i]);
                            }

                            notice.innerText = 'Uploading...';
                            notice.style.color = '#fbbf24';

                            try {
                                const res = await fetch('/upload', { method: 'POST', body: formData });
                                const data = await res.json();
                                if (res.ok && data.success) {
                                    notice.innerText = data.message || 'Upload complete!';
                                    notice.style.color = '#10b981';
                                    setTimeout(() => window.location.reload(), 900);
                                } else {
                                    notice.innerText = data.message || 'Upload failed';
                                    notice.style.color = '#ef4444';
                                }
                            } catch (e) {
                                notice.innerText = 'Error: ' + e.message;
                                notice.style.color = '#ef4444';
                            }
                        }

                        // Live Screen Mirroring WebSocket Receiver
                        (function() {
                            const canvas = document.getElementById('screenCanvas');
                            const ctx = canvas ? canvas.getContext('2d') : null;
                            const badge = document.getElementById('castStatusBadge');
                            const castPanel = document.getElementById('castPanel');
                            const workspace = document.getElementById('mainWorkspace');

                            if (!canvas || !ctx) return;

                            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const wsUrl = protocol + '//' + window.location.host + '/screencast';

                            function connectCast() {
                                const socket = new WebSocket(wsUrl);
                                socket.binaryType = 'arraybuffer';

                                socket.onopen = function() {
                                    if (badge) {
                                        badge.innerText = 'LIVE STREAM';
                                        badge.style.color = '#10b981';
                                    }
                                    if (castPanel) castPanel.classList.remove('hidden');
                                    if (workspace && document.body.getAttribute('data-layout') === 'desktop') {
                                        workspace.classList.add('with-cast');
                                    }
                                };

                                socket.onmessage = function(ev) {
                                    if (typeof ev.data === 'string') {
                                        if (ev.data === 'INACTIVE' && badge) {
                                            badge.innerText = 'STANDBY';
                                        }
                                        return;
                                    }
                                    const blob = new Blob([ev.data], { type: 'image/jpeg' });
                                    const img = new Image();
                                    const url = URL.createObjectURL(blob);
                                    img.onload = function() {
                                        if (canvas.width !== img.width || canvas.height !== img.height) {
                                            canvas.width = img.width;
                                            canvas.height = img.height;
                                        }
                                        ctx.drawImage(img, 0, 0);
                                        URL.revokeObjectURL(url);
                                    };
                                    img.src = url;
                                };

                                socket.onclose = function() {
                                    if (badge) {
                                        badge.innerText = 'STANDBY';
                                        badge.style.color = '#9ca3af';
                                    }
                                    setTimeout(connectCast, 3500);
                                };
                            }

                            connectCast();
                        })();
                    """)
                }
            }
        }
    }
}
