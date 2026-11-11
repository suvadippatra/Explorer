package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.converter.ConversionCategory
import com.troikoss.continuum_explorer.ui.converter.ConversionFormatMatrix
import com.troikoss.continuum_explorer.ui.converter.ConversionTuningConfig
import com.troikoss.continuum_explorer.ui.converter.DocumentDataConversionEngine
import com.troikoss.continuum_explorer.ui.converter.ImageConversionEngine
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.http.defaultForFile
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.html.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Dedicated Ktor Netty HTTP & WebSocket Server.
 * Keeps HTTP routing, Byte-Range media streaming, SyncPlay WebSockets, Screencast MJPEG,
 * and the Remote Web Portal / DeX-style Mini-Apps Studio self-contained while exposing
 * lifecycle hooks for [ServerManager].
 */
class KtorEmbeddedServer(
    private val appContext: Context,
    val port: Int = 8080
) {
    private var server: ApplicationEngine? = null
    private val activeRequests = AtomicInteger(0)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun getActiveRequestsCount(): Int = activeRequests.get().coerceAtLeast(0)

    @Throws(Exception::class)
    fun startOrThrow() {
        if (isRunning && server != null) return

        val engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Range)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
            }
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(45)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                // --- 0. WEBSOCKET ENDPOINT FOR BEATSYNC-STYLE SYNCPLAY PARTY ---
                webSocket("/ws/syncplay") {
                    val clientIp = call.request.origin.remoteHost
                    val peerId = call.request.queryParameters["peerId"] ?: UUID.randomUUID().toString().take(8)
                    val peerName = call.request.queryParameters["name"] ?: "Guest-${clientIp.substringAfterLast('.')}"
                    ContinuumHubService.recordLog(clientIp, "SYNC", "WS_JOIN", peerName)
                    SyncPlayManager.handleWebSocketSession(
                        peerId = peerId,
                        clientIp = clientIp,
                        initialName = peerName,
                        session = this,
                        appContext = appContext.applicationContext
                    )
                }

                // --- 0B. SYNCPLAY REST API & HTTP 206 BYTE-RANGE MEDIA STREAM ---
                get("/api/syncplay/state") {
                    val json = SyncPlayManager.buildStateJson("STATE")
                    call.respondText(json.toString(), ContentType.Application.Json)
                }

                get("/api/syncplay/stream") {
                    val state = SyncPlayManager.roomState.value
                    val file = if (state.isActive && state.mediaFilePath.isNotBlank()) File(state.mediaFilePath) else null
                    if (file == null || !file.exists() || !file.isFile) {
                        call.respondText("No active SyncPlay media file", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    activeRequests.incrementAndGet()
                    try {
                        call.respondFileWithRangeSupport(file, inline = true)
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }

                post("/api/syncplay/command") {
                    val clientIp = call.request.origin.remoteHost
                    try {
                        val body = JSONObject(call.receiveText())
                        val action = body.optString("action", "PLAY").uppercase(Locale.ROOT)
                        val actorName = body.optString("name", "Web-${clientIp.substringAfterLast('.')}").take(28)
                        val passcode = body.optString("passcode", "").trim()

                        if (action == "LOAD_HOST_FILE") {
                            val path = body.optString("path", "").trim()
                            val targetFile = File(path)
                            val roomState = SyncPlayManager.roomState.value
                            if (roomState.requiresPasscode && passcode != roomState.passcode) {
                                val err = JSONObject().put("success", false).put("message", "Invalid room passcode.")
                                call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                                return@post
                            }
                            if (!targetFile.exists() || !targetFile.isFile || !isFileAccessPermitted(targetFile) || !SyncPlayManager.isSupportedMedia(targetFile)) {
                                val err = JSONObject().put("success", false).put("message", "Select a valid shared audio or video file.")
                                call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                                return@post
                            }
                            SyncPlayManager.loadMediaFile(
                                context = appContext.applicationContext,
                                file = targetFile,
                                passcode = roomState.passcode,
                                allowGuestControl = roomState.allowGuestControl,
                                autoStartServer = false
                            )
                            ContinuumHubService.recordLog(clientIp, "SYNC", "LOAD", targetFile.name)
                            val res = JSONObject()
                                .put("success", true)
                                .put("state", SyncPlayManager.buildStateJson("LOAD"))
                            call.respondText(res.toString(), ContentType.Application.Json)
                            return@post
                        }

                        val posMs = if (body.has("positionMs")) body.optLong("positionMs") else null
                        val speed = if (body.has("speed")) body.optDouble("speed", 1.0).toFloat() else null
                        val ok = SyncPlayManager.triggerScheduledCommand(
                            context = appContext.applicationContext,
                            action = action,
                            targetPositionMs = posMs,
                            speed = speed,
                            actorName = actorName,
                            providedPasscode = passcode
                        )
                        if (!ok) {
                            val err = JSONObject()
                                .put("success", false)
                                .put("message", "Command rejected: check passcode or guest control permission.")
                            call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        } else {
                            val res = JSONObject()
                                .put("success", true)
                                .put("state", SyncPlayManager.buildStateJson("COMMAND"))
                            call.respondText(res.toString(), ContentType.Application.Json)
                        }
                    } catch (e: Exception) {
                        val err = JSONObject().put("success", false).put("message", e.message ?: "Error")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    }
                }

                // --- 1. ROOT PORTAL OR SELF-HOSTED WEBSITE ENTRY ---
                get("/") {
                    val clientIp = call.request.origin.remoteHost
                    val hostedMode = SettingsManager.ftpHostedMode.value
                    val customPath = SettingsManager.ftpHostedPath.value
                    val subPath = call.request.queryParameters["path"] ?: ""
                    val forcePortal = call.request.queryParameters["portal"] == "1"
                    val initialTab = call.request.queryParameters["tab"] ?: "files"

                    ContinuumHubService.recordLog(clientIp, "HTTP", "GET /", if (subPath.isEmpty()) "Root" else subPath)

                    if (hostedMode == FtpHostedMode.CUSTOM_FOLDER && customPath.isNotBlank() && !forcePortal && subPath.isEmpty()) {
                        val selectedTarget = File(customPath)
                        if (selectedTarget.exists()) {
                            val htmlFile: File? = when {
                                selectedTarget.isFile && (selectedTarget.name.endsWith(".html", true) || selectedTarget.name.endsWith(".htm", true)) -> selectedTarget
                                selectedTarget.isDirectory -> {
                                    File(selectedTarget, "index.html").takeIf { it.exists() && it.isFile }
                                        ?: File(selectedTarget, "index.htm").takeIf { it.exists() && it.isFile }
                                        ?: selectedTarget.listFiles()?.firstOrNull {
                                            it.isFile && (it.name.endsWith(".html", true) || it.name.endsWith(".htm", true))
                                        }
                                }
                                else -> null
                            }
                            if (htmlFile != null && htmlFile.exists()) {
                                call.respondFile(htmlFile)
                                return@get
                            }
                        }
                    }

                    call.respondHtml(HttpStatusCode.OK) {
                        renderPortalHtml(subPath, initialTab)
                    }
                }

                // --- 2. DEVICE TELEMETRY API ---
                get("/api/telemetry") {
                    val stat = StatFs(Environment.getExternalStorageDirectory().path)
                    val totalBytes = stat.blockSizeLong * stat.blockCountLong
                    val availBytes = stat.blockSizeLong * stat.availableBlocksLong
                    val usedBytes = totalBytes - availBytes

                    val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

                    val st = ServerManager.state.value
                    val json = JSONObject().apply {
                        put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("androidVersion", Build.VERSION.RELEASE)
                        put("batteryPercent", batteryPct)
                        put("storageTotalBytes", totalBytes)
                        put("storageUsedBytes", usedBytes)
                        put("storageFreeBytes", availBytes)
                        put("screenCastActive", ContinuumHubService.isScreenCasting)
                        put("httpRunning", st.isHttpRunning)
                        put("ftpRunning", st.isFtpRunning)
                        put("ftpPort", st.ftp.port)
                        put("syncPlayActive", SyncPlayManager.roomState.value.isActive)
                    }
                    call.respondText(json.toString(), ContentType.Application.Json)
                }

                // --- 2B. REMOTE BROWSER MINI-APPS API ENDPOINTS ---
                get("/api/miniapp/list-files") {
                    if (!SettingsManager.ftpAllowMiniApps.value && !SyncPlayManager.roomState.value.isActive) {
                        val err = JSONObject().put("success", false).put("message", "Remote Mini-Apps disabled by host.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@get
                    }
                    val filesJson = JSONArray()
                    val candidateFiles = collectSharedFileCandidates(maxCount = 200)
                    candidateFiles.forEach { f ->
                        val ext = f.extension.lowercase(Locale.ROOT)
                        filesJson.put(JSONObject().apply {
                            put("name", f.name)
                            put("path", f.absolutePath)
                            put("size", f.length())
                            put("sizeFormatted", formatSize(f.length()))
                            put("ext", ext)
                            put("isMedia", SyncPlayManager.isSupportedMedia(f))
                            put("isText", ext in setOf("md", "txt", "kt", "java", "py", "js", "ts", "html", "htm", "css", "json", "xml", "csv", "tsv", "yaml", "yml", "sh", "log", "ini"))
                        })
                    }
                    val res = JSONObject()
                        .put("success", true)
                        .put("files", filesJson)
                    call.respondText(res.toString(), ContentType.Application.Json)
                }

                get("/api/miniapp/read-file") {
                    if (!SettingsManager.ftpAllowMiniApps.value) {
                        val err = JSONObject().put("success", false).put("message", "Remote Mini-Apps disabled by host.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@get
                    }
                    val path = call.request.queryParameters["path"] ?: ""
                    val file = File(path)
                    val suiteDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
                    val inSuiteDir = try { file.canonicalPath.startsWith(suiteDir.canonicalPath) } catch (_: Exception) { false }

                    if (!file.exists() || !file.isFile || (!isFileAccessPermitted(file) && !inSuiteDir)) {
                        val err = JSONObject().put("success", false).put("message", "File not found or not shared.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@get
                    }
                    if (file.length() > 3 * 1024 * 1024) {
                        val err = JSONObject().put("success", false).put("message", "File exceeds 3 MB browser editor limit.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@get
                    }
                    val content = withContext(Dispatchers.IO) { file.readText() }
                    val res = JSONObject()
                        .put("success", true)
                        .put("fileName", file.name)
                        .put("path", file.absolutePath)
                        .put("content", content)
                    call.respondText(res.toString(), ContentType.Application.Json)
                }

                post("/api/miniapp/save-text") {
                    val clientIp = call.request.origin.remoteHost
                    if (!SettingsManager.ftpAllowMiniApps.value) {
                        val err = JSONObject().put("success", false).put("message", "Remote Mini-Apps are disabled by host admin.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@post
                    }
                    try {
                        val body = JSONObject(call.receiveText())
                        val rawName = body.optString("fileName", "Remote_Note.md").trim()
                        val content = body.optString("content", "")
                        val targetHostPath = body.optString("targetPath", "").trim()
                        val safeName = File(rawName).name.ifBlank { "Remote_Note.md" }

                        val outFile = if (targetHostPath.isNotBlank() && SettingsManager.ftpAllowUpload.value) {
                            val candidate = File(targetHostPath)
                            if (candidate.exists() && candidate.isFile && isFileAccessPermitted(candidate)) candidate
                            else {
                                val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
                                if (!outDir.exists()) outDir.mkdirs()
                                File(outDir, safeName)
                            }
                        } else {
                            val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
                            if (!outDir.exists()) outDir.mkdirs()
                            File(outDir, safeName)
                        }

                        withContext(Dispatchers.IO) {
                            outFile.writeText(content)
                        }

                        ContinuumHubService.recordLog(clientIp, "HTTP", "MINIAPP_SAVE", outFile.name, outFile.length())
                        val res = JSONObject()
                            .put("success", true)
                            .put("path", outFile.absolutePath)
                            .put("message", "Saved to ${outFile.absolutePath}")
                        call.respondText(res.toString(), ContentType.Application.Json)
                    } catch (e: Exception) {
                        val err = JSONObject().put("success", false).put("message", "Save error: ${e.message}")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/api/miniapp/convert") {
                    val clientIp = call.request.origin.remoteHost
                    if (!SettingsManager.ftpAllowMiniApps.value) {
                        val err = JSONObject().put("success", false).put("message", "Remote Mini-Apps are disabled by host admin.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@post
                    }
                    try {
                        val body = JSONObject(call.receiveText())
                        val sourcePath = body.optString("sourcePath", "").trim()
                        val targetFormat = body.optString("targetFormat", "pdf").trim().lowercase(Locale.ROOT)
                        val maxKb = body.optInt("maxKb", 0)

                        val srcFile = File(sourcePath)
                        if (!srcFile.exists() || !srcFile.isFile || !isFileAccessPermitted(srcFile)) {
                            val err = JSONObject().put("success", false).put("message", "Source file not found or not shared.")
                            call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }

                        val outDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumConverted")
                        if (!outDir.exists()) outDir.mkdirs()

                        val outFile = File(outDir, "${srcFile.nameWithoutExtension}_converted.$targetFormat")
                        val tuning = ConversionTuningConfig()
                        val category = ConversionFormatMatrix.resolveCategory(srcFile.extension)
                        val result: Result<File> = withContext(Dispatchers.IO) {
                            if (category == ConversionCategory.IMAGE && targetFormat != "pdf") {
                                ImageConversionEngine.convertImage(appContext, srcFile, targetFormat, outFile, tuning) { _, _ -> }
                            } else {
                                DocumentDataConversionEngine.convertDocumentOrData(appContext, srcFile, targetFormat, outFile, tuning) { _, _ -> }
                            }
                        }

                        val generatedFile = result.getOrNull()
                        if (result.isSuccess && generatedFile != null && generatedFile.exists()) {
                            FtpBucketManager.addItem(appContext, generatedFile)
                            ContinuumHubService.recordLog(clientIp, "HTTP", "MINIAPP_CONVERT", "${srcFile.name} -> ${generatedFile.name}", generatedFile.length())
                            val encPath = URLEncoder.encode(generatedFile.absolutePath, "UTF-8")
                            val res = JSONObject()
                                .put("success", true)
                                .put("outputPath", generatedFile.absolutePath)
                                .put("downloadUrl", "/download?path=$encPath")
                                .put("message", "Converted: ${generatedFile.name} (${formatSize(generatedFile.length())})")
                            call.respondText(res.toString(), ContentType.Application.Json)
                        } else {
                            val err = JSONObject().put("success", false).put("message", result.exceptionOrNull()?.message ?: "Conversion failed")
                            call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    } catch (e: Exception) {
                        val err = JSONObject().put("success", false).put("message", "Convert error: ${e.message}")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                // --- 3. LIVE MJPEG SCREENCAST STREAM & SNAPSHOT ---
                get("/screencast.mjpeg") {
                    val clientIp = call.request.origin.remoteHost
                    ContinuumHubService.recordLog(clientIp, "HTTP", "STREAM", "/screencast.mjpeg")
                    call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0")
                    call.response.header(HttpHeaders.Connection, "keep-alive")

                    val boundary = "ContinuumMjpegBoundary"
                    val contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")

                    activeRequests.incrementAndGet()
                    try {
                        call.respondOutputStream(contentType = contentType) {
                            while (coroutineContext.isActive && ContinuumHubService.isScreenCasting) {
                                val frameBytes = ContinuumHubService.latestScreencastJpeg
                                if (frameBytes != null && frameBytes.isNotEmpty()) {
                                    val header = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frameBytes.size}\r\n\r\n"
                                    write(header.toByteArray(Charsets.US_ASCII))
                                    write(frameBytes)
                                    write("\r\n".toByteArray(Charsets.US_ASCII))
                                    flush()
                                }
                                val fps = SettingsManager.screenCastFps.value.coerceIn(5, 60)
                                delay((1000L / fps).coerceAtLeast(16L))
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }

                get("/screencast-frame") {
                    val frameBytes = ContinuumHubService.latestScreencastJpeg
                    if (frameBytes != null && frameBytes.isNotEmpty()) {
                        call.response.header(HttpHeaders.CacheControl, "no-store")
                        call.respondBytes(frameBytes, ContentType.Image.JPEG)
                    } else {
                        call.respondText("Screencast inactive", status = HttpStatusCode.ServiceUnavailable)
                    }
                }

                // --- 4. SINGLE FILE DOWNLOAD / INLINE PREVIEW WITH HTTP 206 BYTE-RANGE SUPPORT ---
                get("/download") {
                    val clientIp = call.request.origin.remoteHost
                    val filePath = call.request.queryParameters["path"] ?: ""
                    val inline = call.request.queryParameters["inline"] == "true"

                    val file = File(filePath)
                    if (!file.exists() || !file.isFile || !isFileAccessPermitted(file)) {
                        call.respondText("File not found or access denied.", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    ContinuumHubService.recordLog(
                        clientIp = clientIp,
                        protocol = "HTTP",
                        action = if (inline) "PREVIEW" else "DOWNLOAD",
                        details = file.name,
                        bytes = file.length()
                    )

                    activeRequests.incrementAndGet()
                    try {
                        call.respondFileWithRangeSupport(file, inline = inline)
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }

                // --- 5. STREAMING ZIP ARCHIVE DOWNLOAD (FOLDER OR ALL SHARED ITEMS) ---
                get("/download-zip") {
                    val clientIp = call.request.origin.remoteHost
                    val folderPath = call.request.queryParameters["path"] ?: ""

                    val filesToZip: List<File> = if (folderPath.isNotBlank()) {
                        val dir = File(folderPath)
                        if (dir.exists() && dir.isDirectory && isFileAccessPermitted(dir)) {
                            listOf(dir)
                        } else emptyList()
                    } else {
                        getRootSharedFiles()
                    }

                    if (filesToZip.isEmpty()) {
                        call.respondText("No files available to archive.", status = HttpStatusCode.NotFound)
                        return@get
                    }

                    val archiveName = if (filesToZip.size == 1) {
                        "${filesToZip.first().nameWithoutExtension}_Archive.zip"
                    } else {
                        "Continuum_Shared_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip"
                    }

                    ContinuumHubService.recordLog(clientIp, "HTTP", "ZIP_STREAM", archiveName)

                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, archiveName).toString()
                    )

                    activeRequests.incrementAndGet()
                    try {
                        call.respondOutputStream(ContentType.Application.Zip) {
                            ZipOutputStream(this).use { zos ->
                                for (item in filesToZip) {
                                    addFileOrDirToZip(zos, item, "")
                                }
                            }
                        }
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }

                // --- 6. PASSWORD-PROTECTED MULTIPART FILE UPLOAD ---
                post("/upload") {
                    val clientIp = call.request.origin.remoteHost
                    if (!SettingsManager.ftpAllowUpload.value) {
                        val err = JSONObject().put("success", false).put("message", "Uploads are disabled on the host device.")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Forbidden)
                        return@post
                    }

                    val requiredPassword = SettingsManager.ftpUploadPassword.value
                    var providedPassword = ""
                    var targetSubDir = ""
                    val savedFileNames = mutableListOf<String>()
                    var totalBytesUploaded = 0L

                    val defaultDestPath = SettingsManager.ftpUploadFolderPath.value.ifBlank {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    }
                    val baseUploadDir = File(defaultDestPath).apply {
                        if (!exists()) mkdirs()
                    }

                    activeRequests.incrementAndGet()
                    try {
                        val multipart = call.receiveMultipart()
                        val tempFiles = mutableListOf<Pair<String, File>>()

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "password") providedPassword = part.value
                                    if (part.name == "targetDir") targetSubDir = part.value
                                }
                                is PartData.FileItem -> {
                                    val originalName = part.originalFileName?.takeIf { it.isNotBlank() } ?: "uploaded_${System.currentTimeMillis()}"
                                    val safeName = File(originalName).name
                                    val tempFile = File(appContext.cacheDir, "upload_${System.currentTimeMillis()}_$safeName")
                                    part.streamProvider().use { input ->
                                        tempFile.outputStream().buffered().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    tempFiles.add(safeName to tempFile)
                                }
                                else -> {}
                            }
                            part.dispose()
                        }

                        if (requiredPassword.isNotBlank() && providedPassword != requiredPassword) {
                            tempFiles.forEach { it.second.delete() }
                            ContinuumHubService.recordLog(clientIp, "HTTP", "UPLOAD_DENIED", "Invalid upload password")
                            val err = JSONObject().put("success", false).put("message", "Invalid Upload Password")
                            call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                            return@post
                        }

                        val destDir = if (targetSubDir.isNotBlank()) {
                            val candidate = File(targetSubDir)
                            if (candidate.exists() && candidate.isDirectory && isFileAccessPermitted(candidate) && candidate.canWrite()) {
                                candidate
                            } else baseUploadDir
                        } else baseUploadDir

                        for ((safeName, tempFile) in tempFiles) {
                            var outFile = File(destDir, safeName)
                            if (outFile.exists()) {
                                val base = outFile.nameWithoutExtension
                                val ext = outFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                                outFile = File(destDir, "${base}_${System.currentTimeMillis() % 10000}$ext")
                            }
                            tempFile.copyTo(outFile, overwrite = true)
                            tempFile.delete()
                            totalBytesUploaded += outFile.length()
                            savedFileNames.add(outFile.name)
                            if (SettingsManager.ftpHostedMode.value == FtpHostedMode.BATCH_FILES) {
                                FtpBucketManager.addItem(appContext, outFile)
                            }
                        }

                        ContinuumHubService.recordLog(
                            clientIp = clientIp,
                            protocol = "HTTP",
                            action = "UPLOAD",
                            details = "${savedFileNames.joinToString(", ")} -> ${destDir.name}",
                            bytes = totalBytesUploaded
                        )

                        val ok = JSONObject()
                            .put("success", true)
                            .put("message", "Uploaded ${savedFileNames.size} file(s) to ${destDir.absolutePath}")
                        call.respondText(ok.toString(), ContentType.Application.Json)
                    } catch (e: Exception) {
                        Log.e("KtorEmbeddedServer", "Upload failed", e)
                        val err = JSONObject().put("success", false).put("message", "Upload error: ${e.message}")
                        call.respondText(err.toString(), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }

                // --- 7. STATIC ASSET RESOLUTION FOR SELF-HOSTED WEBSITES ---
                get("/{path...}") {
                    val segments = call.parameters.getAll("path") ?: emptyList()
                    val relPath = segments.joinToString("/")
                    val hostedMode = SettingsManager.ftpHostedMode.value
                    val customPath = SettingsManager.ftpHostedPath.value

                    if (hostedMode == FtpHostedMode.CUSTOM_FOLDER && customPath.isNotBlank()) {
                        val selectedTarget = File(customPath)
                        val rootDir = when {
                            !selectedTarget.exists() -> null
                            selectedTarget.isDirectory -> selectedTarget
                            selectedTarget.isFile -> selectedTarget.parentFile
                            else -> null
                        }
                        if (rootDir != null && rootDir.exists()) {
                            val requested = File(rootDir, relPath)
                            val rootCanonical = rootDir.canonicalPath
                            val reqCanonical = requested.canonicalPath
                            if (reqCanonical.startsWith(rootCanonical) && requested.exists()) {
                                if (requested.isDirectory) {
                                    val idx = File(requested, "index.html").takeIf { it.exists() }
                                        ?: File(requested, "index.htm").takeIf { it.exists() }
                                    if (idx != null) {
                                        call.respondFile(idx)
                                        return@get
                                    }
                                } else if (requested.isFile) {
                                    call.respondFileWithRangeSupport(requested, inline = true)
                                    return@get
                                }
                            }
                        }
                    }

                    call.respondRedirect("/?portal=1")
                }
            }
        }

        engine.start(wait = false)
        server = engine
        isRunning = true
        Log.i("KtorEmbeddedServer", "Ktor HTTP/WS Server started on port $port")
    }

    fun start() {
        try {
            startOrThrow()
        } catch (e: Exception) {
            Log.e("KtorEmbeddedServer", "Failed to start Ktor server", e)
        }
    }

    fun stop(gracePeriodMillis: Long = 800L, timeoutMillis: Long = 2000L) {
        try {
            server?.stop(gracePeriodMillis, timeoutMillis)
            Log.i("KtorEmbeddedServer", "Ktor HTTP Server stopped cleanly")
        } catch (e: Exception) {
            Log.e("KtorEmbeddedServer", "Error stopping Ktor server", e)
        } finally {
            server = null
            isRunning = false
            activeRequests.set(0)
        }
    }

    /**
     * Streams [file] with full HTTP 206 Partial Content (`Range: bytes=start-end`) support
     * for instant video/audio seeking and pre-buffering across browsers and mobile players.
     */
    private suspend fun ApplicationCall.respondFileWithRangeSupport(file: File, inline: Boolean) {
        val fileLength = file.length()
        val contentType = ContentType.defaultForFile(file)
        response.header(HttpHeaders.AcceptRanges, "bytes")
        if (!inline) {
            response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString()
            )
        }

        val rangeHeader = request.header(HttpHeaders.Range)
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
            response.header(HttpHeaders.ContentLength, fileLength.toString())
            respondFile(file)
            return
        }

        try {
            val rangeSpec = rangeHeader.removePrefix("bytes=").substringBefore(",").trim()
            val parts = rangeSpec.split("-")
            val start: Long
            val end: Long
            if (parts[0].isEmpty()) {
                val suffixLength = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                start = (fileLength - suffixLength).coerceAtLeast(0L)
                end = fileLength - 1L
            } else {
                start = parts[0].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()?.coerceAtMost(fileLength - 1L)
                    ?: (fileLength - 1L)
            }

            if (start > end || start >= fileLength) {
                response.header(HttpHeaders.ContentRange, "bytes */$fileLength")
                respondText("Requested Range Not Satisfiable", status = HttpStatusCode.RequestedRangeNotSatisfiable)
                return
            }

            val contentLength = (end - start) + 1L
            response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileLength")
            response.header(HttpHeaders.ContentLength, contentLength.toString())

            respondOutputStream(contentType = contentType, status = HttpStatusCode.PartialContent) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = contentLength
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        write(buffer, 0, read)
                        remaining -= read
                    }
                    flush()
                }
            }
        } catch (_: Exception) {
            respondFile(file)
        }
    }

    private fun getRootSharedFiles(): List<File> {
        FtpBucketManager.initialize(appContext)
        val mode = SettingsManager.ftpHostedMode.value
        val customPath = SettingsManager.ftpHostedPath.value

        return when (mode) {
            FtpHostedMode.BATCH_FILES -> {
                FtpBucketManager.bucketItems.mapNotNull { item ->
                    File(item.absolutePath).takeIf { it.exists() }
                }
            }
            FtpHostedMode.CUSTOM_FOLDER -> {
                if (customPath.isNotBlank()) {
                    val f = File(customPath)
                    val dir = if (f.exists() && f.isFile) f.parentFile else f
                    if (dir != null && dir.exists()) {
                        dir.listFiles()?.toList() ?: emptyList()
                    } else emptyList()
                } else emptyList()
            }
            FtpHostedMode.FULL_STORAGE -> {
                Environment.getExternalStorageDirectory().listFiles()?.toList() ?: emptyList()
            }
        }
    }

    private fun collectSharedFileCandidates(maxCount: Int = 200): List<File> {
        val results = LinkedHashMap<String, File>()
        // Include files in /Documents/ContinuumSuite/
        val suiteDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
        if (suiteDir.exists() && suiteDir.isDirectory) {
            suiteDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { f ->
                if (results.size < maxCount) results[f.absolutePath] = f
            }
        }
        val roots = getRootSharedFiles()
        for (item in roots) {
            if (results.size >= maxCount) break
            if (item.isFile) {
                results[item.absolutePath] = item
            } else if (item.isDirectory) {
                item.listFiles()?.take(60)?.forEach { child ->
                    if (results.size < maxCount && child.isFile) {
                        results[child.absolutePath] = child
                    }
                }
            }
        }
        return results.values.toList()
    }

    private fun isFileAccessPermitted(target: File): Boolean {
        return try {
            val targetCanonical = target.canonicalPath
            val suiteDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumSuite")
            val convDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ContinuumConverted")
            if (suiteDir.exists() && targetCanonical.startsWith(suiteDir.canonicalPath)) return true
            if (convDir.exists() && targetCanonical.startsWith(convDir.canonicalPath)) return true
            val syncMediaPath = SyncPlayManager.roomState.value.mediaFilePath
            if (syncMediaPath.isNotBlank() && File(syncMediaPath).canonicalPath == targetCanonical) return true

            val mode = SettingsManager.ftpHostedMode.value
            val customPath = SettingsManager.ftpHostedPath.value
            when (mode) {
                FtpHostedMode.FULL_STORAGE -> {
                    val root = Environment.getExternalStorageDirectory().canonicalPath
                    targetCanonical.startsWith(root)
                }
                FtpHostedMode.CUSTOM_FOLDER -> {
                    if (customPath.isBlank()) false
                    else {
                        val sel = File(customPath)
                        val base = if (sel.isFile) sel.parentFile else sel
                        base != null && targetCanonical.startsWith(base.canonicalPath)
                    }
                }
                FtpHostedMode.BATCH_FILES -> {
                    FtpBucketManager.bucketItems.any { item ->
                        val itemFile = File(item.absolutePath)
                        itemFile.exists() && targetCanonical.startsWith(itemFile.canonicalPath)
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun addFileOrDirToZip(zos: ZipOutputStream, file: File, parentPath: String) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children.isNullOrEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    addFileOrDirToZip(zos, child, entryName)
                }
            }
        } else if (file.isFile) {
            try {
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            } catch (e: Exception) {
                Log.w("KtorEmbeddedServer", "Skipped zipping ${file.name}: ${e.message}")
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun HTML.renderPortalHtml(currentSubPath: String, initialTab: String) {
        val currentDir: File? = if (currentSubPath.isNotBlank()) {
            val candidate = File(currentSubPath)
            if (candidate.exists() && candidate.isDirectory && isFileAccessPermitted(candidate)) candidate else null
        } else null

        val itemsToDisplay: List<File> = (if (currentDir != null) {
            currentDir.listFiles()?.toList() ?: emptyList()
        } else {
            getRootSharedFiles()
        }).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))

        val allowWebUpload = SettingsManager.ftpAllowUpload.value
        val allowMiniApps = SettingsManager.ftpAllowMiniApps.value
        val requireUploadPass = SettingsManager.ftpUploadPassword.value.isNotBlank()
        val hasScreencast = ContinuumHubService.isScreenCasting
        val screenCastAllowed = SettingsManager.screenCastEnabled.value
        val uploadFolder = SettingsManager.ftpUploadFolderPath.value.ifBlank {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }
        val syncState = SyncPlayManager.roomState.value

        head {
            meta(charset = "UTF-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
            title("Continuum Workspace Hub")
            style {
                unsafe {
                    raw("""
                        :root {
                            --bg: #0b0f19;
                            --surface: #131b2e;
                            --surface-hover: #1c2742;
                            --surface-high: #1e293b;
                            --border: rgba(255,255,255,0.09);
                            --primary: #6366f1;
                            --primary-hover: #4f46e5;
                            --accent: #10b981;
                            --warn: #f59e0b;
                            --danger: #ef4444;
                            --text: #f3f4f6;
                            --text-muted: #9ca3af;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
                            background: var(--bg);
                            color: var(--text);
                            min-height: 100vh;
                            padding-bottom: 60px;
                        }
                        .topbar {
                            position: sticky; top: 0; z-index: 40;
                            background: rgba(19, 27, 46, 0.92);
                            backdrop-filter: blur(16px);
                            border-bottom: 1px solid var(--border);
                            padding: 12px 20px;
                            display: flex; flex-wrap: wrap; gap: 12px;
                            align-items: center; justify-content: space-between;
                        }
                        .brand { display: flex; align-items: center; gap: 12px; }
                        .brand-badge {
                            width: 36px; height: 36px; border-radius: 10px;
                            background: linear-gradient(135deg, var(--primary), #8b5cf6);
                            display: flex; align-items: center; justify-content: center;
                            font-weight: 800; font-size: 17px;
                            box-shadow: 0 4px 14px rgba(99,102,241,0.35);
                        }
                        .brand-title { font-size: 1.05rem; font-weight: 700; }
                        .brand-sub { font-size: 0.75rem; color: var(--text-muted); }
                        .toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
                        .subpage-tabs {
                            display: flex; gap: 4px; background: rgba(255,255,255,0.04);
                            padding: 4px; border-radius: 12px; border: 1px solid var(--border);
                            flex-wrap: wrap;
                        }
                        .tab-btn {
                            padding: 7px 13px; border-radius: 8px; border: none;
                            background: transparent; color: var(--text-muted);
                            font-size: 0.82rem; font-weight: 600; cursor: pointer;
                            display: inline-flex; align-items: center; gap: 6px;
                            transition: all 0.15s ease;
                        }
                        .tab-btn.active {
                            background: var(--primary); color: #fff;
                            box-shadow: 0 2px 10px rgba(99,102,241,0.35);
                        }
                        .btn {
                            display: inline-flex; align-items: center; gap: 6px;
                            padding: 7px 13px; border-radius: 9px;
                            font-size: 0.81rem; font-weight: 600;
                            border: 1px solid var(--border);
                            background: var(--surface); color: var(--text);
                            text-decoration: none; cursor: pointer; transition: 0.15s;
                        }
                        .btn:hover { background: var(--surface-hover); }
                        .btn-primary { background: var(--primary); border-color: var(--primary); color: #fff; }
                        .btn-primary:hover { background: var(--primary-hover); }
                        .btn-accent { background: rgba(16,185,129,0.16); border-color: rgba(16,185,129,0.4); color: #34d399; }
                        .btn-sm { padding: 5px 9px; font-size: 0.74rem; border-radius: 7px; }
                        .container { max-width: 1320px; margin: 18px auto; padding: 0 18px; display: flex; flex-direction: column; gap: 16px; }
                        .panel {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 16px;
                            overflow: hidden;
                        }
                        .panel-header {
                            padding: 13px 18px;
                            border-bottom: 1px solid var(--border);
                            display: flex; align-items: center; justify-content: space-between;
                            flex-wrap: wrap; gap: 10px;
                        }
                        .panel-title { font-weight: 700; font-size: 0.94rem; display: flex; align-items: center; gap: 8px; }
                        .tab-section { display: none; }
                        .tab-section.active { display: block; }
                        .cast-stage {
                            background: #000; position: relative;
                            display: flex; align-items: center; justify-content: center;
                            min-height: 360px; max-height: 78vh; overflow: hidden;
                        }
                        .cast-stage img {
                            max-width: 100%; max-height: 76vh; object-fit: contain;
                            transition: transform 0.2s ease;
                        }
                        .search-input, .text-input, .select-input {
                            background: rgba(0,0,0,0.28);
                            border: 1px solid var(--border);
                            color: var(--text);
                            padding: 8px 12px; border-radius: 9px;
                            font-size: 0.83rem; outline: none; width: 100%;
                        }
                        .search-input { width: 220px; }
                        .search-input:focus, .text-input:focus, .select-input:focus { border-color: var(--primary); }
                        .editor-textarea {
                            width: 100%; min-height: 290px;
                            background: rgba(0,0,0,0.35);
                            border: 1px solid var(--border);
                            color: #e5e7eb; padding: 14px; border-radius: 12px;
                            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                            font-size: 0.85rem; line-height: 1.55; resize: vertical; outline: none;
                        }
                        .editor-textarea:focus { border-color: var(--primary); }
                        .file-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fill, minmax(235px, 1fr));
                            gap: 12px; padding: 16px;
                        }
                        .file-card {
                            background: rgba(255,255,255,0.025);
                            border: 1px solid var(--border);
                            border-radius: 12px; padding: 12px;
                            display: flex; flex-direction: column; justify-content: space-between;
                            gap: 10px; transition: 0.15s;
                        }
                        .file-card:hover { background: var(--surface-hover); transform: translateY(-1px); }
                        .file-name { font-weight: 600; font-size: 0.87rem; word-break: break-word; color: var(--text); text-decoration: none; }
                        .file-meta { font-size: 0.74rem; color: var(--text-muted); }
                        .file-actions { display: flex; gap: 5px; flex-wrap: wrap; }
                        .file-table { width: 100%; border-collapse: collapse; }
                        .file-table th, .file-table td {
                            padding: 10px 14px; text-align: left;
                            border-bottom: 1px solid var(--border); font-size: 0.84rem;
                        }
                        .file-table th { color: var(--text-muted); font-weight: 600; font-size: 0.75rem; text-transform: uppercase; background: rgba(0,0,0,0.2); }
                        body[data-view="grid"] .table-wrapper { display: none; }
                        body[data-view="table"] .file-grid { display: none; }
                        .modal-backdrop {
                            position: fixed; inset: 0; background: rgba(0,0,0,0.76);
                            display: none; align-items: center; justify-content: center;
                            z-index: 100; padding: 16px;
                        }
                        .modal {
                            background: var(--surface); border: 1px solid var(--border);
                            border-radius: 16px; max-width: 680px; width: 100%;
                            padding: 20px; display: flex; flex-direction: column; gap: 14px;
                            max-height: 90vh; overflow-y: auto;
                        }
                        .modal-preview { max-width: 920px; }
                        .preview-body {
                            background: #070a12; border-radius: 10px; padding: 12px;
                            max-height: 68vh; overflow: auto; display: flex;
                            align-items: center; justify-content: center;
                        }
                        .preview-body img, .preview-body video { max-width: 100%; max-height: 62vh; border-radius: 8px; }
                        /* DeX-Style Mini-Apps Studio Dock & Workspace */
                        .studio-shell {
                            display: grid;
                            grid-template-columns: 250px 1fr;
                            gap: 16px;
                            align-items: start;
                        }
                        .studio-dock {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 16px;
                            padding: 12px;
                            display: flex;
                            flex-direction: column;
                            gap: 6px;
                        }
                        .dock-app-btn {
                            width: 100%;
                            text-align: left;
                            padding: 11px 12px;
                            border-radius: 11px;
                            border: 1px solid transparent;
                            background: rgba(255,255,255,0.02);
                            color: var(--text);
                            cursor: pointer;
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            transition: all 0.15s ease;
                        }
                        .dock-app-btn:hover { background: var(--surface-hover); }
                        .dock-app-btn.active {
                            background: rgba(99,102,241,0.18);
                            border-color: var(--primary);
                            color: #fff;
                        }
                        .dock-icon {
                            width: 34px; height: 34px; border-radius: 9px;
                            background: rgba(255,255,255,0.06);
                            display: flex; align-items: center; justify-content: center;
                            font-size: 1.05rem; flex-shrink: 0;
                        }
                        .studio-window { display: none; }
                        .studio-window.active { display: block; }
                        .md-split {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 12px;
                        }
                        .md-preview-box {
                            background: rgba(0,0,0,0.28);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            padding: 14px;
                            min-height: 290px;
                            max-height: 460px;
                            overflow-y: auto;
                            font-size: 0.87rem;
                            line-height: 1.6;
                        }
                        .md-preview-box h1, .md-preview-box h2, .md-preview-box h3 { margin: 8px 0 6px; color: #fff; }
                        .md-preview-box code { background: rgba(255,255,255,0.1); padding: 2px 5px; border-radius: 4px; font-family: monospace; }
                        .csv-grid-wrap {
                            overflow-x: auto;
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            max-height: 360px;
                        }
                        .csv-table { width: 100%; border-collapse: collapse; }
                        .csv-table th, .csv-table td {
                            border: 1px solid var(--border);
                            padding: 0;
                            min-width: 110px;
                        }
                        .csv-cell-input {
                            width: 100%; border: none; background: transparent;
                            color: var(--text); padding: 8px 10px; font-size: 0.82rem; outline: none;
                        }
                        .csv-cell-input:focus { background: rgba(99,102,241,0.18); }
                        /* SyncPlay Watch Party Layout */
                        .syncplay-grid {
                            display: grid;
                            grid-template-columns: 1fr 340px;
                            gap: 16px;
                            align-items: start;
                        }
                        .sync-stage {
                            background: #05070e;
                            border-radius: 14px;
                            border: 1px solid var(--border);
                            min-height: 340px;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            padding: 14px;
                            position: relative;
                        }
                        .sync-stage video { width: 100%; max-height: 64vh; border-radius: 10px; background: #000; }
                        .sync-stage audio { width: 100%; max-width: 520px; margin-top: 14px; }
                        .sync-badge {
                            display: inline-flex; align-items: center; gap: 6px;
                            padding: 4px 10px; border-radius: 99px;
                            font-size: 0.74rem; font-weight: 700;
                            background: rgba(16,185,129,0.14); color: #34d399;
                            border: 1px solid rgba(16,185,129,0.35);
                        }
                        .peer-item {
                            padding: 9px 12px;
                            border-radius: 10px;
                            background: rgba(255,255,255,0.03);
                            border: 1px solid var(--border);
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            font-size: 0.8rem;
                        }
                        @media (max-width: 900px) {
                            .studio-shell, .syncplay-grid, .md-split {
                                grid-template-columns: 1fr;
                            }
                        }
                    """)
                }
            }
        }

        body {
            attributes["data-view"] = "grid"

            div("topbar") {
                div("brand") {
                    div("brand-badge") { +"C" }
                    div {
                        div("brand-title") { +"Continuum Workspace Hub" }
                        div("brand-sub") {
                            id = "telemetrySubtitle"
                            +"${Build.MANUFACTURER} ${Build.MODEL} • Unified HTTP/FTP & SyncPlay"
                        }
                    }
                }

                // 4-Tab Workspace Navigation Bar
                div("subpage-tabs") {
                    button(classes = "tab-btn active") {
                        id = "tabBtnFiles"
                        onClick = "switchPortalTab('files')"
                        +"📁 Files (${itemsToDisplay.size})"
                    }
                    button(classes = "tab-btn") {
                        id = "tabBtnSyncPlay"
                        onClick = "switchPortalTab('syncplay')"
                        +"🎬 SyncPlay Party"
                    }
                    if (screenCastAllowed || hasScreencast) {
                        button(classes = "tab-btn") {
                            id = "tabBtnScreen"
                            onClick = "switchPortalTab('screen')"
                            +"🖥️ Screen Share"
                        }
                    }
                    if (allowMiniApps) {
                        button(classes = "tab-btn") {
                            id = "tabBtnMiniApps"
                            onClick = "switchPortalTab('miniapps')"
                            +"✨ Mini-Apps Studio"
                        }
                    }
                }

                div("toolbar") {
                    if (allowWebUpload) {
                        button(classes = "btn btn-primary") {
                            onClick = "openUploadModal()"
                            +"⬆ Upload"
                        }
                    }
                    a(
                        href = if (currentDir != null) "/download-zip?path=${URLEncoder.encode(currentDir.absolutePath, "UTF-8")}" else "/download-zip",
                        classes = "btn"
                    ) {
                        +"📦 Download ZIP"
                    }
                }
            }

            div("container") {
                // =========================================================================
                // SUBPAGE 1: SHARED FILES EXPLORER TAB
                // =========================================================================
                div("tab-section active") {
                    id = "sectionFiles"
                    div("panel") {
                        div("panel-header") {
                            div("panel-title") {
                                if (currentDir != null) {
                                    a(href = "/?portal=1", classes = "btn btn-sm") { +"⬅ Root" }
                                    span { +currentDir.name }
                                } else {
                                    +"📁 Shared Files & Folders (${itemsToDisplay.size})"
                                }
                            }
                            div("toolbar") {
                                input {
                                    id = "searchInput"
                                    classes = setOf("search-input")
                                    type = InputType.search
                                    placeholder = "Search files..."
                                    onInput = "filterPortalFiles(this.value)"
                                }
                                button(classes = "btn btn-sm active") {
                                    id = "btnViewGrid"
                                    onClick = "setViewMode('grid')"
                                    +"Grid"
                                }
                                button(classes = "btn btn-sm") {
                                    id = "btnViewTable"
                                    onClick = "setViewMode('table')"
                                    +"Table"
                                }
                            }
                        }

                        if (itemsToDisplay.isEmpty()) {
                            div {
                                style = "padding: 40px 20px; text-align: center; color: var(--text-muted);"
                                +"No shared files selected yet. You can still use SyncPlay Party, Screen Share, or Mini-Apps Studio!"
                            }
                        } else {
                            div("file-grid") {
                                for (item in itemsToDisplay) {
                                    val encPath = URLEncoder.encode(item.absolutePath, "UTF-8")
                                    val ext = item.extension.lowercase(Locale.ROOT)
                                    val isMedia = !item.isDirectory && SyncPlayManager.isSupportedMedia(item)
                                    val isTextEditable = !item.isDirectory && ext in setOf("md", "txt", "kt", "java", "py", "js", "ts", "html", "htm", "css", "json", "xml", "csv", "tsv")
                                    val isPreviewable = !item.isDirectory && ext in setOf(
                                        "jpg", "jpeg", "png", "gif", "webp", "svg",
                                        "mp4", "webm", "mp3", "wav", "ogg", "pdf",
                                        "txt", "md", "json", "xml", "csv", "tsv", "log", "kt", "py", "js", "html", "css"
                                    )
                                    val safeJsPath = item.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
                                    val safeJsName = item.name.replace("'", "\\'")

                                    div("file-card file-item-entry") {
                                        attributes["data-name"] = item.name.lowercase(Locale.ROOT)
                                        div {
                                            val icon = when {
                                                item.isDirectory -> "📁 "
                                                ext in setOf("jpg", "jpeg", "png", "gif", "webp", "svg") -> "🖼️ "
                                                ext in setOf("mp4", "mkv", "webm", "mov") -> "🎬 "
                                                ext in setOf("mp3", "wav", "flac", "ogg", "m4a") -> "🎵 "
                                                ext == "pdf" -> "📕 "
                                                ext in setOf("zip", "rar", "7z", "tar") -> "🗜️ "
                                                ext in setOf("csv", "tsv") -> "📊 "
                                                else -> "📄 "
                                            }
                                            if (item.isDirectory) {
                                                a(href = "/?portal=1&path=$encPath", classes = "file-name") {
                                                    +"$icon${item.name}"
                                                }
                                            } else {
                                                span("file-name") { +"$icon${item.name}" }
                                            }
                                            div("file-meta") {
                                                val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(item.lastModified()))
                                                if (item.isDirectory) {
                                                    val count = item.listFiles()?.size ?: 0
                                                    +"$count items • $dateStr"
                                                } else {
                                                    +"${formatSize(item.length())} • $dateStr"
                                                }
                                            }
                                        }
                                        div("file-actions") {
                                            if (item.isDirectory) {
                                                a(href = "/?portal=1&path=$encPath", classes = "btn btn-sm") { +"Open" }
                                                a(href = "/download-zip?path=$encPath", classes = "btn btn-sm") { +"ZIP" }
                                            } else {
                                                if (isPreviewable) {
                                                    button(classes = "btn btn-sm") {
                                                        onClick = "openPreviewModal('$safeJsName', '/download?path=$encPath&inline=true', '$ext')"
                                                        +"👁 Preview"
                                                    }
                                                }
                                                if (isMedia) {
                                                    button(classes = "btn btn-sm btn-accent") {
                                                        onClick = "startSyncPlayWithHostFile('$safeJsPath')"
                                                        +"🎬 SyncPlay"
                                                    }
                                                }
                                                if (allowMiniApps && isTextEditable) {
                                                    button(classes = "btn btn-sm") {
                                                        onClick = "openHostFileInStudio('$safeJsPath', '$ext')"
                                                        +"✨ Edit"
                                                    }
                                                }
                                                a(href = "/download?path=$encPath", classes = "btn btn-sm btn-primary") { +"⬇" }
                                            }
                                        }
                                    }
                                }
                            }

                            div("table-wrapper") {
                                table("file-table") {
                                    thead {
                                        tr {
                                            th { +"Name" }
                                            th { +"Size" }
                                            th { +"Modified" }
                                            th { +"Actions" }
                                        }
                                    }
                                    tbody {
                                        for (item in itemsToDisplay) {
                                            val encPath = URLEncoder.encode(item.absolutePath, "UTF-8")
                                            val ext = item.extension.lowercase(Locale.ROOT)
                                            val isMedia = !item.isDirectory && SyncPlayManager.isSupportedMedia(item)
                                            val safeJsPath = item.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
                                            tr("file-item-entry") {
                                                attributes["data-name"] = item.name.lowercase(Locale.ROOT)
                                                td {
                                                    if (item.isDirectory) {
                                                        a(href = "/?portal=1&path=$encPath", classes = "file-name") { +"📁 ${item.name}" }
                                                    } else {
                                                        span("file-name") { +"📄 ${item.name}" }
                                                    }
                                                }
                                                td {
                                                    if (item.isDirectory) +"Folder" else +formatSize(item.length())
                                                }
                                                td {
                                                    +SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.lastModified()))
                                                }
                                                td {
                                                    div("file-actions") {
                                                        if (item.isDirectory) {
                                                            a(href = "/?portal=1&path=$encPath", classes = "btn btn-sm") { +"Open" }
                                                            a(href = "/download-zip?path=$encPath", classes = "btn btn-sm") { +"ZIP" }
                                                        } else {
                                                            if (isMedia) {
                                                                button(classes = "btn btn-sm btn-accent") {
                                                                    onClick = "startSyncPlayWithHostFile('$safeJsPath')"
                                                                    +"🎬 SyncPlay"
                                                                }
                                                            }
                                                            a(href = "/download?path=$encPath", classes = "btn btn-sm btn-primary") { +"Download" }
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

                // =========================================================================
                // SUBPAGE 2: BEATSYNC-INSPIRED SYNCPLAY GROUP WATCH & LISTEN PARTY
                // =========================================================================
                div("tab-section") {
                    id = "sectionSyncPlay"
                    div("syncplay-grid") {
                        // Left Column: Synchronized Video/Audio Stage & Precision Transport Controls
                        div("panel") {
                            div("panel-header") {
                                div("panel-title") {
                                    +"🎬 SyncPlay Watch & Listen Party"
                                    span("sync-badge") {
                                        id = "syncClockBadge"
                                        +"⚡ Calibrating Clock..."
                                    }
                                }
                                div("toolbar") {
                                    span {
                                        id = "syncBufferBadge"
                                        style = "font-size: 0.76rem; color: var(--text-muted);"
                                        +"Pre-buffer ready"
                                    }
                                    button(classes = "btn btn-sm") {
                                        onClick = "forceResyncPlayback()"
                                        +"🎯 Re-Sync Now"
                                    }
                                }
                            }

                            div {
                                style = "padding: 16px; display: flex; flex-direction: column; gap: 14px;"

                                // Media Title & Host File Selector Bar
                                div {
                                    style = "display: flex; flex-wrap: wrap; gap: 8px; align-items: center; justify-content: space-between;"
                                    div {
                                        div {
                                            id = "syncMediaTitle"
                                            style = "font-weight: 700; font-size: 0.96rem;"
                                            +(if (syncState.isActive) syncState.mediaFileName else "No Media Loaded — Pick a Shared Audio/Video File Below")
                                        }
                                        div {
                                            id = "syncLastEventSub"
                                            style = "font-size: 0.76rem; color: var(--text-muted);"
                                            +"Pre-cached HTTP 206 Byte-Range stream • Millisecond-scheduled group execution"
                                        }
                                    }
                                    div {
                                        style = "display: flex; gap: 6px; align-items: center;"
                                        select(classes = "select-input") {
                                            id = "syncHostMediaSelect"
                                            style = "width: 220px;"
                                            option {
                                                value = ""
                                                +"Choose shared media..."
                                            }
                                        }
                                        button(classes = "btn btn-sm btn-primary") {
                                            onClick = "loadSelectedMediaIntoSyncPlay()"
                                            +"Load Media"
                                        }
                                    }
                                }

                                // Video / Audio Stage
                                div("sync-stage") {
                                    id = "syncMediaStage"
                                    video {
                                        id = "syncVideoPlayer"
                                        attributes["playsinline"] = "true"
                                        attributes["preload"] = "auto"
                                        controls = false
                                        style = if (syncState.isActive && syncState.isVideo) "display: block;" else "display: none;"
                                    }
                                    div {
                                        id = "syncAudioVisualizerCard"
                                        style = if (syncState.isActive && !syncState.isVideo) "display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 24px;" else "display: none; flex-direction: column; align-items: center; gap: 12px; padding: 24px;"
                                        div {
                                            style = "width: 84px; height: 84px; border-radius: 22px; background: linear-gradient(135deg, var(--primary), var(--accent)); display: flex; align-items: center; justify-content: center; font-size: 2.2rem; box-shadow: 0 10px 28px rgba(99,102,241,0.35);"
                                            +"🎵"
                                        }
                                        div {
                                            id = "syncAudioTrackLabel"
                                            style = "font-weight: 700; font-size: 1rem; text-align: center;"
                                            +syncState.mediaFileName
                                        }
                                        audio {
                                            id = "syncAudioPlayer"
                                            attributes["preload"] = "auto"
                                            controls = false
                                        }
                                    }
                                    div {
                                        id = "syncEmptyPrompt"
                                        style = if (syncState.isActive) "display: none;" else "text-align: center; color: var(--text-muted); padding: 30px;"
                                        +"Select any video or audio file from the dropdown above, the Files tab (🎬 SyncPlay), or the Android app to start a synchronized watch/listen party."
                                    }
                                }

                                // Synchronized Timeline Scrubber
                                div {
                                    style = "display: flex; align-items: center; gap: 10px;"
                                    span {
                                        id = "syncCurrentTimeLabel"
                                        style = "font-family: monospace; font-size: 0.8rem; min-width: 48px;"
                                        +"00:00"
                                    }
                                    input {
                                        id = "syncSeekSlider"
                                        type = InputType.range
                                        style = "flex: 1; accent-color: var(--primary); cursor: pointer;"
                                        min = "0"
                                        max = "1000"
                                        value = "0"
                                        onChange = "onSyncSliderSeek(this.value)"
                                    }
                                    span {
                                        id = "syncDurationLabel"
                                        style = "font-family: monospace; font-size: 0.8rem; min-width: 48px; text-align: right;"
                                        +"00:00"
                                    }
                                }

                                // Synchronized Transport Controls Bar (Play, Pause, -10s, +10s, Speed, Fullscreen)
                                div {
                                    style = "display: flex; flex-wrap: wrap; gap: 8px; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.03); padding: 10px 14px; border-radius: 12px; border: 1px solid var(--border);"
                                    div("toolbar") {
                                        button(classes = "btn btn-primary") {
                                            id = "btnSyncPlay"
                                            onClick = "sendSyncCommand('PLAY')"
                                            +"▶ Sync Play"
                                        }
                                        button(classes = "btn") {
                                            id = "btnSyncPause"
                                            onClick = "sendSyncCommand('PAUSE')"
                                            +"⏸ Sync Pause"
                                        }
                                        button(classes = "btn btn-sm") {
                                            onClick = "seekRelativeSeconds(-10)"
                                            +"⏪ -10s"
                                        }
                                        button(classes = "btn btn-sm") {
                                            onClick = "seekRelativeSeconds(10)"
                                            +"⏩ +10s"
                                        }
                                    }

                                    div("toolbar") {
                                        span { style = "font-size: 0.78rem; color: var(--text-muted);"; +"Speed:" }
                                        select(classes = "select-input") {
                                            id = "syncSpeedSelect"
                                            style = "width: 82px; padding: 5px 8px;"
                                            onChange = "sendSyncSpeed(this.value)"
                                            listOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0").forEach { spd ->
                                                option {
                                                    value = spd
                                                    if (spd == "1.0") selected = true
                                                    +"${spd}x"
                                                }
                                            }
                                        }
                                        button(classes = "btn btn-sm") {
                                            onClick = "toggleSyncMute()"
                                            id = "btnSyncMute"
                                            +"🔊 Audio"
                                        }
                                        button(classes = "btn btn-sm") {
                                            onClick = "fullscreenSyncStage()"
                                            +"⛶ Fullscreen"
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column: Identity, Passcode Authentication, Connected Peers & Sync Event Log
                        div {
                            style = "display: flex; flex-direction: column; gap: 14px;"

                            // Identity & Passcode Card
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") { +"🔐 Room Access & Identity" }
                                }
                                div {
                                    style = "padding: 14px; display: flex; flex-direction: column; gap: 10px;"
                                    div {
                                        label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Your Display Name" }
                                        input {
                                            id = "syncPeerNameInput"
                                            classes = setOf("text-input")
                                            type = InputType.text
                                            placeholder = "e.g. Alex's Tablet"
                                        }
                                    }
                                    div {
                                        id = "syncPasscodeBox"
                                        label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Room Passcode (if required by Host)" }
                                        div {
                                            style = "display: flex; gap: 6px; margin-top: 4px;"
                                            input {
                                                id = "syncPasscodeInput"
                                                classes = setOf("text-input")
                                                type = InputType.password
                                                placeholder = "Enter passcode..."
                                            }
                                            button(classes = "btn btn-primary btn-sm") {
                                                onClick = "submitSyncAuth()"
                                                +"Verify"
                                            }
                                        }
                                    }
                                    span {
                                        id = "syncAuthStatusMsg"
                                        style = "font-size: 0.76rem; color: var(--accent);"
                                    }
                                }
                            }

                            // Connected Peers & RTT Latency Card
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") {
                                        id = "syncPeersHeading"
                                        +"👥 Connected Peers (0)"
                                    }
                                }
                                div {
                                    id = "syncPeersList"
                                    style = "padding: 12px; display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow-y: auto;"
                                    div {
                                        style = "font-size: 0.8rem; color: var(--text-muted);"
                                        +"Connecting to SyncPlay WebSocket..."
                                    }
                                }
                            }

                            // Real-Time Sync Event Feed
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") { +"⚡ Live Sync Activity" }
                                }
                                div {
                                    id = "syncEventsList"
                                    style = "padding: 12px; display: flex; flex-direction: column; gap: 6px; max-height: 210px; overflow-y: auto; font-family: monospace; font-size: 0.75rem; color: var(--text-muted);"
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // SUBPAGE 3: LIVE SCREEN MIRRORING TAB
                // =========================================================================
                if (screenCastAllowed || hasScreencast) {
                    div("tab-section") {
                        id = "sectionScreen"
                        div("panel") {
                            div("panel-header") {
                                div("panel-title") { +"🖥️ Live Screen Stream (MJPEG)" }
                                div("toolbar") {
                                    button(classes = "btn btn-sm") {
                                        id = "btnPauseStream"
                                        onClick = "togglePauseScreenStream()"
                                        +"⏸ Pause"
                                    }
                                    button(classes = "btn btn-sm") {
                                        onClick = "reloadScreenStream()"
                                        +"🔄 Reconnect"
                                    }
                                    button(classes = "btn btn-sm") {
                                        onClick = "snapshotScreenStream()"
                                        +"📸 Snapshot"
                                    }
                                    button(classes = "btn btn-sm") {
                                        onClick = "rotateScreenStream()"
                                        +"⤵ Rotate 90°"
                                    }
                                    button(classes = "btn btn-sm btn-primary") {
                                        onClick = "fullscreenScreenStream()"
                                        +"⛶ Fullscreen"
                                    }
                                }
                            }
                            div("cast-stage") {
                                id = "castStage"
                                img {
                                    id = "mjpegStreamImg"
                                    alt = "Start Screen Sharing in the Explorer app to view live stream"
                                    src = if (hasScreencast) "/screencast.mjpeg" else ""
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // SUBPAGE 4: DEX-LIKE REMOTE BROWSER MINI-APPS STUDIO (5 INTERACTIVE TOOLS)
                // =========================================================================
                if (allowMiniApps) {
                    div("tab-section") {
                        id = "sectionMiniApps"
                        div("studio-shell") {
                            // Left App Dock Switcher + Host File Loader
                            div("studio-dock") {
                                div {
                                    style = "font-size: 0.76rem; font-weight: 700; text-transform: uppercase; color: var(--text-muted); padding: 4px 8px;"
                                    +"Workspace Apps (5)"
                                }
                                button(classes = "dock-app-btn active") {
                                    id = "dockBtnMarkor"
                                    onClick = "switchStudioApp('markor')"
                                    div("dock-icon") { +"📝" }
                                    div {
                                        div { style = "font-weight: 700; font-size: 0.85rem;"; +"Markor Markdown" }
                                        div { style = "font-size: 0.72rem; color: var(--text-muted);"; +"Live split preview & toolbar" }
                                    }
                                }
                                button(classes = "dock-app-btn") {
                                    id = "dockBtnCode"
                                    onClick = "switchStudioApp('code')"
                                    div("dock-icon") { +"💻" }
                                    div {
                                        div { style = "font-weight: 700; font-size: 0.85rem;"; +"Code Studio" }
                                        div { style = "font-size: 0.72rem; color: var(--text-muted);"; +"Syntax modes & JSON tools" }
                                    }
                                }
                                button(classes = "dock-app-btn") {
                                    id = "dockBtnCsv"
                                    onClick = "switchStudioApp('csv')"
                                    div("dock-icon") { +"📊" }
                                    div {
                                        div { style = "font-weight: 700; font-size: 0.85rem;"; +"CSV Table Studio" }
                                        div { style = "font-size: 0.72rem; color: var(--text-muted);"; +"Interactive spreadsheet grid" }
                                    }
                                }
                                button(classes = "dock-app-btn") {
                                    id = "dockBtnPhoto"
                                    onClick = "switchStudioApp('photo')"
                                    div("dock-icon") { +"🪪" }
                                    div {
                                        div { style = "font-weight: 700; font-size: 0.85rem;"; +"Image KB & Filter" }
                                        div { style = "font-size: 0.72rem; color: var(--text-muted);"; +"Exact-KB resizer & ink filter" }
                                    }
                                }
                                button(classes = "dock-app-btn") {
                                    id = "dockBtnConvert"
                                    onClick = "switchStudioApp('convert')"
                                    div("dock-icon") { +"🔄" }
                                    div {
                                        div { style = "font-weight: 700; font-size: 0.85rem;"; +"Host Converter" }
                                        div { style = "font-size: 0.72rem; color: var(--text-muted);"; +"PDF, Image, Data & Docs" }
                                    }
                                }

                                hr { style = "border: none; border-top: 1px solid var(--border); margin: 6px 0;" }

                                div {
                                    style = "padding: 4px 6px; display: flex; flex-direction: column; gap: 6px;"
                                    div {
                                        style = "font-size: 0.74rem; font-weight: 700; color: var(--text-muted);"
                                        +"📂 Open File from Host"
                                    }
                                    select(classes = "select-input") {
                                        id = "studioHostFileSelect"
                                        option {
                                            value = ""
                                            +"Loading host files..."
                                        }
                                    }
                                    button(classes = "btn btn-sm btn-primary") {
                                        onClick = "loadSelectedFileIntoActiveStudio()"
                                        +"📂 Open in Active App"
                                    }
                                }
                            }

                            // Right Active Studio Window Area
                            div {
                                // --- STUDIO APP 1: MARKOR MARKDOWN STUDIO ---
                                div("panel studio-window active") {
                                    id = "studioWinMarkor"
                                    div("panel-header") {
                                        div("panel-title") { +"📝 Markor Markdown Studio (Live Split Preview)" }
                                        div("toolbar") {
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('# ', '')"; +"H1" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('## ', '')"; +"H2" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('**', '**')"; +"B" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('*', '*')"; +"I" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('- [ ] ', '')"; +"☑ Task" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('`', '`')"; +"Code" }
                                            button(classes = "btn btn-sm") { onClick = "insertMarkdownSyntax('\\n| Col 1 | Col 2 |\\n|---|---|\\n| Val 1 | Val 2 |\\n', '')"; +"Table" }
                                        }
                                    }
                                    div {
                                        style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                        div {
                                            style = "display: flex; gap: 8px; align-items: center; flex-wrap: wrap;"
                                            input {
                                                id = "markorFileName"
                                                classes = setOf("text-input")
                                                style = "max-width: 320px;"
                                                type = InputType.text
                                                value = "Remote_Note.md"
                                                placeholder = "File name (e.g. Project_Notes.md)"
                                            }
                                            span {
                                                id = "markorWordStats"
                                                style = "font-size: 0.78rem; color: var(--text-muted);"
                                                +"0 words • 0 chars"
                                            }
                                        }
                                        div("md-split") {
                                            textArea(classes = "editor-textarea") {
                                                id = "markorEditorArea"
                                                onInput = "renderLiveMarkdownPreview()"
                                                +"# Continuum Markor Studio\n\nWrite **Markdown** notes with live side-by-side preview.\n\n- [x] Unified ServerManager\n- [ ] Try SyncPlay Watch Party\n- [ ] Save directly to Host Android storage"
                                            }
                                            div("md-preview-box") {
                                                id = "markorPreviewPane"
                                            }
                                        }
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;"
                                            span {
                                                id = "markorSaveStatus"
                                                style = "font-size: 0.8rem; color: var(--text-muted);"
                                                +"Saves to /Documents/ContinuumSuite/"
                                            }
                                            div("toolbar") {
                                                button(classes = "btn") {
                                                    onClick = "downloadStudioText('markorFileName', 'markorEditorArea')"
                                                    +"⬇ Download .md"
                                                }
                                                button(classes = "btn btn-primary") {
                                                    onClick = "saveStudioTextToHost('markorFileName', 'markorEditorArea', 'markorSaveStatus')"
                                                    +"💾 Save to Host Device"
                                                }
                                            }
                                        }
                                    }
                                }

                                // --- STUDIO APP 2: CODE STUDIO ---
                                div("panel studio-window") {
                                    id = "studioWinCode"
                                    div("panel-header") {
                                        div("panel-title") { +"💻 Remote Code Studio" }
                                        div("toolbar") {
                                            select(classes = "select-input") {
                                                id = "codeLangPreset"
                                                style = "width: 140px; padding: 5px 8px;"
                                                onChange = "applyCodeTemplate(this.value)"
                                                option { value = "kt"; +"Kotlin (.kt)" }
                                                option { value = "py"; +"Python (.py)" }
                                                option { value = "js"; +"JavaScript (.js)" }
                                                option { value = "json"; +"JSON (.json)" }
                                                option { value = "html"; +"HTML5 (.html)" }
                                            }
                                            button(classes = "btn btn-sm") {
                                                onClick = "formatJsonInCodeStudio()"
                                                +"✨ Format JSON"
                                            }
                                        }
                                    }
                                    div {
                                        style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap;"
                                            input {
                                                id = "codeFileName"
                                                classes = setOf("text-input")
                                                style = "max-width: 320px;"
                                                type = InputType.text
                                                value = "Remote_Script.kt"
                                            }
                                            span {
                                                id = "codeLineStats"
                                                style = "font-size: 0.78rem; color: var(--text-muted); font-family: monospace;"
                                                +"Lines: 3 • Chars: 64"
                                            }
                                        }
                                        textArea(classes = "editor-textarea") {
                                            id = "codeEditorArea"
                                            style = "min-height: 330px;"
                                            onInput = "updateCodeStudioStats()"
                                            +"fun main() {\n    println(\"Hello from Continuum Remote Code Studio!\")\n}"
                                        }
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;"
                                            span {
                                                id = "codeSaveStatus"
                                                style = "font-size: 0.8rem; color: var(--text-muted);"
                                                +"Ready"
                                            }
                                            div("toolbar") {
                                                button(classes = "btn") {
                                                    onClick = "downloadStudioText('codeFileName', 'codeEditorArea')"
                                                    +"⬇ Download Code"
                                                }
                                                button(classes = "btn btn-primary") {
                                                    onClick = "saveStudioTextToHost('codeFileName', 'codeEditorArea', 'codeSaveStatus')"
                                                    +"💾 Save to Host Device"
                                                }
                                            }
                                        }
                                    }
                                }

                                // --- STUDIO APP 3: INTERACTIVE CSV SPREADSHEET STUDIO ---
                                div("panel studio-window") {
                                    id = "studioWinCsv"
                                    div("panel-header") {
                                        div("panel-title") { +"📊 Interactive CSV Spreadsheet Studio" }
                                        div("toolbar") {
                                            button(classes = "btn btn-sm") { onClick = "csvAddRow()"; +"+ Add Row" }
                                            button(classes = "btn btn-sm") { onClick = "csvAddCol()"; +"+ Add Col" }
                                            button(classes = "btn btn-sm") { onClick = "csvDeleteLastRow()"; +"− Row" }
                                            button(classes = "btn btn-sm") { onClick = "csvSortByFirstCol()"; +"⇅ Sort A–Z" }
                                        }
                                    }
                                    div {
                                        style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap;"
                                            input {
                                                id = "csvFileName"
                                                classes = setOf("text-input")
                                                style = "max-width: 320px;"
                                                type = InputType.text
                                                value = "Remote_Data.csv"
                                            }
                                            span {
                                                id = "csvGridStats"
                                                style = "font-size: 0.78rem; color: var(--text-muted);"
                                                +"Click any cell below to edit"
                                            }
                                        }
                                        div("csv-grid-wrap") {
                                            table("csv-table") {
                                                id = "csvInteractiveTable"
                                            }
                                        }
                                        details {
                                            summary {
                                                style = "font-size: 0.78rem; color: var(--text-muted); cursor: pointer;"
                                                +"Raw CSV Source (auto-synced)"
                                            }
                                            textArea(classes = "editor-textarea") {
                                                id = "csvRawArea"
                                                style = "min-height: 110px; margin-top: 8px;"
                                                onInput = "parseRawCsvToTable()"
                                                +"ID,Name,Department,Score\n1,Alice,Engineering,98\n2,Bob,Design,94\n3,Charlie,Product,91"
                                            }
                                        }
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;"
                                            span {
                                                id = "csvSaveStatus"
                                                style = "font-size: 0.8rem; color: var(--text-muted);"
                                                +"Saves to /Documents/ContinuumSuite/"
                                            }
                                            div("toolbar") {
                                                button(classes = "btn") {
                                                    onClick = "downloadStudioText('csvFileName', 'csvRawArea')"
                                                    +"⬇ Download .csv"
                                                }
                                                button(classes = "btn btn-primary") {
                                                    onClick = "saveStudioTextToHost('csvFileName', 'csvRawArea', 'csvSaveStatus')"
                                                    +"💾 Save to Host Device"
                                                }
                                            }
                                        }
                                    }
                                }

                                // --- STUDIO APP 4: IMAGE KB RESIZER & SIGNATURE FILTER STUDIO ---
                                div("panel studio-window") {
                                    id = "studioWinPhoto"
                                    div("panel-header") {
                                        div("panel-title") { +"🪪 Image Exact-KB Resizer & Signature Filter Studio" }
                                        div("toolbar") {
                                            button(classes = "btn btn-sm") {
                                                onClick = "applyConverterPreset(200, 230, 35, 'none')"
                                                +"🪪 Exam Photo (200×230 • 35KB)"
                                            }
                                            button(classes = "btn btn-sm") {
                                                onClick = "applyConverterPreset(140, 60, 15, 'ink')"
                                                +"✍️ Ink Signature (140×60 • 15KB)"
                                            }
                                            button(classes = "btn btn-sm") {
                                                onClick = "applyConverterPreset(1280, 720, 120, 'none')"
                                                +"🖼️ HD 720p (120KB)"
                                            }
                                        }
                                    }
                                    div {
                                        style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                        input {
                                            id = "remoteConvInput"
                                            classes = setOf("text-input")
                                            type = InputType.file
                                            attributes["accept"] = "image/*"
                                            onChange = "previewSelectedPhotoInCanvas()"
                                        }
                                        div {
                                            style = "display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 8px;"
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Width (px)" }
                                                input {
                                                    id = "convWidth"
                                                    classes = setOf("text-input")
                                                    type = InputType.number
                                                    value = "200"
                                                    onInput = "previewSelectedPhotoInCanvas()"
                                                }
                                            }
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Height (px)" }
                                                input {
                                                    id = "convHeight"
                                                    classes = setOf("text-input")
                                                    type = InputType.number
                                                    value = "230"
                                                    onInput = "previewSelectedPhotoInCanvas()"
                                                }
                                            }
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Max Size (KB)" }
                                                input {
                                                    id = "convTargetKb"
                                                    classes = setOf("text-input")
                                                    type = InputType.number
                                                    value = "35"
                                                }
                                            }
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Visual Filter" }
                                                select(classes = "select-input") {
                                                    id = "convFilterMode"
                                                    onChange = "previewSelectedPhotoInCanvas()"
                                                    option { value = "none"; +"Original Color" }
                                                    option { value = "grayscale"; +"Grayscale" }
                                                    option { value = "ink"; +"B&W Signature Ink" }
                                                    option { value = "contrast"; +"High Contrast" }
                                                }
                                            }
                                        }
                                        div {
                                            style = "background: #070a12; border: 1px solid var(--border); border-radius: 12px; padding: 14px; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 180px;"
                                            canvas {
                                                id = "photoPreviewCanvas"
                                                style = "max-width: 100%; max-height: 240px; border-radius: 6px; border: 1px dashed rgba(255,255,255,0.2);"
                                            }
                                        }
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;"
                                            span {
                                                id = "remoteConvStatus"
                                                style = "font-size: 0.8rem; color: var(--accent);"
                                                +"Choose an image above to preview & compress."
                                            }
                                            button(classes = "btn btn-primary") {
                                                onClick = "runBrowserPhotoResizer()"
                                                +"⚡ Compress & Download Exact-KB JPEG"
                                            }
                                        }
                                    }
                                }

                                // --- STUDIO APP 5: HOST FILE CONVERTER ---
                                div("panel studio-window") {
                                    id = "studioWinConvert"
                                    div("panel-header") {
                                        div("panel-title") { +"🔄 Host File Format Converter (VERT + Squoosh Engine)" }
                                    }
                                    div {
                                        style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                        p {
                                            style = "color: var(--text-muted); font-size: 0.82rem;"
                                            +"Pick any shared file from the Host dropdown on the left (or enter its path below), choose a target format, and convert directly on the Android device."
                                        }
                                        input {
                                            id = "hostConvertSourcePath"
                                            classes = setOf("text-input")
                                            type = InputType.text
                                            placeholder = "Host file path (or pick from 'Open File from Host' on the left)"
                                        }
                                        div {
                                            style = "display: grid; grid-template-columns: 1fr 1fr; gap: 10px;"
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Target Format" }
                                                select(classes = "select-input") {
                                                    id = "hostConvertTargetFormat"
                                                    listOf("pdf", "jpg", "png", "webp", "html", "md", "txt", "csv", "json").forEach { fmt ->
                                                        option {
                                                            value = fmt
                                                            +fmt.uppercase(Locale.ROOT)
                                                        }
                                                    }
                                                }
                                            }
                                            div {
                                                label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Target Max KB (0 = automatic)" }
                                                input {
                                                    id = "hostConvertMaxKb"
                                                    classes = setOf("text-input")
                                                    type = InputType.number
                                                    value = "0"
                                                }
                                            }
                                        }
                                        div {
                                            style = "display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;"
                                            span {
                                                id = "hostConvertStatus"
                                                style = "font-size: 0.8rem; color: var(--accent);"
                                            }
                                            button(classes = "btn btn-primary") {
                                                onClick = "runHostFileConversion()"
                                                +"🔄 Convert on Host & Download"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lightbox Media, CSV, Markdown & Code Preview Modal
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
                        button(classes = "btn btn-sm") {
                            onClick = "closePreviewModal()"
                            +"✕ Close"
                        }
                    }
                    div("preview-body") { id = "previewModalBody" }
                    div {
                        style = "display: flex; justify-content: flex-end; gap: 8px;"
                        a(classes = "btn btn-primary", href = "#") {
                            id = "previewDownloadBtn"
                            +"⬇ Download File"
                        }
                    }
                }
            }

            // Upload Modal Dialog with Live Progress Bar
            div("modal-backdrop") {
                id = "uploadModal"
                div("modal") {
                    h3 { +"Upload Files to Host" }
                    p {
                        style = "font-size: 0.8rem; color: var(--text-muted);"
                        +"Destination Folder on Host: $uploadFolder"
                    }
                    if (requireUploadPass) {
                        input {
                            id = "uploadPasswordInput"
                            classes = setOf("text-input")
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
                        classes = setOf("text-input")
                        type = InputType.file
                        multiple = true
                    }
                    input {
                        id = "currentTargetDir"
                        type = InputType.hidden
                        value = uploadFolder
                    }
                    div {
                        style = "height: 6px; width: 100%; background: rgba(255,255,255,0.08); border-radius: 99px; overflow: hidden;"
                        div {
                            id = "uploadProgressBar"
                            style = "height: 100%; width: 0%; background: var(--accent); transition: width 0.15s ease;"
                        }
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
                        let streamRotation = 0;
                        let streamPaused = false;
                        let activeStudioApp = 'markor';
                        let currentLoadedHostPath = '';

                        // --- PORTAL TAB NAVIGATION ---
                        function switchPortalTab(tab) {
                            const sections = {
                                'files': document.getElementById('sectionFiles'),
                                'syncplay': document.getElementById('sectionSyncPlay'),
                                'screen': document.getElementById('sectionScreen'),
                                'miniapps': document.getElementById('sectionMiniApps')
                            };
                            const btns = {
                                'files': document.getElementById('tabBtnFiles'),
                                'syncplay': document.getElementById('tabBtnSyncPlay'),
                                'screen': document.getElementById('tabBtnScreen'),
                                'miniapps': document.getElementById('tabBtnMiniApps')
                            };
                            Object.keys(sections).forEach(k => {
                                if (sections[k]) sections[k].classList.toggle('active', k === tab);
                                if (btns[k]) btns[k].classList.toggle('active', k === tab);
                            });
                            if (tab === 'screen' && !streamPaused) {
                                reloadScreenStream();
                            }
                            if (tab === 'miniapps' || tab === 'syncplay') {
                                refreshHostFileLists();
                            }
                        }

                        function setViewMode(mode) {
                            document.body.setAttribute('data-view', mode);
                            document.getElementById('btnViewGrid').classList.toggle('active', mode === 'grid');
                            document.getElementById('btnViewTable').classList.toggle('active', mode === 'table');
                        }

                        // --- SCREEN MIRRORING CONTROLS ---
                        function reloadScreenStream() {
                            const img = document.getElementById('mjpegStreamImg');
                            streamPaused = false;
                            const btn = document.getElementById('btnPauseStream');
                            if (btn) btn.innerText = '⏸ Pause';
                            if (img) img.src = '/screencast.mjpeg?t=' + Date.now();
                        }

                        function togglePauseScreenStream() {
                            const img = document.getElementById('mjpegStreamImg');
                            const btn = document.getElementById('btnPauseStream');
                            if (!img) return;
                            streamPaused = !streamPaused;
                            if (streamPaused) {
                                img.src = '/screencast-frame?t=' + Date.now();
                                if (btn) btn.innerText = '▶ Resume';
                            } else {
                                img.src = '/screencast.mjpeg?t=' + Date.now();
                                if (btn) btn.innerText = '⏸ Pause';
                            }
                        }

                        function snapshotScreenStream() {
                            const a = document.createElement('a');
                            a.href = '/screencast-frame?t=' + Date.now();
                            a.download = 'Screen_Snapshot_' + Date.now() + '.jpg';
                            a.click();
                        }

                        function rotateScreenStream() {
                            streamRotation = (streamRotation + 90) % 360;
                            const img = document.getElementById('mjpegStreamImg');
                            if (img) img.style.transform = 'rotate(' + streamRotation + 'deg)';
                        }

                        function fullscreenScreenStream() {
                            const stage = document.getElementById('castStage');
                            if (stage && stage.requestFullscreen) stage.requestFullscreen();
                        }

                        function filterPortalFiles(query) {
                            const q = (query || '').toLowerCase().trim();
                            document.querySelectorAll('.file-item-entry').forEach(el => {
                                const name = el.getAttribute('data-name') || '';
                                el.style.display = (!q || name.includes(q)) ? '' : 'none';
                            });
                        }

                        // --- QUICK PREVIEW MODAL ---
                        async function openPreviewModal(name, url, ext) {
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
                                vid.src = url; vid.controls = true; vid.autoplay = true;
                                body.appendChild(vid);
                            } else if (['mp3','wav','ogg'].includes(ext)) {
                                const aud = document.createElement('audio');
                                aud.src = url; aud.controls = true; aud.autoplay = true;
                                body.appendChild(aud);
                            } else if (ext === 'pdf') {
                                const iframe = document.createElement('iframe');
                                iframe.src = url;
                                iframe.style.width = '100%';
                                iframe.style.height = '65vh';
                                iframe.style.border = 'none';
                                body.appendChild(iframe);
                            } else {
                                try {
                                    const res = await fetch(url);
                                    const text = await res.text();
                                    const pre = document.createElement('pre');
                                    pre.style.width = '100%';
                                    pre.style.whiteSpace = 'pre-wrap';
                                    pre.style.fontFamily = 'monospace';
                                    pre.style.fontSize = '12.5px';
                                    pre.style.color = '#e5e7eb';
                                    pre.innerText = text.slice(0, 100000);
                                    body.style.alignItems = 'flex-start';
                                    body.style.justifyContent = 'flex-start';
                                    body.appendChild(pre);
                                } catch (e) {
                                    body.innerText = 'Unable to load preview: ' + e.message;
                                }
                            }
                            modal.style.display = 'flex';
                        }

                        function closePreviewModal() {
                            document.getElementById('previewModalBody').innerHTML = '';
                            document.getElementById('previewModal').style.display = 'none';
                        }

                        function openUploadModal() {
                            document.getElementById('uploadModal').style.display = 'flex';
                            const bar = document.getElementById('uploadProgressBar');
                            if (bar) bar.style.width = '0%';
                        }
                        function closeUploadModal() {
                            document.getElementById('uploadModal').style.display = 'none';
                            document.getElementById('uploadStatusNotice').innerText = '';
                        }

                        function submitFileUpload() {
                            const pass = (document.getElementById('uploadPasswordInput') || {}).value || '';
                            const filesInput = document.getElementById('uploadFilesInput');
                            const targetDir = document.getElementById('currentTargetDir').value;
                            const notice = document.getElementById('uploadStatusNotice');
                            const bar = document.getElementById('uploadProgressBar');

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
                            notice.innerText = 'Uploading... 0%';
                            notice.style.color = '#fbbf24';

                            const xhr = new XMLHttpRequest();
                            xhr.open('POST', '/upload', true);
                            xhr.upload.onprogress = function(e) {
                                if (e.lengthComputable) {
                                    const pct = Math.round((e.loaded / e.total) * 100);
                                    if (bar) bar.style.width = pct + '%';
                                    notice.innerText = 'Uploading... ' + pct + '%';
                                }
                            };
                            xhr.onload = function() {
                                try {
                                    const data = JSON.parse(xhr.responseText || '{}');
                                    if (xhr.status >= 200 && xhr.status < 300 && data.success) {
                                        if (bar) bar.style.width = '100%';
                                        notice.innerText = data.message || 'Upload complete!';
                                        notice.style.color = '#10b981';
                                        setTimeout(() => window.location.reload(), 800);
                                    } else {
                                        notice.innerText = data.message || 'Upload failed';
                                        notice.style.color = '#ef4444';
                                    }
                                } catch (err) {
                                    notice.innerText = 'Upload failed';
                                    notice.style.color = '#ef4444';
                                }
                            };
                            xhr.send(formData);
                        }

                        // =====================================================================
                        // DEX-STYLE MINI-APPS STUDIO LOGIC (5 INTERACTIVE TOOLS + HOST LOADER)
                        // =====================================================================
                        function switchStudioApp(appId) {
                            activeStudioApp = appId;
                            const wins = {
                                'markor': 'studioWinMarkor',
                                'code': 'studioWinCode',
                                'csv': 'studioWinCsv',
                                'photo': 'studioWinPhoto',
                                'convert': 'studioWinConvert'
                            };
                            const btns = {
                                'markor': 'dockBtnMarkor',
                                'code': 'dockBtnCode',
                                'csv': 'dockBtnCsv',
                                'photo': 'dockBtnPhoto',
                                'convert': 'dockBtnConvert'
                            };
                            Object.keys(wins).forEach(k => {
                                const w = document.getElementById(wins[k]);
                                const b = document.getElementById(btns[k]);
                                if (w) w.classList.toggle('active', k === appId);
                                if (b) b.classList.toggle('active', k === appId);
                            });
                            if (appId === 'markor') renderLiveMarkdownPreview();
                            if (appId === 'csv') parseRawCsvToTable();
                        }

                        async function refreshHostFileLists() {
                            try {
                                const res = await fetch('/api/miniapp/list-files');
                                const data = await res.json();
                                if (!data.success || !Array.isArray(data.files)) return;
                                const studioSel = document.getElementById('studioHostFileSelect');
                                const syncSel = document.getElementById('syncHostMediaSelect');
                                if (studioSel) {
                                    studioSel.innerHTML = '<option value="">-- Select a Host File --</option>';
                                    data.files.forEach(f => {
                                        const opt = document.createElement('option');
                                        opt.value = f.path;
                                        opt.setAttribute('data-ext', f.ext || '');
                                        opt.innerText = f.name + ' (' + f.sizeFormatted + ')';
                                        studioSel.appendChild(opt);
                                    });
                                }
                                if (syncSel) {
                                    syncSel.innerHTML = '<option value="">-- Select Shared Video/Audio --</option>';
                                    data.files.filter(f => f.isMedia).forEach(f => {
                                        const opt = document.createElement('option');
                                        opt.value = f.path;
                                        opt.innerText = f.name + ' (' + f.sizeFormatted + ')';
                                        syncSel.appendChild(opt);
                                    });
                                }
                            } catch (e) {}
                        }

                        async function openHostFileInStudio(path, ext) {
                            switchPortalTab('miniapps');
                            if (ext === 'csv' || ext === 'tsv') switchStudioApp('csv');
                            else if (['kt','java','py','js','ts','json','html','css','xml'].includes(ext)) switchStudioApp('code');
                            else switchStudioApp('markor');
                            await loadHostFileIntoStudioByPath(path, ext);
                        }

                        async function loadSelectedFileIntoActiveStudio() {
                            const sel = document.getElementById('studioHostFileSelect');
                            if (!sel || !sel.value) {
                                alert('Pick a file from the Host dropdown first.');
                                return;
                            }
                            const opt = sel.options[sel.selectedIndex];
                            const ext = (opt && opt.getAttribute('data-ext')) || '';
                            if (activeStudioApp === 'convert') {
                                document.getElementById('hostConvertSourcePath').value = sel.value;
                                return;
                            }
                            await loadHostFileIntoStudioByPath(sel.value, ext);
                        }

                        async function loadHostFileIntoStudioByPath(path, ext) {
                            try {
                                const res = await fetch('/api/miniapp/read-file?path=' + encodeURIComponent(path));
                                const data = await res.json();
                                if (!data.success) {
                                    // If binary/image/PDF, route to Host Converter
                                    switchStudioApp('convert');
                                    document.getElementById('hostConvertSourcePath').value = path;
                                    return;
                                }
                                currentLoadedHostPath = data.path || '';
                                if (ext === 'csv' || ext === 'tsv' || activeStudioApp === 'csv') {
                                    switchStudioApp('csv');
                                    document.getElementById('csvFileName').value = data.fileName;
                                    document.getElementById('csvRawArea').value = data.content;
                                    parseRawCsvToTable();
                                } else if (activeStudioApp === 'code') {
                                    document.getElementById('codeFileName').value = data.fileName;
                                    document.getElementById('codeEditorArea').value = data.content;
                                    updateCodeStudioStats();
                                } else {
                                    switchStudioApp('markor');
                                    document.getElementById('markorFileName').value = data.fileName;
                                    document.getElementById('markorEditorArea').value = data.content;
                                    renderLiveMarkdownPreview();
                                }
                            } catch (e) {
                                alert('Could not load file: ' + e.message);
                            }
                        }

                        // --- MARKOR STUDIO HELPERS ---
                        function insertMarkdownSyntax(prefix, suffix) {
                            const area = document.getElementById('markorEditorArea');
                            if (!area) return;
                            const start = area.selectionStart || 0;
                            const end = area.selectionEnd || 0;
                            const sel = area.value.substring(start, end) || 'text';
                            area.value = area.value.substring(0, start) + prefix + sel + suffix + area.value.substring(end);
                            area.focus();
                            renderLiveMarkdownPreview();
                        }

                        function renderLiveMarkdownPreview() {
                            const area = document.getElementById('markorEditorArea');
                            const preview = document.getElementById('markorPreviewPane');
                            const stats = document.getElementById('markorWordStats');
                            if (!area || !preview) return;
                            const raw = area.value || '';
                            const words = raw.trim() ? raw.trim().split(/\s+/).length : 0;
                            if (stats) stats.innerText = words + ' words • ' + raw.length + ' chars';

                            let html = raw
                                .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                                .replace(/^### (.*$)/gim, '<h3>$1</h3>')
                                .replace(/^## (.*$)/gim, '<h2>$1</h2>')
                                .replace(/^# (.*$)/gim, '<h1>$1</h1>')
                                .replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
                                .replace(/\*(.*?)\*/gim, '<em>$1</em>')
                                .replace(/`([^`]+)`/gim, '<code>$1</code>')
                                .replace(/^- \[x\] (.*$)/gim, '<div>✅ <s>$1</s></div>')
                                .replace(/^- \[ \] (.*$)/gim, '<div>⬜ $1</div>')
                                .replace(/^- (.*$)/gim, '<li>$1</li>')
                                .replace(/\n/g, '<br/>');
                            preview.innerHTML = html;
                        }

                        // --- CODE STUDIO HELPERS ---
                        function applyCodeTemplate(lang) {
                            const fn = document.getElementById('codeFileName');
                            const area = document.getElementById('codeEditorArea');
                            const templates = {
                                'kt': ['Remote_Script.kt', 'fun main() {\n    val items = listOf("Continuum", "ServerManager", "SyncPlay")\n    items.forEach { println(it) }\n}'],
                                'py': ['Remote_Script.py', 'def main():\n    for item in ["Continuum", "ServerManager", "SyncPlay"]:\n        print(f"Active module: {item}")\n\nif __name__ == "__main__":\n    main()'],
                                'js': ['Remote_App.js', 'const modules = ["ServerManager", "SyncPlay", "MiniApps"];\nconsole.log("Loaded:", modules.join(", "));'],
                                'json': ['Remote_Config.json', '{\n  "app": "Continuum Explorer",\n  "syncPlay": true,\n  "leadTimeMs": 140\n}'],
                                'html': ['Remote_Page.html', '<!DOCTYPE html>\n<html>\n<head><title>Continuum Page</title></head>\n<body>\n  <h1>Hosted from Continuum</h1>\n</body>\n</html>']
                            };
                            if (templates[lang]) {
                                fn.value = templates[lang][0];
                                area.value = templates[lang][1];
                                updateCodeStudioStats();
                            }
                        }

                        function updateCodeStudioStats() {
                            const area = document.getElementById('codeEditorArea');
                            const stats = document.getElementById('codeLineStats');
                            if (!area || !stats) return;
                            const lines = (area.value || '').split('\n').length;
                            stats.innerText = 'Lines: ' + lines + ' • Chars: ' + (area.value || '').length;
                        }

                        function formatJsonInCodeStudio() {
                            const area = document.getElementById('codeEditorArea');
                            const status = document.getElementById('codeSaveStatus');
                            try {
                                const parsed = JSON.parse(area.value);
                                area.value = JSON.stringify(parsed, null, 2);
                                status.innerText = 'Valid JSON formatted!';
                                status.style.color = '#10b981';
                                updateCodeStudioStats();
                            } catch (e) {
                                status.innerText = 'JSON Syntax Error: ' + e.message;
                                status.style.color = '#ef4444';
                            }
                        }

                        // --- CSV SPREADSHEET STUDIO HELPERS ---
                        let csvMatrix = [['ID', 'Name', 'Department', 'Score'], ['1', 'Alice', 'Engineering', '98'], ['2', 'Bob', 'Design', '94']];

                        function parseRawCsvToTable() {
                            const raw = (document.getElementById('csvRawArea').value || '').trim();
                            const lines = raw ? raw.split(/\r?\n/) : ['Column 1,Column 2'];
                            csvMatrix = lines.map(l => l.split(','));
                            renderCsvTableFromMatrix();
                        }

                        function renderCsvTableFromMatrix() {
                            const tbl = document.getElementById('csvInteractiveTable');
                            if (!tbl) return;
                            tbl.innerHTML = '';
                            const maxCols = csvMatrix.reduce((m, r) => Math.max(m, r.length), 1);
                            csvMatrix.forEach((row, rIdx) => {
                                const tr = document.createElement('tr');
                                for (let cIdx = 0; cIdx < maxCols; cIdx++) {
                                    const cellEl = document.createElement(rIdx === 0 ? 'th' : 'td');
                                    const inp = document.createElement('input');
                                    inp.className = 'csv-cell-input';
                                    inp.style.fontWeight = rIdx === 0 ? '700' : '400';
                                    inp.value = row[cIdx] !== undefined ? row[cIdx] : '';
                                    inp.oninput = function() {
                                        if (!csvMatrix[rIdx]) csvMatrix[rIdx] = [];
                                        csvMatrix[rIdx][cIdx] = inp.value;
                                        syncCsvMatrixToRaw();
                                    };
                                    cellEl.appendChild(inp);
                                    tr.appendChild(cellEl);
                                }
                                tbl.appendChild(tr);
                            });
                            const stats = document.getElementById('csvGridStats');
                            if (stats) stats.innerText = csvMatrix.length + ' rows × ' + maxCols + ' cols';
                        }

                        function syncCsvMatrixToRaw() {
                            const rawArea = document.getElementById('csvRawArea');
                            if (rawArea) {
                                rawArea.value = csvMatrix.map(r => r.join(',')).join('\n');
                            }
                        }

                        function csvAddRow() {
                            const cols = csvMatrix[0] ? csvMatrix[0].length : 3;
                            csvMatrix.push(new Array(cols).fill(''));
                            syncCsvMatrixToRaw();
                            renderCsvTableFromMatrix();
                        }

                        function csvAddCol() {
                            csvMatrix.forEach((r, idx) => r.push(idx === 0 ? 'NewCol' : ''));
                            syncCsvMatrixToRaw();
                            renderCsvTableFromMatrix();
                        }

                        function csvDeleteLastRow() {
                            if (csvMatrix.length > 1) {
                                csvMatrix.pop();
                                syncCsvMatrixToRaw();
                                renderCsvTableFromMatrix();
                            }
                        }

                        function csvSortByFirstCol() {
                            if (csvMatrix.length <= 2) return;
                            const header = csvMatrix[0];
                            const body = csvMatrix.slice(1).sort((a, b) => (a[0] || '').localeCompare(b[0] || ''));
                            csvMatrix = [header, ...body];
                            syncCsvMatrixToRaw();
                            renderCsvTableFromMatrix();
                        }

                        // --- SHARED STUDIO SAVE & DOWNLOAD ---
                        async function saveStudioTextToHost(fnInputId, areaId, statusId) {
                            const fileName = document.getElementById(fnInputId).value || 'Remote_Note.md';
                            const content = document.getElementById(areaId).value || '';
                            const status = document.getElementById(statusId);
                            status.innerText = 'Saving to host...';
                            try {
                                const res = await fetch('/api/miniapp/save-text', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ fileName, content, targetPath: currentLoadedHostPath })
                                });
                                const data = await res.json();
                                status.innerText = data.message || 'Saved!';
                                status.style.color = data.success ? '#10b981' : '#ef4444';
                                refreshHostFileLists();
                            } catch (e) {
                                status.innerText = 'Save failed: ' + e.message;
                                status.style.color = '#ef4444';
                            }
                        }

                        function downloadStudioText(fnInputId, areaId) {
                            const fileName = document.getElementById(fnInputId).value || 'Remote_File.txt';
                            const content = document.getElementById(areaId).value || '';
                            const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
                            const a = document.createElement('a');
                            a.href = URL.createObjectURL(blob);
                            a.download = fileName;
                            a.click();
                        }

                        // --- IMAGE KB RESIZER & SIGNATURE INK FILTER ---
                        function applyConverterPreset(w, h, kb, filterMode) {
                            document.getElementById('convWidth').value = w;
                            document.getElementById('convHeight').value = h;
                            document.getElementById('convTargetKb').value = kb;
                            if (filterMode) document.getElementById('convFilterMode').value = filterMode;
                            previewSelectedPhotoInCanvas();
                        }

                        function applyCanvasPixelFilter(ctx, w, h, mode) {
                            if (!mode || mode === 'none') return;
                            const imgData = ctx.getImageData(0, 0, w, h);
                            const d = imgData.data;
                            for (let i = 0; i < d.length; i += 4) {
                                const lum = 0.299 * d[i] + 0.587 * d[i+1] + 0.114 * d[i+2];
                                if (mode === 'grayscale') {
                                    d[i] = d[i+1] = d[i+2] = lum;
                                } else if (mode === 'ink') {
                                    const v = lum < 148 ? 15 : 255;
                                    d[i] = d[i+1] = d[i+2] = v;
                                } else if (mode === 'contrast') {
                                    const f = 1.45;
                                    d[i] = Math.min(255, Math.max(0, (d[i] - 128) * f + 128));
                                    d[i+1] = Math.min(255, Math.max(0, (d[i+1] - 128) * f + 128));
                                    d[i+2] = Math.min(255, Math.max(0, (d[i+2] - 128) * f + 128));
                                }
                            }
                            ctx.putImageData(imgData, 0, 0);
                        }

                        function previewSelectedPhotoInCanvas() {
                            const input = document.getElementById('remoteConvInput');
                            const canvas = document.getElementById('photoPreviewCanvas');
                            if (!input || !input.files || !input.files[0] || !canvas) return;
                            const targetW = parseInt(document.getElementById('convWidth').value || '200', 10);
                            const targetH = parseInt(document.getElementById('convHeight').value || '230', 10);
                            const filterMode = document.getElementById('convFilterMode').value || 'none';
                            const img = new Image();
                            img.onload = function() {
                                canvas.width = targetW;
                                canvas.height = targetH;
                                const ctx = canvas.getContext('2d');
                                ctx.fillStyle = '#FFFFFF';
                                ctx.fillRect(0, 0, targetW, targetH);
                                ctx.drawImage(img, 0, 0, targetW, targetH);
                                applyCanvasPixelFilter(ctx, targetW, targetH, filterMode);
                            };
                            img.src = URL.createObjectURL(input.files[0]);
                        }

                        async function runBrowserPhotoResizer() {
                            const input = document.getElementById('remoteConvInput');
                            const status = document.getElementById('remoteConvStatus');
                            const canvas = document.getElementById('photoPreviewCanvas');
                            if (!input.files || !input.files[0]) {
                                alert('Please choose an image file first.');
                                return;
                            }
                            const targetW = parseInt(document.getElementById('convWidth').value || '200', 10);
                            const targetH = parseInt(document.getElementById('convHeight').value || '230', 10);
                            const maxKb = parseInt(document.getElementById('convTargetKb').value || '35', 10);
                            const filterMode = document.getElementById('convFilterMode').value || 'none';
                            const file = input.files[0];

                            const img = new Image();
                            img.onload = async function() {
                                canvas.width = targetW;
                                canvas.height = targetH;
                                const ctx = canvas.getContext('2d');
                                ctx.fillStyle = '#FFFFFF';
                                ctx.fillRect(0, 0, targetW, targetH);
                                ctx.drawImage(img, 0, 0, targetW, targetH);
                                applyCanvasPixelFilter(ctx, targetW, targetH, filterMode);

                                let low = 0.08, high = 0.96, bestBlob = null;
                                for (let i = 0; i < 8; i++) {
                                    const mid = (low + high) / 2;
                                    const blob = await new Promise(r => canvas.toBlob(r, 'image/jpeg', mid));
                                    if (blob.size <= maxKb * 1024) {
                                        bestBlob = blob;
                                        low = mid;
                                    } else {
                                        high = mid;
                                    }
                                }
                                if (!bestBlob) {
                                    bestBlob = await new Promise(r => canvas.toBlob(r, 'image/jpeg', 0.08));
                                }
                                const a = document.createElement('a');
                                a.href = URL.createObjectURL(bestBlob);
                                a.download = 'Resized_' + Math.round(bestBlob.size / 1024) + 'KB_' + file.name.replace(/\.[^/.]+$/, "") + '.jpg';
                                a.click();
                                status.innerText = 'Compressed to ' + (bestBlob.size / 1024).toFixed(1) + ' KB (' + targetW + '×' + targetH + ' px)';
                            };
                            img.src = URL.createObjectURL(file);
                        }

                        async function runHostFileConversion() {
                            const sourcePath = document.getElementById('hostConvertSourcePath').value || '';
                            const targetFormat = document.getElementById('hostConvertTargetFormat').value || 'pdf';
                            const maxKb = parseInt(document.getElementById('hostConvertMaxKb').value || '0', 10);
                            const status = document.getElementById('hostConvertStatus');
                            if (!sourcePath.trim()) {
                                alert('Select or enter a host file path to convert.');
                                return;
                            }
                            status.innerText = 'Converting on Android host...';
                            try {
                                const res = await fetch('/api/miniapp/convert', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ sourcePath, targetFormat, maxKb })
                                });
                                const data = await res.json();
                                status.innerText = data.message || '';
                                if (data.success && data.downloadUrl) {
                                    window.open(data.downloadUrl, '_blank');
                                }
                            } catch (e) {
                                status.innerText = 'Conversion error: ' + e.message;
                            }
                        }

                        // =====================================================================
                        // BEATSYNC-INSPIRED SYNCPLAY CLIENT ENGINE (NTP CLOCK + SCHEDULED EXEC)
                        // =====================================================================
                        let syncWs = null;
                        let syncPeerId = 'peer-' + Math.random().toString(36).substring(2, 8);
                        let clockOffsetSamples = [];
                        let estimatedClockOffsetMs = 0;
                        let latestRttMs = 8;
                        let currentStreamUrl = '';
                        let currentMediaIsVideo = true;
                        let scheduledTimerId = null;
                        let isDraggingSlider = false;

                        function getActiveMediaElement() {
                            return currentMediaIsVideo
                                ? document.getElementById('syncVideoPlayer')
                                : document.getElementById('syncAudioPlayer');
                        }

                        function estimatedServerNow() {
                            return Date.now() + estimatedClockOffsetMs;
                        }

                        function connectSyncPlayWebSocket() {
                            const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                            const nameInput = document.getElementById('syncPeerNameInput');
                            const savedName = localStorage.getItem('continuum_sync_name') || ('Guest-' + Math.floor(100 + Math.random() * 899));
                            if (nameInput && !nameInput.value) nameInput.value = savedName;

                            const wsUrl = proto + '//' + window.location.host + '/ws/syncplay?peerId=' + encodeURIComponent(syncPeerId) + '&name=' + encodeURIComponent(savedName);
                            try {
                                syncWs = new WebSocket(wsUrl);
                            } catch (e) {
                                return;
                            }

                            syncWs.onopen = function() {
                                sendClockPing();
                            };

                            syncWs.onmessage = function(ev) {
                                try {
                                    const msg = JSON.parse(ev.data);
                                    handleSyncWsMessage(msg);
                                } catch (e) {}
                            };

                            syncWs.onclose = function() {
                                setTimeout(connectSyncPlayWebSocket, 2500);
                            };
                        }

                        function sendClockPing() {
                            if (!syncWs || syncWs.readyState !== WebSocket.OPEN) return;
                            const media = getActiveMediaElement();
                            const ready = media ? (media.readyState >= 3) : false;
                            syncWs.send(JSON.stringify({
                                type: 'PING',
                                clientSendMs: Date.now(),
                                lastRttMs: Math.round(latestRttMs),
                                lastOffsetMs: Math.round(estimatedClockOffsetMs),
                                bufferReady: ready
                            }));
                        }

                        setInterval(sendClockPing, 2000);

                        function handleSyncWsMessage(msg) {
                            if (msg.type === 'PONG') {
                                const t3 = Date.now();
                                const t0 = msg.clientSendMs || t3;
                                const t1 = msg.serverRecvMs || t3;
                                const t2 = msg.serverSendMs || t3;
                                const rtt = Math.max(1, (t3 - t0) - (t2 - t1));
                                const offset = ((t1 - t0) + (t2 - t3)) / 2;

                                latestRttMs = rtt;
                                clockOffsetSamples.push(offset);
                                if (clockOffsetSamples.length > 7) clockOffsetSamples.shift();
                                const sorted = [...clockOffsetSamples].sort((a, b) => a - b);
                                estimatedClockOffsetMs = sorted[Math.floor(sorted.length / 2)];

                                const badge = document.getElementById('syncClockBadge');
                                if (badge) {
                                    badge.innerText = '⚡ RTT: ' + Math.round(rtt) + 'ms • Synced';
                                }
                                return;
                            }

                            if (msg.type === 'AUTH_RESULT') {
                                const st = document.getElementById('syncAuthStatusMsg');
                                if (st) {
                                    st.innerText = msg.message || '';
                                    st.style.color = msg.authenticated ? '#10b981' : '#ef4444';
                                }
                                return;
                            }

                            if (msg.type === 'ERROR') {
                                const st = document.getElementById('syncAuthStatusMsg');
                                if (st) {
                                    st.innerText = msg.message || 'Action denied';
                                    st.style.color = '#ef4444';
                                }
                                return;
                            }

                            // State / Command / Heartbeat / Welcome update
                            applyRoomStateMessage(msg);
                        }

                        function applyRoomStateMessage(state) {
                            const titleEl = document.getElementById('syncMediaTitle');
                            const emptyEl = document.getElementById('syncEmptyPrompt');
                            const vidEl = document.getElementById('syncVideoPlayer');
                            const audCard = document.getElementById('syncAudioVisualizerCard');
                            const audEl = document.getElementById('syncAudioPlayer');
                            const audLabel = document.getElementById('syncAudioTrackLabel');
                            const speedSel = document.getElementById('syncSpeedSelect');

                            renderSyncPeersAndEvents(state.peers || [], state.events || []);

                            if (!state.isActive || !state.streamUrl) {
                                if (titleEl) titleEl.innerText = 'No Media Loaded — Pick a Shared Audio/Video File';
                                if (emptyEl) emptyEl.style.display = 'block';
                                if (vidEl) { vidEl.pause(); vidEl.style.display = 'none'; }
                                if (audCard) audCard.style.display = 'none';
                                if (audEl) audEl.pause();
                                currentStreamUrl = '';
                                return;
                            }

                            if (emptyEl) emptyEl.style.display = 'none';
                            if (titleEl) titleEl.innerText = state.mediaFileName || 'Shared Media';
                            if (audLabel) audLabel.innerText = state.mediaFileName || 'Shared Audio';

                            currentMediaIsVideo = !!state.isVideo;
                            if (currentMediaIsVideo) {
                                vidEl.style.display = 'block';
                                audCard.style.display = 'none';
                                audEl.pause();
                            } else {
                                vidEl.style.display = 'none';
                                vidEl.pause();
                                audCard.style.display = 'flex';
                            }

                            const media = getActiveMediaElement();
                            if (!media) return;

                            if (currentStreamUrl !== state.streamUrl) {
                                currentStreamUrl = state.streamUrl;
                                media.src = state.streamUrl;
                                media.load();
                            }

                            const targetSpeed = parseFloat(state.playbackSpeed || 1.0);
                            if (Math.abs(media.playbackRate - targetSpeed) > 0.01) {
                                media.playbackRate = targetSpeed;
                            }
                            if (speedSel) speedSel.value = targetSpeed.toString();

                            const sub = document.getElementById('syncLastEventSub');
                            if (sub && state.lastActor) {
                                sub.innerText = 'Last command: ' + state.lastAction + ' by ' + state.lastActor + ' • Lead: ' + (state.scheduleLeadTimeMs || 140) + 'ms';
                            }

                            const execAt = state.executeAtServerEpochMs || 0;
                            const srvNow = estimatedServerNow();
                            const delayMs = execAt - srvNow;

                            if (state.type === 'COMMAND' || state.type === 'LOAD' || state.type === 'WELCOME' || state.type === 'SYNC') {
                                if (scheduledTimerId) {
                                    clearTimeout(scheduledTimerId);
                                    scheduledTimerId = null;
                                }

                                // Compute exact target position at execution instant
                                const targetSec = (state.basePositionMs || 0) / 1000.0;

                                // Pre-seek immediately during the lead window so buffer is warm at execution millisecond
                                if (Math.abs((media.currentTime || 0) - targetSec) > 0.06) {
                                    try { media.currentTime = targetSec; } catch (e) {}
                                }

                                const waitMs = Math.max(2, Math.min(1000, delayMs));
                                scheduledTimerId = setTimeout(() => {
                                    scheduledTimerId = null;
                                    if (state.isPlaying) {
                                        const compensatedSec = ((state.basePositionMs || 0) + Math.max(0, estimatedServerNow() - Math.max(execAt, state.stateUpdatedAtServerEpochMs || execAt)) * targetSpeed) / 1000.0;
                                        if (Math.abs((media.currentTime || 0) - compensatedSec) > 0.08) {
                                            try { media.currentTime = compensatedSec; } catch (e) {}
                                        }
                                        media.play().catch(() => {
                                            // Browser autoplay policy fallback: mute and play
                                            media.muted = true;
                                            const muteBtn = document.getElementById('btnSyncMute');
                                            if (muteBtn) muteBtn.innerText = '🔇 Unmute';
                                            media.play().catch(() => {});
                                        });
                                    } else {
                                        media.pause();
                                        try { media.currentTime = targetSec; } catch (e) {}
                                    }
                                }, waitMs);
                            } else if (state.type === 'HEARTBEAT') {
                                // Smooth drift correction during continuous playback
                                if (state.isPlaying && !media.paused) {
                                    const anchor = Math.max(state.executeAtServerEpochMs || 0, state.stateUpdatedAtServerEpochMs || srvNow);
                                    const expectedMs = (state.basePositionMs || 0) + Math.max(0, srvNow - anchor) * targetSpeed;
                                    const actualMs = (media.currentTime || 0) * 1000.0;
                                    const driftMs = actualMs - expectedMs;
                                    if (Math.abs(driftMs) > 280) {
                                        try { media.currentTime = expectedMs / 1000.0; } catch (e) {}
                                    }
                                } else if (!state.isPlaying && !media.paused) {
                                    media.pause();
                                }
                            }
                        }

                        function sendSyncCommand(action, customPosMs, customSpeed) {
                            const media = getActiveMediaElement();
                            const posMs = customPosMs !== undefined && customPosMs !== null
                                ? Math.round(customPosMs)
                                : Math.round((media ? (media.currentTime || 0) : 0) * 1000);
                            const speed = customSpeed !== undefined && customSpeed !== null
                                ? parseFloat(customSpeed)
                                : parseFloat(document.getElementById('syncSpeedSelect').value || '1.0');
                            const pass = (document.getElementById('syncPasscodeInput') || {}).value || '';
                            const name = (document.getElementById('syncPeerNameInput') || {}).value || 'Guest';
                            localStorage.setItem('continuum_sync_name', name);

                            if (syncWs && syncWs.readyState === WebSocket.OPEN) {
                                syncWs.send(JSON.stringify({
                                    type: 'COMMAND',
                                    action: action,
                                    positionMs: posMs,
                                    speed: speed,
                                    passcode: pass
                                }));
                            } else {
                                fetch('/api/syncplay/command', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ action, positionMs: posMs, speed, name, passcode: pass })
                                }).then(r => r.json()).then(d => {
                                    if (d.state) applyRoomStateMessage(d.state);
                                });
                            }
                        }

                        function seekRelativeSeconds(deltaSec) {
                            const media = getActiveMediaElement();
                            if (!media) return;
                            const nextMs = Math.max(0, ((media.currentTime || 0) + deltaSec) * 1000);
                            sendSyncCommand('SEEK', nextMs, null);
                        }

                        function sendSyncSpeed(val) {
                            sendSyncCommand('SPEED', null, parseFloat(val || '1.0'));
                        }

                        function onSyncSliderSeek(sliderVal) {
                            const media = getActiveMediaElement();
                            if (!media || !media.duration || isNaN(media.duration)) return;
                            const targetMs = (parseFloat(sliderVal) / 1000.0) * media.duration * 1000.0;
                            sendSyncCommand('SEEK', targetMs, null);
                        }

                        function forceResyncPlayback() {
                            fetch('/api/syncplay/state')
                                .then(r => r.json())
                                .then(st => {
                                    st.type = 'SYNC';
                                    applyRoomStateMessage(st);
                                });
                        }

                        function submitSyncAuth() {
                            const pass = (document.getElementById('syncPasscodeInput') || {}).value || '';
                            const name = (document.getElementById('syncPeerNameInput') || {}).value || 'Guest';
                            localStorage.setItem('continuum_sync_name', name);
                            if (syncWs && syncWs.readyState === WebSocket.OPEN) {
                                syncWs.send(JSON.stringify({ type: 'AUTH', passcode: pass, name: name }));
                            }
                        }

                        async function startSyncPlayWithHostFile(path) {
                            switchPortalTab('syncplay');
                            const pass = (document.getElementById('syncPasscodeInput') || {}).value || '';
                            try {
                                const res = await fetch('/api/syncplay/command', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ action: 'LOAD_HOST_FILE', path: path, passcode: pass })
                                });
                                const data = await res.json();
                                if (!data.success) {
                                    alert(data.message || 'Could not start SyncPlay');
                                } else if (data.state) {
                                    applyRoomStateMessage(data.state);
                                }
                            } catch (e) {
                                alert('Error starting SyncPlay: ' + e.message);
                            }
                        }

                        function loadSelectedMediaIntoSyncPlay() {
                            const sel = document.getElementById('syncHostMediaSelect');
                            if (!sel || !sel.value) {
                                alert('Select a shared video or audio file first.');
                                return;
                            }
                            startSyncPlayWithHostFile(sel.value);
                        }

                        function toggleSyncMute() {
                            const media = getActiveMediaElement();
                            const btn = document.getElementById('btnSyncMute');
                            if (!media) return;
                            media.muted = !media.muted;
                            if (btn) btn.innerText = media.muted ? '🔇 Unmute' : '🔊 Audio';
                        }

                        function fullscreenSyncStage() {
                            const stage = document.getElementById('syncMediaStage');
                            if (stage && stage.requestFullscreen) stage.requestFullscreen();
                        }

                        function renderSyncPeersAndEvents(peers, events) {
                            const heading = document.getElementById('syncPeersHeading');
                            const list = document.getElementById('syncPeersList');
                            const evBox = document.getElementById('syncEventsList');
                            if (heading) heading.innerText = '👥 Connected Peers (' + peers.length + ')';
                            if (list) {
                                list.innerHTML = '';
                                if (peers.length === 0) {
                                    list.innerHTML = '<div style="font-size:0.8rem;color:var(--text-muted);">Host active • Waiting for browser peers</div>';
                                } else {
                                    peers.forEach(p => {
                                        const row = document.createElement('div');
                                        row.className = 'peer-item';
                                        const readyBadge = p.ready ? '🟢 Ready' : '🟡 Buffering';
                                        row.innerHTML = '<div><strong>' + p.name + '</strong> <span style="color:var(--text-muted);font-size:0.72rem;">(' + p.ip + ')</span></div>' +
                                            '<div style="font-size:0.74rem;color:#34d399;">' + readyBadge + ' • ' + p.rttMs + 'ms</div>';
                                        list.appendChild(row);
                                    });
                                }
                            }
                            if (evBox) {
                                evBox.innerHTML = '';
                                events.forEach(e => {
                                    const d = document.createElement('div');
                                    d.innerText = e;
                                    evBox.appendChild(d);
                                });
                            }
                        }

                        function formatSeconds(sec) {
                            if (!sec || isNaN(sec) || sec < 0) return '00:00';
                            const m = Math.floor(sec / 60);
                            const s = Math.floor(sec % 60);
                            return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
                        }

                        // Update timeline scrubber smoothly
                        setInterval(() => {
                            const media = getActiveMediaElement();
                            if (!media) return;
                            const curEl = document.getElementById('syncCurrentTimeLabel');
                            const durEl = document.getElementById('syncDurationLabel');
                            const slider = document.getElementById('syncSeekSlider');
                            const bufBadge = document.getElementById('syncBufferBadge');

                            if (curEl) curEl.innerText = formatSeconds(media.currentTime || 0);
                            if (durEl) durEl.innerText = formatSeconds(media.duration || 0);
                            if (slider && media.duration && !isNaN(media.duration) && media.duration > 0) {
                                slider.value = Math.round(((media.currentTime || 0) / media.duration) * 1000);
                            }
                            if (bufBadge) {
                                bufBadge.innerText = media.readyState >= 3 ? '🟢 Pre-Buffer Ready' : '🟡 Caching Stream...';
                            }
                        }, 250);

                        // Initialize live components on load
                        renderLiveMarkdownPreview();
                        parseRawCsvToTable();
                        refreshHostFileLists();
                        connectSyncPlayWebSocket();

                        if ('${initialTab}' === 'screen') switchPortalTab('screen');
                        else if ('${initialTab}' === 'miniapps') switchPortalTab('miniapps');
                        else if ('${initialTab}' === 'syncplay') switchPortalTab('syncplay');
                    """)
                }
            }
        }
    }
}
