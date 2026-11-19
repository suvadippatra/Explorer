package com.troikoss.continuum_explorer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
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
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.rules.MiniAppRegistry
import com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewController
import com.troikoss.continuum_explorer.ui.components.suite.HoverPreviewPopupHost
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.TonalSearchPill
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import com.troikoss.continuum_explorer.ui.components.suite.hoverPreviewTarget
import com.troikoss.continuum_explorer.ui.components.suite.rememberHoverPreviewController
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Desktop-First Ultra-Clean Tonal Flat Studio Start Workspace (for Markdown, Code, Table, and Photo Studios).
 * - Single 48.dp Command Bar (zero decorative icon before title, zero stacked bars).
 * - Desktop (>= 720.dp): 2-Pane Layout (Left 272.dp Tonal Template & Quick-Create Rail + Right Recent Grid/Table).
 * - Mobile (< 720.dp): Compact tonal quick-create header + recent list.
 * - Includes 2-second floating mouse hover preview over recent file names.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilePicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

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
                "image_studio" -> "Canvas.png"
                else -> "Untitled.md"
            }
        )
    }
    var canvasPreset by remember { mutableStateOf("1080x1080") }

    val recentStudioFiles = remember(SuiteRecentDatabase.records.size, toolId, searchQuery) {
        SuiteRecentDatabase.getValidUniversalFiles(context).filter { (rec, _) ->
            val matchesTool = when (toolId) {
                "text_editor" -> rec.editorToolId == "text_editor" && rec.extension in descriptor.supportedExtensions
                "code_studio" -> rec.editorToolId == "code_studio" || rec.extension in descriptor.supportedExtensions
                "csv_editor" -> rec.editorToolId == "csv_editor" || rec.extension in setOf("csv", "tsv")
                "image_studio" -> rec.editorToolId == "image_studio" || rec.extension in setOf("jpg", "jpeg", "png", "webp", "bmp")
                else -> rec.editorToolId == toolId
            }
            val matchesSearch = searchQuery.isBlank() || rec.fileName.contains(searchQuery.trim(), ignoreCase = true)
            matchesTool && matchesSearch
        }
    }

    fun createAndLaunchNewFile(fileNameInput: String) {
        val cleanName = fileNameInput.trim().ifBlank { descriptor.defaultNewFileName ?: "Untitled.txt" }
        try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()
            val outFile = File(targetDirectory, cleanName)
            val ext = outFile.extension.lowercase()
            if (toolId == "image_studio") {
                val (w, h) = when (canvasPreset) {
                    "1920x1080" -> 1920 to 1080
                    "1080x1920" -> 1080 to 1920
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
            } else if (!outFile.exists()) {
                outFile.createNewFile()
                val initialTemplate = when {
                    cleanName.endsWith("todo.txt", ignoreCase = true) -> "(A) First task +Project @Context\n"
                    ext == "csv" -> "Column 1,Column 2,Column 3\nRow 1,Value A,100\nRow 2,Value B,250\n"
                    ext == "tsv" -> "Column 1\tColumn 2\tColumn 3\nRow 1\tValue A\t100\n"
                    ext in listOf("md", "markdown") -> "# ${outFile.nameWithoutExtension}\n\n"
                    ext == "kt" -> "fun main() {\n    println(\"Hello\")\n}\n"
                    ext == "py" -> "def main():\n    print(\"Hello\")\n\nif __name__ == \"__main__\":\n    main()\n"
                    ext == "html" -> "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <title>${outFile.nameWithoutExtension}</title>\n</head>\n<body>\n  <h1>Hello</h1>\n</body>\n</html>\n"
                    ext == "json" -> "{\n  \"name\": \"${outFile.nameWithoutExtension}\"\n}\n"
                    else -> ""
                }
                if (initialTemplate.isNotEmpty()) outFile.writeText(initialTemplate)
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

    @Composable
    fun QuickCreatePanel(isSideRail: Boolean, modifier: Modifier = Modifier) {
        val railMod = if (isSideRail) {
            modifier
                .width(276.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        } else {
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(14.dp)
        }

        Column(
            modifier = railMod,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (toolId == "image_studio") "New Canvas" else "New Document",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Borderless tonal filename field
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    BasicTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("landing_filename_input")
                    )
                }
            }

            // Template / Extension Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                studioExtensions.forEach { extChoice ->
                    val isSelected = newFileName.endsWith(extChoice, ignoreCase = true)
                    Surface(
                        onClick = {
                            newFileName = if (extChoice == "todo.txt") {
                                "todo.txt"
                            } else {
                                val base = newFileName.substringBeforeLast('.').ifBlank { "Untitled" }
                                "$base$extChoice"
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = extChoice,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (toolId == "image_studio") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1080x1080", "1920x1080", "1080x1920").forEach { size ->
                        val selected = canvasPreset == size
                        Surface(
                            onClick = { canvasPreset = size },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = size,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { createAndLaunchNewFile(newFileName) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("landing_create_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Create", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = { showFilePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("landing_open_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (isSideRail) {
                Spacer(modifier = Modifier.weight(1f, fill = false))
            }

            // Save Folder Anchor Pill
            Surface(
                onClick = { showFolderPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = targetDirectory.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change Save Folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun RecentFilesPane(isDesktop: Boolean, availableWidthDp: Int, modifier: Modifier = Modifier) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "recent_title") {
                Text(
                    text = "Recent (${recentStudioFiles.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (recentStudioFiles.isEmpty()) {
                item(key = "empty_studio_recents") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 44.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                descriptor.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No recent files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (isGridView) {
                val cols = when {
                    availableWidthDp >= 780 -> 3
                    else -> 2
                }
                val chunked = recentStudioFiles.chunked(cols)
                items(chunked.size, key = { "landing_grid_$it" }) { rowIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        chunked[rowIdx].forEach { (rec, uFile) ->
                            LandingRecentTile(
                                record = rec,
                                uFile = uFile,
                                hoverController = hoverController,
                                onClick = {
                                    hoverController.dismiss()
                                    uFile.fileRef?.let { onOpenFileInStudio(it) }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - chunked[rowIdx].size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(recentStudioFiles, key = { it.first.absolutePath }) { (rec, uFile) ->
                    LandingRecentRow(
                        record = rec,
                        uFile = uFile,
                        isDesktop = isDesktop,
                        hoverController = hoverController,
                        onClick = {
                            hoverController.dismiss()
                            uFile.fileRef?.let { onOpenFileInStudio(it) }
                        }
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
        val rootWidth = maxWidth
        val isDesktop = rootWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            SingleRowCommandBar(
                navigationContent = {
                    IconButton(
                        onClick = onBackToSuite,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("landing_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                titleContent = {
                    Text(
                        text = descriptor.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                centerContent = {
                    if (isDesktop) {
                        TonalSearchPill(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Filter recent",
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 320.dp)
                        )
                    }
                },
                actionsContent = {
                    IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Switch View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { SettingsManager.toggleFullScreenTabBar() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    QuickCreatePanel(isSideRail = true)
                    TonalVerticalPaneDivider()
                    RecentFilesPane(
                        isDesktop = true,
                        availableWidthDp = (rootWidth.value - 276f).toInt(),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                QuickCreatePanel(isSideRail = false)
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                RecentFilesPane(
                    isDesktop = false,
                    availableWidthDp = rootWidth.value.toInt(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HoverPreviewPopupHost(controller = hoverController)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandingRecentRow(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    isDesktop: Boolean,
    hoverController: HoverPreviewController,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
            .onGloballyPositioned { coords = it }
            .hoverPreviewTarget(uFile, hoverController)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val pos = coords?.positionInWindow()
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
            Spacer(Modifier.width(12.dp))
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
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp)
                )
            }
            Text(
                text = sizeText,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LandingRecentTile(
    record: SuiteRecentRecord,
    uFile: UniversalFile,
    hoverController: HoverPreviewController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sizeText = remember(uFile.length) { Formatter.formatFileSize(context, uFile.length) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .height(96.dp)
            .onGloballyPositioned { coords = it }
            .hoverPreviewTarget(uFile, hoverController)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val pos = coords?.positionInWindow()
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
            Icon(
                imageVector = IconHelper.getIconForItem(uFile),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.extension.uppercase()} • $sizeText",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
