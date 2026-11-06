package com.troikoss.continuum_explorer.ui.converter

import android.content.Intent
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import com.troikoss.continuum_explorer.domain.logic.FileOpenPolicyEngine
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.model.LibraryItem
import com.troikoss.continuum_explorer.model.ScreenSize
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.navigateTo
import com.troikoss.continuum_explorer.utils.open
import com.troikoss.continuum_explorer.utils.openInEdit
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.launch
import java.io.File

/**
 * Special VERT-inspired (`VERT-sh/VERT`) Landing Page & Batch Conversion Workbench.
 * Runs 100% on-device in native Kotlin across Images, Documents/Data, Audio/Video, and Archives.
 * Responsive layout:
 * - Mobile (< 680.dp): Unified scrollable single-column workbench.
 * - Tablet & DeX (>= 680.dp): 2-column split workbench (Left: VERT Dropzone + Quick-Add + Format Matrix;
 *   Right: Batch Conversion Queue + Global "Convert All To" + Per-File Tuning & Output Controls).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileConverterScreen(
    appState: FileExplorerState? = null,
    initialFiles: List<File> = emptyList(),
    onBack: (() -> Unit)? = null,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val queue = remember { mutableStateListOf<ConverterQueueItem>() }
    var selectedCategory by remember { mutableStateOf(ConversionCategory.ALL) }
    var destinationMode by remember { mutableStateOf(OutputDestinationMode.CONVERTED_SUBFOLDER) }
    var customOutputFolder by remember {
        mutableStateOf<File?>(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ContinuumSuite/Converted"
            ).apply { if (!exists()) mkdirs() }
        )
    }

    var expandedTuningItemId by remember { mutableStateOf<String?>(null) }
    var isBatchConverting by remember { mutableStateOf(false) }

    // File / Folder Pickers
    var showMultiFilePicker by remember { mutableStateOf(false) }
    var showAddFolderPicker by remember { mutableStateOf(false) }
    var showCustomDestFolderPicker by remember { mutableStateOf(false) }

    val isFullScreen by SettingsManager.isFullScreenTabBarHidden
    val isWideSidebarCollapsed by SettingsManager.isWideSidebarCollapsed
    val isSmallScreen = appState?.getScreenSize() == ScreenSize.SMALL
    val showMenuButton = isSmallScreen || isWideSidebarCollapsed

    fun enqueueFiles(files: List<File>) {
        var addedCount = 0
        for (file in files) {
            if (!file.exists() || file.isDirectory) continue
            if (queue.any { it.sourceFile.absolutePath == file.absolutePath && it.status != ConversionStatus.COMPLETED }) {
                continue
            }
            val item = FileConverterDispatcher.createQueueItem(file)
            if (item != null) {
                queue.add(0, item)
                addedCount++
            }
        }
        if (addedCount > 0) {
            Toast.makeText(context, "Added $addedCount file(s) to converter queue", Toast.LENGTH_SHORT).show()
        } else if (files.isNotEmpty()) {
            Toast.makeText(context, "Selected file format is already queued or unsupported", Toast.LENGTH_SHORT).show()
        }
    }

    // Load initial files passed from Explorer context menu or appState
    LaunchedEffect(initialFiles, appState?.converterInitialFiles?.size) {
        SuiteRecentDatabase.init(context)
        val pending = mutableListOf<File>()
        pending.addAll(initialFiles)
        appState?.converterInitialFiles?.let { stateFiles ->
            if (stateFiles.isNotEmpty()) {
                pending.addAll(stateFiles)
                stateFiles.clear()
            }
        }
        if (pending.isNotEmpty()) {
            enqueueFiles(pending)
        }
    }

    // Filtered queue by category tab
    val visibleQueue = remember(queue.toList(), selectedCategory) {
        if (selectedCategory == ConversionCategory.ALL) {
            queue.toList()
        } else {
            queue.filter { it.category == selectedCategory }
        }
    }

    // Union of available target formats across visible queue items for "Convert All To"
    val globalTargetOptions = remember(visibleQueue) {
        val counts = linkedMapOf<String, Int>()
        for (item in visibleQueue) {
            for (target in item.availableTargets) {
                counts[target] = (counts[target] ?: 0) + 1
            }
        }
        counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    // Quick-add suggestions from Explorer current directory or SuiteRecentDatabase
    val quickSuggestFiles = remember(appState?.currentPath, SuiteRecentDatabase.records.size, queue.size) {
        val candidates = mutableListOf<File>()
        appState?.currentPath?.listFiles()?.filter {
            it.isFile && !it.name.startsWith(".") && ConversionFormatMatrix.isSupportedFile(it)
        }?.take(8)?.let { candidates.addAll(it) }

        SuiteRecentDatabase.records.map { File(it.absolutePath) }
            .filter { it.exists() && it.isFile && ConversionFormatMatrix.isSupportedFile(it) }
            .forEach { recentFile ->
                if (candidates.none { it.absolutePath == recentFile.absolutePath }) {
                    candidates.add(recentFile)
                }
            }
        candidates.filter { cand ->
            queue.none { it.sourceFile.absolutePath == cand.absolutePath }
        }.take(10)
    }

    fun updateQueueItem(id: String, transform: (ConverterQueueItem) -> ConverterQueueItem) {
        val idx = queue.indexOfFirst { it.id == id }
        if (idx != -1) {
            queue[idx] = transform(queue[idx])
        }
    }

    fun convertSingle(item: ConverterQueueItem) {
        if (item.status == ConversionStatus.CONVERTING) return
        scope.launch {
            updateQueueItem(item.id) {
                it.copy(
                    status = ConversionStatus.CONVERTING,
                    progress = 0.05f,
                    statusMessage = "Starting...",
                    errorMessage = null
                )
            }
            val res = FileConverterDispatcher.convertSingleItem(
                context = context,
                item = item,
                destinationMode = destinationMode,
                customFolder = customOutputFolder,
                onProgress = { prog, msg ->
                    updateQueueItem(item.id) {
                        it.copy(progress = prog.coerceIn(0f, 1f), statusMessage = msg)
                    }
                }
            )
            res.fold(
                onSuccess = { outFile ->
                    updateQueueItem(item.id) {
                        it.copy(
                            status = ConversionStatus.COMPLETED,
                            progress = 1f,
                            statusMessage = "Saved ${outFile.name}",
                            outputFile = outFile,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    updateQueueItem(item.id) {
                        it.copy(
                            status = ConversionStatus.FAILED,
                            progress = 0f,
                            statusMessage = "Failed",
                            errorMessage = err.message ?: "Conversion error"
                        )
                    }
                }
            )
        }
    }

    fun convertAllVisible() {
        val pendingItems = visibleQueue.filter { it.status != ConversionStatus.CONVERTING }
        if (pendingItems.isEmpty()) return
        scope.launch {
            isBatchConverting = true
            var successCount = 0
            for (item in pendingItems) {
                updateQueueItem(item.id) {
                    it.copy(
                        status = ConversionStatus.CONVERTING,
                        progress = 0.05f,
                        statusMessage = "Converting...",
                        errorMessage = null
                    )
                }
                val res = FileConverterDispatcher.convertSingleItem(
                    context = context,
                    item = item,
                    destinationMode = destinationMode,
                    customFolder = customOutputFolder,
                    onProgress = { prog, msg ->
                        updateQueueItem(item.id) {
                            it.copy(progress = prog.coerceIn(0f, 1f), statusMessage = msg)
                        }
                    }
                )
                res.fold(
                    onSuccess = { outFile ->
                        successCount++
                        updateQueueItem(item.id) {
                            it.copy(
                                status = ConversionStatus.COMPLETED,
                                progress = 1f,
                                statusMessage = "Saved ${outFile.name}",
                                outputFile = outFile,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { err ->
                        updateQueueItem(item.id) {
                            it.copy(
                                status = ConversionStatus.FAILED,
                                progress = 0f,
                                statusMessage = "Failed",
                                errorMessage = err.message ?: "Conversion failed"
                            )
                        }
                    }
                )
            }
            isBatchConverting = false
            Toast.makeText(
                context,
                "Batch finished: $successCount/${pendingItems.size} file(s) converted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun bundleVisibleQueueToArchive(archiveExt: String) {
        val filesToBundle = visibleQueue.map { it.outputFile ?: it.sourceFile }.filter { it.exists() }
        if (filesToBundle.isEmpty()) {
            Toast.makeText(context, "Add files to queue first", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isBatchConverting = true
            val outDir = FileConverterDispatcher.resolveOutputDirectory(
                sourceFile = filesToBundle.first(),
                destinationMode = destinationMode,
                customFolder = customOutputFolder
            )
            val outArchive = File(outDir, "VERT_Bundle_${System.currentTimeMillis() / 1000}.$archiveExt")
            val res = ArchiveConversionEngine.bundleFilesIntoArchive(
                context = context,
                files = filesToBundle,
                targetExtension = archiveExt,
                outputFile = outArchive,
                tuning = ConversionTuningConfig(),
                onProgress = { _, _ -> }
            )
            isBatchConverting = false
            res.fold(
                onSuccess = { created ->
                    SuiteRecentDatabase.recordFileActivity(
                        context = context,
                        file = created,
                        editorToolId = "file_converter",
                        editorToolName = "File Converter (VERT)",
                        activityType = com.troikoss.continuum_explorer.data.db.SuiteActivityType.CREATED
                    )
                    Toast.makeText(context, "Created bundle: ${created.name}", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    Toast.makeText(context, "Bundle failed: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun shareConvertedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Multi-file picker dialog
    if (showMultiFilePicker) {
        MultiSelectConverterPickerDialog(
            initialDirectory = appState?.currentPath ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showMultiFilePicker = false },
            onFilesSelected = { selectedFiles ->
                showMultiFilePicker = false
                enqueueFiles(selectedFiles)
            }
        )
    }

    // Add all supported files from a folder
    if (showAddFolderPicker) {
        InAppFilePickerDialog(
            title = "Select Folder to Batch Enqueue",
            selectFolders = true,
            initialDirectory = appState?.currentPath ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showAddFolderPicker = false },
            onSelected = { folder ->
                showAddFolderPicker = false
                if (folder.isDirectory) {
                    val children = folder.listFiles()?.filter {
                        it.isFile && !it.name.startsWith(".") && ConversionFormatMatrix.isSupportedFile(it)
                    }.orEmpty()
                    enqueueFiles(children)
                }
            }
        )
    }

    // Custom output directory picker
    if (showCustomDestFolderPicker) {
        InAppFilePickerDialog(
            title = "Select Custom Output Folder",
            selectFolders = true,
            initialDirectory = customOutputFolder ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showCustomDestFolderPicker = false },
            onSelected = { folder ->
                showCustomDestFolderPicker = false
                if (folder.isDirectory) {
                    customOutputFolder = folder
                    destinationMode = OutputDestinationMode.CUSTOM_FOLDER
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- 1. Top Flush Header Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
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
                            contentDescription = "Show Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("converter_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to My Suite",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.16f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Transform,
                                contentDescription = null,
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "File Converter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "VERT Native • 100% Local",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Add Files Button
                FilledTonalButton(
                    onClick = { showMultiFilePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("converter_add_files_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Files", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Add Folder Button
                OutlinedButton(
                    onClick = { showAddFolderPicker = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder", style = MaterialTheme.typography.labelMedium)
                }

                if (queue.isNotEmpty()) {
                    IconButton(
                        onClick = { queue.clear() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.ClearAll,
                            contentDescription = "Clear Queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { SettingsManager.toggleFullScreenTabBar() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit Fullscreen" else "Fullscreen",
                        tint = if (isFullScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // --- 2. Category Tabs Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConversionCategory.entries.forEach { category ->
                    val count = if (category == ConversionCategory.ALL) {
                        queue.size
                    } else {
                        queue.count { it.category == category }
                    }
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (isSelected) category.accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = category.shortTitle,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = category.accentColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = category.accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // --- 3. Responsive Body: 2-Column Workbench on Tablet/DeX (>= 680.dp), 1-Column on Mobile ---
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideWorkbench = maxWidth >= 680.dp

            if (isWideWorkbench) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column: VERT Dropzone + Quick-Add + Format Compatibility Matrix
                    Column(
                        modifier = Modifier
                            .weight(0.43f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VertLandingDropzoneCard(
                            selectedCategory = selectedCategory,
                            onPickFiles = { showMultiFilePicker = true },
                            onPickFolder = { showAddFolderPicker = true },
                            onGenerateSampleFile = { sampleFile -> enqueueFiles(listOf(sampleFile)) }
                        )

                        if (quickSuggestFiles.isNotEmpty()) {
                            QuickAddSuggestionsCard(
                                files = quickSuggestFiles,
                                onAddFile = { enqueueFiles(listOf(it)) },
                                onAddAll = { enqueueFiles(quickSuggestFiles) }
                            )
                        }

                        FormatCompatibilityMatrixCard(selectedCategory = selectedCategory)
                    }

                    // Right Column: Global Batch Controls + Queue List
                    Column(
                        modifier = Modifier
                            .weight(0.57f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BatchControlPanel(
                            queueSize = visibleQueue.size,
                            globalTargets = globalTargetOptions,
                            destinationMode = destinationMode,
                            customFolder = customOutputFolder,
                            isBatchConverting = isBatchConverting,
                            onApplyGlobalTarget = { targetExt ->
                                visibleQueue.forEach { qItem ->
                                    if (targetExt in qItem.availableTargets) {
                                        updateQueueItem(qItem.id) {
                                            it.copy(
                                                targetFormat = targetExt,
                                                status = ConversionStatus.IDLE,
                                                statusMessage = "Ready",
                                                progress = 0f
                                            )
                                        }
                                    }
                                }
                            },
                            onDestinationModeChange = { mode ->
                                if (mode == OutputDestinationMode.CUSTOM_FOLDER) {
                                    showCustomDestFolderPicker = true
                                } else {
                                    destinationMode = mode
                                }
                            },
                            onPickCustomFolder = { showCustomDestFolderPicker = true },
                            onConvertAll = { convertAllVisible() },
                            onBundleZip = { bundleVisibleQueueToArchive("zip") }
                        )

                        if (visibleQueue.isEmpty()) {
                            EmptyQueuePlaceholderCard(
                                selectedCategory = selectedCategory,
                                onPickFiles = { showMultiFilePicker = true }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(visibleQueue, key = { it.id }) { item ->
                                    ConverterQueueItemCard(
                                        item = item,
                                        isTuningExpanded = expandedTuningItemId == item.id,
                                        onToggleTuning = {
                                            expandedTuningItemId = if (expandedTuningItemId == item.id) null else item.id
                                        },
                                        onTargetFormatSelected = { newTarget ->
                                            updateQueueItem(item.id) {
                                                it.copy(
                                                    targetFormat = newTarget,
                                                    status = ConversionStatus.IDLE,
                                                    progress = 0f,
                                                    statusMessage = "Ready"
                                                )
                                            }
                                        },
                                        onTuningChanged = { newTuning ->
                                            updateQueueItem(item.id) { it.copy(tuning = newTuning) }
                                        },
                                        onConvertNow = { convertSingle(item) },
                                        onRemove = { queue.removeAll { it.id == item.id } },
                                        onPreviewOutput = { outFile ->
                                            appState?.open(outFile.toUniversal())
                                        },
                                        onEditOutput = { outFile ->
                                            appState?.openInEdit(outFile.toUniversal())
                                        },
                                        onOpenOutputFolder = { outFile ->
                                            outFile.parentFile?.let { folder ->
                                                appState?.navigateTo(folder, null, libraryItem = LibraryItem.None)
                                            }
                                        },
                                        onShareOutput = { outFile -> shareConvertedFile(outFile) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile / Compact 1-Column Unified Scrollable Workbench
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        VertLandingDropzoneCard(
                            selectedCategory = selectedCategory,
                            onPickFiles = { showMultiFilePicker = true },
                            onPickFolder = { showAddFolderPicker = true },
                            onGenerateSampleFile = { sampleFile -> enqueueFiles(listOf(sampleFile)) }
                        )
                    }

                    if (quickSuggestFiles.isNotEmpty()) {
                        item {
                            QuickAddSuggestionsCard(
                                files = quickSuggestFiles,
                                onAddFile = { enqueueFiles(listOf(it)) },
                                onAddAll = { enqueueFiles(quickSuggestFiles) }
                            )
                        }
                    }

                    if (visibleQueue.isNotEmpty()) {
                        item {
                            BatchControlPanel(
                                queueSize = visibleQueue.size,
                                globalTargets = globalTargetOptions,
                                destinationMode = destinationMode,
                                customFolder = customOutputFolder,
                                isBatchConverting = isBatchConverting,
                                onApplyGlobalTarget = { targetExt ->
                                    visibleQueue.forEach { qItem ->
                                        if (targetExt in qItem.availableTargets) {
                                            updateQueueItem(qItem.id) {
                                                it.copy(
                                                    targetFormat = targetExt,
                                                    status = ConversionStatus.IDLE,
                                                    statusMessage = "Ready",
                                                    progress = 0f
                                                )
                                            }
                                        }
                                    }
                                },
                                onDestinationModeChange = { mode ->
                                    if (mode == OutputDestinationMode.CUSTOM_FOLDER) {
                                        showCustomDestFolderPicker = true
                                    } else {
                                        destinationMode = mode
                                    }
                                },
                                onPickCustomFolder = { showCustomDestFolderPicker = true },
                                onConvertAll = { convertAllVisible() },
                                onBundleZip = { bundleVisibleQueueToArchive("zip") }
                            )
                        }

                        items(visibleQueue, key = { it.id }) { item ->
                            ConverterQueueItemCard(
                                item = item,
                                isTuningExpanded = expandedTuningItemId == item.id,
                                onToggleTuning = {
                                    expandedTuningItemId = if (expandedTuningItemId == item.id) null else item.id
                                },
                                onTargetFormatSelected = { newTarget ->
                                    updateQueueItem(item.id) {
                                        it.copy(
                                            targetFormat = newTarget,
                                            status = ConversionStatus.IDLE,
                                            progress = 0f,
                                            statusMessage = "Ready"
                                        )
                                    }
                                },
                                onTuningChanged = { newTuning ->
                                    updateQueueItem(item.id) { it.copy(tuning = newTuning) }
                                },
                                onConvertNow = { convertSingle(item) },
                                onRemove = { queue.removeAll { it.id == item.id } },
                                onPreviewOutput = { outFile ->
                                    appState?.open(outFile.toUniversal())
                                },
                                onEditOutput = { outFile ->
                                    appState?.openInEdit(outFile.toUniversal())
                                },
                                onOpenOutputFolder = { outFile ->
                                    outFile.parentFile?.let { folder ->
                                        appState?.navigateTo(folder, null, libraryItem = LibraryItem.None)
                                    }
                                },
                                onShareOutput = { outFile -> shareConvertedFile(outFile) }
                            )
                        }
                    }

                    item {
                        FormatCompatibilityMatrixCard(selectedCategory = selectedCategory)
                    }
                }
            }
        }
    }
}

@Composable
private fun VertLandingDropzoneCard(
    selectedCategory: ConversionCategory,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onGenerateSampleFile: (File) -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.5.dp, selectedCategory.accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPickFiles() }
            .testTag("vert_dropzone_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = selectedCategory.accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = selectedCategory.icon,
                        contentDescription = null,
                        tint = selectedCategory.accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Text(
                text = "Drop or Select ${selectedCategory.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "${selectedCategory.description}. Processed 100% locally on your device with zero size caps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPickFiles,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = selectedCategory.accentColor)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse Files", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onPickFolder,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Folder", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Instant Demo Sample Generator so users can test conversions with 1 tap
            TextButton(
                onClick = {
                    try {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            "ContinuumSuite"
                        ).apply { if (!exists()) mkdirs() }
                        val sample = when (selectedCategory) {
                            ConversionCategory.DOCUMENT_DATA -> File(dir, "SampleDataset.json").apply {
                                writeText(
                                    """
                                    [
                                      {"id": 1, "feature": "Image Converter", "engine": "Native Bitmap/PDF", "local": true},
                                      {"id": 2, "feature": "Data & Docs", "engine": "JSON/CSV/YAML/PDFBox", "local": true},
                                      {"id": 3, "feature": "Audio & Video", "engine": "MediaCodec/RIFF WAV", "local": true}
                                    ]
                                    """.trimIndent()
                                )
                            }
                            else -> File(dir, "VERT_Welcome_Guide.md").apply {
                                writeText(
                                    """
                                    # VERT Native File Converter
                                    
                                    Convert **Images**, **Documents**, **Data Tables**, **Media**, and **Archives** locally on Android.
                                    
                                    - Zero cloud uploads
                                    - Batch queue with global & per-file format controls
                                    - Automatic tracking in My Suite Recent Files
                                    """.trimIndent()
                                )
                            }
                        }
                        onGenerateSampleFile(sample)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not create sample: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Load Sample Test File", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun QuickAddSuggestionsCard(
    files: List<File>,
    onAddFile: (File) -> Unit,
    onAddAll: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUICK-ADD FROM WORKSPACE & RECENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onAddAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Queue All (${files.size})", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                files.forEach { file ->
                    AssistChip(
                        onClick = { onAddFile(file) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatCompatibilityMatrixCard(
    selectedCategory: ConversionCategory
) {
    val pairs = remember(selectedCategory) {
        ConversionFormatMatrix.getFormatSummaryPairs(selectedCategory)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "SUPPORTED LOCAL CONVERSION MATRIX (${selectedCategory.shortTitle.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            pairs.forEach { (sourceLabel, targetsLabel) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.42f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = targetsLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.54f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchControlPanel(
    queueSize: Int,
    globalTargets: List<String>,
    destinationMode: OutputDestinationMode,
    customFolder: File?,
    isBatchConverting: Boolean,
    onApplyGlobalTarget: (String) -> Unit,
    onDestinationModeChange: (OutputDestinationMode) -> Unit,
    onPickCustomFolder: () -> Unit,
    onConvertAll: () -> Unit,
    onBundleZip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Queue Title + Convert All Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BATCH CONVERSION QUEUE ($queueSize)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (destinationMode) {
                            OutputDestinationMode.CONVERTED_SUBFOLDER -> "Output: ./Converted/ subfolder"
                            OutputDestinationMode.SAME_AS_SOURCE -> "Output: Same folder as source file"
                            OutputDestinationMode.CUSTOM_FOLDER -> "Output: ${customFolder?.absolutePath ?: "/Converted"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (queueSize > 1) {
                        OutlinedButton(
                            onClick = onBundleZip,
                            enabled = !isBatchConverting,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pack ZIP", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = onConvertAll,
                        enabled = queueSize > 0 && !isBatchConverting,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("convert_all_button")
                    ) {
                        if (isBatchConverting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Converting...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Convert All ($queueSize)", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Row 2: Global "Convert All To" quick format pills
            if (globalTargets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Convert All To:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    globalTargets.forEach { fmt ->
                        SuggestionChip(
                            onClick = { onApplyGlobalTarget(fmt) },
                            label = {
                                Text(
                                    text = ".${fmt.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }

            // Row 3: Output Destination Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Save To:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutputDestinationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = destinationMode == mode,
                        onClick = { onDestinationModeChange(mode) },
                        label = {
                            Text(mode.title, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
                if (destinationMode == OutputDestinationMode.CUSTOM_FOLDER) {
                    TextButton(
                        onClick = onPickCustomFolder,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Change Folder", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQueuePlaceholderCard(
    selectedCategory: ConversionCategory,
    onPickFiles: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = selectedCategory.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "No files in the ${selectedCategory.shortTitle} conversion queue yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tap 'Add Files' or choose any file from Quick-Add to configure target formats and quality settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(onClick = onPickFiles) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Files to Convert")
            }
        }
    }
}

@Composable
private fun ConverterQueueItemCard(
    item: ConverterQueueItem,
    isTuningExpanded: Boolean,
    onToggleTuning: () -> Unit,
    onTargetFormatSelected: (String) -> Unit,
    onTuningChanged: (ConversionTuningConfig) -> Unit,
    onConvertNow: () -> Unit,
    onRemove: () -> Unit,
    onPreviewOutput: (File) -> Unit,
    onEditOutput: (File) -> Unit,
    onOpenOutputFolder: (File) -> Unit,
    onShareOutput: (File) -> Unit
) {
    val context = LocalContext.current
    val sourceSizeFormatted = remember(item.sourceFile.length()) {
        Formatter.formatFileSize(context, item.sourceFile.length())
    }

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: File Icon, Name, Source -> Target Pill, Tune & Convert Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = item.category.accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.category.icon,
                            contentDescription = null,
                            tint = item.category.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceFile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$sourceSizeFormatted • ${item.sourceExtension.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• ${item.statusMessage}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when (item.status) {
                                ConversionStatus.COMPLETED -> Color(0xFF059669)
                                ConversionStatus.FAILED -> MaterialTheme.colorScheme.error
                                ConversionStatus.CONVERTING -> MaterialTheme.colorScheme.primary
                                ConversionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Tune Button
                IconButton(
                    onClick = onToggleTuning,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tuning Settings",
                        tint = if (isTuningExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Single Convert Button
                FilledTonalButton(
                    onClick = onConvertNow,
                    enabled = item.status != ConversionStatus.CONVERTING,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = if (item.status == ConversionStatus.COMPLETED) "Re-Convert" else "Convert",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from Queue",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Target Format Selection Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = item.sourceExtension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )

                item.availableTargets.forEach { targetExt ->
                    val isSelected = item.targetFormat.equals(targetExt, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTargetFormatSelected(targetExt) },
                        label = {
                            Text(
                                text = ".${targetExt.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Progress Bar during conversion
            if (item.status == ConversionStatus.CONVERTING) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            // Error Banner if Failed
            if (item.status == ConversionStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Expandable Per-File Tuning Drawer
            AnimatedVisibility(visible = isTuningExpanded) {
                PerItemTuningPanel(
                    item = item,
                    onTuningChanged = onTuningChanged
                )
            }

            // Completed Output Action Row
            val outFile = item.outputFile
            if (item.status == ConversionStatus.COMPLETED && outFile != null && outFile.exists()) {
                val outSize = Formatter.formatFileSize(context, outFile.length())
                val isEditableOut = FileOpenPolicyEngine.isEditable(outFile.toUniversal())

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Output: ${outFile.name} ($outSize)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Logged in My Suite Recent Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { onPreviewOutput(outFile) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview", style = MaterialTheme.typography.labelSmall)
                            }

                            if (isEditableOut) {
                                TextButton(
                                    onClick = { onEditOutput(outFile) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            IconButton(
                                onClick = { onOpenOutputFolder(outFile) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Open Folder",
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            IconButton(
                                onClick = { onShareOutput(outFile) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share Converted File",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerItemTuningPanel(
    item: ConverterQueueItem,
    onTuningChanged: (ConversionTuningConfig) -> Unit
) {
    val tuning = item.tuning
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "FORMAT & QUALITY TUNING (${item.targetFormat.uppercase()})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            when (item.category) {
                ConversionCategory.IMAGE -> {
                    if (item.targetFormat in listOf("jpg", "jpeg", "webp")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Compression Quality", style = MaterialTheme.typography.labelSmall)
                            Text("${tuning.imageQuality}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tuning.imageQuality.toFloat(),
                            onValueChange = { onTuningChanged(tuning.copy(imageQuality = it.toInt())) },
                            valueRange = 10f..100f
                        )
                    }

                    Text("Max Resolution Cap:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            0 to "Original",
                            3840 to "4K (3840px)",
                            1920 to "1080p (1920px)",
                            1280 to "720p (1280px)",
                            512 to "512px"
                        ).forEach { (px, label) ->
                            FilterChip(
                                selected = tuning.maxDimensionPx == px,
                                onClick = { onTuningChanged(tuning.copy(maxDimensionPx = px)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Strip EXIF & GPS Metadata (Privacy)", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = tuning.stripExifMetadata,
                            onCheckedChange = { onTuningChanged(tuning.copy(stripExifMetadata = it)) }
                        )
                    }
                }

                ConversionCategory.DOCUMENT_DATA -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pretty-Print JSON / XML / YAML Indentation", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = tuning.prettyPrintData,
                            onCheckedChange = { onTuningChanged(tuning.copy(prettyPrintData = it)) }
                        )
                    }
                    if (item.sourceExtension == "pdf") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("PDF Page Render DPI", style = MaterialTheme.typography.labelSmall)
                            Text("${tuning.pdfRenderDpi} DPI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tuning.pdfRenderDpi.toFloat(),
                            onValueChange = { onTuningChanged(tuning.copy(pdfRenderDpi = it.toInt())) },
                            valueRange = 72f..300f
                        )
                    }
                }

                ConversionCategory.AUDIO_VIDEO -> {
                    if (item.targetFormat == "wav") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Downmix Audio to Mono (1-Channel)", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = tuning.audioMonoChannel,
                                onCheckedChange = { onTuningChanged(tuning.copy(audioMonoChannel = it)) }
                            )
                        }
                    }
                    if (item.targetFormat == "mp4") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Strip Audio Track (Create Muted Video)", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = tuning.stripAudioFromVideo,
                                onCheckedChange = { onTuningChanged(tuning.copy(stripAudioFromVideo = it)) }
                            )
                        }
                    }
                    if (item.targetFormat in listOf("png", "jpg")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Video Frame Timestamp (Seconds)", style = MaterialTheme.typography.labelSmall)
                            Text("${tuning.videoFrameTimestampSec}s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tuning.videoFrameTimestampSec.toFloat(),
                            onValueChange = { onTuningChanged(tuning.copy(videoFrameTimestampSec = it.toInt())) },
                            valueRange = 0f..60f
                        )
                    }
                }

                ConversionCategory.ARCHIVE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Compression Level (0=Store, 9=Ultra)", style = MaterialTheme.typography.labelSmall)
                        Text("Level ${tuning.zipCompressionLevel}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = tuning.zipCompressionLevel.toFloat(),
                        onValueChange = { onTuningChanged(tuning.copy(zipCompressionLevel = it.toInt())) },
                        valueRange = 0f..9f,
                        steps = 8
                    )
                    if (item.targetFormat == "zip") {
                        OutlinedTextField(
                            value = tuning.zipPassword,
                            onValueChange = { onTuningChanged(tuning.copy(zipPassword = it)) },
                            label = { Text("Optional AES-256 ZIP Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                ConversionCategory.ALL -> {}
            }
        }
    }
}

/**
 * Multi-select file picker dialog for batch-queuing multiple files into the File Converter at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectConverterPickerDialog(
    initialDirectory: File,
    onDismiss: () -> Unit,
    onFilesSelected: (List<File>) -> Unit
) {
    val rootStorage = Environment.getExternalStorageDirectory()
    var currentDir by remember {
        mutableStateOf(initialDirectory.takeIf { it.exists() && it.isDirectory } ?: rootStorage)
    }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPaths = remember { mutableStateListOf<String>() }

    val dirItems = remember(currentDir, searchQuery) {
        val raw = try {
            currentDir.listFiles()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        raw.filter { f ->
            if (f.name.startsWith(".")) false
            else if (f.isDirectory) searchQuery.isBlank() || f.name.contains(searchQuery, ignoreCase = true)
            else ConversionFormatMatrix.isSupportedFile(f) &&
                (searchQuery.isBlank() || f.name.contains(searchQuery, ignoreCase = true))
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentDir.parentFile != null && currentDir != rootStorage) {
                            IconButton(
                                onClick = { currentDir.parentFile?.let { currentDir = it } },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                        Column {
                            Text(
                                text = "Select Files to Convert",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentDir.absolutePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search convertible files...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(dirItems, key = { it.absolutePath }) { file ->
                        val isSelected = file.absolutePath in selectedPaths
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (file.isDirectory) {
                                        currentDir = file
                                        searchQuery = ""
                                    } else {
                                        if (isSelected) selectedPaths.remove(file.absolutePath)
                                        else selectedPaths.add(file.absolutePath)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (file.isDirectory) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedPaths.add(file.absolutePath)
                                        else selectedPaths.remove(file.absolutePath)
                                    }
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!file.isDirectory) {
                                    val targets = ConversionFormatMatrix.getAvailableTargets(
                                        ConversionFormatMatrix.resolveSourceExtension(file)
                                    ).joinToString(", ") { it.uppercase() }
                                    Text(
                                        text = "Can convert to: $targets",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val allFilesInFolder = dirItems.filter { it.isFile }
                            allFilesInFolder.forEach { f ->
                                if (f.absolutePath !in selectedPaths) selectedPaths.add(f.absolutePath)
                            }
                        }
                    ) {
                        Text("Select All in Folder")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val resolved = selectedPaths.map { File(it) }.filter { it.exists() && it.isFile }
                                onFilesSelected(resolved)
                            },
                            enabled = selectedPaths.isNotEmpty()
                        ) {
                            Text("Queue (${selectedPaths.size})")
                        }
                    }
                }
            }
        }
    }
}
