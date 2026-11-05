package com.troikoss.continuum_explorer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.troikoss.continuum_explorer.managers.DetailsMode
import com.troikoss.continuum_explorer.managers.FileOperationsManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.NavSection
import com.troikoss.continuum_explorer.model.NetworkConnection
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.ui.activities.PopUpActivity
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.providers.StorageProviders
import com.troikoss.continuum_explorer.ui.components.*
import com.troikoss.continuum_explorer.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * The main layout container for the File Explorer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorer(
    initialPath: String? = null,
    initialUri: String? = null,
    initialArchive: File? = null,
    initialArchiveUri: Uri? = null,
    initialLibraryItem: LibraryItem = LibraryItem.None,
    initialNetworkConnectionId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Tab Management ---
    val tabs = remember { mutableStateListOf<FileExplorerState>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    fun createNewTabState(ctx: Context, scp: CoroutineScope): FileExplorerState {
        return FileExplorerState(ctx, scp).apply {
            onOpenInNewTab = { item ->
                val newState = createNewTabState(ctx, scp)
                val tabFileRef = item.fileRef
                val tabDocRef = item.documentFileRef
                when {
                    item.providerId.startsWith("editor://") -> {
                        newState.activeEditorToolId = item.providerId.removePrefix("editor://")
                        newState.activeEditorToolTitle = item.name
                        newState.libraryItem = LibraryItem.VibeArea
                    }
                    item.provider.capabilities.isRemote -> newState.navigateTo(
                        null, null, addToHistory = false,
                        networkProvider = item.provider, networkId = item.providerId,
                        networkConnectionId = item.provider.connectionId.ifEmpty { null }
                    )
                    item.absolutePath == "recent_suites://" -> {
                        newState.activeEditorToolTitle = "Recent Suites"
                        newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.Recent)
                    }
                    item.absolutePath == "recent://" -> newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.Recent)
                    item.absolutePath == "gallery://" -> newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.Gallery)
                    item.absolutePath == "vibe_area://" -> newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.VibeArea)
                    item.absolutePath == "ftp_server://" -> newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.FtpServer)
                    item.absolutePath == "external_ftp://" -> newState.navigateTo(null, null, addToHistory = false, libraryItem = LibraryItem.ExternalFtp)
                    item.absolutePath == "trash://" -> {
                        val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                        if (!trashDir.exists()) trashDir.mkdirs()
                        newState.navigateTo(trashDir, null, addToHistory = false, libraryItem = LibraryItem.RecycleBin)
                    }
                    ZipUtils.isArchive(item) && tabFileRef != null && SettingsManager.isDefaultArchiveViewerEnabled.value -> newState.navigateTo(
                        newPath = null,
                        newUri = null,
                        addToHistory = false,
                        archiveFile = tabFileRef,
                        archivePath = ""
                    )
                    tabFileRef != null -> {
                        if (item.isDirectory) {
                            newState.navigateTo(tabFileRef, null, addToHistory = false)
                        } else {
                            newState.viewingFile = item
                        }
                    }
                    tabDocRef != null -> {
                        if (item.isDirectory) {
                            newState.navigateTo(null, tabDocRef.uri, addToHistory = false)
                        } else {
                            newState.viewingFile = item
                        }
                    }
                    !item.isDirectory -> {
                        newState.viewingFile = item
                    }
                }
                tabs.add(newState)
                selectedTabIndex = tabs.size - 1
            }
        }
    }

    // Initialize first tab if empty
    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            val firstState = createNewTabState(context, scope)
            when {
                initialArchive != null -> firstState.navigateTo(null, null, addToHistory = false, archiveFile = initialArchive, archivePath = "")
                initialArchiveUri != null -> firstState.navigateTo(null, null, addToHistory = false, archiveUri = initialArchiveUri, archivePath = "")
                initialLibraryItem == LibraryItem.RecycleBin -> {
                    val trashDir = File(Environment.getExternalStorageDirectory(), ".Trash")
                    if (!trashDir.exists()) trashDir.mkdirs()
                    firstState.navigateTo(trashDir, null, addToHistory = false, libraryItem = LibraryItem.RecycleBin)
                }
                initialNetworkConnectionId != null -> {
                    val conn = firstState.appConfigs.networkConnections.find { it.id == initialNetworkConnectionId }
                    if (conn != null) {
                        val provider = StorageProviders.network(conn)
                        firstState.navigateTo(null, null, addToHistory = false, networkProvider = provider, networkId = provider.rootId(), networkConnectionId = conn.id)
                    }
                }
                initialLibraryItem != LibraryItem.None -> firstState.navigateTo(null, null, addToHistory = false, libraryItem = initialLibraryItem)
                initialPath != null -> firstState.navigateTo(File(initialPath), null, addToHistory = false)
                initialUri != null -> firstState.navigateTo(null, Uri.parse(initialUri), addToHistory = false)
            }
            tabs.add(firstState)
        }
    }

    if (tabs.isEmpty()) return // Wait for initialization

    val safeIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val appState = tabs[safeIndex]
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val screenSize = appState.getScreenSize()

    // --- Storage Access Framework Launcher ---
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        appState.handleSafResult(uri)
    }

    // --- Network Storage Launcher ---
    val networkScope = rememberCoroutineScope()
    val onAddNetwork: () -> Unit = {
        networkScope.launch {
            val result = FileOperationsManager.requestNetworkConnection()
            if (result != null) {
                appState.appConfigs.addNetworkConnection(result)
            }
        }
        context.startActivity(Intent(context, PopUpActivity::class.java))
    }
    val onEditNetwork: (NetworkConnection) -> Unit = { existing ->
        networkScope.launch {
            val result = FileOperationsManager.requestNetworkConnection(existing)
            if (result != null) {
                appState.appConfigs.updateNetworkConnection(result)
            }
        }
        context.startActivity(Intent(context, PopUpActivity::class.java))
    }

    // --- Side Effects ---
    LaunchedEffect(appState.currentPath, appState.currentSafUri, appState.folderConfigs.sortParams,
                   appState.currentArchiveFile, appState.currentArchiveUri, appState.currentArchivePath, appState.libraryItem,
                   appState.currentNetworkProvider, appState.currentNetworkId) {
        if (appState.files.isEmpty() || appState.loadedPathKey == "initial") {
            appState.triggerLoad()
        }
    }

    BackHandler(enabled = drawerState.isOpen || appState.viewingFile != null || appState.isVideoFullScreen || appState.isPreviewFullScreen || (appState.isPreviewPaneVisible && screenSize == ScreenSize.SMALL) || appState.selectionManager.selectedItems.isNotEmpty() || appState.backStack.isNotEmpty() || appState.canGoUp) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (appState.isVideoFullScreen) {
            appState.isVideoFullScreen = false
        } else if (appState.openInEditMode) {
            appState.openInEditMode = false
        } else if (appState.viewingFile != null) {
            if (appState.isPreviewFullScreen) {
                appState.isPreviewFullScreen = false
            } else if (tabs.size > 1) {
                tabs.removeAt(safeIndex)
                selectedTabIndex = (safeIndex - 1).coerceAtLeast(0)
            } else {
                appState.viewingFile = null
            }
        } else if (appState.isPreviewFullScreen) {
            appState.isPreviewFullScreen = false
        } else if (appState.selectionManager.selectedItems.isNotEmpty()) {
            appState.selectionManager.clear()
        } else if (appState.isPreviewPaneVisible && screenSize == ScreenSize.SMALL) {
            appState.closePreview()
        } else if (appState.backStack.isNotEmpty()) {
            appState.goBack()
        } else if (appState.canGoUp) {
            appState.goUp()
        }
    }

    val isViewingMediaOrPdf = appState.viewingFile != null ||
        appState.isPreviewFullScreen ||
        appState.isPreviewPaneVisible ||
        (appState.previewFile != null && (
            appState.previewFile!!.name.lowercase().endsWith(".pdf") ||
            com.troikoss.continuum_explorer.utils.IconHelper.isMimeTypePreviewable(appState.previewFile!!)
        ))

    // --- Main Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val shift = event.keyboardModifiers.isShiftPressed
                        if (appState.isShiftPressed != shift) appState.isShiftPressed = shift
                        val activeChange = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                        if (activeChange != null) {
                            val isMouse = (activeChange.type == PointerType.Mouse)
                            if (appState.isMouseInteraction != isMouse) appState.isMouseInteraction = isMouse
                        }
                    }
                }
            }
            .fileDropTarget(appState)
    ) {
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

        LaunchedEffect(drawerState.isOpen, drawerState.targetValue) {
            if (drawerState.isOpen || drawerState.targetValue == DrawerValue.Open) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = (appState.getScreenSize() == ScreenSize.SMALL) && !isViewingMediaOrPdf && (SettingsManager.isSidebarSwipeEnabled.value || drawerState.isOpen),
            drawerContent = {
                ModalDrawerSheet {
                    NavigationContent(
                        appState = appState,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        onAddStorage = { safLauncher.launch(null) },
                        onAddNetwork = onAddNetwork,
                        onEditNetwork = onEditNetwork
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    ExplorerTopBar(
                        tabs = tabs,
                        selectedTabIndex = safeIndex,
                        onTabSelected = { selectedTabIndex = it },
                        onAddTab = {
                            tabs.add(createNewTabState(context, scope))
                            selectedTabIndex = tabs.size - 1
                        },
                        onCloseTab = { index ->
                            if (tabs.size > 1) {
                                tabs.removeAt(index)
                                if (selectedTabIndex >= tabs.size) {
                                    selectedTabIndex = (tabs.size - 1).coerceAtLeast(0)
                                }
                            }
                        },
                        onMenuClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            scope.launch { drawerState.open() }
                        },
                        appState = appState
                    )
                }
            ) { innerPadding ->
                ExplorerBody(
                    modifier = Modifier.padding(innerPadding),
                    appState = appState,
                    onAddStorage = { safLauncher.launch(null) },
                    onAddNetwork = onAddNetwork,
                    onEditNetwork = onEditNetwork,
                    onMenuClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        scope.launch { drawerState.open() }
                    }
                )
            }
        }

        // Batch Rename Dialog
        if (appState.showBatchRenameDialog) {
            com.troikoss.continuum_explorer.ui.components.BatchRenameDialog(
                selectedFiles = appState.selectionManager.selectedItems.toList(),
                onDismiss = { appState.showBatchRenameDialog = false },
                onSuccess = {
                    appState.showBatchRenameDialog = false
                    appState.selectionManager.clear()
                }
            )
        }

        // Full Screen Preview Overlay
        if (appState.isPreviewPaneVisible && appState.isPreviewFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF121212))
                    .statusBarsPadding()
            ) {
                WorkspacePreviewPane(appState = appState)
            }
        }
    }
}

@Composable
private fun NavigationContent(
    appState: FileExplorerState,
    onCloseDrawer: () -> Unit,
    onAddStorage: () -> Unit,
    onAddNetwork: () -> Unit = {},
    onEditNetwork: (NetworkConnection) -> Unit = {}
) {
    val context = LocalContext.current
    NavigationPane(
        appState = appState,
        onItemSelected = { section ->
            navigateToSection(appState, context, section)
            onCloseDrawer()
        },
        onSafItemSelected = { uri ->
            appState.navigateTo(null, uri)
            onCloseDrawer()
        },
        onAddStorageClick = onAddStorage,
        onAddNetworkClick = onAddNetwork,
        onEditNetworkClick = onEditNetwork,
        onNavigate = onCloseDrawer
    )
}

@Composable
private fun ExplorerTopBar(
    tabs: List<FileExplorerState>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Int) -> Unit,
    onMenuClick: () -> Unit,
    appState: FileExplorerState
) {
    if (appState.isVideoFullScreen) return

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        TabBar(
            tabs = tabs.map { it.currentName },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            onAddTab = onAddTab,
            onCloseTab = onCloseTab,
            onMenuClick = onMenuClick,
            appState = appState
        )

        val isSpecialPageOrEditor = appState.libraryItem == LibraryItem.FtpServer ||
                appState.libraryItem == LibraryItem.VibeArea ||
                appState.libraryItem == LibraryItem.ExternalFtp ||
                appState.viewingFile != null ||
                appState.isPreviewPaneVisible ||
                appState.isPreviewFullScreen

        if (!isSpecialPageOrEditor) {
            TopBar(
                onMenuClick = onMenuClick,
                appState = appState
            )
            if (SettingsManager.isCommandBarVisible.value) {
                CommandBar(appState = appState)
            }
        }
    }
}

@Composable
private fun ExplorerBody(
    modifier: Modifier = Modifier,
    appState: FileExplorerState,
    onAddStorage: () -> Unit,
    onAddNetwork: () -> Unit = {},
    onEditNetwork: (NetworkConnection) -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val screenSize = appState.getScreenSize()
    val navPaneWidth = appState.appConfigs.navPaneWidth
    val detailsPaneWidth = appState.appConfigs.detailsPaneWidth

    val isSpecialPage = appState.libraryItem == LibraryItem.FtpServer ||
            appState.libraryItem == LibraryItem.VibeArea ||
            appState.libraryItem == LibraryItem.ExternalFtp

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (isSpecialPage || appState.viewingFile != null) 0.dp else 8.dp,
                    end = if (isSpecialPage || appState.viewingFile != null) 0.dp else 8.dp,
                    bottom = if (appState.viewingFile != null) 0.dp else 8.dp,
                    top = 0.dp
                )
        ) {
            // Navigation Pane (Side)
            if (appState.viewingFile == null && screenSize != ScreenSize.SMALL) {
                Box(modifier = Modifier.fillMaxHeight().padding(end = 8.dp)) {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(navPaneWidth).fillMaxHeight(),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        drawerShape = RoundedCornerShape(16.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        NavigationPane(
                            appState = appState,
                            onItemSelected = { section ->
                                navigateToSection(
                                    appState,
                                    context,
                                    section
                                )
                            },
                            onSafItemSelected = { appState.navigateTo(null, it) },
                            onAddStorageClick = onAddStorage,
                            onAddNetworkClick = onAddNetwork,
                            onEditNetworkClick = onEditNetwork
                        )
                    }
                }
                VerticalResizeHandle(
                    showDivider = false,
                    onResize = { delta ->
                        appState.appConfigs.navPaneWidth = (appState.appConfigs.navPaneWidth + delta).coerceIn(200.dp, 300.dp)
                        appState.appConfigs.savePaneWidths()
                    },
                )
            }

            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                when {
                    appState.viewingFile != null -> {
                        com.troikoss.continuum_explorer.ui.components.ViewOnlyFileViewer(
                            file = appState.viewingFile!!,
                            appState = appState,
                            onMenuClick = onMenuClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    appState.libraryItem == LibraryItem.FtpServer -> com.troikoss.continuum_explorer.ui.components.FtpServerSettingsPanel(appState = appState, onMenuClick = onMenuClick)
                    appState.libraryItem == LibraryItem.VibeArea -> {
                        if (appState.activeEditorToolId != null) {
                            com.troikoss.continuum_explorer.ui.editor.EditorContainer(
                                editorToolId = appState.activeEditorToolId,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            com.troikoss.continuum_explorer.ui.components.VibeAreaScreen(appState = appState, onMenuClick = onMenuClick)
                        }
                    }
                    appState.libraryItem == LibraryItem.ExternalFtp -> com.troikoss.continuum_explorer.ui.components.ExternalFtpViewerPanel(onMenuClick = onMenuClick)
                    else -> FileContent(appState = appState)
                }
            }

            // Preview Area (ViewAreaWindow on the right side)
            if (appState.viewingFile == null && appState.isPreviewPaneVisible && !appState.isPreviewFullScreen) {
                VerticalResizeHandle(
                    showDivider = false,
                    onResize = { delta ->
                        appState.previewPaneWidth =
                            (appState.previewPaneWidth - delta).coerceIn(280.dp, 800.dp)
                    }
                )
                Box(
                    modifier = Modifier
                        .width(appState.previewPaneWidth)
                        .padding(start = 8.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    WorkspacePreviewPane(appState = appState)
                }
            }

            // Details Pane
            if (appState.viewingFile == null && !appState.isPreviewPaneVisible && screenSize == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.PANE) {
                VerticalResizeHandle(
                    showDivider = false,
                    onResize = { delta ->
                        appState.appConfigs.detailsPaneWidth =
                            (appState.appConfigs.detailsPaneWidth - delta).coerceIn(200.dp, 300.dp)
                        appState.appConfigs.savePaneWidths()
                    }
                )
                DetailsPane(
                    appState = appState,
                    modifier = Modifier.width(detailsPaneWidth).padding(start = 8.dp).fillMaxHeight()
                )
            }
        }

        // Details Bar (Bottom)
        if (appState.viewingFile == null && screenSize == ScreenSize.LARGE && SettingsManager.detailsMode.value == DetailsMode.BAR) {
            HorizontalDivider()
            DetailsBar(appState = appState)
        }
    }
}

private fun navigateToSection(appState: FileExplorerState, context: Context, section: NavSection) {
    val internalRoot = Environment.getExternalStorageDirectory()
    when (section) {
        is NavSection.InternalStorage -> appState.navigateTo(internalRoot, null, newRoot = internalRoot)
        is NavSection.RecycleBin -> {
            val trashDir = File(internalRoot, ".Trash")
            if (!trashDir.exists()) trashDir.mkdirs()
            appState.navigateTo(trashDir, null, newRoot = internalRoot, libraryItem = LibraryItem.RecycleBin)
        }
        is NavSection.Recent -> appState.navigateTo(null, null, libraryItem = LibraryItem.Recent)
        is NavSection.Gallery -> appState.navigateTo(null, null, libraryItem = LibraryItem.Gallery)
        is NavSection.VibeArea -> {
            appState.onOpenInNewTab?.invoke(
                UniversalFile(name = "My suite", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "vibe_area://")
            ) ?: appState.navigateTo(null, null, libraryItem = LibraryItem.VibeArea)
        }
        is NavSection.FtpServer -> {
            appState.onOpenInNewTab?.invoke(
                UniversalFile(name = "FTP/HTTP Server", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "ftp_server://")
            ) ?: appState.navigateTo(null, null, libraryItem = LibraryItem.FtpServer)
        }
        is NavSection.ExternalFtp -> {
            appState.onOpenInNewTab?.invoke(
                UniversalFile(name = "External FTP", isDirectory = true, lastModified = 0, length = 0, provider = LocalProvider, providerId = "external_ftp://")
            ) ?: appState.navigateTo(null, null, libraryItem = LibraryItem.ExternalFtp)
        }
        is NavSection.BookmarkFolder -> appState.navigateTo(File(section.path), null)
        is NavSection.NetworkStorage -> {
            val conn = appState.appConfigs.networkConnections.find { it.id == section.connectionId } ?: return
            val provider = StorageProviders.network(conn)
            appState.navigateTo(
                newPath = null, newUri = null,
                networkProvider = provider, networkId = provider.rootId(),
                networkConnectionId = conn.id,
                addToHistory = true,
            )
        }
        is NavSection.RemovableVolume -> {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = storageManager.storageVolumes
            val volumeIndex = section.volumeIndex

            if (volumeIndex < volumes.size) {
                val volume = volumes[volumeIndex]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.let { appState.navigateTo(it, null, newRoot = it) }
                } else {
                    val externalDirs = context.getExternalFilesDirs(null)
                    if (volumeIndex < externalDirs.size) {
                        externalDirs[volumeIndex]?.let { dir ->
                            val root = File(dir.absolutePath.split("/Android")[0])
                            appState.navigateTo(root, null, newRoot = root)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspacePreviewPane(
    appState: FileExplorerState,
    modifier: Modifier = Modifier
) {
    val previewFile = appState.previewFile

    if (previewFile != null) {
        FilePreviewer(
            file = previewFile,
            appState = appState,
            isFullScreen = appState.isPreviewFullScreen,
            onFullScreenToggle = { full ->
                appState.isPreviewFullScreen = full
            },
            onClose = {
                appState.closePreview()
            },
            modifier = modifier
        )
    } else {
        FilePreviewerPlaceholder(
            onClose = { appState.closePreview() },
            modifier = modifier
        )
    }
}
