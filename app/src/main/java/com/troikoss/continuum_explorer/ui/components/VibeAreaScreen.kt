package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.data.db.SuiteRecentRecord
import com.troikoss.continuum_explorer.domain.logic.FileOpenPolicyEngine
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.rules.MiniAppRuleDescriptor
import com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController
import com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewPopupHost
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import com.troikoss.continuum_explorer.ui.components.suite.hoverPreviewTarget
import com.troikoss.continuum_explorer.ui.components.suite.rememberHoverPreviewController
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.open
import com.troikoss.continuum_explorer.utils.openInEdit
import com.troikoss.continuum_explorer.utils.openInFileConverter
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File
import kotlin.math.roundToInt

data class EditorTool(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val icon: ImageVector,
    val color: Color
)

private enum class SuiteRecentFilter(val label: String) {
    ALL("All"),
    DOCS("Docs"),
    CODE("Code"),
    TABLES("Tables"),
    PHOTOS("Photos")
}

/**
 * Desktop-First Ultra-Clean Tonal Flat "My Suite" Workspace.
 * - Zero card outlines; uses pure Material 3 surface container tonal hierarchy.
 * - Single 48.dp Command Bar (no decorative title icon, icon-only New/Open on narrow windows).
 * - Balanced 5-tile Studio Dock on desktop (single row) and 2-column grid on mobile.
 * - Interactive 2-second mouse hover preview over file names (with touch long-press fallback).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VibeAreaScreen(
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val hoverController = rememberHoverPreviewController()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showOpenFilePicker by remember { mutableStateOf(false) }
    var showTargetDirPicker by remember { mutableStateOf(false) }
    var showMobileSearch by remember { mutableStateOf(false) }

    var newFileNameInput by remember { mutableStateOf("notes.md") }
    var targetDirectory by remember {
        mutableStateOf(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ContinuumSuite"
            ).apply { if (!exists()) mkdirs() }.takeIf { it.exists() }
                ?: Environment.getExternalStorageDirectory()
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(SuiteRecentFilter.ALL) }
    var isGridView by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden
    val isWideSidebarCollapsed by SettingsManager.isWideSidebarCollapsed

    LaunchedEffect(Unit) {
        SuiteRecentDatabase.init(context)
    }

    val activeMiniApps = remember { MiniAppRegistry.activeMiniApps }

    val filteredRecents = remember(SuiteRecentDatabase.records.size, searchQuery, activeFilter) {
        val allValid = SuiteRecentDatabase.getValidUniversalFiles(context)
        allValid.filter { (rec, _) ->
            val matchesFilter = when (activeFilter) {
                SuiteRecentFilter.ALL -> true
                SuiteRecentFilter.DOCS -> rec.editorToolId == "text_editor" || rec.extension in setOf("md", "txt", "org", "adoc")
                SuiteRecentFilter.CODE -> rec.editorToolId == "code_studio" || rec.extension in setOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "sql", "sh")
                SuiteRecentFilter.TABLES -> rec.editorToolId == "csv_editor" || rec.extension in setOf("csv", "tsv")
                SuiteRecentFilter.PHOTOS -> rec.editorToolId == "image_studio" || rec.extension in setOf("jpg", "jpeg", "png", "webp", "bmp")
            }
            val matchesQuery = searchQuery.isBlank() ||
                rec.fileName.contains(searchQuery.trim(), ignoreCase = true) ||
                rec.extension.contains(searchQuery.trim(), ignoreCase = true) ||
                rec.editorToolName.contains(searchQuery.trim(), ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    fun openFileDirectlyInEditor(file: File, activityType: SuiteActivityType = SuiteActivityType.EDITED) {
        if (!file.exists() || file.isDirectory) return
        val uFile = file.toUniversal()
        if (!FileOpenPolicyEngine.isEditable(uFile)) {
            Toast.makeText(context, "\"${file.name}\" opened in viewer.", Toast.LENGTH_SHORT).show()
            appState?.open(uFile)
            return
        }
        SuiteRecentDatabase.recordFileActivity(
            context = context,
            file = file,
            activityType = activityType
        )
        appState?.openInEdit(uFile)
    }

    fun handleMiniAppClick(descriptor: MiniAppRuleDescriptor) {
        if (descriptor.id == "file_converter") {
            appState?.openInFileConverter(emptyList())
            return
        }
        if (appState != null) {
            appState.activeEditorToolId = descriptor.id
            appState.activeEditorToolTitle = descriptor.title
        } else {
            showOpenFilePicker = true
        }
    }

    if (showOpenFilePicker) {
        InAppFilePickerDialog(
            title = "Open File in Suite",
            selectFolders = false,
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showOpenFilePicker = false },
            onSelected = { selectedFile ->
                showOpenFilePicker = false
                openFileDirectlyInEditor(selectedFile, SuiteActivityType.EDITED)
            }
        )
    }

    if (showTargetDirPicker) {
        InAppFilePickerDialog(
            title = "Choose Save Folder",
            selectFolders = true,
            initialDirectory = targetDirectory,
            onDismiss = { showTargetDirPicker = false },
            onSelected = { folder ->
                showTargetDirPicker = false
                if (folder.isDirectory) targetDirectory = folder
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text("File name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("notes.md", "todo.txt", "data.csv", "index.html", "script.py", "Main.kt").forEach { preset ->
                            Surface(
                                onClick = { newFileNameInput = preset },
                                shape = RoundedCornerShape(50),
                                color = if (newFileNameInput == preset) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = { showTargetDirPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = targetDirectory.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = newFileNameInput.trim().ifBlank { "notes.md" }
                        runCatching {
                            if (!targetDirectory.exists()) targetDirectory.mkdirs()
                            val outFile = File(targetDirectory, cleanName)
                            if (!outFile.exists()) outFile.createNewFile()
                            showCreateFileDialog = false
                            openFileDirectlyInEditor(outFile, SuiteActivityType.CREATED)
                        }.onFailure {
                            Toast.makeText(context, "Could not create file: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val rootWidth = maxWidth
        val isNarrow = rootWidth < 560.dp
        val isDesktop = rootWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar (zero decorative icon before title)
            SingleRowCommandBar(
                navigationContent = {
                    if (isNarrow || isWideSidebarCollapsed) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("suite_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                titleContent = {
                    Text(
                        text = "My Suite",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                centerContent = {
                    if (!isNarrow) {
                        TonalSearchPill(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search suite files",
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 360.dp),
                            testTagName = "suite_search_input"
                        )
                    }
                },
                actionsContent = {
                    if (isNarrow) {
                        IconButton(
                            onClick = { showMobileSearch = !showMobileSearch },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showMobileSearch || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("suite_new_file_button")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = { showOpenFilePicker = true },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("suite_open_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open File", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showCreateFileDialog = true },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("suite_new_file_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            onClick = { showOpenFilePicker = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("suite_open_file_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Open", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Switch View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { SettingsManager.toggleFullScreenTabBar() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            // Mobile search input only when toggled on narrow windows
            if (isNarrow && showMobileSearch) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    TonalSearchPill(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search suite files",
                        modifier = Modifier.fillMaxWidth(),
                        testTagName = "suite_search_input"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = if (isDesktop) 24.dp else 14.dp,
                    vertical = if (isDesktop) 20.dp else 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Balanced Studio Bento Dock (Single 5-column horizontal row on desktop; 2-column grid on mobile)
                item(key = "studio_dock") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Studios",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isDesktop) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                activeMiniApps.forEach { app ->
                                    TonalStudioTile(
                                        descriptor = app,
                                        onClick = { handleMiniAppClick(app) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                activeMiniApps.chunked(2).forEach { rowApps ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowApps.forEach { app ->
                                            TonalStudioTile(
                                                descriptor = app,
                                                onClick = { handleMiniAppClick(app) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowApps.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Recent Workspace Section Header + Filter Pills
                item(key = "recent_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuiteRecentFilter.entries.forEach { filter ->
                                val selected = activeFilter == filter
                                Surface(
                                    onClick = { activeFilter = filter },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recent Files Table / Grid with 2-second Hover Preview
                if (filteredRecents.isEmpty()) {
                    item(key = "empty_recents") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching files" else "No recent files",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (isGridView) {
                    val cols = when {
                        rootWidth >= 1040.dp -> 4
                        rootWidth >= 720.dp -> 3
                        else -> 2
                    }
                    val chunked = filteredRecents.chunked(cols)
                    items(chunked.size, key = { "grid_row_$it" }) { rowIdx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            chunked[rowIdx].forEach { (record, uFile) ->
                                SuiteRecentGridTile(
                                    record = record,
                                    uFile = uFile,
                                    hoverController = hoverController,
                                    onClick = {
                                        hoverController.dismiss()
                                        uFile.fileRef?.let { openFileDirectlyInEditor(it) }
                                    },
                                    onRemove = { SuiteRecentDatabase.removeRecord(context, record.absolutePath) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - chunked[rowIdx].size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // Clean Desktop Table Header
                    if (isDesktop) {
                        item(key = "table_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(124.dp))
                                Text("Modified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                                Text("Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(74.dp))
                                Spacer(modifier = Modifier.width(36.dp))
                            }
                        }
                    }
                    items(filteredRecents, key = { it.first.absolutePath }) { (record, uFile) ->
                        SuiteRecentTableRow(
                            record = record,
                            uFile = uFile,
                            isDesktop = isDesktop,
                            hoverController = hoverController,
                            onClick = {
                                hoverController.dismiss()
                                uFile.fileRef?.let { openFileDirectlyInEditor(it) }
                            },
                            onRemove = { SuiteRecentDatabase.removeRecord(context, record.absolutePath) }
                        )
                    }
                }
            }
        }

        // Floating 2-second hover preview host over hovered file names
        HoverPreviewPopupHost(controller = hoverController)
    }
}

@Composable
private fun TonalStudioTile(
    descriptor: MiniAppRuleDescriptor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .height(92.dp)
            .testTag("studio_tile_${descriptor.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(descriptor.accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = descriptor.icon,
                    contentDescription = descriptor.title,
                    tint = descriptor.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = shortStudioLabel(descriptor),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun shortStudioLabel(descriptor: MiniAppRuleDescriptor): String = when (descriptor.id) {
    "text_editor" -> "Markdown"
    "code_studio" -> "Code"
    "csv_editor" -> "Tables"
    "image_studio" -> "Photo"
    "file_converter" -> "Converter"
    else -> descriptor.title
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuiteRecentTableRow(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    isDesktop: Boolean,
    hoverController: HoverPreviewController,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sizeText = remember(uFile.length) { Formatter.formatFileSize(context, uFile.length) }
    val timeText = remember(record.timestamp) {
        DateUtils.getRelativeTimeSpanString(record.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowCoords = it }
            .hoverPreviewTarget(uFile, hoverController)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val pos = rowCoords?.positionInWindow()
                    hoverController.showImmediately(
                        uFile,
                        IntOffset((pos?.x ?: 24f).roundToInt(), (pos?.y ?: 120f).roundToInt())
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IconHelper.getIconForItem(uFile),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = record.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isDesktop) {
                Text(
                    text = record.editorToolName.substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(124.dp)
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(74.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuiteRecentGridTile(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    hoverController: HoverPreviewController,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tileCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sizeText = remember(uFile.length) { Formatter.formatFileSize(context, uFile.length) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .height(104.dp)
            .onGloballyPositioned { tileCoords = it }
            .hoverPreviewTarget(uFile, hoverController)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val pos = tileCoords?.positionInWindow()
                    hoverController.showImmediately(
                        uFile,
                        IntOffset((pos?.x ?: 24f).roundToInt(), (pos?.y ?: 120f).roundToInt())
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = IconHelper.getIconForItem(uFile),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.extension.uppercase()} • $sizeText",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
