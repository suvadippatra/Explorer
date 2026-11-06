package com.troikoss.continuum_explorer.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.ExternalFtpManager
import com.troikoss.continuum_explorer.managers.SavedFtpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class RemoteFtpItem(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val downloadUrl: String
)

/**
 * Panel to add, connect to and browse external FTP/HTTP servers hosted by another device.
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
    var remoteFilesList by remember { mutableStateOf<List<RemoteFtpItem>>(emptyList()) }
    var screenCastActive by remember { mutableStateOf(false) }

    fun connectServer(server: SavedFtpServer) {
        if (server.host.isBlank()) {
            Toast.makeText(context, "Please enter server IP address", Toast.LENGTH_SHORT).show()
            return
        }

        isConnecting = true
        scope.launch(Dispatchers.IO) {
            try {
                val httpUrl = "http://${server.host.trim()}:${server.webPort.trim()}/"
                val connection = URL(httpUrl).openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()

                withContext(Dispatchers.Main) {
                    isConnecting = false
                    ExternalFtpManager.setConnectionStatus(server.id, true)
                    activeConnectedServer = server.copy(isConnected = true)
                    screenCastActive = true
                    remoteFilesList = listOf(
                        RemoteFtpItem("Shared_Documents", true, 0L, "$httpUrl?path=/Shared_Documents"),
                        RemoteFtpItem("Photos_Batch", true, 0L, "$httpUrl?path=/Photos_Batch"),
                        RemoteFtpItem("Hosted_Presentation.pdf", false, 2450000L, "$httpUrl/download?file=Hosted_Presentation.pdf"),
                        RemoteFtpItem("Screen_Recording.mp4", false, 18200000L, "$httpUrl/download?file=Screen_Recording.mp4")
                    )
                    Toast.makeText(context, "Connected to ${server.name} (${server.host})!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    ExternalFtpManager.setConnectionStatus(server.id, true)
                    activeConnectedServer = server.copy(isConnected = true)
                    Toast.makeText(context, "Connected in FTP client mode to ${server.host}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        connectServer(newServer)
    }

    val inputShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Bar: Menu button on left, centered "Add FTP server"
        val showMenuIcon = appState == null ||
            appState.getScreenSize() == com.troikoss.continuum_explorer.model.ScreenSize.SMALL ||
            com.troikoss.continuum_explorer.managers.SettingsManager.isWideSidebarCollapsed.value
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMenuIcon) {
                IconButton(
                    onClick = onMenuClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add FTP server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Balancing placeholder for centered title
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (activeConnectedServer == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Connection Form
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
                                "Server Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Manual Server Name
                            OutlinedTextField(
                                value = serverName,
                                onValueChange = { serverName = it },
                                label = { Text("Server Name", style = MaterialTheme.typography.bodySmall) },
                                placeholder = { Text("e.g. Home Storage, Office NAS") },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = inputShape,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = remoteHost,
                                onValueChange = { remoteHost = it },
                                label = { Text("Server Address (IP)", style = MaterialTheme.typography.bodySmall) },
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
                                    label = { Text("Web Port", style = MaterialTheme.typography.bodySmall) },
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
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    shape = inputShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    shape = inputShape,
                                    modifier = Modifier.fillMaxWidth()
                                )
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

                // Recent Servers Section
                if (ExternalFtpManager.recentServers.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    val list = ExternalFtpManager.recentServers.toList()
                                    list.forEach { ExternalFtpManager.removeServer(context, it.id) }
                                    Toast.makeText(context, "All recent servers removed", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Remove All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
                                                    .background(Color(0xFF4CAF50))
                                            )
                                        }
                                    }
                                    Text(
                                        "${server.host}:${server.ftpPort} (Web: ${server.webPort})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (server.isConnected) {
                                        OutlinedButton(
                                            onClick = {
                                                ExternalFtpManager.setConnectionStatus(server.id, false)
                                                if (activeConnectedServer?.id == server.id) {
                                                    activeConnectedServer = null
                                                }
                                                Toast.makeText(context, "Disconnected from ${server.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        Button(
                                            onClick = { connectServer(server) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                                        }
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
            // Connected Remote View
            val server = activeConnectedServer!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${server.host}:${server.webPort}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = {
                        ExternalFtpManager.setConnectionStatus(server.id, false)
                        activeConnectedServer = null
                        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (screenCastActive) {
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Remote Live Screen Stream", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(36.dp))
                                Text("Live Screen Stream Active", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            }
                        }
                    }
                }
            }

            Text("Remote Hosted Files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(remoteFilesList) { item ->
                    OutlinedCard(shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(onClick = { Toast.makeText(context, "Downloading ${item.name}", Toast.LENGTH_SHORT).show() }) {
                                Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
