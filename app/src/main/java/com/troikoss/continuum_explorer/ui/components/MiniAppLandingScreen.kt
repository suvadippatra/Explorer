package com.troikoss.continuum_explorer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.openInEdit
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File
import java.io.FileOutputStream

/**
 * Minimalist Landing Page for every My Suite Studio:
 * - Markor Text & Markdown Studio (`text_editor`)
 * - Code Studio (`code_studio`)
 * - CSV & Table Studio (`csv_editor`)
 * - PhotoEditor Studio (`image_studio`)
 *
 * Features:
 * 1. Unified 50.dp Flush Top Heading Bar with Triple-Line Menu button (`Icons.Default.Menu`),
 *    Back to My Suite button, List/Grid toggle, and Fullscreen toggle.
 * 2. Two primary action cards:
 *    - "Create New File" (with studio-specific format presets or blank canvas creation)
 *    - "Open Existing File to Edit" (launching the universal file picker filtered to that studio)
 * 3. Studio-filtered Recent Edited & Created files supporting both List and Grid layouts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppLandingScreen(
    toolId: String,
    appState: FileExplorerState?,
    onBackToSuite: () -> Unit,
    onMenuClick: () -> Unit,
    onOpenFileInStudio: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val descriptor = remember(toolId) {
        MiniAppRegistry.allMiniApps.firstOrNull { it.id == toolId }
            ?: MiniAppRegistry.activeMiniApps.first()
    }

    val defaultSuiteDir = remember {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ContinuumSuite"
        ).apply { if (!exists()) mkdirs() }.takeIf { it.exists() && it.canWrite() }
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    var targetDirectory by remember { mutableStateOf(defaultSuiteDir) }
    var isGridView by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val studioExtensions = remember(toolId) {
        when (toolId) {
            "text_editor" -> listOf(".md", ".txt", "todo.txt", ".org", ".adoc")
            "code_studio" -> listOf(".kt", ".py", ".js", ".ts", ".html", ".css", ".json", ".xml", ".sql", ".sh")
            "csv_editor" -> listOf(".csv", ".tsv")
            "image_studio" -> listOf(".png", ".jpg", ".webp")
            else -> listOf(".md", ".txt")
        }
    }

    var newFileName by remember(toolId) {
        mutableStateOf(
            when (toolId) {
                "text_editor" -> "Notes.md"
                "code_studio" -> "Main.kt"
                "csv_editor" -> "Sheet1.csv"
                "image_studio" -> "Canvas_1080p.png"
                else -> "Untitled.md"
            }
        )
    }
    var canvasPreset by remember { mutableStateOf("1080x1080 Square") }

    val recentStudioFiles = remember(SuiteRecentDatabase.records.size, toolId) {
        SuiteRecentDatabase.getValidUniversalFiles(context).filter { (rec, _) ->
            when (toolId) {
                "text_editor" -> rec.editorToolId == "text_editor" && rec.extension in descriptor.supportedExtensions
                "code_studio" -> rec.editorToolId == "code_studio" || rec.extension in descriptor.supportedExtensions
                "csv_editor" -> rec.editorToolId == "csv_editor" || rec.extension in setOf("csv", "tsv")
                "image_studio" -> rec.editorToolId == "image_studio" || rec.extension in setOf("jpg", "jpeg", "png", "webp", "bmp")
                else -> rec.editorToolId == toolId
            }
        }
    }

    fun createAndLaunchNewFile(fileNameInput: String) {
        val cleanName = fileNameInput.trim().ifBlank { descriptor.defaultNewFileName ?: "Untitled.txt" }
        try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()
            val outFile = File(targetDirectory, cleanName)
            val ext = outFile.extension.lowercase()
            if (toolId == "image_studio") {
                val (w, h) = when {
                    canvasPreset.startsWith("1920x1080") -> 1920 to 1080
                    canvasPreset.startsWith("1080x1920") -> 1080 to 1920
                    else -> 1080 to 1080
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                FileOutputStream(outFile).use { fos ->
                    val format = when (ext) {
                        "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                        "webp" -> Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.PNG
                    }
                    bmp.compress(format, 95, fos)
                }
                bmp.recycle()
            } else {
                if (!outFile.exists()) {
                    outFile.createNewFile()
                    val initialTemplate = when {
                        cleanName.endsWith("todo.txt", ignoreCase = true) ->
                            "(A) Welcome to Todo.txt in Markor Studio +Project @Context\n"
                        ext == "csv" ->
                            "Column 1,Column 2,Column 3\nRow 1,Sample A,100\nRow 2,Sample B,250\n"
                        ext == "tsv" ->
                            "Column 1\tColumn 2\tColumn 3\nRow 1\tSample A\t100\n"
                        ext in listOf("md", "markdown") ->
                            "# ${outFile.nameWithoutExtension}\n\nStart writing Markdown, LaTeX math, or tasks here.\n"
                        ext == "kt" ->
                            "fun main() {\n    println(\"Hello from Code Studio!\")\n}\n"
                        ext == "py" ->
                            "def main():\n    print(\"Hello from Code Studio!\")\n\nif __name__ == \"__main__\":\n    main()\n"
                        ext == "html" ->
                            "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>${outFile.nameWithoutExtension}</title>\n</head>\n<body>\n  <h1>Hello World</h1>\n</body>\n</html>\n"
                        ext == "json" ->
                            "{\n  \"name\": \"${outFile.nameWithoutExtension}\",\n  \"version\": 1\n}\n"
                        else -> ""
                    }
                    if (initialTemplate.isNotEmpty()) {
                        outFile.writeText(initialTemplate)
                    }
                }
            }
            SuiteRecentDatabase.recordFileActivity(
                context = context,
                file = outFile,
                editorToolId = toolId,
                editorToolName = descriptor.title,
                activityType = SuiteActivityType.CREATED
            )
            onOpenFileInStudio(outFile)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not create file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    if (showFilePicker) {
        InAppFilePickerDialog(
            title = "Open File in ${descriptor.title}",
            selectFolders = false,
            initialDirectory = Environment.getExternalStorageDirectory(),
            allowedExtensions = descriptor.supportedExtensions,
            onDismiss = { showFilePicker = false },
            onSelected = { selectedFile ->
                showFilePicker = false
                SuiteRecentDatabase.recordFileActivity(
                    context = context,
                    file = selectedFile,
                    editorToolId = toolId,
                    editorToolName = descriptor.title,
                    activityType = SuiteActivityType.EDITED
                )
                onOpenFileInStudio(selectedFile)
            }
        )
    }

    if (showFolderPicker) {
        InAppFilePickerDialog(
            title = "Choose Save Folder",
            selectFolders = true,
            initialDirectory = targetDirectory,
            onDismiss = { showFolderPicker = false },
            onSelected = { folder ->
                showFolderPicker = false
                if (folder.isDirectory) targetDirectory = folder
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(descriptor.icon, contentDescription = null, tint = descriptor.accentColor)
                    Text("New File in ${descriptor.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name & Extension") },
                        placeholder = { Text("e.g. notes.md, index.html") },
                        leadingIcon = {
                            Icon(
                                imageVector = descriptor.icon,
                                contentDescription = null,
                                tint = descriptor.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (newFileName.isNotEmpty()) {
                                IconButton(onClick = { newFileName = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = descriptor.accentColor,
                            focusedLabelColor = descriptor.accentColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Format Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            studioExtensions.forEach { extPreset ->
                                val isSelected = if (extPreset == "todo.txt") {
                                    newFileName.equals("todo.txt", ignoreCase = true) || newFileName.endsWith(".todo.txt", ignoreCase = true)
                                } else {
                                    newFileName.endsWith(extPreset, ignoreCase = true)
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (extPreset == "todo.txt") {
                                            newFileName = "todo.txt"
                                        } else {
                                            val base = newFileName.substringBeforeLast('.', newFileName).ifBlank { "Untitled" }
                                            newFileName = "$base$extPreset"
                                        }
                                    },
                                    label = { Text(extPreset, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                )
                            }
                        }
                    }

                    if (toolId == "image_studio") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Blank Canvas Resolution:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                listOf("1080x1080 Square", "1920x1080 Landscape", "1080x1920 Portrait").forEach { preset ->
                                    FilterChip(
                                        selected = canvasPreset == preset,
                                        onClick = { canvasPreset = preset },
                                        label = { Text(preset, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFolderPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Target Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = targetDirectory.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { showFolderPicker = true }) {
                                Text("Change", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateDialog = false
                        createAndLaunchNewFile(newFileName)
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create & Open Editor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Unified 50.dp Flush Top Heading Bar with Single Clean Back Navigation Button
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
                IconButton(
                    onClick = onBackToSuite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to My Suite",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = descriptor.accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = descriptor.icon,
                            contentDescription = null,
                            tint = descriptor.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = descriptor.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = descriptor.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = descriptor.accentColor,
                        maxLines = 1
                    )
                }

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

                IconButton(
                    onClick = { SettingsManager.toggleFullScreenTabBar() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 640.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Action Cards: Create New vs Open File to Edit
                item {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioLandingActionCard(
                                title = if (toolId == "image_studio") "Create Blank Canvas" else "Create New File",
                                subtitle = if (toolId == "image_studio") {
                                    "Start a fresh 1080p PNG/JPG/WEBP canvas in PhotoEditor Studio"
                                } else {
                                    "Create a new ${studioExtensions.joinToString(", ")} document and edit immediately"
                                },
                                icon = Icons.Default.NoteAdd,
                                accentColor = descriptor.accentColor,
                                buttonLabel = "Create New",
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                            StudioLandingActionCard(
                                title = "Open File to Edit",
                                subtitle = "Browse storage for ${descriptor.supportedExtensions.take(8).joinToString(", ") { ".$it" }} files",
                                icon = Icons.Default.FolderOpen,
                                accentColor = MaterialTheme.colorScheme.primary,
                                buttonLabel = "Browse Files",
                                onClick = { showFilePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioLandingActionCard(
                                title = if (toolId == "image_studio") "Create Blank Canvas" else "Create New File",
                                subtitle = if (toolId == "image_studio") {
                                    "Start a fresh 1080p PNG/JPG/WEBP canvas in PhotoEditor Studio"
                                } else {
                                    "Create a new ${studioExtensions.joinToString(", ")} document and edit immediately"
                                },
                                icon = Icons.Default.NoteAdd,
                                accentColor = descriptor.accentColor,
                                buttonLabel = "Create New",
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudioLandingActionCard(
                                title = "Open File to Edit",
                                subtitle = "Browse storage for ${descriptor.supportedExtensions.take(8).joinToString(", ") { ".$it" }} files",
                                icon = Icons.Default.FolderOpen,
                                accentColor = MaterialTheme.colorScheme.primary,
                                buttonLabel = "Browse Files",
                                onClick = { showFilePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Recent Files Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT IN ${descriptor.title.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${recentStudioFiles.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (recentStudioFiles.isEmpty()) {
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
                                    descriptor.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "No recent files in ${descriptor.title} yet. Tap 'Create New' or 'Browse Files' above to begin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (isGridView) {
                    val columns = if (isWide) 4 else 2
                    val chunked = recentStudioFiles.chunked(columns)
                    items(chunked) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (record, uFile) ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val f = File(record.absolutePath)
                                            if (f.exists()) onOpenFileInStudio(f)
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
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            IconHelper.FileThumbnail(
                                                file = uFile,
                                                iconSize = 42.dp,
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                        Text(
                                            text = record.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = Formatter.formatFileSize(context, uFile.length),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    items(recentStudioFiles, key = { it.first.absolutePath }) { (record, uFile) ->
                        val relativeTime = remember(record.timestamp) {
                            DateUtils.getRelativeTimeSpanString(
                                record.timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            ).toString()
                        }
                        OutlinedCard(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val f = File(record.absolutePath)
                                    if (f.exists()) onOpenFileInStudio(f)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    Text(
                                        text = "${Formatter.formatFileSize(context, uFile.length)} • $relativeTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val f = File(record.absolutePath)
                                        if (f.exists()) onOpenFileInStudio(f)
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
}

@Composable
private fun StudioLandingActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
