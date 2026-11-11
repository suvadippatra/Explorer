package com.troikoss.continuum_explorer.ui.components

import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.domain.logic.FileOpenPolicyEngine
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.rules.MiniAppRuleDescriptor
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
 * Redesigned Minimalist "My Suite" Workspace.
 * Enforces `MiniAppRegistry` and `SuiteRecentDatabase` rules:
 * 1. Zero intermediate mid-page: clicking an editor or file opens directly inside the Editor UI tab
 *    (or floating panel / DeX window per `WorkspaceWindowMode`) and returns cleanly on close.
 * 2. Recent Edited & Created Section: displays ONLY files created or edited inside My Suite / Continuum Editors
 *    (backed by `SuiteRecentDatabase`), never device-wide MediaStore downloads.
 * 3. Base64 Codec is hidden from active UI and archived in `archive/base64/ArchivedBase64Codec.kt`.
 * 4. Hides redundant hamburger drawer button on Tablet/DeX when the collapsible rail/sidebar is already open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeAreaScreen(
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Dialog states for New File Creation and File Selection
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showOpenFilePicker by remember { mutableStateOf(false) }
    var showTargetDirPicker by remember { mutableStateOf(false) }

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

    // Search & View state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden
    val isWideSidebarCollapsed by SettingsManager.isWideSidebarCollapsed

    LaunchedEffect(Unit) {
        SuiteRecentDatabase.init(context)
    }

    val activeMiniApps = remember { MiniAppRegistry.activeMiniApps }

    val suiteRecentPairs = remember(SuiteRecentDatabase.records.size, searchQuery) {
        val allValid = SuiteRecentDatabase.getValidUniversalFiles(context)
        if (searchQuery.isBlank()) allValid
        else allValid.filter { (rec, _) ->
            rec.fileName.contains(searchQuery.trim(), ignoreCase = true) ||
                rec.extension.contains(searchQuery.trim(), ignoreCase = true) ||
                rec.editorToolName.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    fun openFileDirectlyInEditor(file: File, activityType: SuiteActivityType = SuiteActivityType.EDITED) {
        if (!file.exists() || file.isDirectory) return
        val uFile = file.toUniversal()
        if (!FileOpenPolicyEngine.isEditable(uFile)) {
            Toast.makeText(
                context,
                "\"${file.name}\" is not an editable text, table, or image file.",
                Toast.LENGTH_SHORT
            ).show()
            appState?.open(uFile)
            return
        }
        SuiteRecentDatabase.recordFileActivity(
            context = context,
            file = file,
            activityType = activityType
        )
        if (appState != null) {
            appState.openInEdit(uFile)
        }
    }

    fun handleMiniAppClick(descriptor: MiniAppRuleDescriptor) {
        if (descriptor.id == "file_converter") {
            if (appState != null) {
                appState.openInFileConverter(emptyList())
            }
            return
        }
        // Open the studio's dedicated minimalist Landing Page (Create New File / Open File to Edit / Recent Studio Files)
        if (appState != null) {
            appState.activeEditorToolId = descriptor.id
            appState.activeEditorToolTitle = descriptor.title
        } else {
            showOpenFilePicker = true
        }
    }

    // Open File Picker Dialog
    if (showOpenFilePicker) {
        InAppFilePickerDialog(
            title = "Select Editable File (Text, Markdown, CSV, Code, Image)",
            selectFolders = false,
            initialDirectory = Environment.getExternalStorageDirectory(),
            onDismiss = { showOpenFilePicker = false },
            onSelected = { selectedFile ->
                showOpenFilePicker = false
                openFileDirectlyInEditor(selectedFile, SuiteActivityType.EDITED)
            }
        )
    }

    // Target Directory Picker for New File
    if (showTargetDirPicker) {
        InAppFilePickerDialog(
            title = "Select Folder for New File",
            selectFolders = true,
            initialDirectory = targetDirectory,
            onDismiss = { showTargetDirPicker = false },
            onSelected = { folder ->
                showTargetDirPicker = false
                if (folder.isDirectory) {
                    targetDirectory = folder
                }
            }
        )
    }

    // Create New File by Extension Dialog
    if (showCreateFileDialog) {
        val cleanName = newFileNameInput.trim()
        val ext = cleanName.substringAfterLast('.', "").lowercase()
        val assignedEditorLayout = remember(cleanName, ext) {
            when {
                cleanName.lowercase().endsWith("todo.txt") ->
                    "Todo.txt Interactive Task Board + Raw Task Editor"
                ext in listOf("md", "markdown", "mdown", "mkd") ->
                    "Markdown Studio (Live KaTeX Math, Prism Code & Mermaid Preview)"
                ext in listOf("csv", "tsv") ->
                    "CSV Interactive Spreadsheet Grid + Column Aligner"
                ext in listOf("org") ->
                    "Org-Mode Outliner & Document Preview Engine"
                ext in listOf("adoc", "asciidoc") ->
                    "AsciiDoc Technical Document Editor & Preview"
                ext in listOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "sh", "sql", "c", "cpp", "yaml", "yml", "toml") ->
                    "Code Editor with Syntax Highlighting (${ext.uppercase()})"
                else ->
                    "Markor Plain Text & Utility Editor"
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Create New File", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text("File Name") },
                        placeholder = { Text("notes.md, sheet.csv, script.py") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Extension Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Quick Format Extensions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                ".md" to "Markdown",
                                ".txt" to "Text",
                                ".csv" to "CSV Grid",
                                "todo.txt" to "Todo.txt",
                                ".json" to "JSON",
                                ".org" to "Org",
                                ".adoc" to "AsciiDoc",
                                ".py" to "Python",
                                ".kt" to "Kotlin",
                                ".html" to "HTML"
                            ).forEach { (presetExt, _) ->
                                val isTodo = presetExt == "todo.txt"
                                val isSelected = if (isTodo) {
                                    cleanName.equals("todo.txt", ignoreCase = true) || cleanName.endsWith(".todo.txt", ignoreCase = true)
                                } else {
                                    cleanName.endsWith(presetExt, ignoreCase = true)
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isTodo) {
                                            newFileNameInput = "todo.txt"
                                        } else {
                                            val base = newFileNameInput.substringBeforeLast('.', newFileNameInput).ifBlank { "untitled" }
                                            newFileNameInput = "$base$presetExt"
                                        }
                                    },
                                    label = { Text(presetExt, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                )
                            }
                        }
                    }

                    // Target Folder Selector
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTargetDirPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Save in Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = targetDirectory.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { showTargetDirPicker = true }) {
                                Text("Change", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Assigned Layout Preview Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Assigned Editor Layout",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = assignedEditorLayout,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cleanName.isEmpty()) {
                            Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            if (!targetDirectory.exists()) targetDirectory.mkdirs()
                            val newFile = File(targetDirectory, cleanName)
                            if (!newFile.exists()) {
                                newFile.createNewFile()
                                if (ext == "csv") {
                                    newFile.writeText("Column 1,Column 2,Column 3\nValue 1,Value 2,Value 3\n")
                                }
                            }
                            showCreateFileDialog = false
                            openFileDirectlyInEditor(newFile, SuiteActivityType.CREATED)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create & Edit Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    val isSmallScreen = appState?.getScreenSize() == ScreenSize.SMALL
    val showMenuButton = isSmallScreen || isWideSidebarCollapsed

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Minimalist Flush Top Header Row (Single Sidebar Rule: only show Menu icon if sidebar is hidden/mobile)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showMenuButton) {
                    IconButton(
                        onClick = {
                            if (!isSmallScreen && isWideSidebarCollapsed) {
                                SettingsManager.setWideSidebarCollapsed(false)
                            } else {
                                onMenuClick()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Show Navigation Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "MySuiteSearchTransition"
                    ) { searching ->
                        if (!searching) {
                            Text(
                                text = "My Suite",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Filter edited or created suite files...",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 13.sp
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Action: New File
                FilledTonalButton(
                    onClick = { showCreateFileDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New File", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Quick Action: Open Editable File
                OutlinedButton(
                    onClick = { showOpenFilePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelMedium)
                }

                // Toggle List/Grid View Button
                IconButton(
                    onClick = { isGridView = !isGridView },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Search Toggle Button
                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (isSearchActive) "Close Search" else "Search Recent Suite Files",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Fullscreen Toggle Button
                IconButton(
                    onClick = { SettingsManager.toggleFullScreenTabBar() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                        tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section 1: Direct-Launch Editing Studios (from MiniAppRegistry, excluding archived Base64)
            item {
                Text(
                    text = "STUDIOS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isGridView) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        activeMiniApps.forEach { descriptor ->
                            OutlinedCard(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { handleMiniAppClick(descriptor) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = descriptor.accentColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = descriptor.icon,
                                                contentDescription = descriptor.title,
                                                tint = descriptor.accentColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = descriptor.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = descriptor.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(activeMiniApps, key = { it.id }) { descriptor ->
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { handleMiniAppClick(descriptor) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = descriptor.accentColor.copy(alpha = 0.14f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = descriptor.icon,
                                        contentDescription = descriptor.title,
                                        tint = descriptor.accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = descriptor.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = descriptor.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Section 2: Recently Edited & Created Files in My Suite (Backed exclusively by SuiteRecentDatabase)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FILES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${suiteRecentPairs.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (suiteRecentPairs.isNotEmpty()) {
                            TextButton(
                                onClick = { SuiteRecentDatabase.clearAll(context) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (suiteRecentPairs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matches for \"$searchQuery\"" else "No recent files yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (isGridView) {
                val columns = if (isSmallScreen) 2 else 4
                val chunkedPairs = suiteRecentPairs.chunked(columns)
                items(chunkedPairs) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (record, uFile) ->
                            val relativeTime = remember(record.timestamp) {
                                DateUtils.getRelativeTimeSpanString(
                                    record.timestamp,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString()
                            }
                            OutlinedCard(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val f = File(record.absolutePath)
                                        if (f.exists()) {
                                            openFileDirectlyInEditor(f, SuiteActivityType.EDITED)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconHelper.FileThumbnail(
                                            file = uFile,
                                            iconSize = 44.dp,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                    Text(
                                        text = record.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = record.editorToolName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${Formatter.formatFileSize(context, uFile.length)} • $relativeTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(suiteRecentPairs, key = { it.first.absolutePath }) { (record, uFile) ->
                    val relativeTime = remember(record.timestamp) {
                        DateUtils.getRelativeTimeSpanString(
                            record.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    }
                    val actionBadge = if (record.activityType == SuiteActivityType.CREATED) "Created" else "Edited"

                    OutlinedCard(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val f = File(record.absolutePath)
                                if (f.exists()) {
                                    openFileDirectlyInEditor(f, SuiteActivityType.EDITED)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                IconHelper.FileThumbnail(
                                    file = uFile,
                                    iconSize = 32.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "$actionBadge in ${record.editorToolName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "• ${Formatter.formatFileSize(context, uFile.length)} • $relativeTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    val f = File(record.absolutePath)
                                    if (f.exists()) {
                                        openFileDirectlyInEditor(f, SuiteActivityType.EDITED)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
