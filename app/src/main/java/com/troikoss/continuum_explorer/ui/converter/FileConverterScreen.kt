package com.troikoss.continuum_explorer.ui.converter

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.open
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class ConverterTopTab(val label: String) {
    QUICK_PRESETS("Presets"),
    SINGLE_FILE("Convert"),
    BATCH_CONVERT("Batch")
}

/**
 * Desktop-First Ultra-Clean Tonal Flat File Converter.
 * - Single 48.dp Command Bar with M3 SingleChoiceSegmentedButtonRow (`Presets` | `Convert` | `Batch`).
 * - True 2-Pane Desktop Workbenches across all 3 modes via `ConverterDesktopPanes.kt` (zero OutlinedCards).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileConverterScreen(
    appState: FileExplorerState? = null,
    initialFiles: List<File> = emptyList(),
    onBack: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isFullScreen by SettingsManager.isFullScreenTabBarHidden

    var activeTab by remember { mutableStateOf(ConverterTopTab.QUICK_PRESETS) }

    // Output Destination
    var destinationMode by remember { mutableStateOf(OutputDestinationMode.CONVERTED_SUBFOLDER) }
    var customOutputFolder by remember {
        mutableStateOf(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ContinuumSuite/Converted"
            ).apply { if (!exists()) mkdirs() }
        )
    }
    var showCustomFolderPicker by remember { mutableStateOf(false) }

    // Presets State
    var presetSubMode by remember { mutableIntStateOf(0) }
    var selectedStudentPreset by remember { mutableStateOf(StudentPresetType.PASSPORT_PHOTO_STANDARD) }
    var studentSourceFile by remember { mutableStateOf<File?>(null) }
    var customMinKb by remember { mutableFloatStateOf(15f) }
    var customMaxKb by remember { mutableFloatStateOf(48f) }
    var customWidthInput by remember { mutableStateOf("200") }
    var customHeightInput by remember { mutableStateOf("230") }
    var enhanceSignatureInk by remember { mutableStateOf(false) }
    var presetProcessing by remember { mutableStateOf(false) }
    var presetProgressText by remember { mutableStateOf("") }
    var presetOutputResult by remember { mutableStateOf<File?>(null) }
    var presetErrorText by remember { mutableStateOf<String?>(null) }

    var pdfSourceFile by remember { mutableStateOf<File?>(null) }
    var selectedPdfCompression by remember { mutableStateOf(PdfCompressionLevel.RECOMMENDED) }
    var pdfTargetMaxKb by remember { mutableFloatStateOf(0f) }
    var showStudentImagePicker by remember { mutableStateOf(false) }
    var showPdfFilePicker by remember { mutableStateOf(false) }

    // Single File State
    var singleItem by remember { mutableStateOf<ConverterQueueItem?>(null) }
    var showSingleFilePicker by remember { mutableStateOf(false) }
    var singleConverting by remember { mutableStateOf(false) }

    // Batch Queue State
    val batchQueue = remember { mutableStateListOf<ConverterQueueItem>() }
    var showBatchFilePicker by remember { mutableStateOf(false) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var globalTargetFormat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(appState?.converterInitialFiles?.size, initialFiles.size) {
        val initFiles = (appState?.converterInitialFiles?.toList().orEmpty() + initialFiles).distinctBy { it.absolutePath }
        if (initFiles.isNotEmpty()) {
            if (initFiles.size == 1) {
                val first = initFiles.first()
                FileConverterDispatcher.createQueueItem(first)?.let {
                    singleItem = it
                    activeTab = ConverterTopTab.SINGLE_FILE
                }
            } else {
                initFiles.forEach { f ->
                    if (batchQueue.none { it.sourceFile.absolutePath == f.absolutePath }) {
                        FileConverterDispatcher.createQueueItem(f)?.let { batchQueue.add(it) }
                    }
                }
                activeTab = ConverterTopTab.BATCH_CONVERT
            }
            appState?.converterInitialFiles?.clear()
        }
    }

    LaunchedEffect(selectedStudentPreset) {
        customMinKb = selectedStudentPreset.minKb.toFloat()
        customMaxKb = selectedStudentPreset.maxKb.toFloat()
        if (selectedStudentPreset.targetWidthPx > 0) {
            customWidthInput = selectedStudentPreset.targetWidthPx.toString()
            customHeightInput = selectedStudentPreset.targetHeightPx.toString()
        } else {
            customWidthInput = ""
            customHeightInput = ""
        }
        enhanceSignatureInk = selectedStudentPreset.highContrastSignature
    }

    // File Pickers
    if (showStudentImagePicker) {
        InAppFilePickerDialog(
            title = "Select Photo or Signature",
            selectFolders = false,
            allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp"),
            onDismiss = { showStudentImagePicker = false },
            onSelected = { file ->
                showStudentImagePicker = false
                studentSourceFile = file
                presetOutputResult = null
                presetErrorText = null
            }
        )
    }

    if (showPdfFilePicker) {
        InAppFilePickerDialog(
            title = "Select PDF Document",
            selectFolders = false,
            allowedExtensions = setOf("pdf"),
            onDismiss = { showPdfFilePicker = false },
            onSelected = { file ->
                showPdfFilePicker = false
                pdfSourceFile = file
                presetOutputResult = null
                presetErrorText = null
            }
        )
    }

    if (showSingleFilePicker) {
        InAppFilePickerDialog(
            title = "Select File to Convert",
            selectFolders = false,
            onDismiss = { showSingleFilePicker = false },
            onSelected = { file ->
                showSingleFilePicker = false
                val item = FileConverterDispatcher.createQueueItem(file)
                if (item != null) singleItem = item
                else Toast.makeText(context, ".${file.extension} is not supported", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showBatchFilePicker) {
        InAppFilePickerDialog(
            title = "Add Files to Queue",
            selectFolders = false,
            allowMultiSelect = true,
            onDismiss = { showBatchFilePicker = false },
            onSelected = { file ->
                showBatchFilePicker = false
                FileConverterDispatcher.createQueueItem(file)?.let { item ->
                    if (batchQueue.none { it.sourceFile.absolutePath == file.absolutePath }) batchQueue.add(item)
                }
            },
            onMultipleSelected = { files ->
                showBatchFilePicker = false
                files.forEach { f ->
                    if (batchQueue.none { it.sourceFile.absolutePath == f.absolutePath }) {
                        FileConverterDispatcher.createQueueItem(f)?.let { batchQueue.add(it) }
                    }
                }
            }
        )
    }

    if (showCustomFolderPicker) {
        InAppFilePickerDialog(
            title = "Output Folder",
            selectFolders = true,
            initialDirectory = customOutputFolder,
            onDismiss = { showCustomFolderPicker = false },
            onSelected = { folder ->
                showCustomFolderPicker = false
                if (folder.isDirectory) {
                    customOutputFolder = folder
                    destinationMode = OutputDestinationMode.CUSTOM_FOLDER
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isWide = maxWidth >= 740.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar
            SingleRowCommandBar(
                navigationContent = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("converter_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                },
                titleContent = {
                    if (isWide) {
                        Text(
                            text = "Converter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                },
                centerContent = {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(32.dp)) {
                        ConverterTopTab.entries.forEachIndexed { idx, tab ->
                            SegmentedButton(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = ConverterTopTab.entries.size),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.testTag("converter_tab_${tab.name.lowercase()}")
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (activeTab == tab) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actionsContent = {
                    if (isWide) {
                        Surface(
                            onClick = { showCustomFolderPicker = true },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = if (destinationMode == OutputDestinationMode.CUSTOM_FOLDER) customOutputFolder.name else "Converted/",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { showCustomFolderPicker = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Folder, contentDescription = "Output Folder", modifier = Modifier.size(18.dp))
                        }
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    ConverterTopTab.QUICK_PRESETS -> {
                        ConverterPresetsWorkbench(
                            isWide = isWide,
                            presetSubMode = presetSubMode,
                            onSubModeChange = {
                                presetSubMode = it
                                presetOutputResult = null
                                presetErrorText = null
                            },
                            selectedStudentPreset = selectedStudentPreset,
                            onStudentPresetChange = { selectedStudentPreset = it },
                            studentSourceFile = studentSourceFile,
                            onPickStudentImage = { showStudentImagePicker = true },
                            customMinKb = customMinKb,
                            customMaxKb = customMaxKb,
                            onMaxKbChange = { customMaxKb = it },
                            customWidthInput = customWidthInput,
                            onWidthChange = { customWidthInput = it },
                            customHeightInput = customHeightInput,
                            onHeightChange = { customHeightInput = it },
                            enhanceSignatureInk = enhanceSignatureInk,
                            onEnhanceInkChange = { enhanceSignatureInk = it },
                            pdfSourceFile = pdfSourceFile,
                            onPickPdf = { showPdfFilePicker = true },
                            selectedPdfCompression = selectedPdfCompression,
                            onPdfCompressionChange = { selectedPdfCompression = it },
                            pdfTargetMaxKb = pdfTargetMaxKb,
                            onPdfTargetMaxKbChange = { pdfTargetMaxKb = it },
                            presetProcessing = presetProcessing,
                            presetProgressText = presetProgressText,
                            presetOutputResult = presetOutputResult,
                            presetErrorText = presetErrorText,
                            onRunImageCompress = {
                                val src = studentSourceFile
                                if (src == null || !src.exists()) {
                                    Toast.makeText(context, "Pick an image first", Toast.LENGTH_SHORT).show()
                                    return@ConverterPresetsWorkbench
                                }
                                presetProcessing = true
                                presetErrorText = null
                                presetOutputResult = null
                                scope.launch {
                                    val outFile = FileConverterDispatcher.resolveUniqueOutputFile(
                                        sourceFile = src,
                                        targetFormat = "jpg",
                                        destinationMode = destinationMode,
                                        customFolder = customOutputFolder
                                    )
                                    val res = SquooshImageCompressor.compressImageToTargetKb(
                                        context = context,
                                        sourceFile = src,
                                        outputFile = outFile,
                                        minTargetKb = customMinKb.roundToInt(),
                                        maxTargetKb = customMaxKb.roundToInt(),
                                        exactWidthPx = customWidthInput.toIntOrNull() ?: 0,
                                        exactHeightPx = customHeightInput.toIntOrNull() ?: 0,
                                        enhanceSignatureContrast = enhanceSignatureInk,
                                        onProgress = { _, msg -> presetProgressText = msg }
                                    )
                                    presetProcessing = false
                                    res.onSuccess { presetOutputResult = it }
                                        .onFailure { presetErrorText = it.message ?: "Compression failed" }
                                }
                            },
                            onRunPdfCompress = {
                                val src = pdfSourceFile
                                if (src == null || !src.exists()) {
                                    Toast.makeText(context, "Select a PDF first", Toast.LENGTH_SHORT).show()
                                    return@ConverterPresetsWorkbench
                                }
                                presetProcessing = true
                                presetErrorText = null
                                presetOutputResult = null
                                scope.launch {
                                    val outFile = FileConverterDispatcher.resolveUniqueOutputFile(
                                        sourceFile = src,
                                        targetFormat = "pdf",
                                        destinationMode = destinationMode,
                                        customFolder = customOutputFolder
                                    )
                                    val res = SquooshImageCompressor.compressPdfToTarget(
                                        context = context,
                                        sourcePdf = src,
                                        outputFile = outFile,
                                        level = selectedPdfCompression,
                                        targetMaxKb = pdfTargetMaxKb.roundToInt(),
                                        onProgress = { _, msg -> presetProgressText = msg }
                                    )
                                    presetProcessing = false
                                    res.onSuccess { presetOutputResult = it }
                                        .onFailure { presetErrorText = it.message ?: "PDF compression failed" }
                                }
                            },
                            onOpenOutput = { out -> appState?.open(out.toUniversal()) }
                        )
                    }

                    ConverterTopTab.SINGLE_FILE -> {
                        ConverterSingleFileWorkbench(
                            isWide = isWide,
                            singleItem = singleItem,
                            singleConverting = singleConverting,
                            onPickFile = { showSingleFilePicker = true },
                            onConvert = { current ->
                                singleConverting = true
                                current.status = ConversionJobStatus.CONVERTING
                                current.errorMessage = null
                                scope.launch {
                                    val res = FileConverterDispatcher.convertSingleItem(
                                        context = context,
                                        item = current,
                                        destinationMode = destinationMode,
                                        customFolder = customOutputFolder,
                                        onProgress = { p, msg ->
                                            current.progress = p
                                            current.statusDetail = msg
                                        }
                                    )
                                    singleConverting = false
                                    res.onSuccess { out ->
                                        current.outputFile = out
                                        current.status = ConversionJobStatus.COMPLETED
                                    }.onFailure { err ->
                                        current.status = ConversionJobStatus.FAILED
                                        current.errorMessage = err.message ?: "Conversion failed"
                                    }
                                }
                            },
                            onOpenOutput = { out -> appState?.open(out.toUniversal()) }
                        )
                    }

                    ConverterTopTab.BATCH_CONVERT -> {
                        ConverterBatchWorkbench(
                            isWide = isWide,
                            batchQueue = batchQueue,
                            isBatchRunning = isBatchRunning,
                            globalTargetFormat = globalTargetFormat,
                            onGlobalTargetSelected = { fmt ->
                                globalTargetFormat = fmt
                                batchQueue.forEach { item ->
                                    if (fmt in item.availableTargets) item.targetFormat = fmt
                                }
                            },
                            onAddFiles = { showBatchFilePicker = true },
                            onClearQueue = {
                                batchQueue.clear()
                                globalTargetFormat = null
                            },
                            onCombineToPdf = {
                                if (batchQueue.size >= 2) {
                                    isBatchRunning = true
                                    scope.launch {
                                        val first = batchQueue.first().sourceFile
                                        val outFile = FileConverterDispatcher.resolveUniqueOutputFile(
                                            sourceFile = File(first.parentFile ?: customOutputFolder, "Combined_Images.pdf"),
                                            targetFormat = "pdf",
                                            destinationMode = destinationMode,
                                            customFolder = customOutputFolder
                                        )
                                        val res = SquooshImageCompressor.combineImagesToPdf(
                                            context = context,
                                            imageFiles = batchQueue.map { it.sourceFile },
                                            outputFile = outFile
                                        )
                                        isBatchRunning = false
                                        res.onSuccess { pdf ->
                                            Toast.makeText(context, "Created ${pdf.name}", Toast.LENGTH_SHORT).show()
                                            appState?.open(pdf.toUniversal())
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onConvertAll = {
                                isBatchRunning = true
                                scope.launch {
                                    for (item in batchQueue) {
                                        item.status = ConversionJobStatus.CONVERTING
                                        val res = FileConverterDispatcher.convertSingleItem(
                                            context = context,
                                            item = item,
                                            destinationMode = destinationMode,
                                            customFolder = customOutputFolder,
                                            onProgress = { p, msg ->
                                                item.progress = p
                                                item.statusDetail = msg
                                            }
                                        )
                                        res.onSuccess { out ->
                                            item.outputFile = out
                                            item.status = ConversionJobStatus.COMPLETED
                                        }.onFailure { err ->
                                            item.status = ConversionJobStatus.FAILED
                                            item.errorMessage = err.message
                                        }
                                    }
                                    isBatchRunning = false
                                }
                            },
                            onOpenOutput = { out -> appState?.open(out.toUniversal()) }
                        )
                    }
                }
            }
        }
    }
}
