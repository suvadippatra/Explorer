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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.data.db.SuiteRecentRecord
import com.troikoss.continuum_explorer.domain.logic.FileOpenPolicyEngine
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.rules.MiniAppRuleDescriptor
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

data class EditorTool(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * Ultra-Clean Tonal Flat Material 3 Desktop & Mobile "My Suite" Workspace.
 * - Single 48dp command bar (no subtitle, no decorative title icon)
 * - Balanced 5-tile horizontal Studio Bento Dock on desktop
 * - Borderless filterable Recent Table / Grid with 2-second desktop hover preview
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
    var showOverflowMenu by remember { mutableStateOf(false) }

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
    var selectedFilterId by remember { mutableStateOf("all") }
    var isGridView by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden
    val isWideSidebarCollapsed by SettingsManager.isWideSidebarCollapsed

    LaunchedEffect(Unit) {
        SuiteRecentDatabase.init(context)
    }

    val activeMiniApps = remember { MiniAppRegistry.activeMiniApps }

    val suiteRecentPairs = remember(SuiteRecentDatabase.records.size, searchQuery, selectedFilterId) {
        val allValid = SuiteRecentDatabase.getValidUniversalFiles(context)
        allValid.filter { (rec, _) ->
            val matchesFilter = when (selectedFilterId) {
                "all" -> true
                "text_editor" -> rec.editorToolId == "text_editor" || rec.extension in setOf("md", "markdown", "txt", "org", "adoc")
                "code_studio" -> rec.editorToolId == "code_studio" || rec.extension in setOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "yaml", "sh", "sql")
                "csv_editor" -> rec.editorToolId == "csv_editor" || rec.extension in setOf("csv", "tsv")
                "image_studio" -> rec.editorToolId == "image_studio" || rec.extension in setOf("jpg", "jpeg", "png", "webp", "bmp")
                else -> true
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                rec.fileName.contains(q, ignoreCase = true) ||
                rec.extension.contains(q, ignoreCase = true) ||
                rec.editorToolName.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    fun openFileDirectlyInEditor(file: File, activityType: SuiteActivityType = SuiteActivityType.EDITED) {
        if (!file.exists() || file.isDirectory) return
        hoverController.dismiss()
        val uFile = file.toUniversal()
        if (!FileOpenPolicyEngine.isEditable(uFile)) {
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
        hoverController.dismiss()
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
            title = "Open File",
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
            title = "Save Folder",
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
        val cleanName = newFileNameInput.trim()
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text("File Name") },
                        placeholder = { Text("notes.md") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(".md", ".txt", ".csv", "todo.txt", ".json", ".py", ".kt", ".html").forEach { presetExt ->
                            val isTodo = presetExt == "todo.txt"
                            val isSelected = if (isTodo) {
                                cleanName.equals("todo.txt", ignoreCase = true)
                            } else {
                                cleanName.endsWith(presetExt, ignoreCase = true)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    newFileNameInput = if (isTodo) {
                                        "todo.txt"
                                    } else {
                                        val base = newFileNameInput.substringBeforeLast('.', newFileNameInput).ifBlank { "untitled" }
                                        "$base$presetExt"
                                    }
                                },
                                label = { Text(presetExt, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showTargetDirPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = targetDirectory.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Folder",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val safeName = cleanName.ifBlank { "untitled.md" }
                    try {
                        if (!targetDirectory.exists()) targetDirectory.mkdirs()
                        val outFile = File(targetDirectory, safeName)
                        if (!outFile.exists()) {
                            outFile.createNewFile()
                            val ext = outFile.extension.lowercase()
                            val seed = when {
                                safeName.endsWith("todo.txt", ignoreCase = true) -> "(A) New task +Project\n"
                                ext == "csv" -> "Column 1,Column 2,Column 3\nItem 1,Value,100\n"
                                ext == "md" -> "# ${outFile.nameWithoutExtension}\n\n"
                                ext == "html" -> "<!DOCTYPE html>\n<html>\n<head><title>${outFile.nameWithoutExtension}</title></head>\n<body>\n<h1>Hello</h1>\n</body>\n</html>\n"
                                else -> ""
                            }
                            if (seed.isNotEmpty()) outFile.writeText(seed)
                        }
                        showCreateFileDialog = false
                        openFileDirectlyInEditor(outFile, SuiteActivityType.CREATED)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Create failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Create") }
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
        val containerWidth = maxWidth
        val isDesktop = containerWidth >= 680.dp
        val isNarrow = containerWidth < 560.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Unified 48dp Single-Row Command Bar
            SingleRowCommandBar(
                modifier = Modifier.testTag("suite_home_command_bar"),
                navigationContent = {
                    if (isNarrow || isWideSidebarCollapsed) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("suite_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Sidebar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                titleContent = {
                    Text(
                        text = "My Suite",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                centerContent = {
                    if (!isNarrow) {
                        TonalSearchPill(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search recent files",
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                            testTagName = "suite_search_input"
                        )
                    }
                },
                actionsContent = {
                    if (isNarrow) {
                        IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("suite_new_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New File",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { showOpenFilePicker = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("suite_open_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open File",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showCreateFileDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("suite_new_file_button"),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        TextButton(
                            onClick = { showOpenFilePicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("suite_open_file_button"),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isGridView) "Table View" else "Grid View") },
                                leadingIcon = {
                                    Icon(
                                        if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    isGridView = !isGridView
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isFullScreen) "Show Tab Bar" else "Fullscreen") },
                                leadingIcon = {
                                    Icon(
                                        if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    SettingsManager.toggleFullScreenTabBar()
                                }
                            )
                        }
                    }
                }
            )

            // Main Workspace Scroll Surface
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = if (isDesktop) 24.dp else 14.dp,
                    vertical = if (isDesktop) 18.dp else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Mobile search pill inside body on narrow windows
                if (isNarrow) {
                    item {
                        TonalSearchPill(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search recent files",
                            modifier = Modifier.fillMaxWidth(),
                            testTagName = "suite_search_input_mobile"
                        )
                    }
                }

                // Balanced 5-Tile Studio Bento Dock
                item {
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                activeMiniApps.forEach { app ->
                                    TonalStudioTile(
                                        descriptor = app,
                                        onClick = { handleMiniAppClick(app) },
                                        modifier = Modifier.width(126.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Recent Files Section Header + Filter Chips + View Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            val filters = listOf(
                                "all" to "All",
                                "text_editor" to "Markdown",
                                "code_studio" to "Code",
                                "csv_editor" to "Table",
                                "image_studio" to "Photo"
                            )
                            filters.forEach { (id, label) ->
                                val selected = selectedFilterId == id
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { selectedFilterId = id }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("suite_recent_view_toggle")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Table View" else "Grid View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Recent Files Content (Empty / Grid / Dense Desktop Table)
                if (suiteRecentPairs.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 22.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching files" else "No recent files yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { showCreateFileDialog = true },
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New File")
                                }
                            }
                        }
                    }
                } else if (isGridView) {
                    val cols = if (containerWidth >= 960.dp) 4 else if (containerWidth >= 620.dp) 3 else 2
                    val rows = suiteRecentPairs.chunked(cols)
                    itemsIndexed(rows) { _, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { (rec, uFile) ->
                                val fileRef = uFile.fileRef ?: File(rec.absolutePath)
                                SuiteRecentGridTile(
                                    record = rec,
                                    uFile = uFile,
                                    fileRef = fileRef,
                                    hoverController = hoverController,
                                    onClick = { openFileDirectlyInEditor(fileRef, SuiteActivityType.EDITED) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // Dense Tonal Table Header on Desktop
                    if (isDesktop) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Name",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.46f)
                                )
                                Text(
                                    text = "Studio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.22f)
                                )
                                Text(
                                    text = "Size",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.14f)
                                )
                                Text(
                                    text = "Modified",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.18f)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = suiteRecentPairs,
                        key = { _, pair -> pair.first.absolutePath }
                    ) { index, (rec, uFile) ->
                        val fileRef = uFile.fileRef ?: File(rec.absolutePath)
                        SuiteRecentTableRow(
                            record = rec,
                            uFile = uFile,
                            fileRef = fileRef,
                            isDesktop = isDesktop,
                            isAlternate = index % 2 == 1,
                            hoverController = hoverController,
                            onClick = { openFileDirectlyInEditor(fileRef, SuiteActivityType.EDITED) }
                        )
                    }
                }
            }
        }

        // 2-Second Hover Preview Overlay
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("studio_tile_${descriptor.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = descriptor.accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = descriptor.icon,
                        contentDescription = descriptor.title,
                        tint = descriptor.accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Text(
                text = descriptor.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuiteRecentTableRow(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    fileRef: File,
    isDesktop: Boolean,
    isAlternate: Boolean,
    hoverController: com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var rowBounds by remember { mutableStateOf<Rect?>(null) }

    val rowBg = if (isAlternate) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surface

    val modifiedText = remember(record.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            record.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    val sizeText = remember(fileRef.length()) {
        Formatter.formatFileSize(context, fileRef.length())
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowBounds = it.boundsInRoot() }
            .hoverPreviewTarget(fileRef, hoverController)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { hoverController.showImmediately(fileRef, rowBounds) }
            )
            .testTag("suite_recent_row_${record.fileName}")
    ) {
        if (isDesktop) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(0.46f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(22.dp)) {
                        IconHelper.FileThumbnail(file = uFile, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(modifier = Modifier.weight(0.22f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = record.editorToolName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(0.14f)
                )

                Text(
                    text = modifiedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.18f)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(28.dp)) {
                    IconHelper.FileThumbnail(file = uFile, modifier = Modifier.fillMaxSize())
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$sizeText • $modifiedText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuiteRecentGridTile(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    fileRef: File,
    hoverController: com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tileBounds by remember { mutableStateOf<Rect?>(null) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .onGloballyPositioned { tileBounds = it.boundsInRoot() }
            .hoverPreviewTarget(fileRef, hoverController)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { hoverController.showImmediately(fileRef, tileBounds) }
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(44.dp)) {
                        IconHelper.FileThumbnail(file = uFile, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Text(
                text = record.fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ".${record.extension.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Formatter.formatFileSize(context, fileRef.length()),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
