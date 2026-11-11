package com.troikoss.continuum_explorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.selectionBackground
import com.troikoss.continuum_explorer.model.*
import com.troikoss.continuum_explorer.ui.components.ItemContextMenu
import com.troikoss.continuum_explorer.utils.*
import com.troikoss.continuum_explorer.utils.IconHelper.FileThumbnail
import com.troikoss.continuum_explorer.utils.IconHelper.FolderPreview
import com.troikoss.continuum_explorer.utils.IconHelper.isMimeTypePreviewable
import androidx.compose.ui.res.stringResource
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.utils.IconHelper.FolderIcon

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Renders a single file or folder item, switching layout based on the current ViewMode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileView(
    file: UniversalFile,
    itemPositions: MutableMap<UniversalFile, Rect>,
    containerCoordinates: LayoutCoordinates?,
    mousePosition: () -> Offset?,
    appState: FileExplorerState,
    focusRequester: FocusRequester,
    isHovered: Boolean,
    hScrollState: ScrollState? = null,
) {
    // Selection
    val selectionManager = appState.selectionManager
    val isSelected = selectionManager.isSelected(file)
    val isLead = selectionManager.leadItem == file

    // Local Hover State for fast 60fps scrolling
    var isItemHovered by remember(file) { mutableStateOf(false) }
    val effectiveHovered = isHovered || isItemHovered

    LaunchedEffect(isItemHovered) {
        if (isItemHovered && SettingsManager.isHoverPreviewEnabled.value && !file.isDirectory) {
            if (appState.previewFile != file) {
                appState.showPreview(file, fullScreen = false)
            }
        }
    }

    // Context Menu
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    val density = LocalDensity.current

    // Lifecycle & Positioning
    DisposableEffect(file) {
        onDispose { itemPositions.remove(file) }
    }


    val mouseTooltipProvider = remember(containerCoordinates) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val mousePos = mousePosition()
                if (mousePos == null || containerCoordinates == null || !containerCoordinates.isAttached) {
                    return IntOffset(
                        x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                        y = anchorBounds.bottom
                    )
                }

                val windowMousePos = containerCoordinates.localToWindow(mousePos)
                return IntOffset(
                    x = windowMousePos.x.toInt() + 15,
                    y = windowMousePos.y.toInt() + 15
                )
            }
        }
    }

    // Layout Configuration
    val viewMode = appState.activeViewMode
    val isGridView = viewMode == ViewMode.GRID || viewMode == ViewMode.GALLERY
    val shape = if (isGridView) RoundedCornerShape(10.dp) else RectangleShape

    Box {
            Surface(
                color = Color.Transparent,
                shape = shape,
                modifier = Modifier
                    .then(if (viewMode != ViewMode.DETAILS) Modifier.fillMaxWidth() else Modifier)
                    .pointerInput(file) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.type == PointerType.Mouse }) {
                                    when (event.type) {
                                        PointerEventType.Enter, PointerEventType.Move -> {
                                            if (!isItemHovered) isItemHovered = true
                                        }
                                        PointerEventType.Exit -> {
                                            if (isItemHovered) isItemHovered = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .fileDragSource(
                        file = file,
                        selectionManager = selectionManager,
                        appState = appState,
                        onShowContextMenu = { offset ->
                            menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                            showMenu = true
                        },
                        onDismissContextMenu = { showMenu = false }
                    )
                    .then(
                        if (file.isDirectory) {
                            val isRemote = file.provider.capabilities.isRemote
                            Modifier.fileDropTarget(
                                appState = appState,
                                destPath = file.fileRef,
                                destSafUri = file.documentFileRef?.uri,
                                destNetworkProvider = if (isRemote) file.provider else null,
                                destNetworkId = if (isRemote) file.providerId else null
                            )
                        } else Modifier
                    )
                    .trackPosition(file, itemPositions, containerCoordinates)
                    .itemGestures(
                        file = file,
                        selectionManager = selectionManager,
                        focusRequester = focusRequester,
                        appState = appState
                    )
                    .contextMenuDetector(enableLongPress = true, aggressive = false) { offset ->
                        if (!selectionManager.isSelected(file)) {
                            selectionManager.handleRowClick(file,
                                isShiftPressed = false,
                                isCtrlPressed = false
                            )
                        }
                        menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                        showMenu = true
                    }
            ) {
                when (viewMode) {
                    ViewMode.GRID -> FileGridView(file, isSelected, effectiveHovered, isLead, appState, mouseTooltipProvider)
                    ViewMode.GALLERY -> FileGalleryView(file, isSelected, effectiveHovered, isLead, appState, mouseTooltipProvider)
                    ViewMode.CONTENT -> FileContentView(file, isSelected, effectiveHovered, isLead, appState, mouseTooltipProvider)
                    ViewMode.DETAILS -> FileDetailsView(file, isSelected, effectiveHovered, isLead, appState, hScrollState, mouseTooltipProvider)
                }
            }

        // Context Menu
        Box(modifier = Modifier.offset(menuOffset.x, menuOffset.y)) {
            ItemContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                appState = appState
            )
        }
    }
}

// --- Specific View Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileGalleryView(
    file: UniversalFile,
    isSelected: Boolean,
    isHovered: Boolean,
    isLead: Boolean,
    appState: FileExplorerState,
    toolTipProvider: PopupPositionProvider
) {
    val tooltipState = rememberTooltipState()
    var isOverflowing by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(10.dp)
    val isPreviewableMedia = !file.isDirectory && isMimeTypePreviewable(file) &&
        !file.name.lowercase().endsWith(".pdf") && !file.name.lowercase().endsWith(".apk") && !file.name.lowercase().endsWith(".txt")

    TooltipBox(
        positionProvider = toolTipProvider,
        tooltip = { if (isOverflowing) PlainTooltip { Text(file.name) } },
        state = tooltipState
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, tileShape),
            contentAlignment = Alignment.Center
        ) {
            IconHelper.ItemIcon(
                file = file,
                isSelected = isSelected,
                contentSize = (appState.folderConfigs.gridItemSize * 0.65f).dp,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fillBounds = isPreviewableMedia
            )

            if (!isPreviewableMedia || file.isDirectory) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f))
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (appState.renamingFile == file) {
                            InlineRenameField(
                                file = file,
                                appState = appState,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, textAlign = TextAlign.Center)
                            )
                        } else {
                            Text(
                                text = file.name,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.renameOnClick(file, appState.selectionManager, appState),
                                onTextLayout = { textLayoutResult ->
                                    isOverflowing = textLayoutResult.hasVisualOverflow
                                }
                            )
                        }
                    }
                }
            } else {
                isOverflowing = true
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .selectionBackground(isSelected, isHovered, isLead, hoverAlpha = false),
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileGridView(
    file: UniversalFile,
    isSelected: Boolean,
    isHovered: Boolean,
    isLead: Boolean,
    appState: FileExplorerState,
    toolTipProvider: PopupPositionProvider
) {
    val tooltipState = rememberTooltipState()
    var isOverflowing by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(10.dp)
    val isPreviewableMedia = !file.isDirectory && isMimeTypePreviewable(file) &&
        !file.name.lowercase().endsWith(".pdf") && !file.name.lowercase().endsWith(".apk") && !file.name.lowercase().endsWith(".txt")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), shape)
            .selectionBackground(isSelected, isHovered, isLead, shape = shape)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val contentSize = (appState.folderConfigs.gridItemSize * 0.56f).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.18f)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            IconHelper.ItemIcon(
                file = file,
                isSelected = isSelected,
                contentSize = contentSize,
                contentScale = if (isPreviewableMedia) ContentScale.Crop else ContentScale.Fit,
                fillBounds = isPreviewableMedia,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (appState.renamingFile == file) {
            InlineRenameField(
                file = file,
                appState = appState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center)
            )
        } else {
            TooltipBox(
                positionProvider = toolTipProvider,
                tooltip = { if (isOverflowing) PlainTooltip { Text(file.name) } },
                state = tooltipState
            ) {
                Text(
                    text = file.name,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.renameOnClick(file, appState.selectionManager, appState),
                    onTextLayout = { textLayoutResult ->
                        isOverflowing = textLayoutResult.hasVisualOverflow
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileContentView(
    file: UniversalFile,
    isSelected: Boolean,
    isHovered: Boolean,
    isLead: Boolean,
    appState: FileExplorerState,
    toolTipProvider: PopupPositionProvider
) {
    val formattedSize = remember(file) { appState.formatSize(file.length) }
    val formattedDate = remember(file) { appState.formatDate(file.lastModified) }

    val tooltipState = rememberTooltipState()
    var isOverflowing by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(8.dp)

    val iconSelectionEnabled = SettingsManager.iconTouchSelection.value

    Column (modifier = Modifier.selectionBackground(isSelected, isHovered, isLead, shape = shape)) {
        ListItem(
            headlineContent = {
                if (appState.renamingFile == file) {
                    InlineRenameField(
                        file = file,
                        appState = appState,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TooltipBox(
                        positionProvider = toolTipProvider,
                        tooltip = { if (isOverflowing) PlainTooltip { Text(file.name) } },
                        state = tooltipState
                    ) {
                        Text(
                            text = file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.renameOnClick(file, appState.selectionManager, appState),
                            onTextLayout = { textLayoutResult ->
                                isOverflowing = textLayoutResult.hasVisualOverflow
                            }
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    if (file.isDirectory) stringResource(R.string.folder_prefix, formattedDate)
                    else stringResource(R.string.file_size_date, formattedSize, formattedDate)
                )
            },
            leadingContent = {
                Box(modifier = Modifier.then(if (iconSelectionEnabled) Modifier.iconTouchToggle(file, appState.selectionManager) else Modifier)) {
                    IconHelper.ItemIcon(
                        file = file,
                        isSelected = isSelected,
                        contentSize = 40.dp,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailsView(
    file: UniversalFile,
    isSelected: Boolean,
    isHovered: Boolean,
    isLead: Boolean,
    appState: FileExplorerState,
    hScrollState: ScrollState?,
    toolTipProvider: PopupPositionProvider
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val nameColumnWidth = appState.folderConfigs.columnWidths.getOrElse(FileColumnType.NAME) { 210.dp }

    val tooltipState = rememberTooltipState()
    var isOverflowing by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(8.dp)

    val iconSelectionEnabled = SettingsManager.iconTouchSelection.value

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hScrollState != null) Modifier.horizontalScroll(hScrollState) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .selectionBackground(isSelected, isHovered, isLead, shape = shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.then(if (iconSelectionEnabled) Modifier.iconTouchToggle(file, appState.selectionManager) else Modifier)) {
                    IconHelper.ItemIcon(
                        file = file,
                        isSelected = isSelected,
                        contentSize = 24.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (appState.renamingFile == file) {
                    InlineRenameField(
                        file = file,
                        appState = appState,
                        modifier = Modifier.width(nameColumnWidth)
                    )
                } else {
                    TooltipBox(
                        positionProvider = toolTipProvider,
                        tooltip = { if (isOverflowing) PlainTooltip { Text(file.name) } },
                        state = tooltipState
                    ) {
                        Text(
                            text = file.name,
                            modifier = Modifier.width(nameColumnWidth).renameOnClick(file, appState.selectionManager, appState),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayoutResult ->
                                isOverflowing = textLayoutResult.hasVisualOverflow
                            }
                        )
                    }
                }

                appState.folderConfigs.visibleColumns.forEach { column ->
                    val width = appState.folderConfigs.columnWidths[column.type] ?: column.minWidth
                    val uuidKey = file.fileRef?.parentFile?.name
                    val meta = uuidKey?.let { appState.recycleBinMetadata[it] }
                    val text = when (column.type) {
                        FileColumnType.DATE -> remember(file.lastModified) { appState.formatDate(file.lastModified) }
                        FileColumnType.SIZE -> if (file.isDirectory) {
                            val dirCountText by produceState(initialValue = "--", key1 = file.providerId, key2 = file.lastModified) {
                                value = withContext(Dispatchers.IO) {
                                    file.fileRef?.list()?.size?.let { n ->
                                        if (n == 1) resources.getString(R.string.details_item_count_singular)
                                        else resources.getString(R.string.details_item_count_plural, n)
                                    } ?: "--"
                                }
                            }
                            dirCountText
                        } else {
                            remember(file.length) { appState.formatSize(file.length) }
                        }
                        FileColumnType.TYPE -> remember(file.name, file.isDirectory) { getFileType(file, context) }
                        FileColumnType.DATE_DELETED -> meta?.deletedAt?.let { appState.formatDate(it) } ?: "--"
                        FileColumnType.DELETED_FROM -> meta?.deletedFrom ?: "--"
                        else -> ""
                    }

                    Spacer(Modifier.width(1.dp))
                    Text(
                        text = text,
                        modifier = Modifier.width(width).padding(start = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
