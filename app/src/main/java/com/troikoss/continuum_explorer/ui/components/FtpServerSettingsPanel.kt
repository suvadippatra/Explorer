package com.troikoss.continuum_explorer.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
 * Minimalist, Material 3 FTP/HTTP File Sharing & Self-Hosted Website Server Panel.
 * Features:
 * - Top Heading Bar with Full-Screen Toggle, Live Activity Logs (when running), and QR Code.
 * - Segmented Mode Toggle switching between:
 *   1. FTP & File Sharing Mode (Selected Files/Folders Bucket or Full Storage + FTP/HTTP Portal + Screen Mirroring)
 *   2. Self-Hosted Website Mode (Host a downloaded GitHub web repo folder or specific .html file locally)
 * - Responsive 1-column (mobile) vs 2-column (tablet/desktop) minimalist layout.
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
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

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
            Toast.makeText(context, "Live Screen Mirroring started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen Mirroring permission cancelled", Toast.LENGTH_SHORT).show()
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
    val isWebsiteMode = hostedMode == FtpHostedMode.CUSTOM_FOLDER

    fun saveAllSettings(reloadIfActive: Boolean = true) {
        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
        SettingsManager.setFtpPort(context, p)
        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
        SettingsManager.setFtpAllowUpload(context, allowWebUploads)
        SettingsManager.setFtpWritePermission(context, allowWritePermissions)
        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
        if (reloadIfActive && isServerRunning) {
            ContinuumHubService.reloadIfRunning(context)
        }
    }

    // Full page file picker overlay
    if (showFullPageFilePicker) {
        FullPageFilePicker(
            title = if (isWebsiteMode) "Select Website Folder or HTML File" else "Select Shared Files & Folders",
            isHtmlMode = isWebsiteMode,
            onDismiss = {
                showFullPageFilePicker = false
                if (!isWebsiteMode && isServerRunning) {
                    ContinuumHubService.reloadIfRunning(context)
                }
            },
            onHtmlSelected = { selected ->
                customFolderPath = selected.absolutePath
                hostedMode = FtpHostedMode.CUSTOM_FOLDER
                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, customFolderPath)
                showFullPageFilePicker = false
                if (isServerRunning) {
                    ContinuumHubService.reloadIfRunning(context)
                }
            }
        )
        return
    }

    val inputShape = RoundedCornerShape(10.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // --- TOP HEADING BAR (Flush under TabBar) ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val showMenuIcon = appState.getScreenSize() == com.troikoss.continuum_explorer.model.ScreenSize.SMALL ||
                        SettingsManager.isWideSidebarCollapsed.value
                    if (showMenuIcon) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Navigation Sidebar")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWebsiteMode) "Self-Hosted Web Server" else "FTP & HTTP File Server",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isServerRunning) {
                        IconButton(onClick = { showLogsDialog = true }) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = "Server Activity & Logs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = "Quick Connect QR",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = { SettingsManager.toggleFullScreenTabBar() }) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit Fullscreen" else "Fullscreen"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- SEGMENTED MODE TOGGLE (Minimalist 2-Mode Switcher) ---
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = !isWebsiteMode,
                        onClick = {
                            if (isWebsiteMode) {
                                hostedMode = if (FtpBucketManager.bucketItems.isNotEmpty()) {
                                    FtpHostedMode.BATCH_FILES
                                } else {
                                    FtpHostedMode.FULL_STORAGE
                                }
                                SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
                                ContinuumHubService.reloadIfRunning(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                Icons.Default.FolderShared,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(
                            text = "FTP & File Share",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SegmentedButton(
                        selected = isWebsiteMode,
                        onClick = {
                            if (!isWebsiteMode) {
                                hostedMode = FtpHostedMode.CUSTOM_FOLDER
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, customFolderPath)
                                ContinuumHubService.reloadIfRunning(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(
                            text = "Self-Hosted Website",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // --- MASTER STATUS & ENDPOINT BANNER ---
                ServerMasterStatusCard(
                    isServerRunning = isServerRunning,
                    isWebsiteMode = isWebsiteMode,
                    uptimeFormatted = uptimeFormatted,
                    ftpUrl = ftpUrl,
                    httpWebUrl = httpWebUrl,
                    context = context,
                    clipboardManager = clipboardManager,
                    onStartToggle = { start ->
                        if (start) {
                            val p = portInput.toIntOrNull() ?: 2121
                            if (p !in 1024..65535) {
                                Toast.makeText(context, "FTP Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
                                return@ServerMasterStatusCard
                            }
                            saveAllSettings(reloadIfActive = false)
                            ContinuumHubService.startService(context)
                            Toast.makeText(context, "Server started", Toast.LENGTH_SHORT).show()
                        } else {
                            ContinuumHubService.stopService(context)
                            Toast.makeText(context, "Server stopped", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // --- MODE-SPECIFIC CONFIGURATION ---
                if (isWebsiteMode) {
                    // SELF-HOSTED WEBSITE MODE (GitHub Web Repo / Static HTML Folder or File)
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SelfHostedWebsiteTargetCard(
                                    customFolderPath = customFolderPath,
                                    httpWebUrl = httpWebUrl,
                                    isServerRunning = isServerRunning,
                                    context = context,
                                    onSelectSource = { showFullPageFilePicker = true },
                                    onClearSource = {
                                        customFolderPath = ""
                                        SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, "")
                                        ContinuumHubService.reloadIfRunning(context)
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ScreenCastCard(
                                    isScreenCasting = isScreenCasting,
                                    screenResolution = screenResolution,
                                    screenRotation = screenRotation,
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
                                    }
                                )
                            }
                        }
                    } else {
                        SelfHostedWebsiteTargetCard(
                            customFolderPath = customFolderPath,
                            httpWebUrl = httpWebUrl,
                            isServerRunning = isServerRunning,
                            context = context,
                            onSelectSource = { showFullPageFilePicker = true },
                            onClearSource = {
                                customFolderPath = ""
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, "")
                                ContinuumHubService.reloadIfRunning(context)
                            }
                        )
                        ScreenCastCard(
                            isScreenCasting = isScreenCasting,
                            screenResolution = screenResolution,
                            screenRotation = screenRotation,
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
                            }
                        )
                    }
                } else {
                    // FTP & HTTP FILE SHARING MODE
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FtpSharedContentCard(
                                    hostedMode = hostedMode,
                                    context = context,
                                    onScopeChange = { mode ->
                                        hostedMode = mode
                                        SettingsManager.setFtpHostedMode(context, mode, customFolderPath)
                                        ContinuumHubService.reloadIfRunning(context)
                                    },
                                    onPickFilesOrFolders = {
                                        hostedMode = FtpHostedMode.BATCH_FILES
                                        SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                        showFullPageFilePicker = true
                                    }
                                )

                                WebUploadCard(
                                    allowWebUploads = allowWebUploads,
                                    uploadPasswordInput = uploadPasswordInput,
                                    showUploadPassword = showUploadPassword,
                                    inputShape = inputShape,
                                    onAllowChange = {
                                        allowWebUploads = it
                                        SettingsManager.setFtpAllowUpload(context, it)
                                    },
                                    onPasswordChange = {
                                        uploadPasswordInput = it
                                        SettingsManager.setFtpUploadPassword(context, it)
                                    },
                                    onToggleShowPass = { showUploadPassword = !showUploadPassword }
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    onApplyNetwork = {
                                        saveAllSettings(reloadIfActive = true)
                                        Toast.makeText(
                                            context,
                                            if (isServerRunning) "Server reloaded with new settings" else "Settings saved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )

                                ScreenCastCard(
                                    isScreenCasting = isScreenCasting,
                                    screenResolution = screenResolution,
                                    screenRotation = screenRotation,
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
                                    }
                                )
                            }
                        }
                    } else {
                        FtpSharedContentCard(
                            hostedMode = hostedMode,
                            context = context,
                            onScopeChange = { mode ->
                                hostedMode = mode
                                SettingsManager.setFtpHostedMode(context, mode, customFolderPath)
                                ContinuumHubService.reloadIfRunning(context)
                            },
                            onPickFilesOrFolders = {
                                hostedMode = FtpHostedMode.BATCH_FILES
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                showFullPageFilePicker = true
                            }
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
                            onApplyNetwork = {
                                saveAllSettings(reloadIfActive = true)
                                Toast.makeText(
                                    context,
                                    if (isServerRunning) "Server reloaded with new settings" else "Settings saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )

                        WebUploadCard(
                            allowWebUploads = allowWebUploads,
                            uploadPasswordInput = uploadPasswordInput,
                            showUploadPassword = showUploadPassword,
                            inputShape = inputShape,
                            onAllowChange = {
                                allowWebUploads = it
                                SettingsManager.setFtpAllowUpload(context, it)
                            },
                            onPasswordChange = {
                                uploadPasswordInput = it
                                SettingsManager.setFtpUploadPassword(context, it)
                            },
                            onToggleShowPass = { showUploadPassword = !showUploadPassword }
                        )

                        ScreenCastCard(
                            isScreenCasting = isScreenCasting,
                            screenResolution = screenResolution,
                            screenRotation = screenRotation,
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
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        val targetQrUrl = if (isWebsiteMode) httpWebUrl else httpWebUrl
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
                            content = targetQrUrl,
                            size = 180.dp,
                            darkColor = Color.Black,
                            lightColor = Color.White
                        )
                    }
                    Text(
                        text = "Web: $httpWebUrl",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isWebsiteMode) {
                        Text(
                            text = "FTP: $ftpUrl",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            LazyColumn(
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
                                            Text(
                                                "${session.requestsCount} reqs • ${session.bytesTransferred / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
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
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(logEntries.size) { idx ->
                                    val log = logEntries[idx]
                                    Text(
                                        text = "[${log.protocol}] ${log.clientIp}: ${log.action} ${log.details}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
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
private fun ServerMasterStatusCard(
    isServerRunning: Boolean,
    isWebsiteMode: Boolean,
    uptimeFormatted: String,
    ftpUrl: String,
    httpWebUrl: String,
    context: Context,
    clipboardManager: ClipboardManager,
    onStartToggle: (Boolean) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServerRunning) "Server Online ($uptimeFormatted)" else "Server Offline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isWebsiteMode) {
                            "Serving static website & assets over local Wi-Fi HTTP"
                        } else {
                            "Serving FTP daemon & interactive Web Portal over Wi-Fi"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isServerRunning,
                    onCheckedChange = onStartToggle
                )
            }

            if (isServerRunning) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // HTTP Endpoint Row
                EndpointRow(
                    label = if (isWebsiteMode) "Website URL" else "Web Portal",
                    url = httpWebUrl,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(httpWebUrl))
                        Toast.makeText(context, "Copied $httpWebUrl", Toast.LENGTH_SHORT).show()
                    },
                    onOpenBrowser = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                        } catch (_: Exception) {}
                    }
                )

                // FTP Endpoint Row (Shown in FTP & File Share mode)
                if (!isWebsiteMode) {
                    EndpointRow(
                        label = "FTP Server",
                        url = ftpUrl,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(ftpUrl))
                            Toast.makeText(context, "Copied $ftpUrl", Toast.LENGTH_SHORT).show()
                        },
                        onOpenBrowser = null
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(
    label: String,
    url: String,
    onCopy: () -> Unit,
    onOpenBrowser: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(16.dp))
                }
                if (onOpenBrowser != null) {
                    IconButton(
                        onClick = onOpenBrowser,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpSharedContentCard(
    hostedMode: FtpHostedMode,
    context: Context,
    onScopeChange: (FtpHostedMode) -> Unit,
    onPickFilesOrFolders: () -> Unit
) {
    val bucketItems = FtpBucketManager.bucketItems
    val isSelectedBucketMode = hostedMode == FtpHostedMode.BATCH_FILES

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Shared Files & Folders Scope",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isSelectedBucketMode,
                    onClick = { onScopeChange(FtpHostedMode.BATCH_FILES) },
                    label = { Text("Selected Files / Folders", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = hostedMode == FtpHostedMode.FULL_STORAGE,
                    onClick = { onScopeChange(FtpHostedMode.FULL_STORAGE) },
                    label = { Text("Full Device Storage", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    shape = RoundedCornerShape(10.dp)
                )
            }

            if (isSelectedBucketMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bucketItems.isEmpty()) {
                                "No specific items picked yet (serving full storage fallback)"
                            } else {
                                "${bucketItems.size} file(s) / folder(s) shared"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (bucketItems.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    FtpBucketManager.clearBucket(context)
                                    ContinuumHubService.reloadIfRunning(context)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        FilledTonalButton(
                            onClick = onPickFilesOrFolders,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Items", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (bucketItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bucketItems.forEach { item ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    FtpBucketManager.removeItem(context, item)
                                    ContinuumHubService.reloadIfRunning(context)
                                },
                                label = {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Sharing entire Internal Storage over FTP and the Web Portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelfHostedWebsiteTargetCard(
    customFolderPath: String,
    httpWebUrl: String,
    isServerRunning: Boolean,
    context: Context,
    onSelectSource: () -> Unit,
    onClearSource: () -> Unit
) {
    val selectedTarget = remember(customFolderPath) {
        if (customFolderPath.isNotBlank()) File(customFolderPath).takeIf { it.exists() } else null
    }
    val detectedEntryHtml = remember(selectedTarget) {
        when {
            selectedTarget == null -> null
            selectedTarget.isFile -> selectedTarget
            selectedTarget.isDirectory -> {
                File(selectedTarget, "index.html").takeIf { it.exists() && it.isFile }
                    ?: File(selectedTarget, "index.htm").takeIf { it.exists() && it.isFile }
                    ?: selectedTarget.listFiles()?.firstOrNull {
                        it.isFile && (it.name.endsWith(".html", true) || it.name.endsWith(".htm", true))
                    }
            }
            else -> null
        }
    }
    val rootDir = remember(selectedTarget) {
        when {
            selectedTarget == null -> null
            selectedTarget.isFile -> selectedTarget.parentFile
            else -> selectedTarget
        }
    }

    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Self-Hosted Website Source",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Host a local web repository (e.g. from GitHub) or an .html file with CSS/JS assets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedTarget == null) {
                        Text(
                            text = "No website folder or HTML file selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'Choose Folder / HTML' to pick a folder containing index.html or any standalone .html file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (selectedTarget.isDirectory) Icons.Default.Folder else Icons.Default.Html,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedTarget.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = onClearSource,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Text(
                            text = "Asset Root: ${rootDir?.absolutePath ?: "-"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (detectedEntryHtml != null) {
                                "Entry Page: ${detectedEntryHtml.name} (Ready)"
                            } else {
                                "Entry Page: No .html file found in folder (will show directory explorer)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (detectedEntryHtml != null) Color(0xFF10B981) else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSelectSource,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Folder / HTML", style = MaterialTheme.typography.labelMedium)
                }

                if (isServerRunning) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Site", style = MaterialTheme.typography.labelMedium)
                    }
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
    onApplyNetwork: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
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
                Text(
                    "FTP Port & Authentication",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onApplyNetwork,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

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
                    modifier = Modifier.width(105.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Anonymous Login", style = MaterialTheme.typography.bodyMedium)
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
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
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
                Text("Allow FTP Write / Upload", style = MaterialTheme.typography.bodyMedium)
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
    onToggleShowPass: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Web Portal Upload Security",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Allow Browser File Uploads", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allowWebUploads, onCheckedChange = onAllowChange)
            }

            if (allowWebUploads) {
                OutlinedTextField(
                    value = uploadPasswordInput,
                    onValueChange = onPasswordChange,
                    label = { Text("Upload Password (leave blank for none)") },
                    visualTransformation = if (showUploadPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleShowPass) {
                            Icon(
                                if (showUploadPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
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
    onResolutionChange: (String) -> Unit,
    onRotationChange: (Int) -> Unit,
    onStartCast: () -> Unit,
    onStopCast: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Live Screen Mirroring",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isScreenCasting) "Streaming live to Web Portal (/screencast)" else "Stream device screen to any browser",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isScreenCasting) {
                    FilledTonalButton(
                        onClick = onStartCast,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Mirror", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onStopCast,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CastConnected, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Mirror", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quality", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1080p", "720p", "480p").forEach { res ->
                        FilterChip(
                            selected = screenResolution == res,
                            onClick = { onResolutionChange(res) },
                            label = { Text(res, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Orientation", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "0°", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (deg, label) ->
                        FilterChip(
                            selected = screenRotation == deg,
                            onClick = { onRotationChange(deg) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }
}
