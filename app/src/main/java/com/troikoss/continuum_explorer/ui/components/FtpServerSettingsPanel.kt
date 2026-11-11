package com.troikoss.continuum_explorer.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.server.ContinuumHubService
import com.troikoss.continuum_explorer.server.ProtocolLifecycleState
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.server.SyncPlayRoomState
import com.troikoss.continuum_explorer.server.UnifiedServerState
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.QrCodeView
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Unified Material 3 FTP/HTTP File Sharing, Remote DeX Mini-Apps Studio, SyncPlay Party &
 * Self-Hosted Website Server Panel powered by [ServerManager] and [SyncPlayManager].
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

    // Reactive Unified Server State & SyncPlay State
    val unifiedState by ServerManager.state.collectAsState()
    val syncRoomState by SyncPlayManager.roomState.collectAsState()

    LaunchedEffect(Unit) {
        FtpBucketManager.initialize(context)
        ServerManager.refreshIpAddress()
    }

    // Preferences state
    val savedPort by SettingsManager.ftpPort
    val savedAnonymous by SettingsManager.isFtpAnonymousAllowed
    val savedUsername by SettingsManager.ftpUsername
    val savedPassword by SettingsManager.ftpPassword
    val savedHostedMode by SettingsManager.ftpHostedMode
    val savedHostedPath by SettingsManager.ftpHostedPath
    val savedUploadPass by SettingsManager.ftpUploadPassword
    val savedAllowUpload by SettingsManager.ftpAllowUpload
    val savedUploadFolder by SettingsManager.ftpUploadFolderPath
    val savedAllowMiniApps by SettingsManager.ftpAllowMiniApps

    val savedScreenCastEnabled by SettingsManager.screenCastEnabled
    val savedResolution by SettingsManager.screenCastResolution
    val savedFps by SettingsManager.screenCastFps
    val savedQuality by SettingsManager.screenCastQuality
    val savedRotation by SettingsManager.screenCastRotation
    val savedAudio by SettingsManager.screenCastAudio

    // Local form state
    var portInput by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var isAnonymousAccess by remember(savedAnonymous) { mutableStateOf(savedAnonymous) }
    var usernameInput by remember(savedUsername) { mutableStateOf(savedUsername) }
    var passwordInput by remember(savedPassword) { mutableStateOf(savedPassword) }
    var uploadPasswordInput by remember(savedUploadPass) { mutableStateOf(savedUploadPass) }
    var allowUnifiedUploads by remember(savedAllowUpload) { mutableStateOf(savedAllowUpload) }
    var uploadFolderPath by remember(savedUploadFolder) {
        mutableStateOf(
            savedUploadFolder.ifBlank {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
        )
    }
    var allowRemoteMiniApps by remember(savedAllowMiniApps) { mutableStateOf(savedAllowMiniApps) }
    var showPassword by remember { mutableStateOf(false) }
    var showUploadPassword by remember { mutableStateOf(false) }
    var hostedMode by remember(savedHostedMode) { mutableStateOf(savedHostedMode) }
    var customFolderPath by remember(savedHostedPath) { mutableStateOf(savedHostedPath) }

    // Screen cast form state
    var screenCastEnabled by remember(savedScreenCastEnabled) { mutableStateOf(savedScreenCastEnabled) }
    var screenResolution by remember(savedResolution) { mutableStateOf(savedResolution) }
    var screenFps by remember(savedFps) { mutableIntStateOf(savedFps) }
    var screenQuality by remember(savedQuality) { mutableIntStateOf(savedQuality) }
    var screenRotation by remember(savedRotation) { mutableIntStateOf(savedRotation) }
    var screenAudio by remember(savedAudio) { mutableStateOf(savedAudio) }

    val isServerRunning = unifiedState.isAnyRunning
    val isScreenCasting = unifiedState.isScreenCasting
    val serverIp = unifiedState.ipAddress
    val activePort = unifiedState.ftp.port
    var uptimeFormatted by remember { mutableStateOf("00:00:00") }

    // Modals & Pickers
    var showQrDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showFullPageFilePicker by remember { mutableStateOf(false) }
    var showUploadFolderPicker by remember { mutableStateOf(false) }
    var showSyncPlayMediaPicker by remember { mutableStateOf(false) }

    // Screen Cast Launcher
    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            SettingsManager.setScreenCastEnabled(context, true)
            SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
            ContinuumHubService.startScreenCast(context, result.resultCode, result.data!!)
            Toast.makeText(context, "Live Screen Mirroring started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen Mirroring permission cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Live uptime & SyncPlay position ticker
    var liveSyncPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(unifiedState.isAnyRunning, unifiedState.hubStartedAtMillis, syncRoomState.isActive, syncRoomState.isPlaying) {
        while (true) {
            if (unifiedState.isAnyRunning && unifiedState.hubStartedAtMillis > 0L) {
                val elapsedSec = ((System.currentTimeMillis() - unifiedState.hubStartedAtMillis) / 1000L).coerceAtLeast(0L)
                val hours = elapsedSec / 3600
                val mins = (elapsedSec % 3600) / 60
                val secs = elapsedSec % 60
                uptimeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
            } else {
                uptimeFormatted = "00:00:00"
            }
            if (syncRoomState.isActive) {
                liveSyncPosMs = syncRoomState.currentPositionMs()
            }
            delay(500L)
        }
    }

    val ftpUrl = remember(isServerRunning, serverIp, activePort, isAnonymousAccess, usernameInput, passwordInput) {
        if (!isAnonymousAccess && usernameInput.isNotBlank()) {
            "ftp://$usernameInput:$passwordInput@$serverIp:$activePort"
        } else {
            "ftp://$serverIp:$activePort"
        }
    }
    val httpWebUrl = remember(serverIp, unifiedState.http.port) { "http://$serverIp:${unifiedState.http.port}" }
    val syncPlayWebUrl = remember(httpWebUrl) { "$httpWebUrl/?tab=syncplay" }
    val isWebsiteMode = hostedMode == FtpHostedMode.CUSTOM_FOLDER

    fun saveAllSettings(reloadIfActive: Boolean = true) {
        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
        SettingsManager.setFtpPort(context, p)
        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
        SettingsManager.setFtpAllowUpload(context, allowUnifiedUploads)
        SettingsManager.setFtpUploadFolderPath(context, uploadFolderPath)
        SettingsManager.setFtpAllowMiniApps(context, allowRemoteMiniApps)
        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
        if (reloadIfActive && unifiedState.isAnyRunning) {
            ServerManager.restartCoordinated(context)
        }
    }

    // Upload destination folder picker dialog
    if (showUploadFolderPicker) {
        InAppFilePickerDialog(
            title = "Select Upload Destination Folder",
            selectFolders = true,
            initialDirectory = File(uploadFolderPath).takeIf { it.exists() } ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showUploadFolderPicker = false },
            onSelected = { folder ->
                uploadFolderPath = folder.absolutePath
                SettingsManager.setFtpUploadFolderPath(context, folder.absolutePath)
                showUploadFolderPicker = false
            }
        )
    }

    // SyncPlay Media File Picker Dialog
    if (showSyncPlayMediaPicker) {
        InAppFilePickerDialog(
            title = "Select Video or Audio File for SyncPlay",
            selectFolders = false,
            allowedExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus"),
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showSyncPlayMediaPicker = false },
            onSelected = { file ->
                showSyncPlayMediaPicker = false
                SyncPlayManager.loadMediaFile(
                    context = context,
                    file = file,
                    passcode = syncRoomState.passcode,
                    allowGuestControl = syncRoomState.allowGuestControl,
                    autoStartServer = true
                )
                Toast.makeText(context, "Loaded ${file.name} in SyncPlay Party", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Full page file picker overlay
    if (showFullPageFilePicker) {
        FullPageFilePicker(
            title = if (isWebsiteMode) "Select Website Folder or HTML File" else "Select Shared Files & Folders",
            isHtmlMode = isWebsiteMode,
            onDismiss = {
                showFullPageFilePicker = false
                if (!isWebsiteMode && unifiedState.isAnyRunning) {
                    ServerManager.restartCoordinated(context)
                }
            },
            onHtmlSelected = { selected ->
                customFolderPath = selected.absolutePath
                hostedMode = FtpHostedMode.CUSTOM_FOLDER
                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, customFolderPath)
                showFullPageFilePicker = false
                if (unifiedState.isAnyRunning) {
                    ServerManager.restartCoordinated(context)
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
            // --- TOP HEADING BAR (Flush under TabBar - Preserved Design) ---
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
                                .background(
                                    when {
                                        unifiedState.isTransitioning -> Color(0xFFF59E0B)
                                        isServerRunning -> Color(0xFF10B981)
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWebsiteMode) "Web Host" else "Server Hub",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { showLogsDialog = true }) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = "Server Activity & Logs",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                    .widthIn(max = 1280.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                hostedMode = FtpHostedMode.BATCH_FILES
                                SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
                                if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
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
                            text = "Server & Sync",
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
                                if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
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
                            text = "Web Host",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // --- UNIFIED SERVER MANAGER STATUS & INDEPENDENT PROTOCOL CONTROLS ---
                ServerMasterStatusCard(
                    unifiedState = unifiedState,
                    isWebsiteMode = isWebsiteMode,
                    uptimeFormatted = uptimeFormatted,
                    ftpUrl = ftpUrl,
                    httpWebUrl = httpWebUrl,
                    context = context,
                    clipboardManager = clipboardManager,
                    onMasterToggle = { start ->
                        if (start) {
                            val p = portInput.toIntOrNull() ?: 2121
                            if (p !in 1024..65535) {
                                Toast.makeText(context, "FTP Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
                                return@ServerMasterStatusCard
                            }
                            saveAllSettings(reloadIfActive = false)
                            ServerManager.startCoordinated(context, startHttp = true, startFtp = !isWebsiteMode)
                        } else {
                            ServerManager.stopCoordinated(context, graceful = true)
                        }
                    },
                    onToggleHttpOnly = { start ->
                        if (start) {
                            saveAllSettings(reloadIfActive = false)
                            ServerManager.startHttpServer(context)
                        } else {
                            ServerManager.stopHttpServer(context, graceful = true)
                        }
                    },
                    onRestartHttpOnly = {
                        saveAllSettings(reloadIfActive = false)
                        ServerManager.restartHttpServer(context)
                    },
                    onToggleFtpOnly = { start ->
                        if (start) {
                            saveAllSettings(reloadIfActive = false)
                            ServerManager.startFtpServer(context)
                        } else {
                            ServerManager.stopFtpServer(context, graceful = true)
                        }
                    },
                    onRestartFtpOnly = {
                        saveAllSettings(reloadIfActive = false)
                        ServerManager.restartFtpServer(context)
                    }
                )

                // --- BEATSYNC-INSPIRED SYNCPLAY GROUP WATCH & LISTEN PARTY CARD ---
                if (!isWebsiteMode) {
                    SyncPlayPartyCard(
                        syncState = syncRoomState,
                        livePositionMs = liveSyncPosMs,
                        syncPlayWebUrl = syncPlayWebUrl,
                        inputShape = inputShape,
                        context = context,
                        clipboardManager = clipboardManager,
                        onPickMediaFile = { showSyncPlayMediaPicker = true }
                    )
                }

                // --- MODE-SPECIFIC MINIMALIST CONFIGURATION ---
                if (isWebsiteMode) {
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
                                    isServerRunning = unifiedState.isHttpRunning,
                                    context = context,
                                    onSelectSource = { showFullPageFilePicker = true },
                                    onClearSource = {
                                        customFolderPath = ""
                                        SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, "")
                                        if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ScreenCastCard(
                                    screenCastEnabled = screenCastEnabled,
                                    isScreenCasting = isScreenCasting,
                                    screenResolution = screenResolution,
                                    screenFps = screenFps,
                                    screenQuality = screenQuality,
                                    screenRotation = screenRotation,
                                    onEnabledChange = { enabled ->
                                        screenCastEnabled = enabled
                                        SettingsManager.setScreenCastEnabled(context, enabled)
                                        if (!enabled && isScreenCasting) {
                                            ContinuumHubService.stopScreenCast(context)
                                        }
                                    },
                                    onResolutionChange = { res ->
                                        screenResolution = res
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onFpsChange = { fps ->
                                        screenFps = fps
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onQualityChange = { q ->
                                        screenQuality = q
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onRotationChange = { rot ->
                                        screenRotation = rot
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onStartCast = {
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
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
                            isServerRunning = unifiedState.isHttpRunning,
                            context = context,
                            onSelectSource = { showFullPageFilePicker = true },
                            onClearSource = {
                                customFolderPath = ""
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.CUSTOM_FOLDER, "")
                                if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
                            }
                        )
                        ScreenCastCard(
                            screenCastEnabled = screenCastEnabled,
                            isScreenCasting = isScreenCasting,
                            screenResolution = screenResolution,
                            screenFps = screenFps,
                            screenQuality = screenQuality,
                            screenRotation = screenRotation,
                            onEnabledChange = { enabled ->
                                screenCastEnabled = enabled
                                SettingsManager.setScreenCastEnabled(context, enabled)
                                if (!enabled && isScreenCasting) {
                                    ContinuumHubService.stopScreenCast(context)
                                }
                            },
                            onResolutionChange = { res ->
                                screenResolution = res
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onFpsChange = { fps ->
                                screenFps = fps
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onQualityChange = { q ->
                                screenQuality = q
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onRotationChange = { rot ->
                                screenRotation = rot
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onStartCast = {
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            },
                            onStopCast = {
                                ContinuumHubService.stopScreenCast(context)
                            }
                        )
                    }
                } else {
                    // FTP, WEB PORTAL & MINI-APPS MODE
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
                                        if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
                                    },
                                    onPickFilesOrFolders = {
                                        hostedMode = FtpHostedMode.BATCH_FILES
                                        SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                        showFullPageFilePicker = true
                                    }
                                )

                                PortalCapabilitiesAndUploadsCard(
                                    allowUnifiedUploads = allowUnifiedUploads,
                                    uploadPasswordInput = uploadPasswordInput,
                                    showUploadPassword = showUploadPassword,
                                    uploadFolderPath = uploadFolderPath,
                                    allowRemoteMiniApps = allowRemoteMiniApps,
                                    inputShape = inputShape,
                                    onAllowUploadChange = {
                                        allowUnifiedUploads = it
                                        SettingsManager.setFtpAllowUpload(context, it)
                                        if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                                    },
                                    onPasswordChange = {
                                        uploadPasswordInput = it
                                        SettingsManager.setFtpUploadPassword(context, it)
                                    },
                                    onToggleShowPass = { showUploadPassword = !showUploadPassword },
                                    onSelectUploadFolder = { showUploadFolderPicker = true },
                                    onAllowMiniAppsChange = {
                                        allowRemoteMiniApps = it
                                        SettingsManager.setFtpAllowMiniApps(context, it)
                                    }
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
                                    inputShape = inputShape,
                                    onPortChange = { portInput = it },
                                    onAnonChange = { isAnonymousAccess = it },
                                    onUserChange = { usernameInput = it },
                                    onPassChange = { passwordInput = it },
                                    onToggleShowPass = { showPassword = !showPassword },
                                    onApplyNetwork = {
                                        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
                                        SettingsManager.setFtpPort(context, p)
                                        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
                                        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
                                        if (unifiedState.isFtpRunning) {
                                            ServerManager.restartFtpServer(context)
                                            Toast.makeText(context, "FTP Server restarted on port $p", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "FTP Settings saved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                ScreenCastCard(
                                    screenCastEnabled = screenCastEnabled,
                                    isScreenCasting = isScreenCasting,
                                    screenResolution = screenResolution,
                                    screenFps = screenFps,
                                    screenQuality = screenQuality,
                                    screenRotation = screenRotation,
                                    onEnabledChange = { enabled ->
                                        screenCastEnabled = enabled
                                        SettingsManager.setScreenCastEnabled(context, enabled)
                                        if (!enabled && isScreenCasting) {
                                            ContinuumHubService.stopScreenCast(context)
                                        }
                                    },
                                    onResolutionChange = { res ->
                                        screenResolution = res
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onFpsChange = { fps ->
                                        screenFps = fps
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onQualityChange = { q ->
                                        screenQuality = q
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onRotationChange = { rot ->
                                        screenRotation = rot
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                    },
                                    onStartCast = {
                                        SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
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
                                if (unifiedState.isAnyRunning) ServerManager.restartCoordinated(context)
                            },
                            onPickFilesOrFolders = {
                                hostedMode = FtpHostedMode.BATCH_FILES
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                showFullPageFilePicker = true
                            }
                        )

                        PortalCapabilitiesAndUploadsCard(
                            allowUnifiedUploads = allowUnifiedUploads,
                            uploadPasswordInput = uploadPasswordInput,
                            showUploadPassword = showUploadPassword,
                            uploadFolderPath = uploadFolderPath,
                            allowRemoteMiniApps = allowRemoteMiniApps,
                            inputShape = inputShape,
                            onAllowUploadChange = {
                                allowUnifiedUploads = it
                                SettingsManager.setFtpAllowUpload(context, it)
                                if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                            },
                            onPasswordChange = {
                                uploadPasswordInput = it
                                SettingsManager.setFtpUploadPassword(context, it)
                            },
                            onToggleShowPass = { showUploadPassword = !showUploadPassword },
                            onSelectUploadFolder = { showUploadFolderPicker = true },
                            onAllowMiniAppsChange = {
                                allowRemoteMiniApps = it
                                SettingsManager.setFtpAllowMiniApps(context, it)
                            }
                        )

                        ScreenCastCard(
                            screenCastEnabled = screenCastEnabled,
                            isScreenCasting = isScreenCasting,
                            screenResolution = screenResolution,
                            screenFps = screenFps,
                            screenQuality = screenQuality,
                            screenRotation = screenRotation,
                            onEnabledChange = { enabled ->
                                screenCastEnabled = enabled
                                SettingsManager.setScreenCastEnabled(context, enabled)
                                if (!enabled && isScreenCasting) {
                                    ContinuumHubService.stopScreenCast(context)
                                }
                            },
                            onResolutionChange = { res ->
                                screenResolution = res
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onFpsChange = { fps ->
                                screenFps = fps
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onQualityChange = { q ->
                                screenQuality = q
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onRotationChange = { rot ->
                                screenRotation = rot
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                            },
                            onStartCast = {
                                SettingsManager.setScreenCastParams(context, screenResolution, screenFps, screenRotation, screenAudio, screenQuality)
                                screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            },
                            onStopCast = {
                                ContinuumHubService.stopScreenCast(context)
                            }
                        )

                        NetworkAuthCard(
                            portInput = portInput,
                            isAnonymousAccess = isAnonymousAccess,
                            usernameInput = usernameInput,
                            passwordInput = passwordInput,
                            showPassword = showPassword,
                            inputShape = inputShape,
                            onPortChange = { portInput = it },
                            onAnonChange = { isAnonymousAccess = it },
                            onUserChange = { usernameInput = it },
                            onPassChange = { passwordInput = it },
                            onToggleShowPass = { showPassword = !showPassword },
                            onApplyNetwork = {
                                val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
                                SettingsManager.setFtpPort(context, p)
                                SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
                                SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
                                if (unifiedState.isFtpRunning) {
                                    ServerManager.restartFtpServer(context)
                                    Toast.makeText(context, "FTP Server restarted on port $p", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "FTP Settings saved", Toast.LENGTH_SHORT).show()
                                }
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
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Quick Connect QR Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val qrTarget = if (syncRoomState.isActive) syncPlayWebUrl else httpWebUrl
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        QrCodeView(
                            content = qrTarget,
                            size = 180.dp,
                            darkColor = Color.Black,
                            lightColor = Color.White
                        )
                    }
                    Text(
                        text = if (syncRoomState.isActive) "SyncPlay Party: $syncPlayWebUrl" else "Web Portal: $httpWebUrl",
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
    unifiedState: UnifiedServerState,
    isWebsiteMode: Boolean,
    uptimeFormatted: String,
    ftpUrl: String,
    httpWebUrl: String,
    context: Context,
    clipboardManager: ClipboardManager,
    onMasterToggle: (Boolean) -> Unit,
    onToggleHttpOnly: (Boolean) -> Unit,
    onRestartHttpOnly: () -> Unit,
    onToggleFtpOnly: (Boolean) -> Unit,
    onRestartFtpOnly: () -> Unit
) {
    val isServerRunning = unifiedState.isAnyRunning

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    unifiedState.isTransitioning -> Color(0xFFF59E0B)
                                    isServerRunning -> Color(0xFF10B981)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            unifiedState.isTransitioning -> "Server Transitioning..."
                            isServerRunning -> "Server Online ($uptimeFormatted)"
                            else -> "Server Standby"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Switch(
                    checked = isServerRunning,
                    onCheckedChange = onMasterToggle
                )
            }

            if (unifiedState.lastGlobalError != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = unifiedState.lastGlobalError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Independent Protocol Control Row 1: Ktor HTTP & WebSocket Server
            ProtocolInstanceControlRow(
                title = if (isWebsiteMode) "Web Server" else "HTTP Server",
                port = unifiedState.http.port,
                state = unifiedState.http.state,
                activeConnections = unifiedState.http.activeConnections,
                url = if (unifiedState.isHttpRunning) httpWebUrl else null,
                onToggle = onToggleHttpOnly,
                onRestart = onRestartHttpOnly,
                onCopyUrl = {
                    clipboardManager.setText(AnnotatedString(httpWebUrl))
                    Toast.makeText(context, "Copied $httpWebUrl", Toast.LENGTH_SHORT).show()
                },
                onOpenBrowser = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                    } catch (_: Exception) {}
                }
            )

            // Independent Protocol Control Row 2: Apache FTP Server
            if (!isWebsiteMode) {
                ProtocolInstanceControlRow(
                    title = "FTP Server",
                    port = unifiedState.ftp.port,
                    state = unifiedState.ftp.state,
                    activeConnections = unifiedState.ftp.activeConnections,
                    url = if (unifiedState.isFtpRunning) ftpUrl else null,
                    onToggle = onToggleFtpOnly,
                    onRestart = onRestartFtpOnly,
                    onCopyUrl = {
                        clipboardManager.setText(AnnotatedString(ftpUrl))
                        Toast.makeText(context, "Copied $ftpUrl", Toast.LENGTH_SHORT).show()
                    },
                    onOpenBrowser = null
                )
            }
        }
    }
}

@Composable
private fun ProtocolInstanceControlRow(
    title: String,
    port: Int,
    state: ProtocolLifecycleState,
    activeConnections: Int,
    url: String?,
    onToggle: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenBrowser: (() -> Unit)?
) {
    val badgeColor = when (state) {
        ProtocolLifecycleState.RUNNING -> Color(0xFF10B981)
        ProtocolLifecycleState.STARTING, ProtocolLifecycleState.STOPPING -> Color(0xFFF59E0B)
        ProtocolLifecycleState.ERROR -> MaterialTheme.colorScheme.error
        ProtocolLifecycleState.STOPPED -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = "$title (:$port)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (state == ProtocolLifecycleState.RUNNING && activeConnections > 0) {
                                "RUNNING ($activeConnections)"
                            } else {
                                state.name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state == ProtocolLifecycleState.RUNNING) {
                        IconButton(
                            onClick = onRestart,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart Protocol", modifier = Modifier.size(15.dp))
                        }
                    }
                    TextButton(
                        onClick = { onToggle(state != ProtocolLifecycleState.RUNNING) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (state == ProtocolLifecycleState.RUNNING) "Stop" else "Start",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state == ProtocolLifecycleState.RUNNING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (url != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onCopyUrl,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(15.dp))
                        }
                        if (onOpenBrowser != null) {
                            IconButton(
                                onClick = onOpenBrowser,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Beatsync-inspired Synchronized Group Media Playback Card (`SyncPlayPartyCard`).
 * Allows the host to pick any video or audio file, configure passcode auth & guest control,
 * and control millisecond-synchronized playback across all connected browsers and devices.
 */
@Composable
private fun SyncPlayPartyCard(
    syncState: SyncPlayRoomState,
    livePositionMs: Long,
    syncPlayWebUrl: String,
    inputShape: RoundedCornerShape,
    context: Context,
    clipboardManager: ClipboardManager,
    onPickMediaFile: () -> Unit
) {
    var passcodeInput by remember(syncState.passcode) { mutableStateOf(syncState.passcode) }
    var allowGuestControl by remember(syncState.allowGuestControl) { mutableStateOf(syncState.allowGuestControl) }
    var leadTimeMs by remember(syncState.scheduleLeadTimeMs) { mutableFloatStateOf(syncState.scheduleLeadTimeMs.toFloat()) }
    var syncHostAudio by remember(syncState.syncHostAudioPlayer) { mutableStateOf(syncState.syncHostAudioPlayer) }
    var showSecurityConfig by remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "SyncPlay Party",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (syncState.isActive) {
                            Text(
                                text = "${syncState.mediaFileName} • ${syncState.connectedPeers.size} peer(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSecurityConfig = !showSecurityConfig },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "SyncPlay Passcode & Sync Settings",
                            tint = if (syncState.requiresPasscode) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = onPickMediaFile,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (syncState.isActive) "Change" else "Select Media",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Expandable Security & Latency Compensation Settings
            if (showSecurityConfig) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = passcodeInput,
                                onValueChange = {
                                    passcodeInput = it
                                    SyncPlayManager.updateRoomSecurity(
                                        passcode = it,
                                        allowGuestControl = allowGuestControl,
                                        scheduleLeadTimeMs = leadTimeMs.toLong(),
                                        syncHostAudioPlayer = syncHostAudio
                                    )
                                },
                                label = { Text("Room Passcode (optional)") },
                                singleLine = true,
                                shape = inputShape,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Guest Playback Control", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = allowGuestControl,
                                onCheckedChange = {
                                    allowGuestControl = it
                                    SyncPlayManager.updateRoomSecurity(passcodeInput, it, leadTimeMs.toLong(), syncHostAudio)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sync Host Audio", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = syncHostAudio,
                                onCheckedChange = {
                                    syncHostAudio = it
                                    SyncPlayManager.updateRoomSecurity(passcodeInput, allowGuestControl, leadTimeMs.toLong(), it)
                                }
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Scheduled Sync Lead Window", style = MaterialTheme.typography.bodySmall)
                                Text("${leadTimeMs.roundToInt()} ms", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = leadTimeMs,
                                onValueChange = {
                                    leadTimeMs = it
                                    SyncPlayManager.updateRoomSecurity(passcodeInput, allowGuestControl, it.toLong(), syncHostAudio)
                                },
                                valueRange = 20f..350f
                            )
                        }
                    }
                }
            }

            if (syncState.isActive) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val mins = (livePositionMs / 1000L) / 60L
                            val secs = (livePositionMs / 1000L) % 60L
                            Text(
                                text = "${if (syncState.isPlaying) "▶ PLAYING" else "⏸ PAUSED"} • ${String.format(Locale.US, "%02d:%02d", mins, secs)} • ${syncState.playbackSpeed}x",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (syncState.isPlaying) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = { SyncPlayManager.stopSession() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("End Party", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Host Synchronized Transport Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    SyncPlayManager.triggerScheduledCommand(
                                        context = context,
                                        action = if (syncState.isPlaying) "PAUSE" else "PLAY",
                                        actorName = "Host"
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (syncState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (syncState.isPlaying) "Sync Pause" else "Sync Play", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    val target = (syncState.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                                    SyncPlayManager.triggerScheduledCommand(context, "SEEK", targetPositionMs = target, actorName = "Host")
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("-10s", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    val target = syncState.currentPositionMs() + 10_000L
                                    SyncPlayManager.triggerScheduledCommand(context, "SEEK", targetPositionMs = target, actorName = "Host")
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+10s", style = MaterialTheme.typography.labelSmall)
                            }

                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                FilterChip(
                                    selected = syncState.playbackSpeed == spd,
                                    onClick = {
                                        SyncPlayManager.triggerScheduledCommand(context, "SPEED", speed = spd, actorName = "Host")
                                    },
                                    label = { Text("${spd}x", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        // Direct Share URL for SyncPlay Room
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = syncPlayWebUrl,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(syncPlayWebUrl))
                                        Toast.makeText(context, "Copied SyncPlay Party URL", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy SyncPlay URL", modifier = Modifier.size(15.dp))
                                }
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(syncPlayWebUrl)))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Open SyncPlay Room", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
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
                text = "Shared Scope",
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
                    label = { Text("Selected (${bucketItems.size})", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = hostedMode == FtpHostedMode.FULL_STORAGE,
                    onClick = { onScopeChange(FtpHostedMode.FULL_STORAGE) },
                    label = { Text("Full Storage", style = MaterialTheme.typography.labelSmall) },
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
                    Text(
                        text = "${bucketItems.size} item(s) shared",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (bucketItems.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    FtpBucketManager.clearBucket(context)
                                    if (ServerManager.state.value.isAnyRunning) {
                                        ServerManager.restartCoordinated(context)
                                    }
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
                                    if (ServerManager.state.value.isAnyRunning) {
                                        ServerManager.restartCoordinated(context)
                                    }
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
            }
        }
    }
}

@Composable
private fun PortalCapabilitiesAndUploadsCard(
    allowUnifiedUploads: Boolean,
    uploadPasswordInput: String,
    showUploadPassword: Boolean,
    uploadFolderPath: String,
    allowRemoteMiniApps: Boolean,
    inputShape: RoundedCornerShape,
    onAllowUploadChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
    onSelectUploadFolder: () -> Unit,
    onAllowMiniAppsChange: (Boolean) -> Unit
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
                "Uploads & Web Apps",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Allow Uploads", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Switch(checked = allowUnifiedUploads, onCheckedChange = onAllowUploadChange)
            }

            if (allowUnifiedUploads) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Upload Destination Folder",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                uploadFolderPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onSelectUploadFolder) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Change", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = uploadPasswordInput,
                    onValueChange = onPasswordChange,
                    label = { Text("Upload Password (optional)") },
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

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Browser Mini-Apps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Switch(checked = allowRemoteMiniApps, onCheckedChange = onAllowMiniAppsChange)
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
            Text(
                text = "Website Source",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

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
    inputShape: RoundedCornerShape,
    onPortChange: (String) -> Unit,
    onAnonChange: (Boolean) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
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
                    Text("Apply to FTP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
        }
    }
}

@Composable
private fun ScreenCastCard(
    screenCastEnabled: Boolean,
    isScreenCasting: Boolean,
    screenResolution: String,
    screenFps: Int,
    screenQuality: Int,
    screenRotation: Int,
    onEnabledChange: (Boolean) -> Unit,
    onResolutionChange: (String) -> Unit,
    onFpsChange: (Int) -> Unit,
    onQualityChange: (Int) -> Unit,
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
                        "Screen Mirroring",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isScreenCasting) {
                        Text(
                            text = "Streaming live (/screencast.mjpeg)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                Switch(
                    checked = screenCastEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (screenCastEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isScreenCasting) "Status: Broadcasting Live" else "Status: Ready to Start",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isScreenCasting) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isScreenCasting) {
                        FilledTonalButton(
                            onClick = onStartCast,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Screen Share", style = MaterialTheme.typography.labelMedium)
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
                            Text("Stop Sharing", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Rate (FPS)", style = MaterialTheme.typography.bodySmall)
                        Text("$screenFps FPS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = screenFps.toFloat(),
                        onValueChange = { onFpsChange(it.roundToInt().coerceIn(5, 30)) },
                        valueRange = 5f..30f,
                        steps = 4
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Compression Level (JPEG Quality)", style = MaterialTheme.typography.bodySmall)
                        Text("$screenQuality%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = screenQuality.toFloat(),
                        onValueChange = { onQualityChange(it.roundToInt().coerceIn(30, 95)) },
                        valueRange = 30f..95f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Resolution", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("720p", "1080p", "Native").forEach { res ->
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
}
