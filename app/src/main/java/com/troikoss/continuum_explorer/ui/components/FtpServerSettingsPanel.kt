package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.troikoss.continuum_explorer.managers.FtpBucketManager
import com.troikoss.continuum_explorer.managers.FtpHostedMode
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.server.ActiveHostingMode
import com.troikoss.continuum_explorer.server.ContinuumHubService
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSAdminConfigStore
import com.troikoss.continuum_explorer.server.omnios.OmniOSPairingManager
import com.troikoss.continuum_explorer.ui.components.hosting.HostingCastingTab
import com.troikoss.continuum_explorer.ui.components.hosting.HostingFtpTab
import com.troikoss.continuum_explorer.ui.components.hosting.HostingHttpTab
import com.troikoss.continuum_explorer.ui.components.hosting.OmniOSFullscreenSurface
import com.troikoss.continuum_explorer.ui.components.hosting.QrDisplayPopupDialog
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.utils.FileExplorerState
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private data class QrPopupPayload(
    val title: String,
    val qrContent: String,
    val displayAddress: String
)

/**
 * Ultra-Clean Tonal Flat Material 3 **Hosting** Hub Shell.
 *
 * Orchestrates three mutually exclusive hosting tabs:
 * - Tab 0: **FTP Hosting** (`HostingFtpTab`)
 * - Tab 1: **HTTP Hosting** (`HostingHttpTab`)
 * - Tab 2: **Casting** powered by OmniOS (`HostingCastingTab` + `HostingAdminInspector` + `OmniOSFullscreenSurface`)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpServerSettingsPanel(
    appState: FileExplorerState,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val cs = MaterialTheme.colorScheme

    val isFullScreen by SettingsManager.isFullScreenTabBarHidden
    val isHeaderPinned by SettingsManager.isSubpageHeaderPinned

    val unifiedState by ServerManager.state.collectAsState()
    val syncRoomState by SyncPlayManager.roomState.collectAsState()
    val activeReceiverSession by OmniOSPairingManager.activeReceiverSession.collectAsState()

    var builtInScreenUrl by remember { mutableStateOf<String?>(null) }

    // Full-bleed DeX-style OmniOS surface takes over the entire viewport for Built-In Screen or Receiver Mode
    if (activeReceiverSession != null) {
        val rx = activeReceiverSession!!
        OmniOSFullscreenSurface(
            url = rx.castUrl,
            sessionLabel = rx.hostDeviceName,
            onExit = { OmniOSPairingManager.disconnectReceiverSession() },
            modifier = modifier
        )
        return
    }
    if (builtInScreenUrl != null) {
        OmniOSFullscreenSurface(
            url = builtInScreenUrl!!,
            sessionLabel = "OmniOS",
            onExit = { builtInScreenUrl = null },
            modifier = modifier
        )
        return
    }

    LaunchedEffect(Unit) {
        FtpBucketManager.initialize(context)
        OmniOSAdminConfigStore.init(context)
        ServerManager.refreshIpAddress()
    }

    // Initial tab follows active exclusive mode if already running
    var selectedTab by remember {
        mutableIntStateOf(
            when (unifiedState.activeHostingMode) {
                ActiveHostingMode.HTTP -> 1
                ActiveHostingMode.CASTING -> 2
                else -> 0
            }
        )
    }

    // Persisted FTP & HTTP settings
    val savedPort by SettingsManager.ftpPort
    val savedAnonymous by SettingsManager.isFtpAnonymousAllowed
    val savedUsername by SettingsManager.ftpUsername
    val savedPassword by SettingsManager.ftpPassword
    val savedHostedMode by SettingsManager.ftpHostedMode
    val savedHostedPath by SettingsManager.ftpHostedPath
    val savedUploadPass by SettingsManager.ftpUploadPassword
    val savedAllowUpload by SettingsManager.ftpAllowUpload
    val savedUploadFolder by SettingsManager.ftpUploadFolderPath
    val savedHttpWebsitePath by SettingsManager.httpWebsitePath
    val savedHttpRequireAuth by SettingsManager.httpWebsiteRequireAuth
    val savedHttpPin by SettingsManager.httpWebsiteAccessPin

    var portInput by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var isAnonymousAccess by remember(savedAnonymous) { mutableStateOf(savedAnonymous) }
    var usernameInput by remember(savedUsername) { mutableStateOf(savedUsername) }
    var passwordInput by remember(savedPassword) { mutableStateOf(savedPassword) }
    var uploadPasswordInput by remember(savedUploadPass) { mutableStateOf(savedUploadPass) }
    var allowUploads by remember(savedAllowUpload) { mutableStateOf(savedAllowUpload) }
    var uploadFolderPath by remember(savedUploadFolder) {
        mutableStateOf(
            savedUploadFolder.ifBlank {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
        )
    }
    var hostedMode by remember(savedHostedMode) { mutableStateOf(savedHostedMode) }
    var customFolderPath by remember(savedHostedPath) { mutableStateOf(savedHostedPath) }
    var websiteTargetPath by remember(savedHttpWebsitePath) { mutableStateOf(savedHttpWebsitePath) }
    var httpRequireAuth by remember(savedHttpRequireAuth) { mutableStateOf(savedHttpRequireAuth) }
    var httpAccessPin by remember(savedHttpPin) { mutableStateOf(savedHttpPin) }

    // Live uptime & SyncPlay position ticker
    var uptimeFormatted by remember { mutableStateOf("00:00:00") }
    var liveSyncPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(unifiedState.isAnyRunning, unifiedState.hubStartedAtMillis, syncRoomState.isActive, syncRoomState.isPlaying) {
        if (!unifiedState.isAnyRunning && !syncRoomState.isActive) {
            uptimeFormatted = "00:00:00"
            return@LaunchedEffect
        }
        while (unifiedState.isAnyRunning || syncRoomState.isActive) {
            if (unifiedState.isAnyRunning && unifiedState.hubStartedAtMillis > 0L) {
                val elapsed = ((System.currentTimeMillis() - unifiedState.hubStartedAtMillis) / 1000L).coerceAtLeast(0L)
                uptimeFormatted = String.format(Locale.US, "%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
            } else {
                uptimeFormatted = "00:00:00"
            }
            if (syncRoomState.isActive) {
                liveSyncPosMs = syncRoomState.currentPositionMs()
            }
            delay(if (syncRoomState.isActive && syncRoomState.isPlaying) 500L else 1000L)
        }
    }

    // Modals & Pickers
    var qrPopup by remember { mutableStateOf<QrPopupPayload?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showFtpSharedItemsPicker by remember { mutableStateOf(false) }
    var showWebsiteSourcePicker by remember { mutableStateOf(false) }
    var showUploadFolderPicker by remember { mutableStateOf(false) }
    var showSyncPlayMediaPicker by remember { mutableStateOf(false) }
    var syncPlayFromCasting by remember { mutableStateOf(false) }
    var showCastAddFilePicker by remember { mutableStateOf(false) }
    var showCastAddFolderPicker by remember { mutableStateOf(false) }

    val serverIp = unifiedState.ipAddress
    val ftpPort = unifiedState.ftp.port.takeIf { it > 0 } ?: 2121
    val httpPort = unifiedState.http.port.takeIf { it > 0 } ?: ServerManager.DEFAULT_HTTP_PORT
    val castPort = unifiedState.casting.port.takeIf { it > 0 } ?: ServerManager.DEFAULT_CASTING_PORT

    val ftpUrl = remember(serverIp, ftpPort, isAnonymousAccess, usernameInput, passwordInput) {
        if (!isAnonymousAccess && usernameInput.isNotBlank()) {
            "ftp://$usernameInput:$passwordInput@$serverIp:$ftpPort"
        } else {
            "ftp://$serverIp:$ftpPort"
        }
    }
    val httpWebUrl = remember(serverIp, httpPort) { "http://$serverIp:$httpPort" }
    val syncPlayWebUrl = remember(httpWebUrl) { "$httpWebUrl/?tab=syncplay" }
    val castWebUrl = remember(serverIp, castPort) { "http://$serverIp:$castPort" }

    fun saveFtpSettings(reloadFtpIfActive: Boolean = false) {
        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 2121
        SettingsManager.setFtpPort(context, p)
        SettingsManager.setFtpAnonymousAllowed(context, isAnonymousAccess)
        SettingsManager.setFtpCredentials(context, usernameInput, passwordInput)
        SettingsManager.setFtpUploadPassword(context, uploadPasswordInput)
        SettingsManager.setFtpAllowUpload(context, allowUploads)
        SettingsManager.setFtpUploadFolderPath(context, uploadFolderPath)
        SettingsManager.setFtpHostedMode(context, hostedMode, customFolderPath)
        if (reloadFtpIfActive && unifiedState.isFtpRunning) {
            ServerManager.restartFtpServer(context)
        }
    }

    // --- FULL-PAGE & MODAL FILE PICKERS ---
    if (showFtpSharedItemsPicker) {
        FullPageFilePicker(
            title = "Select Shared Files",
            isHtmlMode = false,
            onDismiss = {
                showFtpSharedItemsPicker = false
                if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
            },
            onHtmlSelected = { showFtpSharedItemsPicker = false }
        )
        return
    }

    if (showWebsiteSourcePicker) {
        FullPageFilePicker(
            title = "Select Website Folder or HTML",
            isHtmlMode = true,
            onDismiss = { showWebsiteSourcePicker = false },
            onHtmlSelected = { selected ->
                websiteTargetPath = selected.absolutePath
                SettingsManager.setHttpWebsitePath(context, selected.absolutePath)
                showWebsiteSourcePicker = false
                if (!unifiedState.isHttpRunning) ServerManager.startHttpServer(context)
                else ServerManager.restartHttpServer(context)
            }
        )
        return
    }

    if (showUploadFolderPicker) {
        InAppFilePickerDialog(
            title = "Upload Folder",
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

    if (showSyncPlayMediaPicker) {
        InAppFilePickerDialog(
            title = "Select Media",
            selectFolders = false,
            allowedExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus"),
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showSyncPlayMediaPicker = false },
            onSelected = { file ->
                showSyncPlayMediaPicker = false
                if (syncPlayFromCasting) {
                    if (!unifiedState.isCastingRunning) {
                        ServerManager.startCastingServer(context, castPort)
                    }
                    SyncPlayManager.loadMediaFile(
                        context = context,
                        file = file,
                        passcode = syncRoomState.passcode,
                        allowGuestControl = syncRoomState.allowGuestControl,
                        autoStartServer = false
                    )
                } else {
                    SyncPlayManager.loadMediaFile(
                        context = context,
                        file = file,
                        passcode = syncRoomState.passcode,
                        allowGuestControl = syncRoomState.allowGuestControl,
                        autoStartServer = true
                    )
                }
                Toast.makeText(context, "SyncPlay loaded: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCastAddFilePicker) {
        InAppFilePickerDialog(
            title = "Share Files for Download",
            selectFolders = false,
            allowMultiSelect = true,
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showCastAddFilePicker = false },
            onSelected = { file ->
                OmniOSAdminConfigStore.addSharedPath(file.absolutePath)
                showCastAddFilePicker = false
            },
            onMultipleSelected = { files ->
                files.forEach { OmniOSAdminConfigStore.addSharedPath(it.absolutePath) }
                showCastAddFilePicker = false
            }
        )
    }

    if (showCastAddFolderPicker) {
        InAppFilePickerDialog(
            title = "Share Folder for Download",
            selectFolders = true,
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showCastAddFolderPicker = false },
            onSelected = { folder ->
                OmniOSAdminConfigStore.addSharedPath(folder.absolutePath)
                showCastAddFolderPicker = false
            }
        )
    }

    @Composable
    fun HostingSegmentedTabs(modifier: Modifier = Modifier) {
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = { Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("hosting_tab_ftp")
            ) {
                Text("FTP Hosting", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("hosting_tab_http")
            ) {
                Text("HTTP Hosting", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SegmentedButton(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = { Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("hosting_tab_casting")
            ) {
                Text("Casting", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    fun HostingTopBar(isWide: Boolean) {
        SingleRowCommandBar(
            navigationContent = {
                val showMenuIcon = appState.getScreenSize() == ScreenSize.SMALL ||
                    SettingsManager.isWideSidebarCollapsed.value
                if (showMenuIcon) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("hosting_menu_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Navigation Sidebar")
                    }
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            },
            titleContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (unifiedState.isAnyRunning) cs.primary else cs.outlineVariant)
                    )
                    Text(
                        text = "Hosting",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (unifiedState.isAnyRunning) {
                        Text(
                            text = uptimeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary
                        )
                    }
                }
            },
            centerContent = {
                if (isWide) {
                    HostingSegmentedTabs(modifier = Modifier.widthIn(max = 460.dp))
                }
            },
            actionsContent = {
                IconButton(
                    onClick = { SettingsManager.toggleSubpageHeaderPinned(context) },
                    modifier = Modifier.testTag("hosting_pin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (isHeaderPinned) "Unpin Header" else "Pin Header",
                        tint = if (isHeaderPinned) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showLogsDialog = true },
                    modifier = Modifier.testTag("hosting_logs_button")
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = "Activity Logs", tint = cs.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        qrPopup = when (selectedTab) {
                            1 -> QrPopupPayload("HTTP Website QR", httpWebUrl, httpWebUrl)
                            2 -> QrPopupPayload("Host QR", OmniOSPairingManager.buildHostQrPayload(castPort), castWebUrl)
                            else -> if (syncRoomState.isActive) {
                                QrPopupPayload("SyncPlay QR", syncPlayWebUrl, syncPlayWebUrl)
                            } else {
                                QrPopupPayload("FTP QR", ftpUrl, ftpUrl)
                            }
                        }
                    },
                    modifier = Modifier.testTag("hosting_quick_qr_button")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = "Quick QR", tint = cs.primary)
                }
                IconButton(
                    onClick = { SettingsManager.toggleFullScreenTabBar() },
                    modifier = Modifier.testTag("hosting_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit Fullscreen" else "Fullscreen"
                    )
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(cs.surface)
    ) {
        val isWideScreen = maxWidth >= 740.dp

        Column(modifier = Modifier.fillMaxSize()) {
            if (isHeaderPinned) {
                HostingTopBar(isWide = isWideScreen)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (!isHeaderPinned) {
                    HostingTopBar(isWide = isWideScreen)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 1320.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isWideScreen) {
                        HostingSegmentedTabs(modifier = Modifier.fillMaxWidth())
                    }

                    if (unifiedState.lastGlobalError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cs.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = unifiedState.lastGlobalError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> HostingFtpTab(
                            isWideScreen = isWideScreen,
                            unifiedState = unifiedState,
                            syncState = syncRoomState,
                            liveSyncPosMs = liveSyncPosMs,
                            ftpUrl = ftpUrl,
                            syncPlayWebUrl = syncPlayWebUrl,
                            hostedMode = hostedMode,
                            portInput = portInput,
                            isAnonymousAccess = isAnonymousAccess,
                            usernameInput = usernameInput,
                            passwordInput = passwordInput,
                            allowUploads = allowUploads,
                            uploadPasswordInput = uploadPasswordInput,
                            uploadFolderPath = uploadFolderPath,
                            onToggleFtp = { enable ->
                                saveFtpSettings(reloadFtpIfActive = false)
                                if (enable) ServerManager.startFtpServer(context)
                                else ServerManager.stopFtpServer(context, graceful = true)
                            },
                            onRestartFtp = {
                                saveFtpSettings(reloadFtpIfActive = false)
                                ServerManager.restartFtpServer(context)
                            },
                            onShowQr = { title, content, label ->
                                qrPopup = QrPopupPayload(title, content, label)
                            },
                            onScopeChange = { mode ->
                                hostedMode = mode
                                SettingsManager.setFtpHostedMode(context, mode, customFolderPath)
                                if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                            },
                            onPickSharedItems = {
                                hostedMode = FtpHostedMode.BATCH_FILES
                                SettingsManager.setFtpHostedMode(context, FtpHostedMode.BATCH_FILES, customFolderPath)
                                showFtpSharedItemsPicker = true
                            },
                            onPortChange = { portInput = it },
                            onAnonymousChange = { isAnonymousAccess = it },
                            onUsernameChange = { usernameInput = it },
                            onPasswordChange = { passwordInput = it },
                            onApplyNetwork = {
                                saveFtpSettings(reloadFtpIfActive = true)
                                Toast.makeText(context, "FTP settings applied", Toast.LENGTH_SHORT).show()
                            },
                            onAllowUploadsChange = {
                                allowUploads = it
                                SettingsManager.setFtpAllowUpload(context, it)
                                if (unifiedState.isFtpRunning) ServerManager.restartFtpServer(context)
                            },
                            onUploadPasswordChange = {
                                uploadPasswordInput = it
                                SettingsManager.setFtpUploadPassword(context, it)
                            },
                            onSelectUploadFolder = { showUploadFolderPicker = true },
                            onPickSyncPlayMedia = {
                                syncPlayFromCasting = false
                                showSyncPlayMediaPicker = true
                            }
                        )

                        1 -> HostingHttpTab(
                            isWideScreen = isWideScreen,
                            unifiedState = unifiedState,
                            websiteTargetPath = websiteTargetPath,
                            httpWebUrl = httpWebUrl,
                            httpRequireAuth = httpRequireAuth,
                            httpAccessPin = httpAccessPin,
                            onToggleHttp = { enable ->
                                if (enable) ServerManager.startHttpServer(context)
                                else ServerManager.stopHttpServer(context, graceful = true)
                            },
                            onRestartHttp = { ServerManager.restartHttpServer(context) },
                            onShowQr = { title, content, label ->
                                qrPopup = QrPopupPayload(title, content, label)
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

                        else -> HostingCastingTab(
                            isWideScreen = isWideScreen,
                            unifiedState = unifiedState,
                            syncState = syncRoomState,
                            liveSyncPosMs = liveSyncPosMs,
                            onShowQr = { title, qrContent, displayAddress ->
                                qrPopup = QrPopupPayload(title, qrContent, displayAddress)
                            },
                            onLaunchBuiltInScreen = { url ->
                                builtInScreenUrl = url
                            },
                            onAddSharedFile = { showCastAddFilePicker = true },
                            onAddSharedFolder = { showCastAddFolderPicker = true },
                            onPickSyncPlayMedia = {
                                syncPlayFromCasting = true
                                showSyncPlayMediaPicker = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Reusable Tonal QR Popup Dialog
    qrPopup?.let { payload ->
        QrDisplayPopupDialog(
            title = payload.title,
            qrContent = payload.qrContent,
            displayAddress = payload.displayAddress,
            onDismiss = { qrPopup = null }
        )
    }

    // Tonal Activity & Clients Logs Modal
    if (showLogsDialog) {
        HostingLogsDialog(onDismiss = { showLogsDialog = false })
    }
}

@Composable
private fun HostingLogsDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clientSessions = ContinuumHubService.clientSessions.values.toList()
    val logEntries = ContinuumHubService.logEntries.toList()
    var selectedLogTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 500.dp),
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (logEntries.isNotEmpty() || clientSessions.isNotEmpty()) {
                            TextButton(
                                onClick = { ContinuumHubService.clearLogs() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("Clear", color = cs.error)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close logs")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedLogTab == 0,
                        onClick = { selectedLogTab = 0 },
                        label = { Text("Clients (${clientSessions.size})") }
                    )
                    FilterChip(
                        selected = selectedLogTab == 1,
                        onClick = { selectedLogTab = 1 },
                        label = { Text("Logs (${logEntries.size})") }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 380.dp)
                ) {
                    if (selectedLogTab == 0) {
                        if (clientSessions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No connected clients", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(clientSessions.size) { idx ->
                                    val session = clientSessions[idx]
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cs.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(session.clientIp, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "${session.requestsCount} req • ${session.bytesTransferred / 1024} KB",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cs.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = session.protocol,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = cs.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (logEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No activity yet", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(logEntries.size) { idx ->
                                    val log = logEntries[idx]
                                    Text(
                                        text = "[${log.protocol}] ${log.clientIp}: ${log.action} ${log.details}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = cs.onSurface
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
