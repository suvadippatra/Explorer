package com.troikoss.continuum_explorer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewPopupHost
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import com.troikoss.continuum_explorer.ui.components.suite.hoverPreviewTarget
import com.troikoss.continuum_explorer.ui.components.suite.rememberHoverPreviewController
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import java.io.File
import java.io.FileOutputStream

private data class StudioTemplatePreset(
    val label: String,
    val fileName: String,
    val canvasSize: Pair<Int, Int>? = null
)

/**
 * Ultra-Clean Tonal Flat Material 3 2-Pane Desktop Start Workspace for Individual Studios:
 * - Left 268dp `surfaceContainerLow` Template & Inline-Create Rail + Save Folder Anchor
 * - Right `surface` Recent Studio Files Visual Grid / Table with 2-second desktop hover preview
 * - Cleanly stacks into single-column layout on narrow mobile screens (< 680dp)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val hoverController = rememberHoverPreviewController()

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
    var isGridView by remember { mutableStateOf(true) }
    var showFilePicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    val studioExtensions = remember(toolId) {
        when (toolId) {
            "text_editor" -> listOf(".md", ".txt", "todo.txt", ".org", ".adoc")
            "code_studio" -> listOf(".kt", ".py", ".js", ".ts", ".html", ".css", ".json", ".sql", ".sh")
            "csv_editor" -> listOf(".csv", ".tsv")
            "image_studio" -> listOf(".png", ".jpg", ".webp")
            else -> listOf(".md", ".txt")
        }
    }

    val templates = remember(toolId) {
        when (toolId) {
            "text_editor" -> listOf(
                StudioTemplatePreset("Blank Note", "Note.md"),
                StudioTemplatePreset("Task List", "todo.txt"),
                StudioTemplatePreset("Meeting Doc", "Meeting.md"),
                StudioTemplatePreset("Plain Text", "Scratch.txt")
            )
            "code_studio" -> listOf(
                StudioTemplatePreset("HTML Page", "index.html"),
                StudioTemplatePreset("Kotlin File", "Main.kt"),
                StudioTemplatePreset("Python Script", "script.py"),
                StudioTemplatePreset("JSON Config", "config.json")
            )
            "csv_editor" -> listOf(
                StudioTemplatePreset("Blank Sheet", "Sheet1.csv"),
                StudioTemplatePreset("Budget Table", "Budget.csv"),
                StudioTemplatePreset("TSV Data", "Data.tsv")
            )
            "image_studio" -> listOf(
                StudioTemplatePreset("1080×1080 Square", "Square_1080.png", 1080 to 1080),
                StudioTemplatePreset("1920×1080 Wide", "Canvas_1080p.png", 1920 to 1080),
                StudioTemplatePreset("1080×1920 Story", "Story_1080x1920.png", 1080 to 1920)
            )
            else -> listOf(StudioTemplatePreset("Blank File", "Untitled.md"))
        }
    }

    var newFileName by remember(toolId) {
        mutableStateOf(
            when (toolId) {
                "text_editor" -> "Notes.md"
                "code_studio" -> "Main.kt"
                "csv_editor" -> "Sheet1.csv"
                "image_studio" -> "Canvas_1080.png"
                else -> "Untitled.md"
            }
        )
    }
    var canvasWidth by remember { mutableIntStateOf(1080) }
    var canvasHeight by remember { mutableIntStateOf(1080) }

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

    fun createAndLaunchNewFile(fileNameInput: String, customDims: Pair<Int, Int>? = null) {
        hoverController.dismiss()
        val cleanName = fileNameInput.trim().ifBlank { descriptor.defaultNewFileName ?: "Untitled.txt" }
        try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()
            val outFile = File(targetDirectory, cleanName)
            val ext = outFile.extension.lowercase()
            if (toolId == "image_studio") {
                val (w, h) = customDims ?: (canvasWidth to canvasHeight)
                val bmp = Bitmap.createBitmap(w.coerceIn(64, 4096), h.coerceIn(64, 4096), Bitmap.Config.ARGB_8888)
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
                            "(A) First task +Project @Context\n"
                        ext == "csv" ->
                            "Column 1,Column 2,Column 3\nRow 1,Sample A,100\nRow 2,Sample B,250\n"
                        ext == "tsv" ->
                            "Column 1\tColumn 2\tColumn 3\nRow 1\tSample A\t100\n"
                        ext in listOf("md", "markdown") ->
                            "# ${outFile.nameWithoutExtension}\n\n"
                        ext == "kt" ->
                            "fun main() {\n    println(\"Hello\")\n}\n"
                        ext == "py" ->
                            "def main():\n    print(\"Hello\")\n\nif __name__ == \"__main__\":\n    main()\n"
                        ext == "html" ->
                            "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>${outFile.nameWithoutExtension}</title>\n</head>\n<body>\n  <h1>Hello</h1>\n</body>\n</html>\n"
                        ext == "json" ->
                            "{\n  \"name\": \"${outFile.nameWithoutExtension}\",\n  \"version\": 1\n}\n"
                        else -> ""
                    }
                    if (initialTemplate.isNotEmpty()) outFile.writeText(initialTemplate)
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
            title = "Open in ${descriptor.title}",
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
            title = "Save Folder",
            selectFolders = true,
            initialDirectory = targetDirectory,
            onDismiss = { showFolderPicker = false },
            onSelected = { folder ->
                showFolderPicker = false
                if (folder.isDirectory) targetDirectory = folder
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
        val isNarrow = containerWidth < 520.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Single 48dp Command Bar
            SingleRowCommandBar(
                modifier = Modifier.testTag("studio_landing_command_bar"),
                navigationContent = {
                    IconButton(
                        onClick = onBackToSuite,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("studio_landing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Suite",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                titleContent = {
                    Text(
                        text = descriptor.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actionsContent = {
                    if (isNarrow) {
                        IconButton(
                            onClick = { showFilePicker = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("studio_landing_open_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open", modifier = Modifier.size(19.dp))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { showFilePicker = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("studio_landing_open_button"),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("studio_landing_view_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Table View" else "Grid View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(19.dp))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Folder") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showFolderPicker = true
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

            // Main Workspace: 2-Pane Desktop Start Layout vs Single-Column Mobile Layout
            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Left 268dp Tonal Template & Inline-Create Rail
                    Column(
                        modifier = Modifier
                            .width(268.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            placeholder = { Text("File name") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_inline_filename_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            studioExtensions.forEach { ext ->
                                val isSelected = newFileName.endsWith(ext, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            newFileName = if (ext == "todo.txt") {
                                                "todo.txt"
                                            } else {
                                                val base = newFileName.substringBeforeLast('.', newFileName).ifBlank { "Untitled" }
                                                "$base$ext"
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ext,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { createAndLaunchNewFile(newFileName) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("studio_inline_create_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create", fontWeight = FontWeight.SemiBold)
                        }

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Text(
                            text = "Templates",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            templates.forEach { preset ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (preset.canvasSize != null) {
                                                canvasWidth = preset.canvasSize.first
                                                canvasHeight = preset.canvasSize.second
                                            }
                                            createAndLaunchNewFile(preset.fileName, preset.canvasSize)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Save Folder Anchor
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showFolderPicker = true }
                                .testTag("studio_save_folder_anchor")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = targetDirectory.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    TonalVerticalPaneDivider()

                    // Right Recent Studio Files Stage
                    StudioRecentFilesStage(
                        recentFiles = recentStudioFiles,
                        isGridView = isGridView,
                        availableWidthDp = containerWidth - 270.dp,
                        hoverController = hoverController,
                        onOpenFile = { file ->
                            hoverController.dismiss()
                            SuiteRecentDatabase.recordFileActivity(
                                context = context,
                                file = file,
                                editorToolId = toolId,
                                editorToolName = descriptor.title,
                                activityType = SuiteActivityType.EDITED
                            )
                            onOpenFileInStudio(file)
                        },
                        onPickFromStorage = { showFilePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // Compact Mobile Layout
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                    OutlinedTextField(
                                        value = newFileName,
                                        onValueChange = { newFileName = it },
                                        placeholder = { Text("File name") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { createAndLaunchNewFile(newFileName) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(50.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    templates.forEach { preset ->
                                        AssistChip(
                                            onClick = { createAndLaunchNewFile(preset.fileName, preset.canvasSize) },
                                            label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Recent (${recentStudioFiles.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (recentStudioFiles.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "No recent files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(onClick = { showFilePicker = true }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(recentStudioFiles, key = { _, p -> p.first.absolutePath }) { idx, (rec, uFile) ->
                            val fileRef = uFile.fileRef ?: File(rec.absolutePath)
                            LandingRecentRow(
                                record = rec,
                                uFile = uFile,
                                fileRef = fileRef,
                                isAlternate = idx % 2 == 1,
                                isDesktop = false,
                                hoverController = hoverController,
                                onClick = { onOpenFileInStudio(fileRef) }
                            )
                        }
                    }
                }
            }
        }

        // 2-Second Hover Preview Overlay
        HoverPreviewPopupHost(controller = hoverController)
    }
}

@Composable
private fun StudioRecentFilesStage(
    recentFiles: List<Pair<SuiteRecentRecord, UniversalFile>>,
    isGridView: Boolean,
    availableWidthDp: androidx.compose.ui.unit.Dp,
    hoverController: com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController,
    onOpenFile: (File) -> Unit,
    onPickFromStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Recent (${recentFiles.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recentFiles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "No recent files in this studio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onPickFromStorage,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open File")
                        }
                    }
                }
            }
        } else if (isGridView) {
            val cols = if (availableWidthDp >= 720.dp) 4 else if (availableWidthDp >= 480.dp) 3 else 2
            val chunked = recentFiles.chunked(cols)
            itemsIndexed(chunked) { _, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (rec, uFile) ->
                        val fileRef = uFile.fileRef ?: File(rec.absolutePath)
                        LandingRecentGridCard(
                            record = rec,
                            uFile = uFile,
                            fileRef = fileRef,
                            hoverController = hoverController,
                            onClick = { onOpenFile(fileRef) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(cols - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            itemsIndexed(recentFiles, key = { _, p -> p.first.absolutePath }) { idx, (rec, uFile) ->
                val fileRef = uFile.fileRef ?: File(rec.absolutePath)
                LandingRecentRow(
                    record = rec,
                    uFile = uFile,
                    fileRef = fileRef,
                    isAlternate = idx % 2 == 1,
                    isDesktop = true,
                    hoverController = hoverController,
                    onClick = { onOpenFile(fileRef) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandingRecentGridCard(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    fileRef: File,
    hoverController: com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .hoverPreviewTarget(fileRef, hoverController)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { hoverController.showImmediately(fileRef, bounds) }
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
                    .height(82.dp)
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
            Text(
                text = Formatter.formatFileSize(context, fileRef.length()),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandingRecentRow(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    fileRef: File,
    isAlternate: Boolean,
    isDesktop: Boolean,
    hoverController: com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val modified = remember(record.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            record.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isAlternate) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .hoverPreviewTarget(fileRef, hoverController)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { hoverController.showImmediately(fileRef, bounds) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Formatter.formatFileSize(context, fileRef.length()),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isDesktop) {
                Text(
                    text = modified,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(100.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
