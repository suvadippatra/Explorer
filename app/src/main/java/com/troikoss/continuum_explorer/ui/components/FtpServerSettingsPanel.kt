package com.troikoss.continuum_explorer.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
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
 * Separated Dual-Mode Server Hub & Desktop-Optimized Network Control Center:
 * - Mode 0 (FTP, SyncPlay & Cast): Independent switches for FTP Server (:2121),
 *   SyncPlay Host Space (mandatory PIN, Host main control, zero-freeze playback),
 *   Screen Mirroring, and Secure Uploads & Isolated Mini-Apps Sandbox.
 * - Mode 1 (HTTP Website Host): Dedicated HTTP Website Server (:8080) for hosting an
 *   unzipped website folder or single HTML file with live in-app preview and Wi-Fi PIN gate.
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
    val isHeaderPinned by SettingsManager.isSubpageHeaderPinned

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
    val savedHttpWebsitePath by SettingsManager.httpWebsitePath
    val savedHttpRequireAuth by SettingsManager.httpWebsiteRequireAuth
    val savedHttpPin by SettingsManager.httpWebsiteAccessPin

    val savedScreenCastEnabled by SettingsManager.screenCastEnabled
    val savedResolution by SettingsManager.screenCastResolution
    val savedFps by SettingsManager.screenCastFps
    val savedQuality by SettingsManager.screenCastQuality
    val savedRotation by SettingsManager.screenCastRotation
    val savedAudio by SettingsManager.screenCastAudio

    // Top-level Mode Tab: 0 = FTP, SyncPlay & Cast | 1 = HTTP Website Host
    var selectedModeTab by remember { mutableIntStateOf(0) }

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
    var websiteTargetPath by remember(savedHttpWebsitePath) { mutableStateOf(savedHttpWebsitePath) }
    var httpRequireAuth by remember(savedHttpRequireAuth) { mutableStateOf(savedHttpRequireAuth) }
    var httpAccessPin by remember(savedHttpPin) { mutableStateOf(savedHttpPin) }

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
    var showFtpSharedItemsPicker by remember { mutableStateOf(false) }
    var showWebsiteSourcePicker by remember { mutableStateOf(false) }
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
            if (!unifiedState.isHttpRunning) {
                ServerManager.startHttpServer(context)
            }
            ContinuumHubService.startScreenCast(context, result.resultCode, result.data!!)
            Toast.makeText(context, "Live Screen Mirroring started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen Mirroring permission cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Live uptime & SyncPlay position ticker (only ticks when server or SyncPlay room is active)
    var liveSyncPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(unifiedState.isAnyRunning, unifiedState.hubStartedAtMillis, syncRoomState.isActive, syncRoomState.isPlaying) {
        if (!unifiedState.isAnyRunning && !syncRoomState.isActive) {
            uptimeFormatted = "00:00:00"
            return@LaunchedEffect
        }
        while (unifiedState.isAnyRunning || syncRoomState.isActive) {
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
            delay(if (syncRoomState.isActive && syncRoomState.isPlaying) 500L else 1000L)
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
    val isWebsiteMode = selectedModeTab == 1

    fun saveFtpSettings(reloadFtpIfActive: Boolean = false) {
        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
        SettingsManager.setFtpPort(context, p)
        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
        SettingsManager.setFtpAllowUpload(context, allowUnifiedUploads)
        SettingsManager.setFtpUploadFolderPath(context, uploadFolderPath)
        SettingsManager.setFtpAllowMiniApps(context, allowRemoteMiniApps)
        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
        if (reloadFtpIfActive && unifiedState.isFtpRunning) {
            ServerManager.restartFtpServer(context)
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
            title = "Select Video or Audio File for SyncPlay Space",
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
                Toast.makeText(context, "Loaded ${file.name} in SyncPlay Space (PIN: ${SyncPlayManager.roomState.value.passcode})", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Full page file picker for FTP Shared Items
    if (showFtpSharedItemsPicker) {
        FullPageFilePicker(
            title = "Select Shared Files & Folders for FTP / Portal",
            isHtmlMode = false,
            onDismiss = {
                showFtpSharedItemsPicker = false
                if (unifiedState.isFtpRunning) {
                    ServerManager.restartFtpServer(context)
                }
            },
            onHtmlSelected = {
                showFtpSharedItemsPicker = false
            }
        )
        return
    }

    // Full page file picker for HTTP Website Host (Folder with unzipped website OR single HTML file)
    if (showWebsiteSourcePicker) {
        FullPageFilePicker(
            title = "Select Website Folder (Unzipped Site) or HTML File",
            isHtmlMode = true,
            onDismiss = {
                showWebsiteSourcePicker = false
            },
            onHtmlSelected = { selected ->
                websiteTargetPath = selected.absolutePath
                SettingsManager.setHttpWebsitePath(context, selected.absolutePath)
                showWebsiteSourcePicker = false
                if (!unifiedState.isHttpRunning) {
                    ServerManager.startHttpServer(context)
                } else {
                    ServerManager.restartHttpServer(context)
                }
            }
        )
        return
    }

    val inputShape = RoundedCornerShape(10.dp)

    @Composable
    fun TopHeadingBar() {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
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
                        text = if (isWebsiteMode) "HTTP Website Host" else "FTP & SyncPlay Hub",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isServerRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "($uptimeFormatted)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = { SettingsManager.toggleSubpageHeaderPinned(context) }
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (isHeaderPinned) "Header Locked on Top (Tap to Unlock)" else "Pin Header on Top",
                        tint = if (isHeaderPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isWideScreen = maxWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky / Locked Header when pinned
            if (isHeaderPinned) {
                TopHeadingBar()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (!isHeaderPinned) {
                    TopHeadingBar()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1320.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- 2-MODE ARCHITECTURE SWITCHER (FTP Collaboration Hub vs. HTTP Website Host) ---
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedModeTab == 0,
                            onClick = { selectedModeTab = 0 },
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
                                text = "FTP, SyncPlay & Cast",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        SegmentedButton(
                            selected = selectedModeTab == 1,
                            onClick = { selectedModeTab = 1 },
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
                                text = "HTTP Website Host",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (unifiedState.lastGlobalError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = unifiedState.lastGlobalError!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (!isWebsiteMode) {
                        // =========================================================================
                        // MODE 0: FTP SERVER + SYNCPLAY SPACE + SCREEN MIRRORING + SANDBOX UPLOADS
                        // Each service has its OWN independent switch — never turns everything on at once!
                        // =========================================================================
                        if (isWideScreen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Left Desktop Column: Independent FTP Server + Shared Scope + Auth
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FtpDedicatedServerCard(
                                        unifiedState = unifiedState,
                                        ftpUrl = ftpUrl,
                                        hostedMode = hostedMode,
                                        portInput = portInput,
                                        isAnonymousAccess = isAnonymousAccess,
                                        usernameInput = usernameInput,
                                        passwordInput = passwordInput,
                                        showPassword = showPassword,
                                        inputShape = inputShape,
                                        context = context,
                                        clipboardManager = clipboardManager,
                                        onToggleFtp = { enable ->
                                            saveFtpSettings(reloadFtpIfActive = false)
                                            if (enable) ServerManager.startFtpServer(context)
                                            else ServerManager.stopFtpServer(context, graceful = true)
                                        },
                                        onRestartFtp = {
                                            saveFtpSettings(reloadFtpIfActive = false)
                                            ServerManager.restartFtpServer(context)
                                        },
                                        onScopeChange = { mode ->
                                            hostedMode = mode
                                            SettingsManager.setFtpHostedMode(context, mode, customFolderPath)
                                            if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                                        },
                                        onPickFilesOrFolders = {
                                            hostedMode = FtpHostedMode.BATCH_FILES
                                            SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                            showFtpSharedItemsPicker = true
                                        },
                                        onPortChange = { portInput = it },
                                        onAnonChange = { isAnonymousAccess = it },
                                        onUserChange = { usernameInput = it },
                                        onPassChange = { passwordInput = it },
                                        onToggleShowPass = { showPassword = !showPassword },
                                        onApplyNetwork = {
                                            saveFtpSettings(reloadFtpIfActive = true)
                                            Toast.makeText(context, "FTP configuration applied", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    PortalCapabilitiesAndUploadsCard(
                                        allowUnifiedUploads = allowUnifiedUploads,
                                        uploadPasswordInput = uploadPasswordInput,
                                        showUploadPassword = showUploadPassword,
                                        uploadFolderPath = uploadFolderPath,
                                        allowRemoteMiniApps = allowRemoteMiniApps,
                                        httpRequireAuth = httpRequireAuth,
                                        httpAccessPin = httpAccessPin,
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
                                        },
                                        onHttpSecurityChange = { reqAuth, pin ->
                                            httpRequireAuth = reqAuth
                                            httpAccessPin = pin
                                            SettingsManager.setHttpWebsiteSecurity(context, reqAuth, pin)
                                        }
                                    )
                                }

                                // Right Desktop Column: SyncPlay Party Space + Screen Mirroring
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    SyncPlayPartyCard(
                                        syncState = syncRoomState,
                                        isHttpRunning = unifiedState.isHttpRunning,
                                        livePositionMs = liveSyncPosMs,
                                        syncPlayWebUrl = syncPlayWebUrl,
                                        inputShape = inputShape,
                                        context = context,
                                        clipboardManager = clipboardManager,
                                        onPickMediaFile = { showSyncPlayMediaPicker = true }
                                    )

                                    ScreenCastCard(
                                        screenCastEnabled = screenCastEnabled,
                                        isScreenCasting = isScreenCasting,
                                        screenResolution = screenResolution,
                                        screenFps = screenFps,
                                        screenQuality = screenQuality,
                                        screenRotation = screenRotation,
                                        httpStreamUrl = "$httpWebUrl/screencast.mjpeg",
                                        clipboardManager = clipboardManager,
                                        context = context,
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
                            // Mobile Single-Column Stack
                            FtpDedicatedServerCard(
                                unifiedState = unifiedState,
                                ftpUrl = ftpUrl,
                                hostedMode = hostedMode,
                                portInput = portInput,
                                isAnonymousAccess = isAnonymousAccess,
                                usernameInput = usernameInput,
                                passwordInput = passwordInput,
                                showPassword = showPassword,
                                inputShape = inputShape,
                                context = context,
                                clipboardManager = clipboardManager,
                                onToggleFtp = { enable ->
                                    saveFtpSettings(reloadFtpIfActive = false)
                                    if (enable) ServerManager.startFtpServer(context)
                                    else ServerManager.stopFtpServer(context, graceful = true)
                                },
                                onRestartFtp = {
                                    saveFtpSettings(reloadFtpIfActive = false)
                                    ServerManager.restartFtpServer(context)
                                },
                                onScopeChange = { mode ->
                                    hostedMode = mode
                                    SettingsManager.setFtpHostedMode(context, mode, customFolderPath)
                                    if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                                },
                                onPickFilesOrFolders = {
                                    hostedMode = FtpHostedMode.BATCH_FILES
                                    SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                    showFtpSharedItemsPicker = true
                                },
                                onPortChange = { portInput = it },
                                onAnonChange = { isAnonymousAccess = it },
                                onUserChange = { usernameInput = it },
                                onPassChange = { passwordInput = it },
                                onToggleShowPass = { showPassword = !showPassword },
                                onApplyNetwork = {
                                    saveFtpSettings(reloadFtpIfActive = true)
                                    Toast.makeText(context, "FTP configuration applied", Toast.LENGTH_SHORT).show()
                                }
                            )

                            SyncPlayPartyCard(
                                syncState = syncRoomState,
                                isHttpRunning = unifiedState.isHttpRunning,
                                livePositionMs = liveSyncPosMs,
                                syncPlayWebUrl = syncPlayWebUrl,
                                inputShape = inputShape,
                                context = context,
                                clipboardManager = clipboardManager,
                                onPickMediaFile = { showSyncPlayMediaPicker = true }
                            )

                            ScreenCastCard(
                                screenCastEnabled = screenCastEnabled,
                                isScreenCasting = isScreenCasting,
                                screenResolution = screenResolution,
                                screenFps = screenFps,
                                screenQuality = screenQuality,
                                screenRotation = screenRotation,
                                httpStreamUrl = "$httpWebUrl/screencast.mjpeg",
                                clipboardManager = clipboardManager,
                                context = context,
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

                            PortalCapabilitiesAndUploadsCard(
                                allowUnifiedUploads = allowUnifiedUploads,
                                uploadPasswordInput = uploadPasswordInput,
                                showUploadPassword = showUploadPassword,
                                uploadFolderPath = uploadFolderPath,
                                allowRemoteMiniApps = allowRemoteMiniApps,
                                httpRequireAuth = httpRequireAuth,
                                httpAccessPin = httpAccessPin,
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
                                },
                                onHttpSecurityChange = { reqAuth, pin ->
                                    httpRequireAuth = reqAuth
                                    httpAccessPin = pin
                                    SettingsManager.setHttpWebsiteSecurity(context, reqAuth, pin)
                                }
                            )
                        }
                    } else {
                        // =========================================================================
                        // MODE 1: DEDICATED HTTP WEBSITE HOST
                        // Hosts an unzipped website folder or single HTML file (like a networked
                        // version of HTML File Preview supporting entire folders & relative assets)
                        // =========================================================================
                        if (isWideScreen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HttpWebsiteServerCard(
                                        unifiedState = unifiedState,
                                        websiteTargetPath = websiteTargetPath,
                                        httpWebUrl = httpWebUrl,
                                        httpRequireAuth = httpRequireAuth,
                                        httpAccessPin = httpAccessPin,
                                        inputShape = inputShape,
                                        context = context,
                                        clipboardManager = clipboardManager,
                                        onToggleHttp = { enable ->
                                            if (enable) ServerManager.startHttpServer(context)
                                            else ServerManager.stopHttpServer(context, graceful = true)
                                        },
                                        onRestartHttp = {
                                            ServerManager.restartHttpServer(context)
                                        },
                                        onSelectSource = { showWebsiteSourcePicker = true },
                                        onClearSource = {
                                            websiteTargetPath = ""
                                            SettingsManager.setHttpWebsitePath(context, "")
                                        },
                                        onSecurityChange = { reqAuth, pin ->
                                            httpRequireAuth = reqAuth
                                            httpAccessPin = pin
                                            SettingsManager.setHttpWebsiteSecurity(context, reqAuth, pin)
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1.25f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LiveHostedWebsitePreviewCard(
                                        websiteTargetPath = websiteTargetPath,
                                        httpWebUrl = httpWebUrl,
                                        isHttpRunning = unifiedState.isHttpRunning,
                                        context = context
                                    )
                                }
                            }
                        } else {
                            HttpWebsiteServerCard(
                                unifiedState = unifiedState,
                                websiteTargetPath = websiteTargetPath,
                                httpWebUrl = httpWebUrl,
                                httpRequireAuth = httpRequireAuth,
                                httpAccessPin = httpAccessPin,
                                inputShape = inputShape,
                                context = context,
                                clipboardManager = clipboardManager,
                                onToggleHttp = { enable ->
                                    if (enable) ServerManager.startHttpServer(context)
                                    else ServerManager.stopHttpServer(context, graceful = true)
                                },
                                onRestartHttp = {
                                    ServerManager.restartHttpServer(context)
                                },
                                onSelectSource = { showWebsiteSourcePicker = true },
                                onClearSource = {
                                    websiteTargetPath = ""
                                    SettingsManager.setHttpWebsitePath(context, "")
                                },
                                onSecurityChange = { reqAuth, pin ->
                                    httpRequireAuth = reqAuth
                                    httpAccessPin = pin
                                    SettingsManager.setHttpWebsiteSecurity(context, reqAuth, pin)
                                }
                            )

                            LiveHostedWebsitePreviewCard(
                                websiteTargetPath = websiteTargetPath,
                                httpWebUrl = httpWebUrl,
                                isHttpRunning = unifiedState.isHttpRunning,
                                context = context
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
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
                    val qrTarget = when {
                        isWebsiteMode -> httpWebUrl
                        syncRoomState.isActive -> syncPlayWebUrl
                        unifiedState.isFtpRunning -> ftpUrl
                        else -> httpWebUrl
                    }
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
                        text = qrTarget,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (syncRoomState.isActive) {
                        Text(
                            text = "SyncPlay Room PIN: ${syncRoomState.passcode}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
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

/**
 * Independent FTP Server Card (`:2121`):
 * Can be hosted with or without a file/folder (Upload Dropzone Only, Selected Items, or Full Storage).
 */
@Composable
private fun FtpDedicatedServerCard(
    unifiedState: UnifiedServerState,
    ftpUrl: String,
    hostedMode: FtpHostedMode,
    portInput: String,
    isAnonymousAccess: Boolean,
    usernameInput: String,
    passwordInput: String,
    showPassword: Boolean,
    inputShape: RoundedCornerShape,
    context: Context,
    clipboardManager: ClipboardManager,
    onToggleFtp: (Boolean) -> Unit,
    onRestartFtp: () -> Unit,
    onScopeChange: (FtpHostedMode) -> Unit,
    onPickFilesOrFolders: () -> Unit,
    onPortChange: (String) -> Unit,
    onAnonChange: (Boolean) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
    onApplyNetwork: () -> Unit
) {
    val isFtpRunning = unifiedState.isFtpRunning
    val bucketItems = FtpBucketManager.bucketItems
    val isSelectedBucketMode = hostedMode == FtpHostedMode.BATCH_FILES
    var showAuthOptions by remember { mutableStateOf(false) }

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
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isFtpRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                    )
                    Column {
                        Text(
                            text = "FTP Server (:${unifiedState.ftp.port})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isFtpRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isFtpRunning) "Online • ${unifiedState.ftp.activeConnections} session(s)"
                            else "Host with or without files/folders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isFtpRunning) {
                        IconButton(onClick = onRestartFtp, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart FTP", modifier = Modifier.size(16.dp))
                        }
                    }
                    Switch(
                        checked = isFtpRunning,
                        onCheckedChange = onToggleFtp
                    )
                }
            }

            if (isFtpRunning) {
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
                        Text(
                            text = ftpUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(ftpUrl))
                                Toast.makeText(context, "Copied $ftpUrl", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy FTP URL", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Shared Scope Selection (Upload-Only Dropzone / Selected Files & Folders / Full Storage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FTP Shared Scope",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = { showAuthOptions = !showAuthOptions },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAuthOptions) "Hide Auth" else "Port & Auth", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = isSelectedBucketMode && bucketItems.isEmpty(),
                    onClick = {
                        FtpBucketManager.clearBucket(context)
                        onScopeChange(FtpHostedMode.BATCH_FILES)
                    },
                    label = { Text("Dropzone Only (No Files)", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = isSelectedBucketMode && bucketItems.isNotEmpty(),
                    onClick = { onScopeChange(FtpHostedMode.BATCH_FILES) },
                    label = { Text("Selected (${bucketItems.size})", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = hostedMode == FtpHostedMode.FULL_STORAGE,
                    onClick = { onScopeChange(FtpHostedMode.FULL_STORAGE) },
                    label = { Text("Full Storage", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(15.dp)) },
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
                        text = if (bucketItems.isEmpty()) "Isolated Upload Dropzone active (0 host files exposed)"
                        else "${bucketItems.size} item(s) shared",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (bucketItems.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    FtpBucketManager.clearBucket(context)
                                    if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
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
                                    if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                                },
                                label = { Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(13.dp))
                                }
                            )
                        }
                    }
                }
            }

            if (showAuthOptions) {
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("FTP Port", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = onPortChange,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = inputShape,
                                    modifier = Modifier.width(96.dp)
                                )
                                FilledTonalButton(
                                    onClick = onApplyNetwork,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Apply", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Read-Only Anonymous Login", style = MaterialTheme.typography.bodySmall)
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
        }
    }
}

/**
 * Dedicated HTTP Website Server Card (`:8080`) for Mode 1:
 * Hosts an unzipped website folder or single HTML file as a live website over the network.
 */
@Composable
private fun HttpWebsiteServerCard(
    unifiedState: UnifiedServerState,
    websiteTargetPath: String,
    httpWebUrl: String,
    httpRequireAuth: Boolean,
    httpAccessPin: String,
    inputShape: RoundedCornerShape,
    context: Context,
    clipboardManager: ClipboardManager,
    onToggleHttp: (Boolean) -> Unit,
    onRestartHttp: () -> Unit,
    onSelectSource: () -> Unit,
    onClearSource: () -> Unit,
    onSecurityChange: (Boolean, String) -> Unit
) {
    val isHttpRunning = unifiedState.isHttpRunning
    val selectedTarget = remember(websiteTargetPath) {
        if (websiteTargetPath.isNotBlank()) File(websiteTargetPath).takeIf { it.exists() } else null
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
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isHttpRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                    )
                    Column {
                        Text(
                            text = "HTTP Website Server (:${unifiedState.http.port})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isHttpRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHttpRunning) "Serving live website on LAN" else "Host an unzipped website folder or HTML file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isHttpRunning) {
                        IconButton(onClick = onRestartHttp, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart HTTP Server", modifier = Modifier.size(16.dp))
                        }
                    }
                    Switch(
                        checked = isHttpRunning,
                        onCheckedChange = onToggleHttp
                    )
                }
            }

            if (isHttpRunning) {
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
                        Text(
                            text = httpWebUrl,
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
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(httpWebUrl))
                                    Toast.makeText(context, "Copied $httpWebUrl", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Website URL", modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Website Source Target Box
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
                            text = "No website folder or HTML file selected yet.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Pick an unzipped website folder (with index.html, CSS, JS, images) or a single .html file.",
                            style = MaterialTheme.typography.labelSmall,
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
                                Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Text(
                            text = selectedTarget.absolutePath,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (detectedEntryHtml != null) "Entry Page: ${detectedEntryHtml.name} (Ready)"
                            else "No index.html found in folder",
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
                    Text("Select Website Folder / HTML", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Untrusted Wi-Fi Security Gate for HTTP Website Hosting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Require PIN on Untrusted Wi-Fi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Blocks unauthorized visitors until they enter the Host PIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = httpRequireAuth,
                    onCheckedChange = { onSecurityChange(it, httpAccessPin) }
                )
            }

            if (httpRequireAuth) {
                OutlinedTextField(
                    value = httpAccessPin,
                    onValueChange = { onSecurityChange(true, it) },
                    label = { Text("Website Access PIN") },
                    singleLine = true,
                    shape = inputShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Interactive In-App Live Website Preview Card for Mode 1 (HTTP Website Host).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveHostedWebsitePreviewCard(
    websiteTargetPath: String,
    httpWebUrl: String,
    isHttpRunning: Boolean,
    context: Context
) {
    val selectedTarget = remember(websiteTargetPath) {
        if (websiteTargetPath.isNotBlank()) File(websiteTargetPath).takeIf { it.exists() } else null
    }
    val localEntryHtml = remember(selectedTarget) {
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
    var reloadNonce by remember { mutableIntStateOf(0) }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Web, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Live Website Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { reloadNonce++ }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Preview", modifier = Modifier.size(16.dp))
                    }
                    if (isHttpRunning) {
                        IconButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpWebUrl)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in External Browser", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (localEntryHtml == null && !isHttpRunning) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select a website folder or HTML file to preview it live here and host it on your network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val targetUrl = remember(isHttpRunning, httpWebUrl, localEntryHtml, reloadNonce) {
                    when {
                        isHttpRunning -> "$httpWebUrl/?v=$reloadNonce"
                        localEntryHtml != null -> "file://${localEntryHtml.absolutePath}"
                        else -> "about:blank"
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    key(targetUrl, reloadNonce) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()
                                    loadUrl(targetUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Beatsync-inspired Synchronized Group Media Space Card (`SyncPlayPartyCard`).
 * Enforces mandatory room PIN to join ("Without password no join") and Host Main Control by default.
 */
@Composable
private fun SyncPlayPartyCard(
    syncState: SyncPlayRoomState,
    isHttpRunning: Boolean,
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
                            text = "SyncPlay Host Space",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (syncState.isActive) "${syncState.mediaFileName} • ${syncState.connectedPeers.size} peer(s)"
                            else "PIN-protected group watch & listen space",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            tint = Color(0xFF10B981),
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
                            text = if (syncState.isActive) "Change Media" else "Select Media",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Always-visible Mandatory Room PIN & Control Mode Banner
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                        Text(
                            text = "Room PIN: ${syncState.passcode}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (syncState.allowGuestControl) "Co-Control" else "Host Main Control",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(syncState.passcode))
                                Toast.makeText(context, "Copied Room PIN: ${syncState.passcode}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN", modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = {
                                val newPin = SyncPlayManager.generatePin()
                                passcodeInput = newPin
                                SyncPlayManager.updateRoomSecurity(newPin, allowGuestControl, leadTimeMs.toLong(), syncHostAudio)
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Generate New PIN", modifier = Modifier.size(14.dp))
                        }
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
                        OutlinedTextField(
                            value = passcodeInput,
                            onValueChange = {
                                passcodeInput = it
                                if (it.isNotBlank()) {
                                    SyncPlayManager.updateRoomSecurity(
                                        passcode = it,
                                        allowGuestControl = allowGuestControl,
                                        scheduleLeadTimeMs = leadTimeMs.toLong(),
                                        syncHostAudioPlayer = syncHostAudio
                                    )
                                }
                            },
                            label = { Text("Mandatory Room Passcode / PIN") },
                            singleLine = true,
                            shape = inputShape,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Guest Playback Control", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("Off = Host has exclusive main control", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                            Text("Sync Host Device Audio", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
                                onClick = { SyncPlayManager.stopSession(clearMediaHistory = true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Stop & Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                                        clipboardManager.setText(AnnotatedString("$syncPlayWebUrl (PIN: ${syncState.passcode})"))
                                        Toast.makeText(context, "Copied SyncPlay URL & PIN", Toast.LENGTH_SHORT).show()
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
private fun PortalCapabilitiesAndUploadsCard(
    allowUnifiedUploads: Boolean,
    uploadPasswordInput: String,
    showUploadPassword: Boolean,
    uploadFolderPath: String,
    allowRemoteMiniApps: Boolean,
    httpRequireAuth: Boolean,
    httpAccessPin: String,
    inputShape: RoundedCornerShape,
    onAllowUploadChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleShowPass: () -> Unit,
    onSelectUploadFolder: () -> Unit,
    onAllowMiniAppsChange: (Boolean) -> Unit,
    onHttpSecurityChange: (Boolean, String) -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "Secure Uploads & Mini-Apps Sandbox",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Authenticated File Uploads", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Blocks executable scripts & isolates uploads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                                "Isolated Upload Destination",
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
                    label = { Text("Upload Password (Required)") },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sandboxed Browser Mini-Apps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Confined to ContinuumSuite/MiniAppsSandbox", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = allowRemoteMiniApps, onCheckedChange = onAllowMiniAppsChange)
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Untrusted Wi-Fi PIN Firewall", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Require PIN before opening web portal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = httpRequireAuth,
                    onCheckedChange = { onHttpSecurityChange(it, httpAccessPin) }
                )
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
    httpStreamUrl: String,
    clipboardManager: ClipboardManager,
    context: Context,
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
                    Text(
                        text = if (isScreenCasting) "Broadcasting live (/screencast.mjpeg)" else "Independent MJPEG screen share",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isScreenCasting) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                if (isScreenCasting) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                            Text(
                                text = httpStreamUrl,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(httpStreamUrl))
                                    Toast.makeText(context, "Copied Stream URL", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Stream URL", modifier = Modifier.size(14.dp))
                            }
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
                        Text("JPEG Quality", style = MaterialTheme.typography.bodySmall)
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
