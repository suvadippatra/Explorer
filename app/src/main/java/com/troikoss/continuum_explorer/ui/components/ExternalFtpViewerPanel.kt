package com.troikoss.continuum_explorer.ui.components

import android.annotation.SuppressLint
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.troikoss.continuum_explorer.managers.ExternalFtpManager
import com.troikoss.continuum_explorer.managers.SavedFtpServer
import com.troikoss.continuum_explorer.managers.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class RemoteFtpItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val downloadUrl: String
)

data class RemoteHostTelemetry(
    val deviceName: String = "",
    val batteryPercent: Int = -1,
    val freeStorage: String = "",
    val totalStorage: String = "",
    val screencastActive: Boolean = false,
    val allowMiniApps: Boolean = false
)

/**
 * Redesigned "Add FTP Server" Client Panel:
 * - Connects to both Explorer HTTP/FTP portals and standard third-party FTP/HTTP servers.
 * - Native FTP/HTTP File Browser first (zero hardcoded mock files; real directory navigation & file downloads).
 * - Displays live remote host telemetry (Device Name, Battery %, Storage Free/Total) when connected to an Explorer host.
 * - Includes a 1-tap toggle to switch between "Native File Browser" and "Built-in Web Browser (WebView)"
 *   for viewing live Screen Sharing, using Remote Mini-Apps, or browsing custom third-party web portals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalFtpViewerPanel(
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ExternalFtpManager.init(context)
    }

    var serverName by remember { mutableStateOf("") }
    var remoteHost by remember { mutableStateOf("") }
    var remotePort by remember { mutableStateOf("2121") }
    var webPort by remember { mutableStateOf("8080") }
    var isAnonymous by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isConnecting by remember { mutableStateOf(false) }
    var activeConnectedServer by remember { mutableStateOf<SavedFtpServer?>(null) }
    var currentRemotePath by remember { mutableStateOf("") }
    var parentRemotePath by remember { mutableStateOf("") }
    var remoteFilesList by remember { mutableStateOf<List<RemoteFtpItem>>(emptyList()) }
    var hostTelemetry by remember { mutableStateOf<RemoteHostTelemetry?>(null) }
    var useBuiltInWebBrowser by remember { mutableStateOf(false) }
    var webBrowserInitialPath by remember { mutableStateOf("/") }
    var connectionProtocolLabel by remember { mutableStateOf("HTTP Portal API") }

    fun loadDirectoryFromServer(server: SavedFtpServer, targetPath: String = "") {
        isConnecting = true
        scope.launch(Dispatchers.IO) {
            val cleanHost = server.host.trim().removePrefix("http://").removePrefix("ftp://").substringBefore("/")
            val cleanWebPort = server.webPort.trim().ifEmpty { "8080" }
            val cleanFtpPort = server.ftpPort.trim().toIntOrNull() ?: 2121
            val baseUrl = "http://$cleanHost:$cleanWebPort"

            // 1. Try Explorer Native JSON API (/api/files & /api/device-status)
            try {
                val encodedPath = URLEncoder.encode(targetPath, "UTF-8")
                val apiUrl = URL("$baseUrl/api/files?path=$encodedPath")
                val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 4500
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(jsonStr)
                    val arr = root.optJSONArray("items")
                    val parsedItems = mutableListOf<RemoteFtpItem>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val itemObj = arr.getJSONObject(i)
                            val dlPath = itemObj.optString("downloadUrl", "")
                            parsedItems.add(
                                RemoteFtpItem(
                                    name = itemObj.optString("name", "Item"),
                                    path = itemObj.optString("path", ""),
                                    isDirectory = itemObj.optBoolean("isDirectory", false),
                                    sizeBytes = itemObj.optLong("sizeBytes", 0L),
                                    downloadUrl = if (dlPath.startsWith("http")) dlPath else "$baseUrl$dlPath"
                                )
                            )
                        }
                    }

                    // Also fetch device telemetry if available
                    val telemetry = try {
                        val statusConn = (URL("$baseUrl/api/device-status").openConnection() as HttpURLConnection).apply {
                            connectTimeout = 2000
                            readTimeout = 2000
                        }
                        if (statusConn.responseCode == 200) {
                            val stObj = JSONObject(statusConn.inputStream.bufferedReader().use { it.readText() })
                            RemoteHostTelemetry(
                                deviceName = stObj.optString("deviceName", server.name),
                                batteryPercent = stObj.optInt("batteryPercent", -1),
                                freeStorage = stObj.optString("freeStorageFormatted", ""),
                                totalStorage = stObj.optString("totalStorageFormatted", ""),
                                screencastActive = stObj.optBoolean("screencastActive", false),
                                allowMiniApps = stObj.optBoolean("allowMiniApps", false)
                            )
                        } else null
                    } catch (_: Exception) {
                        null
                    }

                    withContext(Dispatchers.Main) {
                        isConnecting = false
                        ExternalFtpManager.setConnectionStatus(server.id, true)
                        activeConnectedServer = server.copy(isConnected = true)
                        currentRemotePath = root.optString("currentPath", targetPath)
                        parentRemotePath = root.optString("parentPath", "")
                        remoteFilesList = parsedItems
                        hostTelemetry = telemetry
                        connectionProtocolLabel = "Native HTTP + FTP"
                    }
                    return@launch
                }
            } catch (_: Exception) {
                // Fallback to Apache Commons Net FTP Client below
            }

            // 2. Try Real Apache Commons Net FTP Socket Connection (Works with any third-party FTP server)
            val ftpClient = FTPClient()
            try {
                ftpClient.connectTimeout = 4000
                ftpClient.connect(cleanHost, cleanFtpPort)
                val user = if (server.isAnonymous || server.username.isBlank()) "anonymous" else server.username
                val pass = if (server.isAnonymous) "anonymous@" else password
                val loggedIn = ftpClient.login(user, pass)
                if (loggedIn) {
                    ftpClient.enterLocalPassiveMode()
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                    val dirToList = targetPath.ifBlank { "/" }
                    val ftpFiles = ftpClient.listFiles(dirToList) ?: emptyArray()
                    val parsedFtpItems = ftpFiles
                        .filter { it.name != "." && it.name != ".." }
                        .map { f ->
                            val fullPath = if (dirToList.endsWith("/")) "$dirToList${f.name}" else "$dirToList/${f.name}"
                            RemoteFtpItem(
                                name = f.name,
                                path = fullPath,
                                isDirectory = f.isDirectory,
                                sizeBytes = f.size.coerceAtLeast(0L),
                                downloadUrl = "ftp://$cleanHost:$cleanFtpPort$fullPath"
                            )
                        }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                    val calcParent = if (dirToList == "/" || dirToList.isBlank()) {
                        ""
                    } else {
                        dirToList.substringBeforeLast('/', "/").ifBlank { "/" }
                    }

                    withContext(Dispatchers.Main) {
                        isConnecting = false
                        ExternalFtpManager.setConnectionStatus(server.id, true)
                        activeConnectedServer = server.copy(isConnected = true)
                        currentRemotePath = dirToList
                        parentRemotePath = calcParent
                        remoteFilesList = parsedFtpItems
                        hostTelemetry = null
                        connectionProtocolLabel = "Native FTP Socket (:$cleanFtpPort)"
                    }
                    return@launch
                }
            } catch (_: Exception) {
                // Fallback to Built-in Web Browser if third-party HTTP portal is reachable
            } finally {
                try {
                    if (ftpClient.isConnected) {
                        ftpClient.logout()
                        ftpClient.disconnect()
                    }
                } catch (_: Exception) {}
            }

            // 3. Check if custom HTTP web server is reachable and open in Built-in Web Browser
            try {
                val httpConn = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                httpConn.connect()
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    ExternalFtpManager.setConnectionStatus(server.id, true)
                    activeConnectedServer = server.copy(isConnected = true)
                    useBuiltInWebBrowser = true
                    connectionProtocolLabel = "Built-in Web Portal"
                    Toast.makeText(context, "Opened third-party web portal in Built-in Browser", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    Toast.makeText(
                        context,
                        "Could not reach ${server.host} on FTP (:${server.ftpPort}) or HTTP (:${server.webPort})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun downloadRemoteItem(server: SavedFtpServer, item: RemoteFtpItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .apply { if (!exists()) mkdirs() }
                val destFile = File(downloadsDir, item.name)

                if (item.downloadUrl.startsWith("http")) {
                    URL(item.downloadUrl).openStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val cleanHost = server.host.trim()
                    val cleanFtpPort = server.ftpPort.trim().toIntOrNull() ?: 2121
                    val ftp = FTPClient()
                    ftp.connect(cleanHost, cleanFtpPort)
                    val user = if (server.isAnonymous || server.username.isBlank()) "anonymous" else server.username
                    val pass = if (server.isAnonymous) "anonymous@" else password
                    ftp.login(user, pass)
                    ftp.enterLocalPassiveMode()
                    ftp.setFileType(FTP.BINARY_FILE_TYPE)
                    FileOutputStream(destFile).use { output ->
                        ftp.retrieveFile(item.path, output)
                    }
                    ftp.logout()
                    ftp.disconnect()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads: ${destFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var showUploadPicker by remember { mutableStateOf(false) }
    var previewingRemoteItem by remember { mutableStateOf<RemoteFtpItem?>(null) }
    var previewRemoteText by remember { mutableStateOf<String?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    fun uploadLocalFileToServer(server: SavedFtpServer, localFile: File) {
        scope.launch(Dispatchers.IO) {
            try {
                val cleanHost = server.host.trim().removePrefix("http://").removePrefix("ftp://").substringBefore("/")
                val cleanFtpPort = server.ftpPort.trim().toIntOrNull() ?: 2121
                val ftp = FTPClient()
                try {
                    ftp.connectTimeout = 4000
                    ftp.connect(cleanHost, cleanFtpPort)
                    val user = if (server.isAnonymous || server.username.isBlank()) "anonymous" else server.username
                    val pass = if (server.isAnonymous) "anonymous@" else password
                    if (ftp.login(user, pass)) {
                        ftp.enterLocalPassiveMode()
                        ftp.setFileType(FTP.BINARY_FILE_TYPE)
                        val remoteTarget = if (currentRemotePath.isBlank() || currentRemotePath == "/") {
                            "/${localFile.name}"
                        } else if (currentRemotePath.endsWith("/")) {
                            "$currentRemotePath${localFile.name}"
                        } else {
                            "$currentRemotePath/${localFile.name}"
                        }
                        localFile.inputStream().use { input ->
                            ftp.storeFile(remoteTarget, input)
                        }
                        ftp.logout()
                        ftp.disconnect()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Uploaded ${localFile.name} via FTP", Toast.LENGTH_SHORT).show()
                            loadDirectoryFromServer(server, currentRemotePath)
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                    try { if (ftp.isConnected) ftp.disconnect() } catch (_: Exception) {}
                }

                // Fallback: HTTP Multipart POST to /upload
                val cleanWebPort = server.webPort.trim().ifEmpty { "8080" }
                val boundary = "----ExplorerClientBoundary${System.currentTimeMillis()}"
                val url = URL("http://$cleanHost:$cleanWebPort/upload")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 10000
                    doOutput = true
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }
                conn.outputStream.use { out ->
                    val header = "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"file_0\"; filename=\"${localFile.name}\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n"
                    out.write(header.toByteArray())
                    localFile.inputStream().use { it.copyTo(out) }
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                    out.flush()
                }
                val code = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (code in 200..299) {
                        Toast.makeText(context, "Uploaded ${localFile.name} to Host", Toast.LENGTH_SHORT).show()
                        loadDirectoryFromServer(server, currentRemotePath)
                    } else {
                        Toast.makeText(context, "Upload rejected by host (HTTP $code)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openInlineRemotePreview(server: SavedFtpServer, item: RemoteFtpItem) {
        previewingRemoteItem = item
        previewRemoteText = null
        val ext = item.name.substringAfterLast('.', "").lowercase()
        if (ext in listOf("txt", "md", "csv", "tsv", "json", "xml", "kt", "py", "js", "html", "css", "log")) {
            isPreviewLoading = true
            scope.launch(Dispatchers.IO) {
                val txt = try {
                    if (item.downloadUrl.startsWith("http")) {
                        URL(item.downloadUrl).openStream().bufferedReader().use { it.readText().take(80_000) }
                    } else {
                        val cleanHost = server.host.trim()
                        val cleanFtpPort = server.ftpPort.trim().toIntOrNull() ?: 2121
                        val ftp = FTPClient()
                        ftp.connect(cleanHost, cleanFtpPort)
                        val user = if (server.isAnonymous || server.username.isBlank()) "anonymous" else server.username
                        val pass = if (server.isAnonymous) "anonymous@" else password
                        ftp.login(user, pass)
                        ftp.enterLocalPassiveMode()
                        val baos = java.io.ByteArrayOutputStream()
                        ftp.retrieveFile(item.path, baos)
                        ftp.logout()
                        ftp.disconnect()
                        baos.toString("UTF-8").take(80_000)
                    }
                } catch (e: Exception) {
                    "Unable to preview remote file: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    previewRemoteText = txt
                    isPreviewLoading = false
                }
            }
        } else {
            isPreviewLoading = false
        }
    }

    if (showUploadPicker && activeConnectedServer != null) {
        InAppFilePickerDialog(
            title = "Select File to Upload to ${activeConnectedServer!!.name}",
            selectFolders = false,
            onDismiss = { showUploadPicker = false },
            onSelected = { pickedFile ->
                showUploadPicker = false
                uploadLocalFileToServer(activeConnectedServer!!, pickedFile)
            }
        )
    }

    previewingRemoteItem?.let { remoteItem ->
        val ext = remoteItem.name.substringAfterLast('.', "").lowercase()
        AlertDialog(
            onDismissRequest = { previewingRemoteItem = null },
            title = { Text(remoteItem.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isPreviewLoading -> CircularProgressIndicator()
                        ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") && remoteItem.downloadUrl.startsWith("http") -> {
                            coil.compose.AsyncImage(
                                model = remoteItem.downloadUrl,
                                contentDescription = remoteItem.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ext in listOf("csv", "tsv") && previewRemoteText != null -> {
                            com.troikoss.continuum_explorer.ui.preview.CsvInteractiveTablePreview(
                                rawText = previewRemoteText!!,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        previewRemoteText != null -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = previewRemoteText!!,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "Remote file (${formatRemoteSize(remoteItem.sizeBytes)}). Tap Download to open locally.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val srv = activeConnectedServer
                        previewingRemoteItem = null
                        if (srv != null) downloadRemoteItem(srv, remoteItem)
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewingRemoteItem = null }) { Text("Close") }
            }
        )
    }

    fun handleAddAndConnect() {
        if (remoteHost.isBlank()) {
            Toast.makeText(context, "Please enter remote host IP address", Toast.LENGTH_SHORT).show()
            return
        }
        val finalName = serverName.ifBlank { "FTP Server (${remoteHost.trim()})" }
        val newServer = SavedFtpServer(
            id = System.currentTimeMillis().toString(),
            name = finalName,
            host = remoteHost.trim(),
            ftpPort = remotePort.trim().ifEmpty { "2121" },
            webPort = webPort.trim().ifEmpty { "8080" },
            isAnonymous = isAnonymous,
            username = username.trim(),
            isConnected = false
        )
        ExternalFtpManager.addOrUpdateServer(context, newServer)
        useBuiltInWebBrowser = false
        loadDirectoryFromServer(newServer, "")
    }

    val inputShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 50.dp Flush Top Heading Bar with Triple-Line Menu Button on Left
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            val showMenuIcon = appState == null ||
                appState.getScreenSize() == com.troikoss.continuum_explorer.model.ScreenSize.SMALL ||
                SettingsManager.isWideSidebarCollapsed.value

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showMenuIcon) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Navigation Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeConnectedServer?.name ?: "Add FTP / HTTP Server",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activeConnectedServer != null) {
                    TextButton(
                        onClick = {
                            ExternalFtpManager.setConnectionStatus(activeConnectedServer!!.id, false)
                            activeConnectedServer = null
                            hostTelemetry = null
                            remoteFilesList = emptyList()
                            useBuiltInWebBrowser = false
                        }
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        if (activeConnectedServer == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Connect to Remote FTP or HTTP Portal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Browses files natively first (Explorer HTTP API or standard FTP socket), with a 1-tap Built-in Web Browser toggle for live screen streams and custom portals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = serverName,
                                onValueChange = { serverName = it },
                                label = { Text("Server Label (optional)", style = MaterialTheme.typography.bodySmall) },
                                placeholder = { Text("e.g. Tablet Host, Office NAS") },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = inputShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = remoteHost,
                                onValueChange = { remoteHost = it },
                                label = { Text("Server IP / Hostname", style = MaterialTheme.typography.bodySmall) },
                                placeholder = { Text("192.168.1.100") },
                                leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = inputShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = remotePort,
                                    onValueChange = { remotePort = it },
                                    label = { Text("FTP Port", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    shape = inputShape,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = webPort,
                                    onValueChange = { webPort = it },
                                    label = { Text("HTTP Web Port", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    shape = inputShape,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Anonymous Login", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                            }

                            if (!isAnonymous) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text("Username", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        shape = inputShape,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Password", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        shape = inputShape,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Button(
                                onClick = { handleAddAndConnect() },
                                enabled = !isConnecting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connecting...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add & Connect Server", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Saved & Recent Servers Section
                if (ExternalFtpManager.recentServers.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved & Recent Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    ExternalFtpManager.recentServers.toList().forEach {
                                        ExternalFtpManager.removeServer(context, it.id)
                                    }
                                }
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    items(ExternalFtpManager.recentServers, key = { it.id }) { server ->
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(server.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (server.isConnected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                        }
                                    }
                                    Text(
                                        "FTP ${server.host}:${server.ftpPort} • HTTP :${server.webPort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            useBuiltInWebBrowser = false
                                            loadDirectoryFromServer(server, "")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Connect", style = MaterialTheme.typography.labelSmall)
                                    }

                                    IconButton(
                                        onClick = { ExternalFtpManager.removeServer(context, server.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- CONNECTED SERVER VIEW (Native File Browser + 1-Tap Built-in Web Browser Toggle) ---
            val server = activeConnectedServer!!
            val cleanHost = server.host.trim().removePrefix("http://").removePrefix("ftp://").substringBefore("/")
            val httpPortalUrl = "http://$cleanHost:${server.webPort.trim().ifEmpty { "8080" }}$webBrowserInitialPath"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1-Tap Switcher: Native FTP/HTTP File Browser vs Built-in Web Browser
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useBuiltInWebBrowser,
                        onClick = { useBuiltInWebBrowser = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Native File Browser", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = useBuiltInWebBrowser,
                        onClick = {
                            webBrowserInitialPath = "/"
                            useBuiltInWebBrowser = true
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Open in Built-in Web Browser", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Remote Host Telemetry Banner (when connected to an Explorer Host)
                hostTelemetry?.let { telemetry ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = buildString {
                                        append(telemetry.deviceName.ifBlank { server.name })
                                        if (telemetry.batteryPercent >= 0) append(" • 🔋 ${telemetry.batteryPercent}%")
                                        if (telemetry.freeStorage.isNotBlank()) append(" • 💾 ${telemetry.freeStorage} free")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (telemetry.screencastActive) {
                                    AssistChip(
                                        onClick = {
                                            webBrowserInitialPath = "/?tab=screen"
                                            useBuiltInWebBrowser = true
                                        },
                                        label = { Text("Live Screen", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.CastConnected, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                                if (telemetry.allowMiniApps) {
                                    AssistChip(
                                        onClick = {
                                            webBrowserInitialPath = "/?tab=miniapps"
                                            useBuiltInWebBrowser = true
                                        },
                                        label = { Text("Remote Mini-Apps", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (useBuiltInWebBrowser) {
                    // Built-in WebView Portal Renderer (renders live MJPEG screen stream, remote Mini-Apps, or custom web portals)
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        BuiltInPortalWebView(url = httpPortalUrl)
                    }
                } else {
                    // Native FTP / HTTP Directory Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (parentRemotePath.isNotEmpty() || currentRemotePath.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { loadDirectoryFromServer(server, parentRemotePath) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Up", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = currentRemotePath.ifBlank { "/" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { showUploadPicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Upload", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                text = connectionProtocolLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { loadDirectoryFromServer(server, currentRemotePath) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (isConnecting) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (remoteFilesList.isEmpty()) {
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No shared files in this folder", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "If the host is sharing Live Screen or Remote Mini-Apps with 0 files, switch to 'Open in Built-in Web Browser' above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(remoteFilesList, key = { it.path + it.name }) { item ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.isDirectory) {
                                                loadDirectoryFromServer(server, item.path)
                                            } else {
                                                openInlineRemotePreview(server, item)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    item.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (item.isDirectory) "Folder" else formatRemoteSize(item.sizeBytes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (item.isDirectory) {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = "Open Folder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                IconButton(
                                                    onClick = { openInlineRemotePreview(server, item) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Visibility,
                                                        contentDescription = "Inline Preview",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                FilledTonalIconButton(
                                                    onClick = { downloadRemoteItem(server, item) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = "Download to Device",
                                                        modifier = Modifier.size(18.dp)
                                                    )
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BuiltInPortalWebView(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    )
}

private fun formatRemoteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
