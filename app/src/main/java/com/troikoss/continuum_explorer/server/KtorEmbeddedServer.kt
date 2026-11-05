package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.Environment
import android.util.Log
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.isActive
import kotlinx.html.*
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration

import io.ktor.server.request.*
import io.ktor.http.content.*
import com.troikoss.continuum_explorer.managers.SettingsManager

/**
 * KtorEmbeddedServer
 *
 * Lightweight, Netty-backed Ktor HTTP & WebSocket server operating on port 8080.
 * Serves an interactive HTML5 Continuum Dashboard, downloadable files from storage,
 * file upload handler with security password verification,
 * and a live low-latency /screencast WebSocket stream.
 */
class KtorEmbeddedServer(
    private val context: Context,
    private val port: Int = 8080,
    private val rootDirectory: File = Environment.getExternalStorageDirectory(),
    private val getScreenCastManager: () -> ScreenCastManager?
) {
    companion object {
        private const val TAG = "KtorEmbeddedServer"
    }

    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return

        val effectiveRootDir = if (rootDirectory.exists()) rootDirectory else Environment.getExternalStorageDirectory()

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
                    // HTML5 Dashboard & Static HTML Server Interface
                    get("/") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val forceExplorer = call.parameters["explorer"] == "true"
                        val requestedPath = call.parameters["path"]?.let {
                            URLDecoder.decode(it, "UTF-8")
                        }
                        val rootDir = effectiveRootDir
                        val currentDir = if (!requestedPath.isNullOrEmpty()) {
                            val f = File(requestedPath)
                            if (f.exists() && f.isDirectory && f.canonicalPath.startsWith(rootDir.canonicalPath)) {
                                f
                            } else {
                                rootDir
                            }
                        } else {
                            rootDir
                        }

                        val indexHtml = File(currentDir, "index.html")
                        if (!forceExplorer && requestedPath.isNullOrEmpty() && indexHtml.exists() && indexHtml.isFile) {
                            ContinuumHubService.addLog(clientIp, "Website", "Served index.html", indexHtml.name, bytes = indexHtml.length())
                            call.respondFile(indexHtml)
                        } else {
                            ContinuumHubService.addLog(clientIp, "Website", "Browsed directory", currentDir.name)
                            val files = currentDir.listFiles()?.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            ) ?: emptyList()

                            call.respondHtml(HttpStatusCode.OK) {
                                renderDashboard(currentDir, rootDir, files)
                            }
                        }
                    }

                    // Direct File Download / Stream route
                    get("/download") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val filePath = call.parameters["file"]?.let {
                            URLDecoder.decode(it, "UTF-8")
                        }
                        if (filePath.isNullOrEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, "Missing file parameter")
                            return@get
                        }

                        val file = File(filePath)
                        val rootDir = effectiveRootDir
                        if (file.exists() && file.isFile && (file.canonicalPath.startsWith(rootDir.canonicalPath) || file.canonicalPath.startsWith(Environment.getExternalStorageDirectory().canonicalPath))) {
                            ContinuumHubService.addLog(clientIp, "HTTP", "Downloaded ${file.name}", "${file.length()} bytes", bytes = file.length())
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    file.name
                                ).toString()
                            )
                            call.respondFile(file)
                        } else {
                            ContinuumHubService.addLog(clientIp, "HTTP", "404 Not Found", filePath)
                            call.respond(HttpStatusCode.NotFound, "File not found or access restricted")
                        }
                    }

                    // Live Screen Sharing WebSocket Route
                    webSocket("/screencast") {
                        val screenCast = getScreenCastManager()
                        if (screenCast == null) {
                            send(Frame.Text("ERROR: Screen capture is not active. Grant permission in Explorer app."))
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

                    // API Status route
                    get("/api/status") {
                        val hasScreencast = getScreenCastManager() != null
                        call.respondText(
                            """{"status":"online","port":$port,"screencast":$hasScreencast}""",
                            ContentType.Application.Json
                        )
                    }

                    // Security Password-Gated Multipart File Upload Route
                    post("/upload") {
                        val expectedPass = SettingsManager.ftpUploadPassword.value
                        val isUploadAllowed = SettingsManager.ftpAllowUpload.value

                        if (!isUploadAllowed) {
                            call.respondText("""{"success":false,"message":"Uploads are disabled by host"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                            return@post
                        }

                        var providedPass = call.request.headers["X-Upload-Password"] ?: ""
                        var targetFolderPath = effectiveRootDir.absolutePath
                        val multipart = call.receiveMultipart()
                        var savedFilesCount = 0

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "password") {
                                        providedPass = part.value
                                    } else if (part.name == "targetDir" && part.value.isNotBlank()) {
                                        val f = File(part.value)
                                        if (f.exists() && f.isDirectory) {
                                            targetFolderPath = f.absolutePath
                                        }
                                    }
                                }
                                is PartData.FileItem -> {
                                    if (providedPass == expectedPass) {
                                        val originalName = part.originalFileName ?: "uploaded_${System.currentTimeMillis()}"
                                        val saveFile = File(targetFolderPath, originalName)
                                        part.streamProvider().use { input ->
                                            saveFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        savedFilesCount++
                                        ContinuumHubService.recordTransfer(saveFile.length(), isUpload = true)
                                    }
                                }
                                else -> {}
                            }
                            part.dispose()
                        }

                        if (providedPass != expectedPass) {
                            call.respondText("""{"success":false,"message":"Incorrect upload password"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        } else {
                            call.respondText("""{"success":true,"message":"Successfully uploaded $savedFilesCount file(s)"}""", ContentType.Application.Json, HttpStatusCode.OK)
                        }
                    }
                }
            }.start(wait = false)

            Log.i(TAG, "Ktor Server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ktor Server", e)
        }
    }

    fun stop() {
        try {
            engine?.stop(1000, 2000)
            engine = null
            Log.i(TAG, "Ktor Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ktor Server", e)
        }
    }

    private fun HTML.renderDashboard(currentDir: File, rootDir: File, files: List<File>) {
        head {
            title("Explorer Continuum Hub")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            style {
                unsafe {
                    raw("""
                        :root {
                            --bg: #121212;
                            --surface: #1E1E1E;
                            --surface-hover: #292929;
                            --primary: #4CAF50;
                            --accent: #00E676;
                            --text: #E0E0E0;
                            --text-secondary: #9E9E9E;
                            --border: #333333;
                            --screencast-bg: #0A0A0A;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            background-color: var(--bg);
                            color: var(--text);
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                            font-size: 13px;
                            display: flex;
                            flex-direction: column;
                            min-height: 100vh;
                        }
                        header {
                            background: var(--surface);
                            border-bottom: 1px solid var(--border);
                            padding: 12px 18px;
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            flex-wrap: wrap;
                            gap: 10px;
                        }
                        .brand { display: flex; align-items: center; gap: 10px; }
                        .brand h1 { font-size: 1.05rem; font-weight: 600; color: #FFF; }
                        .badge {
                            background: rgba(0, 230, 118, 0.15);
                            color: var(--accent);
                            font-size: 0.7rem;
                            font-weight: 600;
                            padding: 3px 6px;
                            border-radius: 4px;
                            border: 1px solid rgba(0, 230, 118, 0.3);
                        }
                        .container {
                            display: flex;
                            flex-direction: column;
                            gap: 16px;
                            padding: 16px;
                            flex: 1;
                        }
                        .screen-pane-hidden {
                            display: none !important;
                        }
                        @media (max-width: 768px) {
                            .container { padding: 10px; gap: 12px; }
                            header { padding: 10px 12px; }
                            .brand h1 { font-size: 0.95rem; }
                        }
                        .card {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 8px;
                            overflow: hidden;
                            display: flex;
                            flex-direction: column;
                        }
                        .card-header {
                            padding: 10px 14px;
                            background: #252525;
                            border-bottom: 1px solid var(--border);
                            font-weight: 600;
                            font-size: 0.85rem;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }
                        .card-body { padding: 12px; flex: 1; overflow-y: auto; }
                        .path-bar {
                            background: #181818;
                            padding: 8px 12px;
                            font-family: monospace;
                            font-size: 0.78rem;
                            color: var(--text-secondary);
                            border-bottom: 1px solid var(--border);
                            word-break: break-all;
                        }
                        .file-list { list-style: none; }
                        .file-item {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            padding: 10px 14px;
                            border-bottom: 1px solid #282828;
                            transition: background 0.15s;
                        }
                        .file-item:hover { background: var(--surface-hover); }
                        .file-link {
                            color: var(--text);
                            text-decoration: none;
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            overflow: hidden;
                            text-overflow: ellipsis;
                            white-space: nowrap;
                        }
                        .file-link:hover { color: var(--accent); }
                        .btn-download {
                            background: #333;
                            color: #FFF;
                            text-decoration: none;
                            padding: 4px 10px;
                            border-radius: 4px;
                            font-size: 0.8rem;
                        }
                        .btn-download:hover { background: var(--primary); }
                        
                        /* Desktop vs Mobile View Grid mode */
                        .view-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
                            gap: 10px;
                            list-style: none;
                        }
                        .view-grid .file-item {
                            flex-direction: column;
                            align-items: center;
                            text-align: center;
                            padding: 14px 8px;
                            background: #222;
                            border-radius: 6px;
                            border: 1px solid #2A2A2A;
                        }
                        .view-grid .file-link {
                            flex-direction: column;
                            white-space: normal;
                            max-width: 100%;
                            word-break: break-word;
                            font-size: 0.8rem;
                        }
                        .view-grid .btn-download {
                            margin-top: 8px;
                        }

                        .canvas-container {
                            background: var(--screencast-bg);
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            min-height: 380px;
                            position: relative;
                        }
                        canvas {
                            max-width: 100%;
                            height: auto;
                            border-radius: 4px;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                        }
                        .stream-status {
                            position: absolute;
                            top: 10px;
                            right: 10px;
                            padding: 4px 8px;
                            font-size: 0.75rem;
                            border-radius: 4px;
                            background: rgba(0,0,0,0.7);
                            color: #FFF;
                        }
                        .header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
                        .btn-header {
                            background: #4CAF50;
                            color: #FFF;
                            border: none;
                            padding: 6px 12px;
                            border-radius: 6px;
                            font-size: 0.82rem;
                            font-weight: 600;
                            cursor: pointer;
                            text-decoration: none;
                            display: inline-flex;
                            align-items: center;
                            gap: 6px;
                        }
                        .btn-header:hover { background: #388E3C; }
                        .btn-screen { background: #00897B; }
                        .btn-screen:hover { background: #00695C; }
                        .btn-viewmode { background: #37474F; }
                        .btn-viewmode:hover { background: #455A64; }
                        .modal-overlay {
                            position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
                            background: rgba(0,0,0,0.75); display: none; justify-content: center; align-items: center; z-index: 1000;
                        }
                        .modal-card {
                            background: #222; border: 1px solid #444; border-radius: 12px; padding: 24px;
                            width: 90%; max-width: 420px; display: flex; flex-direction: column; gap: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.8);
                        }
                        .modal-card h2 { font-size: 1.1rem; color: #FFF; }
                        .modal-card input {
                            background: #111; border: 1px solid #444; border-radius: 6px; padding: 10px; color: #FFF; font-size: 0.9rem;
                        }
                        .modal-actions { display: flex; justify-content: flex-end; gap: 10px; }
                        .btn-cancel { background: #444; color: #CCC; border: none; padding: 8px 14px; border-radius: 6px; cursor: pointer; }
                    """)
                }
            }
        }
        body {
            header {
                div("brand") {
                    h1 { +"Explorer Continuum Hub" }
                    span("badge") { +"ACTIVE" }
                }
                div("header-actions") {
                    val hasScreencast = getScreenCastManager() != null
                    if (hasScreencast) {
                        button(classes = "btn-header btn-screen") {
                            onClick = "toggleScreenShare()"
                            id = "btnToggleScreen"
                            +"📺 Show Screen"
                        }
                    }
                    button(classes = "btn-header btn-viewmode") {
                        onClick = "toggleViewMode()"
                        id = "btnToggleView"
                        +"📱 Mobile View"
                    }
                    if (SettingsManager.ftpAllowUpload.value) {
                        button(classes = "btn-header") {
                            onClick = "openUploadModal()"
                            +"⬆ Upload Files"
                        }
                    }
                    span {
                        style = "font-size: 0.85rem; color: #9E9E9E; margin-left: 8px;"
                        +"HTTP: $port | FTP: 2121"
                    }
                }
            }

            // Hidden Modal Dialog for File Upload
            div {
                id = "uploadModal"
                classes = setOf("modal-overlay")
                div("modal-card") {
                    h2 { +"⬆ Security File Upload" }
                    p {
                        style = "font-size: 0.8rem; color: #AAA;"
                        +"Enter host security password to upload files to ${currentDir.name}"
                    }
                    input {
                        id = "uploadPasswordInput"
                        type = InputType.password
                        placeholder = "Upload Security Password"
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
                    div("modal-actions") {
                        button(classes = "btn-cancel") {
                            onClick = "closeUploadModal()"
                            +"Cancel"
                        }
                        button(classes = "btn-header") {
                            onClick = "submitFileUpload()"
                            +"Upload Now"
                        }
                    }
                    span {
                        id = "uploadStatusNotice"
                        style = "font-size: 0.8rem; color: #00E676;"
                    }
                }
            }

            div("container") {
                // Screencast Pane (hidden by default, toggled via screen button)
                div("card screen-pane-hidden") {
                    id = "screenCard"
                    div("card-header") {
                        span { +"Live Screen Cast" }
                        div {
                            span("badge") { id = "cast-status"; +"Connecting..." }
                            button(classes = "btn-header btn-screen") {
                                style = "margin-left: 10px; padding: 2px 8px; font-size: 0.75rem;"
                                onClick = "toggleScreenShare()"
                                +"✕ Hide"
                            }
                        }
                    }
                    div("canvas-container") {
                        canvas {
                            id = "screenCanvas"
                            width = "1280"
                            height = "720"
                        }
                    }
                }

                // File Explorer & Downloads Pane
                div("card") {
                    div("card-header") {
                        span { +"Storage & Downloads" }
                        if (currentDir.canonicalPath != rootDir.canonicalPath && currentDir.parentFile != null) {
                            val parentEncoded = URLEncoder.encode(currentDir.parentFile.absolutePath, "UTF-8")
                            a(href = "/?path=$parentEncoded") {
                                style = "color: #4CAF50; text-decoration: none; font-size: 0.85rem;"
                                +"↑ Parent Folder"
                            }
                        }
                    }
                    div("path-bar") {
                        +currentDir.absolutePath
                    }
                    div("card-body") {
                        ul("file-list") {
                            id = "mainFileList"
                            for (file in files) {
                                val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                                li("file-item") {
                                    if (file.isDirectory) {
                                        a(classes = "file-link", href = "/?path=$encodedPath") {
                                            +"📁 ${file.name}"
                                        }
                                        span {
                                            style = "color: #777; font-size: 0.8rem;"
                                            +"Folder"
                                        }
                                    } else {
                                        div {
                                            span("file-link") {
                                                +"📄 ${file.name}"
                                            }
                                            span {
                                                style = "color: #666; font-size: 0.75rem; margin-left: 24px;"
                                                +"${file.length() / 1024} KB"
                                            }
                                        }
                                        a(classes = "btn-download", href = "/download?file=$encodedPath") {
                                            +"Download"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // JavaScript WebSocket Receiver & Upload Manager
            script {
                unsafe {
                    raw("""
                        window.toggleScreenShare = function() {
                            const card = document.getElementById('screenCard');
                            const btn = document.getElementById('btnToggleScreen');
                            if (!card) return;
                            const isHidden = card.classList.contains('screen-pane-hidden');
                            if (isHidden) {
                                card.classList.remove('screen-pane-hidden');
                                if (btn) btn.innerText = '📺 Hide Screen';
                                card.scrollIntoView({ behavior: 'smooth' });
                            } else {
                                card.classList.add('screen-pane-hidden');
                                if (btn) btn.innerText = '📺 Show Screen';
                            }
                        };

                        let isDesktopView = true;
                        window.toggleViewMode = function() {
                            const list = document.getElementById('mainFileList');
                            const btn = document.getElementById('btnToggleView');
                            if (!list) return;
                            isDesktopView = !isDesktopView;
                            if (isDesktopView) {
                                list.classList.remove('view-grid');
                                if (btn) btn.innerText = '📱 Mobile View';
                            } else {
                                list.classList.add('view-grid');
                                if (btn) btn.innerText = '💻 Desktop View';
                            }
                        };

                        window.openUploadModal = function() {
                            document.getElementById('uploadModal').style.display = 'flex';
                        };
                        window.closeUploadModal = function() {
                            document.getElementById('uploadModal').style.display = 'none';
                            document.getElementById('uploadStatusNotice').innerText = '';
                        };
                        window.submitFileUpload = async function() {
                            const pass = document.getElementById('uploadPasswordInput').value;
                            const filesInput = document.getElementById('uploadFilesInput');
                            const targetDir = document.getElementById('currentTargetDir').value;
                            const statusNotice = document.getElementById('uploadStatusNotice');

                            if (!pass) { alert('Please enter upload security password'); return; }
                            if (!filesInput.files || filesInput.files.length === 0) { alert('Please select at least one file to upload'); return; }

                            const formData = new FormData();
                            formData.append('password', pass);
                            formData.append('targetDir', targetDir);
                            for (let i = 0; i < filesInput.files.length; i++) {
                                formData.append('file_' + i, filesInput.files[i]);
                            }

                            statusNotice.innerText = 'Uploading files... Please wait.';
                            statusNotice.style.color = '#FFA726';

                            try {
                                const response = await fetch('/upload', { method: 'POST', body: formData });
                                const result = await response.json();
                                if (response.ok && result.success) {
                                    statusNotice.innerText = 'Upload successful!';
                                    statusNotice.style.color = '#00E676';
                                    setTimeout(() => { window.location.reload(); }, 1200);
                                } else {
                                    alert('Upload failed: ' + (result.message || 'Incorrect password'));
                                    statusNotice.innerText = 'Failed: ' + (result.message || 'Incorrect password');
                                    statusNotice.style.color = '#EF5350';
                                }
                            } catch (e) {
                                alert('Upload error: ' + e.message);
                                statusNotice.innerText = 'Error: ' + e.message;
                                statusNotice.style.color = '#EF5350';
                            }
                        };

                        (function() {
                            const canvas = document.getElementById('screenCanvas');
                            const ctx = canvas.getContext('2d');
                            const status = document.getElementById('cast-status');

                            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const wsUrl = protocol + '//' + window.location.host + '/screencast';

                            function connectWebSocket() {
                                if (!status) return;
                                status.innerText = 'Connecting...';
                                status.style.color = '#FFA726';

                                const socket = new WebSocket(wsUrl);
                                socket.binaryType = 'arraybuffer';

                                socket.onopen = function() {
                                    status.innerText = 'STREAMING';
                                    status.style.color = '#00E676';
                                };

                                socket.onmessage = function(event) {
                                    if (typeof event.data === 'string') {
                                        console.log('Server info:', event.data);
                                        return;
                                    }

                                    const blob = new Blob([event.data], { type: 'image/jpeg' });
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
                                    status.innerText = 'Disconnected';
                                    status.style.color = '#EF5350';
                                    setTimeout(connectWebSocket, 3000);
                                };

                                socket.onerror = function(err) {
                                    console.error('WebSocket Error:', err);
                                };
                            }

                            connectWebSocket();
                        })();
                    """)
                }
            }
        }
    }
}
