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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.troikoss.continuum_explorer.managers.RecentFilesManager
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.providers.LocalProvider
import com.troikoss.continuum_explorer.ui.editor.EditorContainer
import com.troikoss.continuum_explorer.ui.editor.NativeImageViewerEditor
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class EditorTool(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val icon: ImageVector,
    val color: Color
)

private val EDITABLE_TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "mdown", "mkd", "json", "json5", "xml", "csv", "tsv", "log",
    "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "scss",
    "c", "cpp", "h", "hpp", "sh", "bash", "yaml", "yml", "ini", "conf", "properties",
    "gradle", "sql", "org", "adoc", "asciidoc", "wiki", "toml", "rst", "tex", "bib"
)

private val EDITABLE_IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "bmp"
)

private val NON_EDITABLE_MEDIA_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m4v",
    "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus",
    "apk", "zip", "rar", "7z", "tar", "gz", "iso", "so", "dex"
)

/**
 * Redesigned Minimalist "My Suite" Workspace.
 * Features:
 * - Create a new text/code/markdown/CSV/Todo.txt file by writing its filename + extension,
 *   automatically assigning and launching the matching specialized Markor editor layout.
 * - Open & edit existing supported files (Text, Markdown, Code, CSV, Todo.txt, Org, AsciiDoc, Images, Base64),
 *   gracefully rejecting non-editable formats like Video.
 * - Full-Screen toggle to hide/show the top TabBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeAreaScreen(
    appState: com.troikoss.continuum_explorer.utils.FileExplorerState? = null,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedEditor by remember { mutableStateOf<EditorTool?>(null) }
    var activeFileToEdit by remember { mutableStateOf<File?>(null) }
    var activeImageToEdit by remember { mutableStateOf<UniversalFile?>(null) }

    // Dialog states for New File Creation and File Selection
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showOpenFilePicker by remember { mutableStateOf(false) }
    var showTargetDirPicker by remember { mutableStateOf(false) }

    var newFileNameInput by remember { mutableStateOf("notes.md") }
    var targetDirectory by remember {
        mutableStateOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .takeIf { it.exists() } ?: Environment.getExternalStorageDirectory()
        )
    }

    // Search & View state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    val editorsList = remember {
        listOf(
            EditorTool(
                id = "text_editor",
                title = "Markor Text & Code Studio",
                description = "Markdown (KaTeX/Prism/Mermaid), Todo.txt, CSV Grid, Org-mode, AsciiDoc & Code",
                badge = "Text / Code",
                icon = Icons.Default.EditNote,
                color = Color(0xFF1E88E5)
            ),
            EditorTool(
                id = "image_studio",
                title = "PhotoEditor Image Studio",
                description = "Crop, rotate, color filters, brightness/contrast sliders, vector brush & text layers",
                badge = "JPG / PNG / WEBP",
                icon = Icons.Default.Tune,
                color = Color(0xFFE91E63)
            ),
            EditorTool(
                id = "base64",
                title = "Base64 Inspector & Encoder",
                description = "Inspect and generate Base64 strings or data URIs for files under 2 MB",
                badge = "< 2 MB",
                icon = Icons.Default.Code,
                color = Color(0xFF00C853)
            )
        )
    }

    var recentFiles by remember { mutableStateOf<List<UniversalFile>>(emptyList()) }
    var isLoadingRecent by remember { mutableStateOf(true) }

    LaunchedEffect(activeFileToEdit, activeImageToEdit) {
        withContext(Dispatchers.IO) {
            val list = RecentFilesManager.getRecentFiles(context)
            recentFiles = list
            isLoadingRecent = false
        }
    }

    // Filter recent files to show editable statuses clearly
    val filteredRecentFiles = remember(recentFiles, searchQuery) {
        if (searchQuery.isBlank()) recentFiles
        else recentFiles.filter { file ->
            file.name.contains(searchQuery.trim(), ignoreCase = true) ||
                file.name.substringAfterLast('.', "").contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    fun isFileEditable(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        if (ext in NON_EDITABLE_MEDIA_EXTENSIONS) return false
        return ext in EDITABLE_TEXT_EXTENSIONS ||
            ext in EDITABLE_IMAGE_EXTENSIONS ||
            lower == "todo.txt" || lower == "done.txt" || lower == "readme" || lower == "license"
    }

    fun routeFileToProperEditor(file: File) {
        val lower = file.name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        when {
            ext in NON_EDITABLE_MEDIA_EXTENSIONS -> {
                Toast.makeText(
                    context,
                    "\".${ext.uppercase()}\" files (like video/audio/archives) cannot be edited in My Suite. Open them in Explorer to preview.",
                    Toast.LENGTH_LONG
                ).show()
            }
            ext in EDITABLE_IMAGE_EXTENSIONS -> {
                val uFile = file.toUniversal()
                if (appState != null) {
                    appState.openInEditMode = true
                    appState.viewingFile = uFile
                } else {
                    activeImageToEdit = uFile
                }
            }
            ext in EDITABLE_TEXT_EXTENSIONS || lower == "todo.txt" || lower == "done.txt" || ext.isEmpty() -> {
                activeFileToEdit = file
            }
            else -> {
                // Allow opening custom extensions in Markor Text Studio if <= 2MB
                if (file.length() <= 2 * 1024 * 1024) {
                    activeFileToEdit = file
                } else {
                    Toast.makeText(context, "Unsupported binary file (> 2 MB)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Open File Picker Dialog
    if (showOpenFilePicker) {
        InAppFilePickerDialog(
            title = "Select Editable File (Text, Markdown, CSV, Code, Image)",
            selectFolders = false,
            initialDirectory = targetDirectory,
            onDismiss = { showOpenFilePicker = false },
            onSelected = { selectedFile ->
                showOpenFilePicker = false
                routeFileToProperEditor(selectedFile)
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
                ext in NON_EDITABLE_MEDIA_EXTENSIONS ->
                    "Unsupported for Creation (Media/Archive format)"
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
                    Text(
                        "Type a file name with its extension. My Suite automatically assigns the proper editor layout based on the extension.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text("File Name & Extension") },
                        placeholder = { Text("e.g. notes.md, budget.csv, todo.txt, script.py") },
                        singleLine = true,
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
                        if (ext in NON_EDITABLE_MEDIA_EXTENSIONS) {
                            Toast.makeText(context, "Cannot create binary media/archive file in text studio", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            if (!targetDirectory.exists()) targetDirectory.mkdirs()
                            val newFile = File(targetDirectory, cleanName)
                            if (!newFile.exists()) {
                                // Seed initial helpful template for CSV or Todo.txt if brand new
                                newFile.createNewFile()
                                if (ext == "csv") {
                                    newFile.writeText("Column 1,Column 2,Column 3\nValue 1,Value 2,Value 3\n")
                                }
                            }
                            showCreateFileDialog = false
                            activeFileToEdit = newFile
                            Toast.makeText(context, "Opened ${newFile.name}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create & Edit")
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            activeImageToEdit != null -> {
                NativeImageViewerEditor(
                    file = activeImageToEdit!!,
                    appState = appState ?: return@Box,
                    onMenuClick = { activeImageToEdit = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            activeFileToEdit != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { activeFileToEdit = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to My Suite",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "My Suite • ${activeFileToEdit!!.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { showCreateFileDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New File", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { SettingsManager.toggleFullScreenTabBar() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Full Screen",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    EditorContainer(
                        initialFile = activeFileToEdit,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
            selectedEditor != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { selectedEditor = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to My Suite",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = selectedEditor!!.icon,
                                    contentDescription = null,
                                    tint = selectedEditor!!.color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedEditor!!.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { SettingsManager.toggleFullScreenTabBar() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Full Screen",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    EditorContainer(
                        initialFile = null,
                        editorToolId = selectedEditor?.id,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Minimalist Flush Top Header Row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onMenuClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
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
                                                                text = "Filter recent files by name or extension...",
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
                                    contentDescription = if (isSearchActive) "Close Search" else "Search Recent Files",
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
                        // Section 1: Quick Create & Studio Launchers
                        item {
                            Text(
                                text = "EDITING STUDIOS",
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
                                    editorsList.forEach { tool ->
                                        OutlinedCard(
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (tool.id == "image_studio") {
                                                        showOpenFilePicker = true
                                                    } else {
                                                        selectedEditor = tool
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
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = tool.color.copy(alpha = 0.14f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = tool.icon,
                                                            contentDescription = tool.title,
                                                            tint = tool.color,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = tool.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = tool.badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(editorsList) { tool ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (tool.id == "image_studio") {
                                                showOpenFilePicker = true
                                            } else {
                                                selectedEditor = tool
                                            }
                                        }
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
                                            color = tool.color.copy(alpha = 0.14f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = tool.icon,
                                                    contentDescription = tool.title,
                                                    tint = tool.color,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = tool.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = tool.badge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = tool.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
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

                        // Section 2: Recent Files with Direct Edit Action for Supported Formats
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECENT FILES (TAP TO EDIT SUPPORTED TYPES)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${filteredRecentFiles.size} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isLoadingRecent) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else if (filteredRecentFiles.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotBlank()) "No recent files match \"$searchQuery\"" else "No recent files yet. Create a new file or open an existing document above.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredRecentFiles.take(30), key = { it.providerId }) { file ->
                                val editable = remember(file.name) { isFileEditable(file.name) }
                                val ext = remember(file.name) { file.name.substringAfterLast('.', "").uppercase() }
                                val relativeTime = remember(file.lastModified) {
                                    if (file.lastModified > 0) {
                                        DateUtils.getRelativeTimeSpanString(
                                            file.lastModified,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        ).toString()
                                    } else "Recently"
                                }

                                OutlinedCard(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val fRef = file.fileRef ?: (file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) })
                                            if (fRef != null && fRef.exists()) {
                                                if (editable) {
                                                    routeFileToProperEditor(fRef)
                                                } else {
                                                    // Open view-only in separate tab if not editable
                                                    Toast.makeText(context, "${file.name} is view-only (cannot be edited in My Suite)", Toast.LENGTH_SHORT).show()
                                                    appState?.onOpenInNewTab?.invoke(file)
                                                }
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
                                                file = file,
                                                iconSize = 32.dp,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = ext.ifEmpty { "FILE" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (editable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "• ${Formatter.formatFileSize(context, file.length)} • $relativeTime",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (editable) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val fRef = file.fileRef ?: (file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) })
                                                    if (fRef != null && fRef.exists()) {
                                                        routeFileToProperEditor(fRef)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Edit", style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = "View Only",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
        }
    }
}
