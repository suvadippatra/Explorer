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
 * - In FTP & File Share Mode: Serves an interactive, modern, responsive File Hosting Portal optimized
 *   for variable screen sizes and aspect ratios (Grid Cards & Table views, Breadcrumbs, Inline Lightbox
 *   Preview, Drag-and-Drop Uploads, and a Hideable Live Screen Stream Player).
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
                    // 1. Root Route: Self-Hosted Website OR Interactive Explorer Web Portal
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

                    // 3A. Live Screen Sharing WebSocket Route
                    webSocket("/screencast") {
                        val screenCast = getScreenCastManager()
                        if (screenCast == null || !screenCast.isActive()) {
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

                    // 3B. Live Screen Sharing HTTP Frame Snapshot Fallback Route (works even if WebSockets are blocked)
                    get("/screencast-frame") {
                        val screenCast = getScreenCastManager()
                        val frame = screenCast?.getLatestFrame()
                        if (screenCast == null || !screenCast.isActive() || frame == null) {
                            call.respond(HttpStatusCode.NoContent)
                            return@get
                        }
                        call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
                        call.respondBytes(frame, ContentType.Image.JPEG, HttpStatusCode.OK)
                    }

                    // 4. API Status Route
                    get("/api/status") {
                        val screenCast = getScreenCastManager()
                        val hasScreencast = screenCast != null && screenCast.isActive()
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

    private fun getFileEmojiAndBadge(file: File): Pair<String, String> {
        if (file.isDirectory) return Pair("📁", "FOLDER")
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic" -> Pair("🖼️", ext.uppercase())
            "mp4", "mkv", "webm", "mov", "avi" -> Pair("🎬", ext.uppercase())
            "mp3", "wav", "flac", "m4a", "ogg", "aac" -> Pair("🎵", ext.uppercase())
            "pdf" -> Pair("📕", "PDF")
            "zip", "rar", "7z", "tar", "gz" -> Pair("🗜️", ext.uppercase())
            "apk" -> Pair("📦", "APK")
            "md", "txt", "json", "xml", "csv", "log" -> Pair("📝", ext.uppercase())
            "kt", "java", "py", "js", "ts", "html", "css", "sh" -> Pair("💻", ext.uppercase())
            else -> Pair("📄", ext.uppercase().ifBlank { "FILE" })
        }
    }

    private fun HTML.renderDashboard(
        currentDir: File,
        rootDir: File,
        files: List<File>,
        isRootView: Boolean,
        hasWebsiteEntry: Boolean
    ) {
        val screenCast = getScreenCastManager()
        val hasScreencast = screenCast != null && screenCast.isActive()
        val allowUploads = SettingsManager.ftpAllowUpload.value
        val requireUploadPass = SettingsManager.ftpUploadPassword.value.isNotBlank()
        val ftpPort = ContinuumHubService.activeFtpPort
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

        val folderCount = files.count { it.isDirectory }
        val fileCount = files.count { !it.isDirectory }
        val totalBytes = files.filter { !it.isDirectory }.sumOf { it.length() }

        head {
            title("Explorer • File Hosting Portal")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0, maximum-scale=5.0")
            style {
                unsafe {
                    raw("""
                        :root {
                            --bg: #0b0e14;
                            --surface: #131822;
                            --surface-elevated: #1a2130;
                            --surface-hover: #232c3f;
                            --primary: #6366f1;
                            --primary-hover: #4f46e5;
                            --primary-soft: rgba(99, 102, 241, 0.14);
                            --accent: #10b981;
                            --accent-soft: rgba(16, 185, 129, 0.14);
                            --text: #f3f4f6;
                            --text-muted: #94a3b8;
                            --border: #263044;
                            --danger: #ef4444;
                            --radius: 14px;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            background: radial-gradient(circle at top right, #161d2f 0%, var(--bg) 55%);
                            color: var(--text);
                            font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, sans-serif;
                            font-size: 14px;
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            line-height: 1.45;
                        }
                        header {
                            background: rgba(19, 24, 34, 0.88);
                            backdrop-filter: blur(14px);
                            border-bottom: 1px solid var(--border);
                            padding: 12px clamp(12px, 2.5vw, 28px);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            flex-wrap: wrap;
                            gap: 12px;
                            position: sticky;
                            top: 0;
                            z-index: 60;
                        }
                        .brand {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            flex-wrap: wrap;
                        }
                        .brand-logo {
                            width: 34px;
                            height: 34px;
                            border-radius: 10px;
                            background: linear-gradient(135deg, #6366f1, #8b5cf6);
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 18px;
                            box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
                        }
                        .brand-title {
                            font-size: 1.05rem;
                            font-weight: 700;
                            letter-spacing: -0.01em;
                            color: #fff;
                        }
                        .status-pill {
                            background: var(--accent-soft);
                            color: var(--accent);
                            border: 1px solid rgba(16, 185, 129, 0.3);
                            font-size: 0.73rem;
                            font-weight: 600;
                            padding: 4px 10px;
                            border-radius: 999px;
                            display: inline-flex;
                            align-items: center;
                            gap: 6px;
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
                            border-radius: 10px;
                            padding: 3px;
                            gap: 2px;
                        }
                        .seg-btn {
                            background: transparent;
                            color: var(--text-muted);
                            border: none;
                            padding: 6px 11px;
                            font-size: 0.78rem;
                            font-weight: 600;
                            border-radius: 7px;
                            cursor: pointer;
                            transition: all 0.16s ease;
                            white-space: nowrap;
                        }
                        .seg-btn.active {
                            background: var(--primary);
                            color: #fff;
                        }
                        .btn {
                            background: var(--surface-elevated);
                            color: var(--text);
                            border: 1px solid var(--border);
                            padding: 7px 12px;
                            border-radius: 9px;
                            font-size: 0.8rem;
                            font-weight: 600;
                            cursor: pointer;
                            text-decoration: none;
                            display: inline-flex;
                            align-items: center;
                            gap: 6px;
                            transition: all 0.15s ease;
                            white-space: nowrap;
                        }
                        .btn:hover {
                            background: var(--surface-hover);
                            border-color: #3b4966;
                            transform: translateY(-1px);
                        }
                        .btn-primary {
                            background: var(--primary);
                            border-color: var(--primary);
                            color: #fff;
                        }
                        .btn-primary:hover { background: var(--primary-hover); }
                        .btn-cast-active {
                            background: rgba(16, 185, 129, 0.16);
                            border-color: rgba(16, 185, 129, 0.45);
                            color: #34d399;
                        }

                        /* Main responsive container */
                        .workspace {
                            flex: 1;
                            width: 100%;
                            max-width: 1680px;
                            margin: 0 auto;
                            padding: clamp(10px, 2vw, 22px);
                            display: grid;
                            gap: 18px;
                            grid-template-columns: 1fr;
                        }
                        body[data-layout="desktop"] .workspace.cast-visible {
                            grid-template-columns: minmax(340px, 42%) 1fr;
                            align-items: start;
                        }
                        @media (max-width: 1024px) {
                            body[data-layout="desktop"] .workspace.cast-visible {
                                grid-template-columns: 1fr;
                            }
                        }

                        /* Summary Metrics Bar */
                        .stats-strip {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
                            gap: 10px;
                            margin-bottom: 14px;
                        }
                        .stat-card {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            padding: 11px 14px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                        }
                        .stat-label {
                            font-size: 0.74rem;
                            color: var(--text-muted);
                            text-transform: uppercase;
                            letter-spacing: 0.04em;
                        }
                        .stat-val {
                            font-size: 1rem;
                            font-weight: 700;
                            color: #fff;
                            margin-top: 2px;
                        }

                        .panel {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: var(--radius);
                            overflow: hidden;
                            display: flex;
                            flex-direction: column;
                            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
                        }
                        .panel-header {
                            padding: 13px 16px;
                            background: var(--surface-elevated);
                            border-bottom: 1px solid var(--border);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 10px;
                            flex-wrap: wrap;
                        }
                        .panel-title {
                            font-weight: 700;
                            font-size: 0.92rem;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }

                        /* Interactive Breadcrumbs & Search Subbar */
                        .subbar {
                            padding: 10px 16px;
                            background: #0e131b;
                            border-bottom: 1px solid var(--border);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 12px;
                            flex-wrap: wrap;
                        }
                        .breadcrumbs {
                            display: flex;
                            align-items: center;
                            gap: 6px;
                            flex-wrap: wrap;
                            font-size: 0.82rem;
                        }
                        .crumb {
                            color: var(--text-muted);
                            text-decoration: none;
                            padding: 3px 8px;
                            border-radius: 6px;
                            background: rgba(255,255,255,0.04);
                            transition: all 0.14s;
                        }
                        .crumb:hover {
                            color: #fff;
                            background: var(--primary-soft);
                        }
                        .search-box {
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            flex: 1;
                            max-width: 340px;
                            min-width: 200px;
                        }
                        .search-input {
                            width: 100%;
                            background: var(--surface);
                            border: 1px solid var(--border);
                            color: var(--text);
                            padding: 7px 12px;
                            border-radius: 9px;
                            font-size: 0.82rem;
                            outline: none;
                        }
                        .search-input:focus {
                            border-color: var(--primary);
                        }

                        /* Interactive File Grid View */
                        .file-grid {
                            display: none;
                            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                            gap: 12px;
                            padding: 16px;
                        }
                        body[data-view="grid"] .file-grid {
                            display: grid;
                        }
                        body[data-view="grid"] .table-wrapper {
                            display: none;
                        }
                        .file-card {
                            background: var(--surface-elevated);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            padding: 14px;
                            display: flex;
                            flex-direction: column;
                            justify-content: space-between;
                            gap: 12px;
                            transition: all 0.16s ease;
                        }
                        .file-card:hover {
                            background: var(--surface-hover);
                            border-color: #435375;
                            transform: translateY(-2px);
                        }
                        .card-top {
                            display: flex;
                            align-items: flex-start;
                            gap: 11px;
                        }
                        .card-icon {
                            width: 40px;
                            height: 40px;
                            border-radius: 10px;
                            background: rgba(255,255,255,0.05);
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 20px;
                            flex-shrink: 0;
                        }
                        .card-title {
                            font-weight: 600;
                            color: var(--text);
                            text-decoration: none;
                            word-break: break-word;
                            display: -webkit-box;
                            -webkit-line-clamp: 2;
                            -webkit-box-orient: vertical;
                            overflow: hidden;
                            font-size: 0.86rem;
                        }
                        .card-title:hover { color: #818cf8; }
                        .card-meta {
                            font-size: 0.74rem;
                            color: var(--text-muted);
                            margin-top: 3px;
                        }
                        .card-actions {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 6px;
                            padding-top: 8px;
                            border-top: 1px solid rgba(255,255,255,0.06);
                        }
                        .badge-ext {
                            font-size: 0.68rem;
                            font-weight: 700;
                            padding: 2px 7px;
                            border-radius: 5px;
                            background: rgba(99, 102, 241, 0.15);
                            color: #a5b4fc;
                        }

                        /* Responsive Table View */
                        .table-wrapper {
                            width: 100%;
                            overflow-x: auto;
                        }
                        .file-table {
                            width: 100%;
                            border-collapse: collapse;
                        }
                        .file-table th {
                            text-align: left;
                            padding: 11px 16px;
                            font-size: 0.73rem;
                            text-transform: uppercase;
                            letter-spacing: 0.05em;
                            color: var(--text-muted);
                            border-bottom: 1px solid var(--border);
                            background: #10151f;
                            user-select: none;
                        }
                        .file-table td {
                            padding: 11px 16px;
                            border-bottom: 1px solid rgba(255,255,255,0.05);
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

                        @media (max-width: 680px) {
                            .col-type, .col-date { display: none; }
                            .file-table th, .file-table td { padding: 10px 12px; }
                        }

                        /* Hideable Screen Mirroring Stage */
                        .cast-stage {
                            background: #05070b;
                            min-height: 320px;
                            max-height: 78vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            position: relative;
                            padding: 12px;
                            overflow: hidden;
                        }
                        #screenCanvas {
                            max-width: 100%;
                            max-height: 72vh;
                            width: auto;
                            height: auto;
                            border-radius: 8px;
                            box-shadow: 0 8px 30px rgba(0,0,0,0.7);
                            transition: transform 0.2s ease;
                        }
                        .hidden { display: none !important; }

                        /* Modals (Upload & Lightbox Preview) */
                        .modal-backdrop {
                            position: fixed;
                            inset: 0;
                            background: rgba(5, 7, 12, 0.78);
                            backdrop-filter: blur(6px);
                            display: none;
                            align-items: center;
                            justify-content: center;
                            z-index: 200;
                            padding: 16px;
                        }
                        .modal {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 16px;
                            padding: 20px;
                            width: 100%;
                            max-width: 480px;
                            display: flex;
                            flex-direction: column;
                            gap: 14px;
                            box-shadow: 0 20px 50px rgba(0,0,0,0.6);
                        }
                        .modal-preview {
                            max-width: 960px;
                            max-height: 90vh;
                        }
                        .preview-body {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            background: #080a0f;
                            border-radius: 10px;
                            overflow: auto;
                            min-height: 260px;
                            max-height: 70vh;
                            padding: 12px;
                        }
                        .preview-body img, .preview-body video {
                            max-width: 100%;
                            max-height: 65vh;
                            border-radius: 6px;
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
            attributes["data-view"] = "grid"

            header {
                div("brand") {
                    div("brand-logo") { +"📁" }
                    div {
                        div("brand-title") { +"Explorer File Portal" }
                        div {
                            style = "font-size: 0.73rem; color: var(--text-muted);"
                            +"FTP :$ftpPort • HTTP :$port"
                        }
                    }
                    span("status-pill") { +"● Online" }
                    if (hasWebsiteEntry) {
                        a(href = "/", classes = "btn") {
                            +"🌐 Open Hosted Site"
                        }
                    }
                }

                div("toolbar") {
                    // View Mode Switcher (Interactive Cards Grid vs Table View)
                    div("segmented") {
                        button(classes = "seg-btn active") {
                            id = "btnViewGrid"
                            onClick = "setViewMode('grid')"
                            +"⊞ Grid"
                        }
                        button(classes = "seg-btn") {
                            id = "btnViewTable"
                            onClick = "setViewMode('table')"
                            +"☰ Table"
                        }
                    }

                    // Hideable Screen Mirroring Toggle Button (Hidden by default so file hosting UI is clean)
                    button(classes = if (hasScreencast) "btn btn-cast-active" else "btn") {
                        id = "btnToggleCastPlayer"
                        onClick = "toggleCastPlayer()"
                        +if (hasScreencast) "📺 Show Live Screen (Active)" else "📺 Show Live Screen"
                    }

                    if (allowUploads) {
                        button(classes = "btn btn-primary") {
                            onClick = "openUploadModal()"
                            +"⬆ Upload Files"
                        }
                    }
                }
            }

            // Main Responsive Workspace (Screen Share Player starts HIDDEN by default)
            div(classes = "workspace") {
                id = "mainWorkspace"

                // Hideable Live Screen Mirroring Panel
                div(classes = "panel hidden") {
                    id = "castPanel"
                    div("panel-header") {
                        div("panel-title") {
                            +"📺 Live Device Screen Stream"
                            span("status-pill") {
                                id = "castStatusBadge"
                                +if (hasScreencast) "LIVE" else "STANDBY"
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
                            button(classes = "btn") {
                                onClick = "toggleCastPlayer(false)"
                                +"✕ Hide Player"
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

                // Main File Hosting & Explorer Panel
                div {
                    // Summary Metrics Strip
                    div("stats-strip") {
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Hosted Mode" }
                                div("stat-val") {
                                    +when (hostedMode) {
                                        FtpHostedMode.BATCH_FILES -> "Selected Bucket"
                                        FtpHostedMode.CUSTOM_FOLDER -> "Website Folder"
                                        FtpHostedMode.FULL_STORAGE -> "Device Storage"
                                    }
                                }
                            }
                            span { style = "font-size: 20px;"; +"🗂️" }
                        }
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Folders & Files" }
                                div("stat-val") { +"$folderCount folders • $fileCount files" }
                            }
                            span { style = "font-size: 20px;"; +"📊" }
                        }
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Directory Size" }
                                div("stat-val") { +formatBytes(totalBytes) }
                            }
                            span { style = "font-size: 20px;"; +"💾" }
                        }
                    }

                    div("panel") {
                        id = "explorerPanel"
                        div("panel-header") {
                            div("panel-title") {
                                +when (hostedMode) {
                                    FtpHostedMode.BATCH_FILES -> "Shared Files & Folders (${files.size})"
                                    FtpHostedMode.CUSTOM_FOLDER -> "Website Assets (${currentDir.name})"
                                    FtpHostedMode.FULL_STORAGE -> currentDir.name.ifBlank { "Storage" }
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
                            div("breadcrumbs") {
                                a(href = "/?explorer=true", classes = "crumb") { +"⌂ Root" }
                                if (!isRootView) {
                                    span { style = "color: var(--text-muted);"; +"/" }
                                    span("crumb") { +currentDir.name }
                                }
                            }
                            div("search-box") {
                                input {
                                    id = "fileSearchInput"
                                    classes = setOf("search-input")
                                    type = InputType.search
                                    placeholder = "Search files & folders..."
                                    onInput = "filterPortalFiles(this.value)"
                                }
                            }
                        }

                        // 1. INTERACTIVE GRID CARDS VIEW
                        div("file-grid") {
                            id = "fileGridContainer"
                            if (files.isEmpty()) {
                                div {
                                    style = "grid-column: 1 / -1; text-align: center; padding: 36px; color: var(--text-muted);"
                                    +"This directory is empty."
                                }
                            } else {
                                for (file in files) {
                                    val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                                    val isDir = file.isDirectory
                                    val (emoji, badge) = getFileEmojiAndBadge(file)
                                    val sizeLabel = if (isDir) {
                                        val count = try { file.list()?.size ?: 0 } catch (_: Exception) { 0 }
                                        "$count items"
                                    } else {
                                        formatBytes(file.length())
                                    }
                                    val safeJsName = file.name.replace("'", "\\'")
                                    val extLower = file.extension.lowercase(Locale.US)
                                    val isPreviewableMedia = !isDir && extLower in listOf(
                                        "jpg", "jpeg", "png", "gif", "webp", "svg", "mp4", "webm", "mp3", "wav", "ogg"
                                    )

                                    div("file-card file-item-entry") {
                                        attributes["data-name"] = file.name.lowercase(Locale.US)
                                        div("card-top") {
                                            div("card-icon") { +emoji }
                                            div {
                                                style = "flex: 1; min-width: 0;"
                                                if (isDir) {
                                                    a(classes = "card-title", href = "/?explorer=true&path=$encodedPath") {
                                                        +file.name
                                                    }
                                                } else if (isPreviewableMedia) {
                                                    a(
                                                        classes = "card-title",
                                                        href = "javascript:void(0)"
                                                    ) {
                                                        attributes["onclick"] = "openPreviewModal('$safeJsName', '/download?file=$encodedPath&inline=true', '$extLower')"
                                                        +file.name
                                                    }
                                                } else {
                                                    a(classes = "card-title", href = "/download?file=$encodedPath&inline=true", target = "_blank") {
                                                        +file.name
                                                    }
                                                }
                                                div("card-meta") {
                                                    +"$sizeLabel • ${dateFormat.format(Date(file.lastModified()))}"
                                                }
                                            }
                                        }
                                        div("card-actions") {
                                            span("badge-ext") { +badge }
                                            div("toolbar") {
                                                if (isDir) {
                                                    a(classes = "btn", href = "/?explorer=true&path=$encodedPath") {
                                                        +"Open →"
                                                    }
                                                } else {
                                                    if (isPreviewableMedia) {
                                                        button(classes = "btn") {
                                                            onClick = "openPreviewModal('$safeJsName', '/download?file=$encodedPath&inline=true', '$extLower')"
                                                            +"👁 Preview"
                                                        }
                                                    }
                                                    a(classes = "btn btn-primary", href = "/download?file=$encodedPath") {
                                                        +"⬇ Save"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. DETAILED TABLE VIEW
                        div("table-wrapper") {
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
                                            val (emoji, badge) = getFileEmojiAndBadge(file)
                                            tr(classes = "file-row file-item-entry") {
                                                attributes["data-name"] = file.name.lowercase(Locale.US)
                                                td {
                                                    if (isDir) {
                                                        a(classes = "item-link", href = "/?explorer=true&path=$encodedPath") {
                                                            +"$emoji ${file.name}"
                                                        }
                                                    } else {
                                                        a(classes = "item-link", href = "/download?file=$encodedPath&inline=true", target = "_blank") {
                                                            +"$emoji ${file.name}"
                                                        }
                                                    }
                                                }
                                                td(classes = "col-type") {
                                                    span("badge-ext") { +badge }
                                                }
                                                td {
                                                    style = "color: var(--text-muted); font-size: 0.82rem;"
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
                                                        a(classes = "btn btn-primary", href = "/download?file=$encodedPath") {
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
                }
            }

            // Lightbox Media Preview Modal
            div("modal-backdrop") {
                id = "previewModal"
                div("modal modal-preview") {
                    div {
                        style = "display: flex; justify-content: space-between; align-items: center; gap: 10px;"
                        h3 {
                            id = "previewModalTitle"
                            style = "font-size: 0.95rem; word-break: break-all;"
                            +"Preview"
                        }
                        button(classes = "btn") {
                            onClick = "closePreviewModal()"
                            +"✕ Close"
                        }
                    }
                    div("preview-body") {
                        id = "previewModalBody"
                    }
                    div {
                        style = "display: flex; justify-content: flex-end; gap: 8px;"
                        a(classes = "btn btn-primary", href = "#") {
                            id = "previewDownloadBtn"
                            +"⬇ Download File"
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
                        let isCastVisible = false;
                        let castSocket = null;
                        let castPollingTimer = null;

                        function setViewMode(mode) {
                            document.body.setAttribute('data-view', mode);
                            document.getElementById('btnViewGrid').classList.toggle('active', mode === 'grid');
                            document.getElementById('btnViewTable').classList.toggle('active', mode === 'table');
                            try { localStorage.setItem('explorer_portal_view', mode); } catch (e) {}
                        }

                        // Restore saved view preference
                        try {
                            const savedView = localStorage.getItem('explorer_portal_view');
                            if (savedView === 'table' || savedView === 'grid') {
                                setViewMode(savedView);
                            }
                        } catch (e) {}

                        function toggleCastPlayer(forceState) {
                            const castPanel = document.getElementById('castPanel');
                            const ws = document.getElementById('mainWorkspace');
                            const btn = document.getElementById('btnToggleCastPlayer');
                            isCastVisible = typeof forceState === 'boolean' ? forceState : !isCastVisible;

                            if (isCastVisible) {
                                castPanel.classList.remove('hidden');
                                ws.classList.add('cast-visible');
                                btn.innerText = '📺 Hide Live Screen';
                                startCastConnection();
                            } else {
                                castPanel.classList.add('hidden');
                                ws.classList.remove('cast-visible');
                                btn.innerText = '📺 Show Live Screen';
                            }
                        }

                        function filterPortalFiles(query) {
                            const q = (query || '').toLowerCase().trim();
                            const items = document.querySelectorAll('.file-item-entry');
                            items.forEach(el => {
                                const name = el.getAttribute('data-name') || '';
                                el.style.display = (!q || name.includes(q)) ? '' : 'none';
                            });
                        }

                        function openPreviewModal(name, url, ext) {
                            const modal = document.getElementById('previewModal');
                            const title = document.getElementById('previewModalTitle');
                            const body = document.getElementById('previewModalBody');
                            const dl = document.getElementById('previewDownloadBtn');
                            title.innerText = name;
                            dl.href = url.replace('&inline=true', '');
                            body.innerHTML = '';

                            if (['jpg','jpeg','png','gif','webp','svg'].includes(ext)) {
                                const img = document.createElement('img');
                                img.src = url;
                                body.appendChild(img);
                            } else if (['mp4','webm'].includes(ext)) {
                                const vid = document.createElement('video');
                                vid.src = url;
                                vid.controls = true;
                                vid.autoplay = true;
                                body.appendChild(vid);
                            } else if (['mp3','wav','ogg'].includes(ext)) {
                                const aud = document.createElement('audio');
                                aud.src = url;
                                aud.controls = true;
                                aud.autoplay = true;
                                body.appendChild(aud);
                            }
                            modal.style.display = 'flex';
                        }

                        function closePreviewModal() {
                            const modal = document.getElementById('previewModal');
                            const body = document.getElementById('previewModalBody');
                            body.innerHTML = '';
                            modal.style.display = 'none';
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
                                    setTimeout(() => window.location.reload(), 800);
                                } else {
                                    notice.innerText = data.message || 'Upload failed';
                                    notice.style.color = '#ef4444';
                                }
                            } catch (e) {
                                notice.innerText = 'Error: ' + e.message;
                                notice.style.color = '#ef4444';
                            }
                        }

                        function drawBlobToCanvas(blob) {
                            const canvas = document.getElementById('screenCanvas');
                            const ctx = canvas ? canvas.getContext('2d') : null;
                            if (!canvas || !ctx) return;
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
                        }

                        function startHttpSnapshotFallback() {
                            if (castPollingTimer) return;
                            const badge = document.getElementById('castStatusBadge');
                            castPollingTimer = setInterval(async () => {
                                if (!isCastVisible) return;
                                try {
                                    const res = await fetch('/screencast-frame?t=' + Date.now(), { cache: 'no-store' });
                                    if (res.status === 200) {
                                        const blob = await res.blob();
                                        drawBlobToCanvas(blob);
                                        if (badge) {
                                            badge.innerText = 'LIVE STREAM';
                                            badge.style.color = '#10b981';
                                        }
                                    } else if (badge) {
                                        badge.innerText = 'STANDBY (Start Mirroring in App)';
                                    }
                                } catch (e) {}
                            }, 160);
                        }

                        function startCastConnection() {
                            if (castSocket && (castSocket.readyState === WebSocket.OPEN || castSocket.readyState === WebSocket.CONNECTING)) {
                                return;
                            }
                            const badge = document.getElementById('castStatusBadge');
                            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const wsUrl = protocol + '//' + window.location.host + '/screencast';

                            try {
                                castSocket = new WebSocket(wsUrl);
                                castSocket.binaryType = 'arraybuffer';

                                castSocket.onopen = function() {
                                    if (badge) {
                                        badge.innerText = 'LIVE STREAM';
                                        badge.style.color = '#10b981';
                                    }
                                };

                                castSocket.onmessage = function(ev) {
                                    if (typeof ev.data === 'string') {
                                        if (ev.data === 'INACTIVE' && badge) {
                                            badge.innerText = 'STANDBY (Start Mirroring in App)';
                                        }
                                        return;
                                    }
                                    const blob = new Blob([ev.data], { type: 'image/jpeg' });
                                    drawBlobToCanvas(blob);
                                };

                                castSocket.onerror = function() {
                                    startHttpSnapshotFallback();
                                };

                                castSocket.onclose = function() {
                                    startHttpSnapshotFallback();
                                };
                            } catch (e) {
                                startHttpSnapshotFallback();
                            }
                        }
                    """)
                }
            }
        }
    }
}
