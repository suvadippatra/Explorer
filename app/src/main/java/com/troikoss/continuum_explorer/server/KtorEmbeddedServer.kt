package com.troikoss.continuum_explorer.server

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.html.*
import org.json.JSONArray
import org.json.JSONObject
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
 * Lightweight, Netty-backed Ktor HTTP, Static Website, MJPEG/WebSocket Screen Stream & Remote Mini-Apps Server on port 8080.
 * - Supports starting with 0 selected files/folders (empty bucket) so users can host Screen Sharing or Remote Mini-Apps standalone.
 * - Exposes live host telemetry (Device Name, Battery %, Storage Free/Total) on the web portal and via `/api/device-status`.
 * - Provides `/api/files` JSON listing for native FTP/HTTP clients (like ExternalFtpViewerPanel).
 * - Streams live screen via `/screencast.mjpeg` (universal multipart MJPEG), `/screencast` (WebSocket), and `/screencast-frame`.
 * - Hosts Remote Browser Mini-Apps (`Files`, `Screen`, `Mini-Apps` top sub-navigation tabs) when enabled by admin.
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

    private data class DeviceTelemetry(
        val deviceName: String,
        val batteryPercent: Int,
        val isCharging: Boolean,
        val freeBytes: Long,
        val totalBytes: Long,
        val usedPercent: Int,
        val freeFormatted: String,
        val totalFormatted: String
    )

    private fun readDeviceTelemetry(): DeviceTelemetry {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty()
        val model = Build.MODEL.orEmpty()
        val devName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
        val charging = bm?.isCharging ?: false

        val stat = try {
            StatFs(Environment.getExternalStorageDirectory().absolutePath)
        } catch (_: Exception) {
            null
        }
        val freeB = stat?.availableBytes ?: 0L
        val totalB = (stat?.totalBytes ?: 1L).coerceAtLeast(1L)
        val usedPct = (((totalB - freeB).toDouble() / totalB.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        return DeviceTelemetry(
            deviceName = devName.ifBlank { "Android Host" },
            batteryPercent = batteryPct,
            isCharging = charging,
            freeBytes = freeB,
            totalBytes = totalB,
            usedPercent = usedPct,
            freeFormatted = formatBytes(freeB),
            totalFormatted = formatBytes(totalB)
        )
    }

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
                        val initialTab = call.parameters["tab"] ?: "files"

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

                        val files: List<File> = when {
                            hostedMode == FtpHostedMode.BATCH_FILES && requestedPath.isNullOrEmpty() -> {
                                // Respect 0-selected-files mode: if bucket is empty, return emptyList()!
                                FtpBucketManager.bucketItems.filter { it.exists() }.sortedWith(
                                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                                )
                            }
                            else -> {
                                currentDir.listFiles()?.sortedWith(
                                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                                ) ?: emptyList()
                            }
                        }

                        ContinuumHubService.addLog(clientIp, "HTTP", "Browsed portal", currentDir.name)

                        call.respondHtml(HttpStatusCode.OK) {
                            renderDashboard(
                                currentDir = currentDir,
                                rootDir = effectiveRootDir,
                                files = files,
                                isRootView = requestedPath.isNullOrEmpty(),
                                hasWebsiteEntry = resolvedEntryHtml?.exists() == true,
                                initialTab = initialTab
                            )
                        }
                    }

                    // 2. Native JSON File Listing Route (used by ExternalFtpViewerPanel & remote mini-apps)
                    get("/api/files") {
                        val requestedPath = call.parameters["path"]?.let {
                            URLDecoder.decode(it, "UTF-8")
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

                        val files: List<File> = if (hostedMode == FtpHostedMode.BATCH_FILES && requestedPath.isNullOrEmpty()) {
                            FtpBucketManager.bucketItems.filter { it.exists() }.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            )
                        } else {
                            currentDir.listFiles()?.sortedWith(
                                compareBy({ !it.isDirectory }, { it.name.lowercase() })
                            ) ?: emptyList()
                        }

                        val arr = JSONArray()
                        for (f in files) {
                            val encoded = URLEncoder.encode(f.absolutePath, "UTF-8")
                            val obj = JSONObject().apply {
                                put("name", f.name)
                                put("path", f.absolutePath)
                                put("isDirectory", f.isDirectory)
                                put("sizeBytes", if (f.isDirectory) 0L else f.length())
                                put("lastModified", f.lastModified())
                                put("downloadUrl", "/download?file=$encoded")
                            }
                            arr.put(obj)
                        }
                        val rootObj = JSONObject().apply {
                            put("currentPath", currentDir.absolutePath)
                            put("currentName", currentDir.name.ifBlank { "Root" })
                            put("parentPath", if (requestedPath.isNullOrEmpty()) "" else (currentDir.parentFile?.absolutePath ?: ""))
                            put("items", arr)
                        }
                        call.respondText(rootObj.toString(), ContentType.Application.Json)
                    }

                    // 3. Direct File Download / Inline Preview Route
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

                    // 4A. Live Screen Sharing MJPEG Stream Route (Universal Browser & Native Client Support)
                    get("/screencast.mjpeg") {
                        val screenCast = getScreenCastManager()
                        if (!SettingsManager.screenCastEnabled.value || screenCast == null || !screenCast.isActive()) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "Live screen sharing is not active")
                            return@get
                        }
                        call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
                        call.response.header(HttpHeaders.Connection, "keep-alive")
                        call.respondBytesWriter(
                            contentType = ContentType.parse("multipart/x-mixed-replace; boundary=frame")
                        ) {
                            val boundaryBytes = "--frame\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray()
                            val tailBytes = "\r\n".toByteArray()
                            while (true) {
                                val activeCast = getScreenCastManager()
                                if (activeCast == null || !activeCast.isActive()) break
                                val frame = activeCast.getLatestFrame()
                                if (frame != null) {
                                    writeFully(boundaryBytes, 0, boundaryBytes.size)
                                    writeFully(frame, 0, frame.size)
                                    writeFully(tailBytes, 0, tailBytes.size)
                                    flush()
                                }
                                val fps = SettingsManager.screenCastFps.value.coerceIn(5, 30)
                                delay((1000L / fps).coerceAtLeast(33L))
                            }
                        }
                    }

                    // 4B. Live Screen Sharing WebSocket Route
                    webSocket("/screencast") {
                        val screenCast = getScreenCastManager()
                        if (!SettingsManager.screenCastEnabled.value || screenCast == null || !screenCast.isActive()) {
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

                    // 4C. Live Screen Sharing HTTP Frame Snapshot Fallback Route
                    get("/screencast-frame") {
                        val screenCast = getScreenCastManager()
                        val frame = screenCast?.getLatestFrame()
                        if (!SettingsManager.screenCastEnabled.value || screenCast == null || !screenCast.isActive() || frame == null) {
                            call.respond(HttpStatusCode.NoContent)
                            return@get
                        }
                        call.response.header(HttpHeaders.CacheControl, "no-store, no-cache, must-revalidate, max-age=0")
                        call.respondBytes(frame, ContentType.Image.JPEG, HttpStatusCode.OK)
                    }

                    // 5. API Status & Device Telemetry Route
                    get("/api/status") {
                        val telemetry = readDeviceTelemetry()
                        val screenCast = getScreenCastManager()
                        val hasScreencast = SettingsManager.screenCastEnabled.value && screenCast != null && screenCast.isActive()
                        val activeFtpPort = ContinuumHubService.activeFtpPort
                        val json = JSONObject().apply {
                            put("status", "online")
                            put("httpPort", port)
                            put("ftpPort", activeFtpPort)
                            put("mode", hostedMode.name)
                            put("screencast", hasScreencast)
                            put("screenCastEnabled", SettingsManager.screenCastEnabled.value)
                            put("fps", SettingsManager.screenCastFps.value)
                            put("quality", SettingsManager.screenCastQuality.value)
                            put("allowMiniApps", SettingsManager.ftpAllowMiniApps.value)
                            put("allowUploads", SettingsManager.ftpAllowUpload.value)
                            put("deviceName", telemetry.deviceName)
                            put("batteryPercent", telemetry.batteryPercent)
                            put("freeStorage", telemetry.freeFormatted)
                            put("totalStorage", telemetry.totalFormatted)
                        }
                        call.respondText(json.toString(), ContentType.Application.Json)
                    }

                    get("/api/device-status") {
                        val telemetry = readDeviceTelemetry()
                        val screenCast = getScreenCastManager()
                        val hasScreencast = SettingsManager.screenCastEnabled.value && screenCast != null && screenCast.isActive()
                        val json = JSONObject().apply {
                            put("deviceName", telemetry.deviceName)
                            put("batteryPercent", telemetry.batteryPercent)
                            put("isCharging", telemetry.isCharging)
                            put("usedStoragePercent", telemetry.usedPercent)
                            put("freeStorageBytes", telemetry.freeBytes)
                            put("totalStorageBytes", telemetry.totalBytes)
                            put("freeStorageFormatted", telemetry.freeFormatted)
                            put("totalStorageFormatted", telemetry.totalFormatted)
                            put("screencastActive", hasScreencast)
                            put("allowMiniApps", SettingsManager.ftpAllowMiniApps.value)
                            put("allowUploads", SettingsManager.ftpAllowUpload.value)
                            put("uploadFolder", SettingsManager.ftpUploadFolderPath.value)
                        }
                        call.respondText(json.toString(), ContentType.Application.Json)
                    }

                    // 6A. Remote Browser Mini-Apps Save Endpoint (Markor Markdown, Code Studio, CSV Studio)
                    post("/api/miniapp/save-text") {
                        if (!SettingsManager.ftpAllowMiniApps.value) {
                            call.respondText(
                                """{"success":false,"message":"Remote Mini-Apps are disabled by the host administrator."}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                            return@post
                        }
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val bodyText = call.receiveText()
                        val payload = try { JSONObject(bodyText) } catch (_: Exception) { JSONObject() }
                        val rawName = payload.optString("fileName", "Remote_Note.md").trim()
                            .replace("/", "_")
                            .replace("\\", "_")
                        val content = payload.optString("content", "")

                        val suiteDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            "ContinuumSuite"
                        ).apply { if (!exists()) mkdirs() }

                        val targetFile = File(suiteDir, rawName.ifBlank { "Remote_${System.currentTimeMillis()}.txt" })
                        targetFile.writeText(content)

                        SuiteRecentDatabase.recordFileActivity(
                            context = this@KtorEmbeddedServer.context,
                            file = targetFile,
                            activityType = SuiteActivityType.CREATED
                        )
                        ContinuumHubService.addLog(
                            clientIp,
                            "Mini-App",
                            "Saved ${targetFile.name}",
                            "${targetFile.length()} B",
                            bytes = targetFile.length()
                        )
                        val resp = JSONObject().apply {
                            put("success", true)
                            put("message", "Saved to Host: ${targetFile.absolutePath}")
                            put("path", targetFile.absolutePath)
                        }
                        call.respondText(resp.toString(), ContentType.Application.Json)
                    }

                    // 6B. Remote Browser Mini-Apps Host File Converter Endpoint (/api/miniapp/convert)
                    post("/api/miniapp/convert") {
                        if (!SettingsManager.ftpAllowMiniApps.value) {
                            call.respondText(
                                """{"success":false,"message":"Remote Mini-Apps are disabled by the host administrator."}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                            return@post
                        }
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val bodyText = call.receiveText()
                        val payload = try { JSONObject(bodyText) } catch (_: Exception) { JSONObject() }
                        val sourcePath = payload.optString("sourcePath", "").trim()
                        val targetFormat = payload.optString("targetFormat", "pdf").trim().lowercase()
                        val maxKb = payload.optInt("maxKb", 0)

                        val srcFile = File(sourcePath)
                        if (!srcFile.exists() || !srcFile.isFile) {
                            call.respondText(
                                """{"success":false,"message":"Source file not found on host: $sourcePath"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                            return@post
                        }

                        val convertedDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            "ContinuumSuite/Converted"
                        ).apply { if (!exists()) mkdirs() }

                        val outFile = File(convertedDir, "${srcFile.nameWithoutExtension}_converted.$targetFormat")
                        val result = if (maxKb > 0 && targetFormat in listOf("jpg", "jpeg")) {
                            com.troikoss.continuum_explorer.ui.converter.SquooshImageCompressor.compressImageToTargetKb(
                                context = this@KtorEmbeddedServer.context,
                                sourceFile = srcFile,
                                outputFile = outFile,
                                minTargetKb = (maxKb - 10).coerceAtLeast(5),
                                maxTargetKb = maxKb
                            )
                        } else {
                            val item = com.troikoss.continuum_explorer.ui.converter.FileConverterDispatcher.createQueueItem(srcFile)
                            if (item != null) {
                                item.targetFormat = targetFormat
                                com.troikoss.continuum_explorer.ui.converter.FileConverterDispatcher.convertSingleItem(
                                    context = this@KtorEmbeddedServer.context,
                                    item = item,
                                    destinationMode = com.troikoss.continuum_explorer.ui.converter.OutputDestinationMode.CUSTOM_FOLDER,
                                    customFolder = convertedDir,
                                    onProgress = { _, _ -> }
                                )
                            } else {
                                Result.failure(IllegalArgumentException("Unsupported file type"))
                            }
                        }

                        result.fold(
                            onSuccess = { generated ->
                                ContinuumHubService.addLog(
                                    clientIp,
                                    "Mini-App Convert",
                                    "Converted ${srcFile.name} -> ${generated.name}",
                                    "${generated.length() / 1024} KB",
                                    bytes = generated.length()
                                )
                                val encoded = URLEncoder.encode(generated.absolutePath, "UTF-8")
                                val resp = JSONObject().apply {
                                    put("success", true)
                                    put("message", "Converted: ${generated.name} (${generated.length() / 1024} KB)")
                                    put("outputPath", generated.absolutePath)
                                    put("downloadUrl", "/download?file=$encoded")
                                }
                                call.respondText(resp.toString(), ContentType.Application.Json)
                            },
                            onFailure = { err ->
                                val resp = JSONObject().apply {
                                    put("success", false)
                                    put("message", err.message ?: "Host conversion failed")
                                }
                                call.respondText(resp.toString(), ContentType.Application.Json)
                            }
                        )
                    }

                    // 7. Unified Multipart File Upload Route (Honors SettingsManager.ftpUploadFolderPath)
                    post("/upload") {
                        val clientIp = try { call.request.local.remoteHost } catch (_: Exception) { "Client" }
                        val expectedPass = SettingsManager.ftpUploadPassword.value
                        val isUploadAllowed = SettingsManager.ftpAllowUpload.value

                        if (!isUploadAllowed) {
                            call.respondText(
                                """{"success":false,"message":"Uploads are disabled on the host device"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.Forbidden
                            )
                            return@post
                        }

                        var providedPass = call.request.headers["X-Upload-Password"] ?: ""
                        val configuredUploadDir = SettingsManager.ftpUploadFolderPath.value.ifBlank {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        }
                        var targetFolderPath = if (hostedMode == FtpHostedMode.BATCH_FILES) {
                            configuredUploadDir
                        } else {
                            configuredUploadDir.ifBlank { effectiveRootDir.absolutePath }
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
                                            "${saveFile.length() / 1024} KB -> ${destFolder.name}",
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
                                """{"success":true,"message":"Uploaded $savedFilesCount file(s) to $targetFolderPath"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.OK
                            )
                        }
                    }

                    // 8. Catch-All Static Website Asset Route
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
        hasWebsiteEntry: Boolean,
        initialTab: String
    ) {
        val telemetry = readDeviceTelemetry()
        val screenCast = getScreenCastManager()
        val hasScreencast = SettingsManager.screenCastEnabled.value && screenCast != null && screenCast.isActive()
        val isScreenCastEnabled = SettingsManager.screenCastEnabled.value
        val allowUploads = SettingsManager.ftpAllowUpload.value
        val allowMiniApps = SettingsManager.ftpAllowMiniApps.value
        val requireUploadPass = SettingsManager.ftpUploadPassword.value.isNotBlank()
        val uploadFolder = SettingsManager.ftpUploadFolderPath.value.ifBlank {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }
        val ftpPort = ContinuumHubService.activeFtpPort
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

        val folderCount = files.count { it.isDirectory }
        val fileCount = files.count { !it.isDirectory }
        val totalBytes = files.filter { !it.isDirectory }.sumOf { it.length() }

        head {
            title("${telemetry.deviceName} • Explorer Web Portal")
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
                            background: rgba(19, 24, 34, 0.92);
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
                            width: 36px;
                            height: 36px;
                            border-radius: 10px;
                            background: linear-gradient(135deg, #6366f1, #8b5cf6);
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 18px;
                        }
                        .brand-title {
                            font-size: 1rem;
                            font-weight: 700;
                            color: #fff;
                        }
                        .telemetry-pills {
                            display: flex;
                            align-items: center;
                            gap: 6px;
                            flex-wrap: wrap;
                        }
                        .pill {
                            background: var(--surface-elevated);
                            border: 1px solid var(--border);
                            color: var(--text);
                            font-size: 0.74rem;
                            font-weight: 600;
                            padding: 4px 10px;
                            border-radius: 999px;
                            display: inline-flex;
                            align-items: center;
                            gap: 5px;
                        }
                        .pill-online {
                            background: var(--accent-soft);
                            color: var(--accent);
                            border-color: rgba(16, 185, 129, 0.3);
                        }
                        .nav-tabs {
                            display: inline-flex;
                            background: var(--bg);
                            border: 1px solid var(--border);
                            border-radius: 11px;
                            padding: 3px;
                            gap: 3px;
                        }
                        .nav-tab {
                            background: transparent;
                            color: var(--text-muted);
                            border: none;
                            padding: 7px 14px;
                            font-size: 0.81rem;
                            font-weight: 600;
                            border-radius: 8px;
                            cursor: pointer;
                            transition: all 0.15s ease;
                        }
                        .nav-tab.active {
                            background: var(--primary);
                            color: #fff;
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
                            white-space: nowrap;
                        }
                        .btn:hover { background: var(--surface-hover); }
                        .btn-primary {
                            background: var(--primary);
                            border-color: var(--primary);
                            color: #fff;
                        }
                        .btn-primary:hover { background: var(--primary-hover); }
                        .workspace {
                            flex: 1;
                            width: 100%;
                            max-width: 1600px;
                            margin: 0 auto;
                            padding: clamp(10px, 2vw, 22px);
                        }
                        .tab-section { display: none; }
                        .tab-section.active { display: block; }
                        .stats-strip {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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
                            font-size: 0.72rem;
                            color: var(--text-muted);
                            text-transform: uppercase;
                        }
                        .stat-val {
                            font-size: 0.96rem;
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
                        }
                        .search-input, .editor-textarea, .text-input {
                            width: 100%;
                            background: var(--bg);
                            border: 1px solid var(--border);
                            color: var(--text);
                            padding: 8px 12px;
                            border-radius: 9px;
                            font-size: 0.84rem;
                            outline: none;
                        }
                        .editor-textarea {
                            min-height: 280px;
                            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                            resize: vertical;
                        }
                        .file-grid {
                            display: none;
                            grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                            gap: 12px;
                            padding: 16px;
                        }
                        body[data-view="grid"] .file-grid { display: grid; }
                        body[data-view="grid"] .table-wrapper { display: none; }
                        .file-card {
                            background: var(--surface-elevated);
                            border: 1px solid var(--border);
                            border-radius: 12px;
                            padding: 14px;
                            display: flex;
                            flex-direction: column;
                            justify-content: space-between;
                            gap: 12px;
                        }
                        .card-top { display: flex; align-items: flex-start; gap: 11px; }
                        .card-icon {
                            width: 40px; height: 40px; border-radius: 10px;
                            background: rgba(255,255,255,0.05);
                            display: flex; align-items: center; justify-content: center;
                            font-size: 20px; flex-shrink: 0;
                        }
                        .card-title {
                            font-weight: 600; color: var(--text); text-decoration: none;
                            word-break: break-word; font-size: 0.86rem;
                        }
                        .card-meta { font-size: 0.74rem; color: var(--text-muted); margin-top: 3px; }
                        .card-actions {
                            display: flex; align-items: center; justify-content: space-between;
                            gap: 6px; padding-top: 8px; border-top: 1px solid rgba(255,255,255,0.06);
                        }
                        .badge-ext {
                            font-size: 0.68rem; font-weight: 700; padding: 2px 7px;
                            border-radius: 5px; background: rgba(99, 102, 241, 0.15); color: #a5b4fc;
                        }
                        .table-wrapper { width: 100%; overflow-x: auto; }
                        .file-table { width: 100%; border-collapse: collapse; }
                        .file-table th {
                            text-align: left; padding: 11px 16px; font-size: 0.73rem;
                            text-transform: uppercase; color: var(--text-muted);
                            border-bottom: 1px solid var(--border); background: #10151f;
                        }
                        .file-table td {
                            padding: 11px 16px; border-bottom: 1px solid rgba(255,255,255,0.05);
                        }
                        .item-link {
                            color: var(--text); text-decoration: none; font-weight: 500;
                            display: inline-flex; align-items: center; gap: 10px;
                        }
                        .cast-stage {
                            background: #05070b;
                            min-height: 360px;
                            max-height: 80vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            padding: 16px;
                            overflow: hidden;
                        }
                        #mjpegStreamImg {
                            max-width: 100%;
                            max-height: 74vh;
                            border-radius: 10px;
                            box-shadow: 0 8px 30px rgba(0,0,0,0.7);
                            transition: transform 0.2s ease;
                        }
                        .miniapps-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
                            gap: 16px;
                        }
                        .modal-backdrop {
                            position: fixed; inset: 0;
                            background: rgba(5, 7, 12, 0.78);
                            backdrop-filter: blur(6px);
                            display: none; align-items: center; justify-content: center;
                            z-index: 200; padding: 16px;
                        }
                        .modal {
                            background: var(--surface); border: 1px solid var(--border);
                            border-radius: 16px; padding: 20px; width: 100%; max-width: 520px;
                            display: flex; flex-direction: column; gap: 14px;
                        }
                        .modal-preview { max-width: 960px; max-height: 90vh; }
                        .preview-body {
                            display: flex; align-items: center; justify-content: center;
                            background: #080a0f; border-radius: 10px; overflow: auto;
                            min-height: 260px; max-height: 70vh; padding: 12px;
                        }
                        .preview-body img, .preview-body video { max-width: 100%; max-height: 65vh; }
                    """)
                }
            }
        }
        body {
            attributes["data-view"] = "grid"

            header {
                div("brand") {
                    div("brand-logo") { +"📁" }
                    div {
                        div("brand-title") { +telemetry.deviceName }
                        div {
                            style = "font-size: 0.72rem; color: var(--text-muted);"
                            +"Explorer Portal • FTP :$ftpPort • HTTP :$port"
                        }
                    }
                    div("telemetry-pills") {
                        span("pill pill-online") { +"● Online" }
                        span("pill") {
                            id = "pillBattery"
                            +"${if (telemetry.isCharging) "⚡" else "🔋"} ${telemetry.batteryPercent}%${if (telemetry.isCharging) " (Charging)" else ""}"
                        }
                        span("pill") {
                            id = "pillStorage"
                            +"💾 ${telemetry.freeFormatted} free / ${telemetry.totalFormatted} (${telemetry.usedPercent}% used)"
                        }
                    }
                }

                // Top Sub-Navigation Tabs (Files | Screen | Mini-Apps)
                div("nav-tabs") {
                    button(classes = "nav-tab active") {
                        id = "tabBtnFiles"
                        onClick = "switchPortalTab('files')"
                        +"🗂️ Files (${files.size})"
                    }
                    if (isScreenCastEnabled) {
                        button(classes = "nav-tab") {
                            id = "tabBtnScreen"
                            onClick = "switchPortalTab('screen')"
                            +if (hasScreencast) "📺 Live Screen (LIVE)" else "📺 Live Screen"
                        }
                    }
                    if (allowMiniApps) {
                        button(classes = "nav-tab") {
                            id = "tabBtnMiniApps"
                            onClick = "switchPortalTab('miniapps')"
                            +"✨ Mini-Apps"
                        }
                    }
                }

                div("toolbar") {
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
                    if (hasWebsiteEntry) {
                        a(href = "/", classes = "btn") { +"🌐 Hosted Site" }
                    }
                    if (allowUploads) {
                        button(classes = "btn btn-primary") {
                            onClick = "openUploadModal()"
                            +"⬆ Upload Files"
                        }
                    }
                }
            }

            div("workspace") {
                // --- SUBPAGE 1: FILES TAB ---
                div("tab-section active") {
                    id = "sectionFiles"

                    div("stats-strip") {
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Host Device" }
                                div("stat-val") { +telemetry.deviceName }
                            }
                            span { style = "font-size: 20px;"; +"📱" }
                        }
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Battery & Power" }
                                div("stat-val") { +"${telemetry.batteryPercent}% ${if (telemetry.isCharging) "⚡ Charging" else "On Battery"}" }
                            }
                            span { style = "font-size: 20px;"; +if (telemetry.isCharging) "⚡" else "🔋" }
                        }
                        div("stat-card") {
                            div {
                                style = "flex: 1; margin-right: 10px;"
                                div("stat-label") { +"Available Storage (${telemetry.usedPercent}% Used)" }
                                div("stat-val") { +"${telemetry.freeFormatted} free / ${telemetry.totalFormatted}" }
                                div {
                                    style = "margin-top: 6px; height: 5px; width: 100%; background: rgba(255,255,255,0.1); border-radius: 99px; overflow: hidden;"
                                    div {
                                        style = "height: 100%; width: ${telemetry.usedPercent}%; background: var(--primary);"
                                    }
                                }
                            }
                            span { style = "font-size: 20px;"; +"💾" }
                        }
                        div("stat-card") {
                            div {
                                div("stat-label") { +"Shared Scope" }
                                div("stat-val") { +"$folderCount folders • $fileCount files (${formatBytes(totalBytes)})" }
                            }
                            span { style = "font-size: 20px;"; +"📊" }
                        }
                    }

                    div("panel") {
                        div("panel-header") {
                            div("panel-title") {
                                +when (hostedMode) {
                                    FtpHostedMode.BATCH_FILES -> "Shared Files & Folders (${files.size})"
                                    FtpHostedMode.CUSTOM_FOLDER -> "Website Assets (${currentDir.name})"
                                    FtpHostedMode.FULL_STORAGE -> currentDir.name.ifBlank { "Device Storage" }
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
                                    a(href = "/?explorer=true", classes = "btn") { +"⌂ Root" }
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
                            div {
                                style = "min-width: 220px; max-width: 340px; flex: 1;"
                                input {
                                    classes = setOf("search-input")
                                    type = InputType.search
                                    placeholder = "Filter files & folders..."
                                    onInput = "filterPortalFiles(this.value)"
                                }
                            }
                        }

                        // Grid View
                        div("file-grid") {
                            if (files.isEmpty()) {
                                div {
                                    style = "grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--text-muted);"
                                    +"No files or folders shared in this view. You can still use Live Screen or Remote Mini-Apps from the top tabs."
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
                                        "jpg", "jpeg", "png", "gif", "webp", "svg", "mp4", "webm", "mp3", "wav", "ogg", "pdf",
                                        "csv", "tsv", "md", "txt", "json", "xml", "kt", "py", "js", "html", "css", "log"
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
                                                    a(classes = "card-title", href = "javascript:void(0)") {
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
                                                    a(classes = "btn", href = "/?explorer=true&path=$encodedPath") { +"Open →" }
                                                } else {
                                                    if (isPreviewableMedia) {
                                                        button(classes = "btn") {
                                                            onClick = "openPreviewModal('$safeJsName', '/download?file=$encodedPath&inline=true', '$extLower')"
                                                            +"👁 Preview"
                                                        }
                                                    }
                                                    a(classes = "btn btn-primary", href = "/download?file=$encodedPath") { +"⬇ Save" }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Table View
                        div("table-wrapper") {
                            table("file-table") {
                                thead {
                                    tr {
                                        th { +"Name" }
                                        th { +"Type" }
                                        th { +"Size" }
                                        th { +"Modified" }
                                        th { style = "text-align: right;"; +"Actions" }
                                    }
                                }
                                tbody {
                                    if (files.isEmpty()) {
                                        tr {
                                            td {
                                                colSpan = "5"
                                                style = "text-align: center; padding: 32px; color: var(--text-muted);"
                                                +"No shared files in this folder."
                                            }
                                        }
                                    } else {
                                        for (file in files) {
                                            val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                                            val isDir = file.isDirectory
                                            val (emoji, badge) = getFileEmojiAndBadge(file)
                                            tr(classes = "file-item-entry") {
                                                attributes["data-name"] = file.name.lowercase(Locale.US)
                                                td {
                                                    if (isDir) {
                                                        a(classes = "item-link", href = "/?explorer=true&path=$encodedPath") { +"$emoji ${file.name}" }
                                                    } else {
                                                        a(classes = "item-link", href = "/download?file=$encodedPath&inline=true", target = "_blank") { +"$emoji ${file.name}" }
                                                    }
                                                }
                                                td { span("badge-ext") { +badge } }
                                                td {
                                                    +if (isDir) "Folder" else formatBytes(file.length())
                                                }
                                                td { +dateFormat.format(Date(file.lastModified())) }
                                                td {
                                                    style = "text-align: right;"
                                                    if (isDir) {
                                                        a(classes = "btn", href = "/?explorer=true&path=$encodedPath") { +"Open" }
                                                    } else {
                                                        a(classes = "btn btn-primary", href = "/download?file=$encodedPath") { +"⬇ Download" }
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

                // --- SUBPAGE 2: LIVE SCREEN SHARING TAB ---
                if (isScreenCastEnabled) {
                    div("tab-section") {
                        id = "sectionScreen"
                        div("panel") {
                            div("panel-header") {
                                div("panel-title") {
                                    +"📺 Live Screen Stream (${telemetry.deviceName})"
                                    span("pill pill-online") {
                                        id = "castStatusBadge"
                                        +if (hasScreencast) "LIVE (${SettingsManager.screenCastFps.value} FPS • ${SettingsManager.screenCastQuality.value}%)" else "STANDBY"
                                    }
                                }
                                div("toolbar") {
                                    button(classes = "btn") {
                                        id = "btnPauseStream"
                                        onClick = "togglePauseScreenStream()"
                                        +"⏸ Pause"
                                    }
                                    button(classes = "btn") {
                                        onClick = "snapshotScreenStream()"
                                        +"📸 Snapshot"
                                    }
                                    button(classes = "btn") {
                                        onClick = "rotateScreenStream()"
                                        +"↻ Rotate 90°"
                                    }
                                    button(classes = "btn") {
                                        onClick = "fullscreenScreenStream()"
                                        +"⛶ Fullscreen"
                                    }
                                    button(classes = "btn btn-primary") {
                                        onClick = "reloadScreenStream()"
                                        +"⟳ Reconnect"
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

                // --- SUBPAGE 3: REMOTE BROWSER MINI-APPS TAB (5-Tool Suite) ---
                if (allowMiniApps) {
                    div("tab-section") {
                        id = "sectionMiniApps"
                        div("panel") {
                            style = "margin-bottom: 14px;"
                            div("panel-header") {
                                div("panel-title") { +"✨ My Suite Remote Browser Mini-Apps (5 Tools)" }
                                div("toolbar") {
                                    button(classes = "btn btn-primary") {
                                        onClick = "selectMiniAppPreset('markor')"
                                        +"1. Markor Markdown"
                                    }
                                    button(classes = "btn") {
                                        onClick = "selectMiniAppPreset('code')"
                                        +"2. Code Studio"
                                    }
                                    button(classes = "btn") {
                                        onClick = "selectMiniAppPreset('csv')"
                                        +"3. CSV Table Editor"
                                    }
                                }
                            }
                        }
                        div("miniapps-grid") {
                            // Tools 1, 2 & 3: Remote Markor Markdown Studio, Code Studio & CSV Table Editor
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") {
                                        id = "remoteStudioHeading"
                                        +"📝 1. Markor Markdown, 2. Code Studio & 3. CSV Table Editor"
                                    }
                                }
                                div {
                                    style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                    p {
                                        style = "color: var(--text-muted); font-size: 0.82rem;"
                                        +"Author Markdown (.md), Code (.kt, .py, .js, .json), or CSV spreadsheets (.csv) in your browser and save directly to the host device's My Suite workspace."
                                    }
                                    input {
                                        id = "remoteEditorFileName"
                                        classes = setOf("text-input")
                                        type = InputType.text
                                        value = "Remote_Note.md"
                                        placeholder = "File name (e.g. Note.md, script.py, data.csv)"
                                    }
                                    textArea(classes = "editor-textarea") {
                                        id = "remoteEditorContent"
                                        placeholder = "# Welcome to Remote Markor Studio\nWrite markdown, code, or CSV rows here..."
                                    }
                                    div {
                                        style = "display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap;"
                                        span {
                                            id = "remoteEditorStatus"
                                            style = "font-size: 0.8rem; color: var(--text-muted);"
                                            +"Saves to /Documents/ContinuumSuite/"
                                        }
                                        div("toolbar") {
                                            button(classes = "btn") {
                                                onClick = "downloadRemoteTextLocal()"
                                                +"⬇ Download to PC/Phone"
                                            }
                                            button(classes = "btn btn-primary") {
                                                onClick = "saveRemoteTextToHost()"
                                                +"💾 Save to Host Device"
                                            }
                                        }
                                    }
                                }
                            }

                            // Tool 4: Remote Student Exam Photo/Signature Exact-KB Resizer & Filter Tool
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") { +"🪪 4. Image KB Resizer & Signature Filter Tool" }
                                }
                                div {
                                    style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                    p {
                                        style = "color: var(--text-muted); font-size: 0.82rem;"
                                        +"Resize Passport Photos (20–50 KB, 200×230 px) and Signatures (10–20 KB, 140×60 px) with binary-search JPEG compression right in your browser."
                                    }
                                    div("toolbar") {
                                        button(classes = "btn") {
                                            onClick = "applyConverterPreset(200, 230, 35)"
                                            +"🪪 Exam Photo (200×230 • 35 KB)"
                                        }
                                        button(classes = "btn") {
                                            onClick = "applyConverterPreset(140, 60, 15)"
                                            +"✍️ Signature (140×60 • 15 KB)"
                                        }
                                    }
                                    input {
                                        id = "remoteConvInput"
                                        classes = setOf("text-input")
                                        type = InputType.file
                                        attributes["accept"] = "image/*"
                                    }
                                    div {
                                        style = "display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px;"
                                        div {
                                            label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Width (px)" }
                                            input {
                                                id = "convWidth"
                                                classes = setOf("text-input")
                                                type = InputType.number
                                                value = "200"
                                            }
                                        }
                                        div {
                                            label { style = "font-size: 0.74rem; color: var(--text-muted);"; +"Height (px)" }
                                            input {
                                                id = "convHeight"
                                                classes = setOf("text-input")
                                                type = InputType.number
                                                value = "230"
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
                                    }
                                    button(classes = "btn btn-primary") {
                                        onClick = "runBrowserPhotoResizer()"
                                        +"⚡ Compress & Download Exact-KB Image"
                                    }
                                    span {
                                        id = "remoteConvStatus"
                                        style = "font-size: 0.8rem; color: var(--accent);"
                                    }
                                }
                            }

                            // Tool 5: Remote Host File Converter (triggers /api/miniapp/convert on the Android Host)
                            div("panel") {
                                div("panel-header") {
                                    div("panel-title") { +"🔄 5. Host File Converter (VERT + Squoosh Engine)" }
                                }
                                div {
                                    style = "padding: 16px; display: flex; flex-direction: column; gap: 12px;"
                                    p {
                                        style = "color: var(--text-muted); font-size: 0.82rem;"
                                        +"Convert any file on the host Android device (Images, PDFs, Markdown, CSV, JSON, Audio/Video) and download the converted output."
                                    }
                                    input {
                                        id = "hostConvertSourcePath"
                                        classes = setOf("text-input")
                                        type = InputType.text
                                        placeholder = "Host file path (e.g. /storage/emulated/0/Download/sample.png)"
                                    }
                                    div {
                                        style = "display: grid; grid-template-columns: 1fr 1fr; gap: 8px;"
                                        input {
                                            id = "hostConvertTargetFormat"
                                            classes = setOf("text-input")
                                            type = InputType.text
                                            value = "pdf"
                                            placeholder = "Target format (pdf, jpg, png, webp, html, json)"
                                        }
                                        input {
                                            id = "hostConvertMaxKb"
                                            classes = setOf("text-input")
                                            type = InputType.number
                                            value = "0"
                                            placeholder = "Max KB (0 = auto)"
                                        }
                                    }
                                    button(classes = "btn btn-primary") {
                                        onClick = "runHostFileConversion()"
                                        +"🔄 Convert on Host & Download"
                                    }
                                    span {
                                        id = "hostConvertStatus"
                                        style = "font-size: 0.8rem; color: var(--accent);"
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
                        button(classes = "btn") {
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

                        function switchPortalTab(tab) {
                            const sections = {
                                'files': document.getElementById('sectionFiles'),
                                'screen': document.getElementById('sectionScreen'),
                                'miniapps': document.getElementById('sectionMiniApps')
                            };
                            const btns = {
                                'files': document.getElementById('tabBtnFiles'),
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
                        }

                        function setViewMode(mode) {
                            document.body.setAttribute('data-view', mode);
                            document.getElementById('btnViewGrid').classList.toggle('active', mode === 'grid');
                            document.getElementById('btnViewTable').classList.toggle('active', mode === 'table');
                        }

                        function reloadScreenStream() {
                            const img = document.getElementById('mjpegStreamImg');
                            streamPaused = false;
                            const btn = document.getElementById('btnPauseStream');
                            if (btn) btn.innerText = '⏸ Pause';
                            if (img) {
                                img.src = '/screencast.mjpeg?t=' + Date.now();
                            }
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
                                    if (ext === 'csv' || ext === 'tsv') {
                                        const sep = ext === 'tsv' ? '\t' : ',';
                                        const rows = text.split(/\r?\n/).filter(r => r.trim().length > 0).slice(0, 150);
                                        const tbl = document.createElement('table');
                                        tbl.className = 'file-table';
                                        rows.forEach((r, idx) => {
                                            const tr = document.createElement('tr');
                                            r.split(sep).forEach(cell => {
                                                const el = document.createElement(idx === 0 ? 'th' : 'td');
                                                el.innerText = cell.replace(/^"|"$/g, '');
                                                tr.appendChild(el);
                                            });
                                            tbl.appendChild(tr);
                                        });
                                        body.style.alignItems = 'flex-start';
                                        body.style.justifyContent = 'flex-start';
                                        body.appendChild(tbl);
                                    } else {
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
                                    }
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
                            xhr.onerror = function() {
                                notice.innerText = 'Network error during upload';
                                notice.style.color = '#ef4444';
                            };
                            xhr.send(formData);
                        }

                        function selectMiniAppPreset(mode) {
                            const fn = document.getElementById('remoteEditorFileName');
                            const area = document.getElementById('remoteEditorContent');
                            if (mode === 'markor') {
                                fn.value = 'Remote_Note.md';
                                area.value = '# Remote Markor Note\n\n- [ ] Task item\n- **Bold note**';
                            } else if (mode === 'code') {
                                fn.value = 'Remote_Script.kt';
                                area.value = 'fun main() {\n    println("Hello from Remote Code Studio!")\n}';
                            } else if (mode === 'csv') {
                                fn.value = 'Remote_Data.csv';
                                area.value = 'ID,Name,Department,Score\n1,Alice,Engineering,98\n2,Bob,Design,94';
                            }
                        }

                        async function saveRemoteTextToHost() {
                            const fileName = document.getElementById('remoteEditorFileName').value || 'Remote_Note.md';
                            const content = document.getElementById('remoteEditorContent').value || '';
                            const status = document.getElementById('remoteEditorStatus');
                            status.innerText = 'Saving to host...';
                            try {
                                const res = await fetch('/api/miniapp/save-text', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ fileName, content })
                                });
                                const data = await res.json();
                                status.innerText = data.message || 'Saved!';
                                status.style.color = data.success ? '#10b981' : '#ef4444';
                            } catch (e) {
                                status.innerText = 'Save failed: ' + e.message;
                                status.style.color = '#ef4444';
                            }
                        }

                        function downloadRemoteTextLocal() {
                            const fileName = document.getElementById('remoteEditorFileName').value || 'Remote_Note.md';
                            const content = document.getElementById('remoteEditorContent').value || '';
                            const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
                            const a = document.createElement('a');
                            a.href = URL.createObjectURL(blob);
                            a.download = fileName;
                            a.click();
                        }

                        function applyConverterPreset(w, h, kb) {
                            document.getElementById('convWidth').value = w;
                            document.getElementById('convHeight').value = h;
                            document.getElementById('convTargetKb').value = kb;
                        }

                        async function runBrowserPhotoResizer() {
                            const input = document.getElementById('remoteConvInput');
                            const status = document.getElementById('remoteConvStatus');
                            if (!input.files || !input.files[0]) {
                                alert('Please choose an image file first.');
                                return;
                            }
                            const targetW = parseInt(document.getElementById('convWidth').value || '200', 10);
                            const targetH = parseInt(document.getElementById('convHeight').value || '230', 10);
                            const maxKb = parseInt(document.getElementById('convTargetKb').value || '35', 10);
                            const file = input.files[0];

                            const img = new Image();
                            img.onload = async function() {
                                const canvas = document.createElement('canvas');
                                canvas.width = targetW;
                                canvas.height = targetH;
                                const ctx = canvas.getContext('2d');
                                ctx.fillStyle = '#FFFFFF';
                                ctx.fillRect(0, 0, targetW, targetH);
                                ctx.drawImage(img, 0, 0, targetW, targetH);

                                let low = 0.1, high = 0.95, bestBlob = null;
                                for (let i = 0; i < 7; i++) {
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
                                    bestBlob = await new Promise(r => canvas.toBlob(r, 'image/jpeg', 0.1));
                                }
                                const a = document.createElement('a');
                                a.href = URL.createObjectURL(bestBlob);
                                a.download = 'Resized_' + Math.round(bestBlob.size / 1024) + 'KB_' + file.name.replace(/\.[^/.]+$/, "") + '.jpg';
                                a.click();
                                status.innerText = 'Done! Output size: ' + (bestBlob.size / 1024).toFixed(1) + ' KB (' + targetW + '×' + targetH + ' px)';
                            };
                            img.src = URL.createObjectURL(file);
                        }

                        async function runHostFileConversion() {
                            const sourcePath = document.getElementById('hostConvertSourcePath').value || '';
                            const targetFormat = document.getElementById('hostConvertTargetFormat').value || 'pdf';
                            const maxKb = parseInt(document.getElementById('hostConvertMaxKb').value || '0', 10);
                            const status = document.getElementById('hostConvertStatus');
                            if (!sourcePath.trim()) {
                                alert('Enter a host file path to convert.');
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

                        if ('${initialTab}' === 'screen') switchPortalTab('screen');
                        else if ('${initialTab}' === 'miniapps') switchPortalTab('miniapps');
                    """)
                }
            }
        }
    }
}
