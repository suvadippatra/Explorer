package com.troikoss.continuum_explorer.ui.converter

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.open
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class ConverterMode(val label: String) {
    PRESETS("Presets"),
    CONVERT("Convert"),
    BATCH("Batch")
}

/**
 * Ultra-Clean Tonal Flat Material 3 File Converter Screen.
 * - Single 48dp Command Bar with M3 SingleChoiceSegmentedButtonRow (`Presets` | `Convert` | `Batch`)
 * - 2-Pane Desktop Workbenches (`ConverterDesktopPanes.kt`) with responsive mobile stacking
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

    var activeMode by remember { mutableStateOf(ConverterMode.PRESETS) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Shared Output Destination
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
    var customMinKb by remember { mutableFloatStateOf(20f) }
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
    var singleTick by remember { mutableIntStateOf(0) }
    var showSingleFilePicker by remember { mutableStateOf(false) }
    var singleConverting by remember { mutableStateOf(false) }

    // Batch Queue State
    val batchQueue = remember { mutableStateListOf<ConverterQueueItem>() }
    var batchTick by remember { mutableIntStateOf(0) }
    var showBatchFilePicker by remember { mutableStateOf(false) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var globalTargetFormat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(appState?.converterInitialFiles?.size, initialFiles.size) {
        val initFiles = (appState?.converterInitialFiles?.toList().orEmpty() + initialFiles).distinctBy { it.absolutePath }
        if (initFiles.isNotEmpty()) {
            if (initFiles.size == 1) {
                FileConverterDispatcher.createQueueItem(initFiles.first())?.let {
                    singleItem = it
                    activeMode = ConverterMode.CONVERT
                }
            } else {
                initFiles.forEach { f ->
                    if (batchQueue.none { it.sourceFile.absolutePath == f.absolutePath }) {
                        FileConverterDispatcher.createQueueItem(f)?.let { batchQueue.add(it) }
                    }
                }
                activeMode = ConverterMode.BATCH
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

    // Pickers
    if (showStudentImagePicker) {
        InAppFilePickerDialog(
            title = "Select Image",
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
            title = "Select PDF",
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
            title = "Select File",
            selectFolders = false,
            onDismiss = { showSingleFilePicker = false },
            onSelected = { file ->
                showSingleFilePicker = false
                val item = FileConverterDispatcher.createQueueItem(file)
                if (item != null) {
                    singleItem = item
                } else {
                    Toast.makeText(context, "Unsupported format: .${file.extension}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showBatchFilePicker) {
        InAppFilePickerDialog(
            title = "Add Files",
            selectFolders = false,
            allowMultiSelect = true,
            onDismiss = { showBatchFilePicker = false },
            onSelected = { file ->
                showBatchFilePicker = false
                FileConverterDispatcher.createQueueItem(file)?.let { item ->
                    if (batchQueue.none { it.sourceFile.absolutePath == file.absolutePath }) {
                        batchQueue.add(item)
                    }
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
        val isCompactBar = maxWidth < 560.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Unified 48dp Single-Row Command Bar with Segmented Mode Switcher
            SingleRowCommandBar(
                modifier = Modifier.testTag("converter_command_bar"),
                navigationContent = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("converter_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                titleContent = {
                    Text(
                        text = "Converter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                centerContent = {
                    if (!isCompactBar) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("converter_mode_segmented")
                        ) {
                            ConverterMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = activeMode == mode,
                                    onClick = { activeMode = mode },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = ConverterMode.entries.size),
                                    icon = {},
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                },
                actionsContent = {
                    if (!isCompactBar) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showCustomFolderPicker = true }
                                .testTag("converter_output_folder_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when (destinationMode) {
                                        OutputDestinationMode.CONVERTED_SUBFOLDER -> "/Converted"
                                        OutputDestinationMode.SAME_AS_SOURCE -> "Same Folder"
                                        OutputDestinationMode.CUSTOM_FOLDER -> customOutputFolder.name
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
                                text = { Text("Output: /Converted") },
                                leadingIcon = {
                                    Icon(
                                        if (destinationMode == OutputDestinationMode.CONVERTED_SUBFOLDER) Icons.Default.Check else Icons.Default.Folder,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    destinationMode = OutputDestinationMode.CONVERTED_SUBFOLDER
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Output: Same Folder") },
                                leadingIcon = {
                                    Icon(
                                        if (destinationMode == OutputDestinationMode.SAME_AS_SOURCE) Icons.Default.Check else Icons.Default.FolderOpen,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    destinationMode = OutputDestinationMode.SAME_AS_SOURCE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Choose Output Folder") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showCustomFolderPicker = true
                                }
                            )
                            HorizontalDivider()
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

            // Mobile mode segmented strip when top bar is narrow
            if (isCompactBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                        ConverterMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = activeMode == mode,
                                onClick = { activeMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = ConverterMode.entries.size),
                                icon = {},
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Active Workbench Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (activeMode) {
                    ConverterMode.PRESETS -> {
                        ConverterPresetsWorkbench(
                            isWide = isWide,
                            presetSubMode = presetSubMode,
                            onSubModeChange = {
                                presetSubMode = it
                                presetOutputResult = null
                                presetErrorText = null
                            },
                            selectedStudentPreset = selectedStudentPreset,
                            onSelectStudentPreset = { selectedStudentPreset = it },
                            studentSourceFile = studentSourceFile,
                            onPickStudentImage = { showStudentImagePicker = true },
                            customMinKb = customMinKb,
                            onMinKbChange = { customMinKb = it },
                            customMaxKb = customMaxKb,
                            onMaxKbChange = { customMaxKb = it },
                            customWidthInput = customWidthInput,
                            onWidthInputChange = { customWidthInput = it },
                            customHeightInput = customHeightInput,
                            onHeightInputChange = { customHeightInput = it },
                            enhanceSignatureInk = enhanceSignatureInk,
                            onEnhanceInkChange = { enhanceSignatureInk = it },
                            pdfSourceFile = pdfSourceFile,
                            onPickPdf = { showPdfFilePicker = true },
                            selectedPdfCompression = selectedPdfCompression,
                            onSelectPdfCompression = { selectedPdfCompression = it },
                            pdfTargetMaxKb = pdfTargetMaxKb,
                            onPdfTargetMaxKbChange = { pdfTargetMaxKb = it },
                            presetProcessing = presetProcessing,
                            presetProgressText = presetProgressText,
                            presetOutputResult = presetOutputResult,
                            presetErrorText = presetErrorText,
                            onRunImagePreset = {
                                val src = studentSourceFile ?: return@ConverterPresetsWorkbench
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
                                val src = pdfSourceFile ?: return@ConverterPresetsWorkbench
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
                                        .onFailure { presetErrorText = it.message ?: "Compression failed" }
                                }
                            },
                            onOpenOutput = { out -> appState?.open(out.toUniversal()) }
                        )
                    }

                    ConverterMode.CONVERT -> {
                        key(singleTick) {
                            ConverterSingleFileWorkbench(
                                isWide = isWide,
                                singleItem = singleItem,
                                singleConverting = singleConverting,
                                onPickFile = { showSingleFilePicker = true },
                                onTargetFormatChange = { fmt ->
                                    singleItem?.targetFormat = fmt
                                    singleTick++
                                },
                                onImageQualityChange = { q ->
                                    singleItem?.let { it.tuning = it.tuning.copy(imageQuality = q) }
                                    singleTick++
                                },
                                onConvert = {
                                    val current = singleItem ?: return@ConverterSingleFileWorkbench
                                    singleConverting = true
                                    current.status = ConversionStatus.CONVERTING
                                    current.errorMessage = null
                                    singleTick++
                                    scope.launch {
                                        val res = FileConverterDispatcher.convertSingleItem(
                                            context = context,
                                            item = current,
                                            destinationMode = destinationMode,
                                            customFolder = customOutputFolder,
                                            onProgress = { p, msg ->
                                                current.progress = p
                                                current.statusDetail = msg
                                                singleTick++
                                            }
                                        )
                                        singleConverting = false
                                        res.onSuccess { out ->
                                            current.outputFile = out
                                            current.status = ConversionStatus.COMPLETED
                                        }.onFailure { err ->
                                            current.status = ConversionStatus.FAILED
                                            current.errorMessage = err.message ?: "Conversion failed"
                                        }
                                        singleTick++
                                    }
                                },
                                onOpenOutput = { out -> appState?.open(out.toUniversal()) }
                            )
                        }
                    }

                    ConverterMode.BATCH -> {
                        val commonTargets = remember(batchQueue.size, batchTick) {
                            if (batchQueue.isEmpty()) emptyList()
                            else batchQueue.map { it.availableTargets.toSet() }
                                .reduceOrNull { acc, set -> acc.intersect(set) }
                                ?.toList()
                                .orEmpty()
                        }
                        val allAreImages = remember(batchQueue.size) {
                            batchQueue.isNotEmpty() && batchQueue.all { it.category == ConversionCategory.IMAGE }
                        }

                        key(batchTick) {
                            ConverterBatchWorkbench(
                                isWide = isWide,
                                batchQueue = batchQueue.toList(),
                                commonTargets = commonTargets,
                                allAreImages = allAreImages,
                                globalTargetFormat = globalTargetFormat,
                                isBatchRunning = isBatchRunning,
                                onAddFiles = { showBatchFilePicker = true },
                                onClearQueue = {
                                    batchQueue.clear()
                                    globalTargetFormat = null
                                    batchTick++
                                },
                                onSelectGlobalTarget = { target ->
                                    globalTargetFormat = target
                                    batchQueue.forEach { it.targetFormat = target }
                                    batchTick++
                                },
                                onItemTargetChange = { item, fmt ->
                                    item.targetFormat = fmt
                                    batchTick++
                                },
                                onRemoveItem = { item ->
                                    batchQueue.remove(item)
                                    batchTick++
                                },
                                onCombineToPdf = {
                                    if (batchQueue.size < 2) return@ConverterBatchWorkbench
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
                                },
                                onConvertAll = {
                                    isBatchRunning = true
                                    scope.launch {
                                        for (item in batchQueue) {
                                            item.status = ConversionStatus.CONVERTING
                                            batchTick++
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
                                                item.status = ConversionStatus.COMPLETED
                                            }.onFailure { err ->
                                                item.status = ConversionStatus.FAILED
                                                item.errorMessage = err.message
                                            }
                                            batchTick++
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
}
