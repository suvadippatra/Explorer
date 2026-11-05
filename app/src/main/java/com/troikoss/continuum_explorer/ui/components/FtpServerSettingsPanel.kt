package com.troikoss.continuum_explorer.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.server.ContinuumHubService
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.QrCodeView
import kotlinx.coroutines.delay
import java.io.File

/**
 * Material 3 Card-Based FTP/HTTP & Screen Cast Server Panel.
 * Features:
 * - Top heading with log button ONLY displayed when server is running
 * - Material 3 cards grouping similar parameters
 * - Responsive 1-column (mobile) vs 2-column (tablet/desktop) layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpServerSettingsPanel(
    appState: FileExplorerState,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Initialize Bucket Manager
    LaunchedEffect(Unit) {
        FtpBucketManager.initialize(context)
    }

    // Preferences state
    val savedPort by SettingsManager.ftpPort
    val savedAnonymous by SettingsManager.isFtpAnonymousAllowed
    val savedUsername by SettingsManager.ftpUsername
    val savedPassword by SettingsManager.ftpPassword
    val savedWritePerm by SettingsManager.ftpWritePermission
    val savedHostedMode by SettingsManager.ftpHostedMode
    val savedHostedPath by SettingsManager.ftpHostedPath
    val savedUploadPass by SettingsManager.ftpUploadPassword
    val savedAllowUpload by SettingsManager.ftpAllowUpload

    val savedResolution by SettingsManager.screenCastResolution
    val savedFps by SettingsManager.screenCastFps
    val savedRotation by SettingsManager.screenCastRotation
    val savedAudio by SettingsManager.screenCastAudio

    // Local form state
    var portInput by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var isAnonymousAccess by remember(savedAnonymous) { mutableStateOf(savedAnonymous) }
    var usernameInput by remember(savedUsername) { mutableStateOf(savedUsername) }
    var passwordInput by remember(savedPassword) { mutableStateOf(savedPassword) }
    var uploadPasswordInput by remember(savedUploadPass) { mutableStateOf(savedUploadPass) }
    var allowWebUploads by remember(savedAllowUpload) { mutableStateOf(savedAllowUpload) }
    var showPassword by remember { mutableStateOf(false) }
    var showUploadPassword by remember { mutableStateOf(false) }
    var allowWritePermissions by remember(savedWritePerm) { mutableStateOf(savedWritePerm) }
    var hostedMode by remember(savedHostedMode) { mutableStateOf(savedHostedMode) }
    var customFolderPath by remember(savedHostedPath) { mutableStateOf(savedHostedPath) }

    // Screen cast form state
    var screenResolution by remember(savedResolution) { mutableStateOf(savedResolution) }
    var screenFps by remember(savedFps) { mutableIntStateOf(savedFps) }
    var screenRotation by remember(savedRotation) { mutableIntStateOf(savedRotation) }
    var screenAudio by remember(savedAudio) { mutableStateOf(savedAudio) }

    // Live state
    var isServerRunning by remember { mutableStateOf(ContinuumHubService.isRunning) }
    var isScreenCasting by remember { mutableStateOf(ContinuumHubService.isScreenCasting) }
    var serverIp by remember { mutableStateOf(ContinuumHubService.serverIpAddress) }
    var activePort by remember { mutableStateOf(ContinuumHubService.activeFtpPort) }
    var uptimeFormatted by remember { mutableStateOf("00:00:00") }

    // Modals & Pickers
    var showQrDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showFullPageFilePicker by remember { mutableStateOf(false) }
    var explanationDialogText by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Screen Cast Launcher
    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
            ContinuumHubService.startScreenCast(context, result.resultCode, result.data!!)
            Toast.makeText(context, "Live Screen Sharing started!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen Sharing permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    // Live update loop
    LaunchedEffect(Unit) {
        while (true) {
            isServerRunning = ContinuumHubService.isRunning
            isScreenCasting = ContinuumHubService.isScreenCasting
            serverIp = ContinuumHubService.serverIpAddress
            activePort = ContinuumHubService.activeFtpPort

            if (isServerRunning && ContinuumHubService.startTimeMillis > 0L) {
                val elapsedSec = ((System.currentTimeMillis() - ContinuumHubService.startTimeMillis) / 1000L).coerceAtLeast(0L)
                val hours = elapsedSec / 3600
                val mins = (elapsedSec % 3600) / 60
                val secs = elapsedSec % 60
                uptimeFormatted = String.format("%02d:%02d:%02d", hours, mins, secs)
            } else {
                uptimeFormatted = "00:00:00"
            }
            delay(1000L)
        }
    }

    val ftpUrl = remember(isServerRunning, serverIp, activePort, isAnonymousAccess, usernameInput, passwordInput) {
        if (!isAnonymousAccess && usernameInput.isNotBlank()) {
            "ftp://$usernameInput:$passwordInput@$serverIp:$activePort"
        } else {
            "ftp://$serverIp:$activePort"
        }
    }
    val httpWebUrl = remember(serverIp) { "http://$serverIp:8080" }

    // Full page file picker overlay
    if (showFullPageFilePicker) {
        val isHtmlMode = (hostedMode == FtpHostedMode.CUSTOM_FOLDER)
        FullPageFilePicker(
            title = if (isHtmlMode) "Select HTML File or Folder" else "Select Files",
            isHtmlMode = isHtmlMode,
            onDismiss = { showFullPageFilePicker = false },
            onHtmlSelected = { selected ->
                customFolderPath = selected.absolutePath
                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, customFolderPath)
                showFullPageFilePicker = false
            }
        )
        return
    }

    // Explanation Dialog
    if (explanationDialogText != null) {
        AlertDialog(
            onDismissRequest = { explanationDialogText = null },
            title = { Text(explanationDialogText!!.first, fontWeight = FontWeight.Bold) },
            text = { Text(explanationDialogText!!.second, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { explanationDialogText = null }) { Text("Got it") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    val inputShape = RoundedCornerShape(12.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- TOP HEADING BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "FTP/HTTP Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Log Button: ONLY shown when server is RUNNING
                    if (isServerRunning) {
                        IconButton(onClick = { showLogsDialog = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = "Server Logs", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Cards Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isWideScreen) {
                    // TWO COLUMN CARD GRID ON TABLET / DESKTOP WIDE SCREENS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ServerControlCard(
                                isServerRunning = isServerRunning,
                                serverIp = serverIp,
                                activePort = activePort,
                                uptimeFormatted = uptimeFormatted,
                                ftpUrl = ftpUrl,
                                httpWebUrl = httpWebUrl,
                                hostedMode = hostedMode,
                                customFolderPath = customFolderPath,
                                context = context,
                                clipboardManager = clipboardManager,
                                onStartToggle = { start ->
                                    if (start) {
                                        val p = portInput.toIntOrNull() ?: 2121
                                        if (p !in 1024..65535) {
                                            Toast.makeText(context, "Port must be 1024-65535", Toast.LENGTH_SHORT).show()
                                            return@ServerControlCard
                                        }
                                        SettingsManager.setFtpPort(context, p)
                                        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
                                        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
                                        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
                                        SettingsManager.setFtpAllowUpload(context, allowWebUploads)
                                        SettingsManager.setFtpWritePermission(context, allowWritePermissions)
                                        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)

                                        ContinuumHubService.startService(context)
                                        Toast.makeText(context, "Server started in background", Toast.LENGTH_SHORT).show()
                                    } else {
                                        ContinuumHubService.stopService(context)
                                        Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onHostedModeChange = { mode ->
                                    hostedMode = mode
                                    SettingsManager.setFtpHostedMode(context, mode, if (mode == FtpHostedMode.CUSTOM_FOLDER) customFolderPath else "")
                                },
                                onChooseFilePicker = { showFullPageFilePicker = true },
                                onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                            )

                            WebUploadCard(
                                allowWebUploads = allowWebUploads,
                                uploadPasswordInput = uploadPasswordInput,
                                showUploadPassword = showUploadPassword,
                                inputShape = inputShape,
                                onAllowChange = { allowWebUploads = it },
                                onPasswordChange = { uploadPasswordInput = it },
                                onToggleShowPass = { showUploadPassword = !showUploadPassword },
                                onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            NetworkAuthCard(
                                portInput = portInput,
                                isAnonymousAccess = isAnonymousAccess,
                                usernameInput = usernameInput,
                                passwordInput = passwordInput,
                                showPassword = showPassword,
                                allowWritePermissions = allowWritePermissions,
                                inputShape = inputShape,
                                onPortChange = { portInput = it },
                                onAnonChange = { isAnonymousAccess = it },
                                onUserChange = { usernameInput = it },
                                onPassChange = { passwordInput = it },
                                onToggleShowPass = { showPassword = !showPassword },
                                onAllowWriteChange = { allowWritePermissions = it },
                                onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                            )

                            ScreenCastCard(
                                isScreenCasting = isScreenCasting,
                                screenResolution = screenResolution,
                                screenRotation = screenRotation,
                                context = context,
                                onResolutionChange = { res ->
                                    screenResolution = res
                                    SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                                },
                                onRotationChange = { rot ->
                                    screenRotation = rot
                                    SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                                },
                                onStartCast = {
                                    SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                                    screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                },
                                onStopCast = {
                                    ContinuumHubService.stopScreenCast(context)
                                    Toast.makeText(context, "Screen Sharing stopped", Toast.LENGTH_SHORT).show()
                                },
                                onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                            )
                        }
                    }
                } else {
                    // SINGLE COLUMN STACK FOR NARROW MOBILE SCREENS
                    ServerControlCard(
                        isServerRunning = isServerRunning,
                        serverIp = serverIp,
                        activePort = activePort,
                        uptimeFormatted = uptimeFormatted,
                        ftpUrl = ftpUrl,
                        httpWebUrl = httpWebUrl,
                        hostedMode = hostedMode,
                        customFolderPath = customFolderPath,
                        context = context,
                        clipboardManager = clipboardManager,
                        onStartToggle = { start ->
                            if (start) {
                                val p = portInput.toIntOrNull() ?: 2121
                                if (p !in 1024..65535) {
                                    Toast.makeText(context, "Port must be 1024-65535", Toast.LENGTH_SHORT).show()
                                    return@ServerControlCard
                                }
                                SettingsManager.setFtpPort(context, p)
                                SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
                                SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
                                SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
                                SettingsManager.setFtpAllowUpload(context, allowWebUploads)
                                SettingsManager.setFtpWritePermission(context, allowWritePermissions)
                                SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)

                                ContinuumHubService.startService(context)
                                Toast.makeText(context, "Server started in background", Toast.LENGTH_SHORT).show()
                            } else {
                                ContinuumHubService.stopService(context)
                                Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onHostedModeChange = { mode ->
                            hostedMode = mode
                            SettingsManager.setFtpHostedMode(context, mode, if (mode == FtpHostedMode.CUSTOM_FOLDER) customFolderPath else "")
                        },
                        onChooseFilePicker = { showFullPageFilePicker = true },
                        onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                    )

                    NetworkAuthCard(
                        portInput = portInput,
                        isAnonymousAccess = isAnonymousAccess,
                        usernameInput = usernameInput,
                        passwordInput = passwordInput,
                        showPassword = showPassword,
                        allowWritePermissions = allowWritePermissions,
                        inputShape = inputShape,
                        onPortChange = { portInput = it },
                        onAnonChange = { isAnonymousAccess = it },
                        onUserChange = { usernameInput = it },
                        onPassChange = { passwordInput = it },
                        onToggleShowPass = { showPassword = !showPassword },
                        onAllowWriteChange = { allowWritePermissions = it },
                        onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                    )

                    WebUploadCard(
                        allowWebUploads = allowWebUploads,
                        uploadPasswordInput = uploadPasswordInput,
                        showUploadPassword = showUploadPassword,
                        inputShape = inputShape,
                        onAllowChange = { allowWebUploads = it },
                        onPasswordChange = { uploadPasswordInput = it },
                        onToggleShowPass = { showUploadPassword = !showUploadPassword },
                        onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                    )

                    ScreenCastCard(
                        isScreenCasting = isScreenCasting,
                        screenResolution = screenResolution,
                        screenRotation = screenRotation,
                        context = context,
                        onResolutionChange = { res ->
                            screenResolution = res
                            SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                        },
                        onRotationChange = { rot ->
                            screenRotation = rot
                            SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                        },
                        onStartCast = {
                            SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio)
                            screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                        },
                        onStopCast = {
                            ContinuumHubService.stopScreenCast(context)
                            Toast.makeText(context, "Screen Sharing stopped", Toast.LENGTH_SHORT).show()
                        },
                        onExplain = { title, desc -> explanationDialogText = Pair(title, desc) }
                    )
                }

                // SAVE / APPLY BUTTON
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull() ?: 2121
                        SettingsManager.setFtpPort(context, p)
                        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
                        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
                        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
                        SettingsManager.setFtpAllowUpload(context, allowWebUploads)
                        SettingsManager.setFtpWritePermission(context, allowWritePermissions)
                        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)

                        if (isServerRunning) {
                            ContinuumHubService.stopService(context)
                            ContinuumHubService.startService(context)
                            Toast.makeText(context, "Server updated & restarted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isServerRunning) "Apply & Restart" else "Save Settings", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Quick Connect QR Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        QrCodeView(
                            content = if (hostedMode == FtpHostedMode.CUSTOM_FOLDER) httpWebUrl else ftpUrl,
                            size = 180.dp,
                            darkColor = Color.Black,
                            lightColor = Color.White
                        )
                    }
                    Text(
                        text = if (hostedMode == FtpHostedMode.CUSTOM_FOLDER) httpWebUrl else ftpUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Server Logs Dialog
    if (showLogsDialog) {
        val clientSessions = ContinuumHubService.clientSessions.values.toList()
        val logEntries = ContinuumHubService.logEntries.toList()
        var selectedLogTab by remember { mutableIntStateOf(0) }

        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Server Activity & Clients", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (logEntries.isNotEmpty() || clientSessions.isNotEmpty()) {
                        TextButton(
                            onClick = { ContinuumHubService.clearLogs() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLogTab == 0,
                            onClick = { selectedLogTab = 0 },
                            label = { Text("Clients (${clientSessions.size})", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = selectedLogTab == 1,
                            onClick = { selectedLogTab = 1 },
                            label = { Text("Activity Log (${logEntries.size})", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (selectedLogTab == 0) {
                        if (clientSessions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No connected clients yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(clientSessions.size) { idx ->
                                    val session = clientSessions[idx]
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(session.clientIp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(session.protocol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Text("${session.requestsCount} reqs • ${session.bytesTransferred / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (logEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No server logs recorded yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(logEntries.size) { idx ->
                                    val log = logEntries[idx]
                                    Text(text = "[${log.protocol}] ${log.clientIp}: ${log.action} ${log.details}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ServerControlCard(
    isServerRunning: Boolean,
    serverIp: String,
    activePort: Int,
    uptimeFormatted: String,
    ftpUrl: String,
    httpWebUrl: String,
    hostedMode: FtpHostedMode,
    customFolderPath: String,
    context: Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onStartToggle: (Boolean) -> Unit,
    onHostedModeChange: (FtpHostedMode) -> Unit,
    onChooseFilePicker: () -> Unit,
    onExplain: (String, String) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isServerRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServerRunning) "Service Active" else "Service Inactive",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { onExplain("Server Service", "Starts FTP and HTTP server on local Wi-Fi.") },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Switch(checked = isServerRunning, onCheckedChange = onStartToggle)
            }

            if (isServerRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("IP: $serverIp | FTP: $activePort", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text("Uptime: $uptimeFormatted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(ftpUrl))
                                Toast.makeText(context, "FTP URL copied", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Copy FTP", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hosting Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = hostedMode == FtpHostedMode.BATCH_FILES || hostedMode == FtpHostedMode.FULL_STORAGE,
                        onClick = { onHostedModeChange(FtpHostedMode.BATCH_FILES) },
                        label = { Text("FTP Bucket", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = hostedMode == FtpHostedMode.CUSTOM_FOLDER,
                        onClick = { onHostedModeChange(FtpHostedMode.CUSTOM_FOLDER) },
                        label = { Text("HTML Site", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (hostedMode == FtpHostedMode.CUSTOM_FOLDER) {
                        Text(
                            text = if (customFolderPath.isNotBlank()) "Target: ${File(customFolderPath).name}" else "No HTML Target",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        val count = FtpBucketManager.bucketItems.size
                        Text(
                            text = if (count > 0) "Bucket: $count item(s) selected" else "Full Storage Directory",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onChooseFilePicker,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Choose", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun NetworkAuthCard(
    portInput: String,
    isAnonymousAccess: Boolean,
    usernameInput: String,
    passwordInput: String,
    showPassword: Boolean,
    allowWritePermissions: Boolean,
    inputShape: RoundedCornerShape,
    onPortChange: (String) -> Unit,
    onAnonChange: (Boolean) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
    onAllowWriteChange: (Boolean) -> Unit,
    onExplain: (String, String) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Network & Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("FTP Port", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    placeholder = { Text("2121") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = inputShape,
                    modifier = Modifier.width(110.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Anonymous Access", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isAnonymousAccess, onCheckedChange = onAnonChange)
            }

            if (!isAnonymousAccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = onUserChange,
                        label = { Text("Username") },
                        singleLine = true,
                        shape = inputShape,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPassChange,
                        label = { Text("Password") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = onToggleShowPass) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        singleLine = true,
                        shape = inputShape,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Allow Upload / Write", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allowWritePermissions, onCheckedChange = onAllowWriteChange)
            }
        }
    }
}

@Composable
private fun WebUploadCard(
    allowWebUploads: Boolean,
    uploadPasswordInput: String,
    showUploadPassword: Boolean,
    inputShape: RoundedCornerShape,
    onAllowChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
    onExplain: (String, String) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Web Upload & Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Web Upload Password", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allowWebUploads, onCheckedChange = onAllowChange)
            }

            if (allowWebUploads) {
                OutlinedTextField(
                    value = uploadPasswordInput,
                    onValueChange = onPasswordChange,
                    label = { Text("Upload Password") },
                    visualTransformation = if (showUploadPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleShowPass) {
                            Icon(if (showUploadPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    singleLine = true,
                    shape = inputShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ScreenCastCard(
    isScreenCasting: Boolean,
    screenResolution: String,
    screenRotation: Int,
    context: Context,
    onResolutionChange: (String) -> Unit,
    onRotationChange: (Int) -> Unit,
    onStartCast: () -> Unit,
    onStopCast: () -> Unit,
    onExplain: (String, String) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Screen Sharing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                if (!isScreenCasting) {
                    FilledTonalButton(
                        onClick = onStartCast,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start Cast", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onStopCast,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.VideocamOff, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop Cast", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Resolution", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1080p", "720p", "480p").forEach { res ->
                        FilterChip(
                            selected = screenResolution == res,
                            onClick = { onResolutionChange(res) },
                            label = { Text(res, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rotation", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, label) ->
                        FilterChip(
                            selected = screenRotation == deg,
                            onClick = { onRotationChange(deg) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    }
}
