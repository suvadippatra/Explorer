package com.troikoss.continuum_explorer.ui.converter

import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.open
import com.troikoss.continuum_explorer.utils.toUniversal
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class ConverterTopTab(val title: String, val badge: String) {
    QUICK_PRESETS("1. Quick Presets", "Photo / Sign / PDF KB"),
    SINGLE_FILE("2. Single File", "VERT Dynamic"),
    BATCH_CONVERT("3. Batch & Combine", "Multi-File Queue")
}

/**
 * Redesigned Minimalist File Converter Screen combining:
 * 1. Quick Presets Tab (Squoosh-inspired Student Exam Photo 20–50 KB, Signature 10–20 KB with white-paper contrast cleanup,
 *    Custom Target KB Slider, and Native PDF Size Compressor).
 * 2. Single File Tab (VERT-inspired dynamic file type detector that shows all compatible target formats for any picked file).
 * 3. Batch Convert & Combine Images to PDF Tab.
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

    // --- TAB 1: QUICK PRESETS STATE (Student Photo / Signature KB + PDF Compressor) ---
    var presetSubMode by remember { mutableIntStateOf(0) } // 0 = Photo/Signature KB, 1 = PDF Size Compressor
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
    var pdfTargetMaxKb by remember { mutableFloatStateOf(0f) } // 0 = automatic by level
    var showStudentImagePicker by remember { mutableStateOf(false) }
    var showPdfFilePicker by remember { mutableStateOf(false) }

    // --- TAB 2: SINGLE FILE (VERT DYNAMIC) STATE ---
    var singleItem by remember { mutableStateOf<ConverterQueueItem?>(null) }
    var showSingleFilePicker by remember { mutableStateOf(false) }
    var singleConverting by remember { mutableStateOf(false) }

    // --- TAB 3: BATCH & IMAGE-TO-PDF QUEUE STATE ---
    val batchQueue = remember { mutableStateListOf<ConverterQueueItem>() }
    var showBatchFilePicker by remember { mutableStateOf(false) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var globalTargetFormat by remember { mutableStateOf<String?>(null) }

    // Preload initial files passed from Explorer context menu or FloatingMiniAppHost
    LaunchedEffect(appState?.converterInitialFiles?.size, initialFiles.size) {
        val initFiles = (appState?.converterInitialFiles?.toList().orEmpty() + initialFiles).distinctBy { it.absolutePath }
        if (initFiles.isNotEmpty()) {
            if (initFiles.size == 1) {
                val first = initFiles.first()
                val item = FileConverterDispatcher.createQueueItem(first)
                if (item != null) {
                    singleItem = item
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

    // Sync student preset defaults when preset selection changes
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

    // File Picker Dialogs
    if (showStudentImagePicker) {
        InAppFilePickerDialog(
            title = "Select Photo or Signature Image",
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
            title = "Select PDF Document to Compress",
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
            title = "Select Any File to Convert (VERT Engine)",
            selectFolders = false,
            onDismiss = { showSingleFilePicker = false },
            onSelected = { file ->
                showSingleFilePicker = false
                val item = FileConverterDispatcher.createQueueItem(file)
                if (item != null) {
                    singleItem = item
                } else {
                    Toast.makeText(context, "Format .${file.extension} is not supported for conversion", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showBatchFilePicker) {
        InAppFilePickerDialog(
            title = "Add Files to Batch Queue",
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
            title = "Select Output Folder",
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Unified 50.dp Flush Top Heading Bar with Triple-Line Menu Button on Left
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
                IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Toggle Navigation Sidebar",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.Transform,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "File Converter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "100% On-Device • Squoosh KB + VERT Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                // Output Folder Pill
                AssistChip(
                    onClick = { showCustomFolderPicker = true },
                    label = {
                        Text(
                            text = when (destinationMode) {
                                OutputDestinationMode.CONVERTED_SUBFOLDER -> "Out: /Converted/"
                                OutputDestinationMode.SAME_AS_SOURCE -> "Out: Same Folder"
                                OutputDestinationMode.CUSTOM_FOLDER -> "Out: ${customOutputFolder.name}"
                            },
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.height(30.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

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

        // Minimalist 3-Tab Switcher
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConverterTopTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { activeTab = tab }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tab.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Body Content per Active Tab
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 700.dp

            when (activeTab) {
                ConverterTopTab.QUICK_PRESETS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Sub-mode switcher: Student Exam Photo/Signature KB vs PDF Size Compressor
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = presetSubMode == 0,
                                    onClick = {
                                        presetSubMode = 0
                                        presetOutputResult = null
                                        presetErrorText = null
                                    },
                                    label = { Text("Student Photo & Signature KB Resizer", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = presetSubMode == 1,
                                    onClick = {
                                        presetSubMode = 1
                                        presetOutputResult = null
                                        presetErrorText = null
                                    },
                                    label = { Text("PDF Size Compressor", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (presetSubMode == 0) {
                            // STUDENT PHOTO & SIGNATURE EXACT KB RESIZER
                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "1. Select Photo or Signature Image",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = studentSourceFile?.let {
                                                        "${it.name} • Original: ${Formatter.formatFileSize(context, it.length())}"
                                                    } ?: "Supports JPG, PNG, WEBP, BMP for online exam portals (SSC, UPSC, JEE, NEET, IBPS, State Portals)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { showStudentImagePicker = true },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (studentSourceFile == null) "Pick Image" else "Change")
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "2. Choose Exam Preset or Custom KB Target",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        StudentPresetType.entries.forEach { preset ->
                                            val isSelected = selectedStudentPreset == preset
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedStudentPreset = preset }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { selectedStudentPreset = preset }
                                                        )
                                                        Column {
                                                            Text(
                                                                text = preset.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = preset.subtitle,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider()

                                        // Fine-tune Target KB Range & Dimensions
                                        Text(
                                            text = "Target File Size Range: ${customMinKb.roundToInt()} KB – ${customMaxKb.roundToInt()} KB",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("Max KB:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                                            Slider(
                                                value = customMaxKb,
                                                onValueChange = {
                                                    customMaxKb = it
                                                    if (customMinKb >= it) customMinKb = (it - 5f).coerceAtLeast(0f)
                                                },
                                                valueRange = 8f..300f,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("${customMaxKb.roundToInt()} KB", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customWidthInput,
                                                onValueChange = { customWidthInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Width (px, blank = auto)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = customHeightInput,
                                                onValueChange = { customHeightInput = it.filter { c -> c.isDigit() } },
                                                label = { Text("Height (px, blank = auto)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("High-Contrast Signature Ink Cleanup", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                Text("Turns gray paper background pure white & sharpens dark pen ink", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Switch(
                                                checked = enhanceSignatureInk,
                                                onCheckedChange = { enhanceSignatureInk = it }
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val src = studentSourceFile
                                                if (src == null || !src.exists()) {
                                                    Toast.makeText(context, "Please pick an image first", Toast.LENGTH_SHORT).show()
                                                    return@Button
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
                                                    val wPx = customWidthInput.toIntOrNull() ?: 0
                                                    val hPx = customHeightInput.toIntOrNull() ?: 0
                                                    val res = SquooshImageCompressor.compressImageToTargetKb(
                                                        context = context,
                                                        sourceFile = src,
                                                        outputFile = outFile,
                                                        minTargetKb = customMinKb.roundToInt(),
                                                        maxTargetKb = customMaxKb.roundToInt(),
                                                        exactWidthPx = wPx,
                                                        exactHeightPx = hPx,
                                                        enhanceSignatureContrast = enhanceSignatureInk,
                                                        onProgress = { _, msg -> presetProgressText = msg }
                                                    )
                                                    presetProcessing = false
                                                    res.onSuccess { generated ->
                                                        presetOutputResult = generated
                                                    }.onFailure { err ->
                                                        presetErrorText = err.message ?: "Compression failed"
                                                    }
                                                }
                                            },
                                            enabled = !presetProcessing && studentSourceFile != null,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (presetProcessing) presetProgressText.ifBlank { "Compressing..." }
                                                else "Generate Exact ${customMinKb.roundToInt()}–${customMaxKb.roundToInt()} KB JPG Now",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // PDF SIZE COMPRESSOR
                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "1. Select PDF Document to Compress",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = pdfSourceFile?.let {
                                                        "${it.name} • Original Size: ${Formatter.formatFileSize(context, it.length())}"
                                                    } ?: "Shrink scanned PDFs, certificates & forms for portal uploads",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { showPdfFilePicker = true },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (pdfSourceFile == null) "Pick PDF" else "Change")
                                            }
                                        }

                                        HorizontalDivider()

                                        Text(
                                            text = "2. Select PDF Compression Level",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        PdfCompressionLevel.entries.forEach { level ->
                                            val isSelected = selectedPdfCompression == level
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedPdfCompression = level }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { selectedPdfCompression = level }
                                                    )
                                                    Column {
                                                        Text(level.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        Text(level.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = if (pdfTargetMaxKb <= 0f) "Max PDF Size: Auto"
                                                else "Max PDF Budget: ${pdfTargetMaxKb.roundToInt()} KB",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.width(150.dp)
                                            )
                                            Slider(
                                                value = pdfTargetMaxKb,
                                                onValueChange = { pdfTargetMaxKb = it },
                                                valueRange = 0f..2000f,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                val src = pdfSourceFile
                                                if (src == null || !src.exists()) {
                                                    Toast.makeText(context, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
                                                    return@Button
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
                                                    res.onSuccess { generated ->
                                                        presetOutputResult = generated
                                                    }.onFailure { err ->
                                                        presetErrorText = err.message ?: "PDF compression failed"
                                                    }
                                                }
                                            },
                                            enabled = !presetProcessing && pdfSourceFile != null,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (presetProcessing) presetProgressText.ifBlank { "Compressing PDF..." }
                                                else "Compress PDF Now",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Output Result Banner for Tab 1
                        presetOutputResult?.let { out ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ready: ${out.name} (${Formatter.formatFileSize(context, out.length())})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = out.absolutePath,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = { appState?.open(out.toUniversal()) }
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Preview")
                                        }
                                    }
                                }
                            }
                        }

                        presetErrorText?.let { err ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                ConverterTopTab.SINGLE_FILE -> {
                    // TAB 2: VERT-STYLE DYNAMIC SINGLE FILE CONVERTER
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSingleFilePicker = true }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = singleItem?.sourceFile?.name ?: "Select Any File to See Available Target Formats",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = singleItem?.let {
                                            "Detected: .${it.sourceExtension.uppercase()} (${it.category.title}) • ${Formatter.formatFileSize(context, it.sourceFile.length())}"
                                        } ?: "Images (PNG, JPG, WEBP, BMP, ICO, SVG, PDF) • Docs & Data (MD, HTML, PDF, TXT, JSON, CSV, XML, YAML, DOCX, XLSX) • Media (MP4, WAV, M4A, AAC) • Archives (ZIP, TAR, GZ)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    FilledTonalButton(onClick = { showSingleFilePicker = true }) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (singleItem == null) "Browse & Pick File" else "Pick Different File")
                                    }
                                }
                            }
                        }

                        singleItem?.let { current ->
                            item {
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Available Output Formats for .${current.sourceExtension.uppercase()}:",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            current.availableTargets.forEach { fmt ->
                                                val isChosen = current.targetFormat.equals(fmt, ignoreCase = true)
                                                FilterChip(
                                                    selected = isChosen,
                                                    onClick = { current.targetFormat = fmt },
                                                    label = {
                                                        Text(
                                                            text = ".${fmt.uppercase()}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    },
                                                    leadingIcon = if (isChosen) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                                    } else null
                                                )
                                            }
                                        }

                                        if (current.category == ConversionCategory.IMAGE) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("Quality: ${current.tuning.imageQuality}%", style = MaterialTheme.typography.labelMedium)
                                                Slider(
                                                    value = current.tuning.imageQuality.toFloat(),
                                                    onValueChange = {
                                                        current.tuning = current.tuning.copy(imageQuality = it.roundToInt())
                                                    },
                                                    valueRange = 15f..100f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
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
                                            enabled = !singleConverting,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (singleConverting) current.statusDetail.ifBlank { "Converting..." }
                                                else "Convert ${current.sourceFile.name} → .${current.targetFormat.uppercase()}",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        current.outputFile?.let { out ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "Saved: ${out.name} (${Formatter.formatFileSize(context, out.length())})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            out.absolutePath,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    FilledTonalButton(onClick = { appState?.open(out.toUniversal()) }) {
                                                        Text("Open")
                                                    }
                                                }
                                            }
                                        }

                                        current.errorMessage?.let { err ->
                                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ConverterTopTab.BATCH_CONVERT -> {
                    // TAB 3: BATCH CONVERT & COMBINE IMAGES TO PDF
                    val commonTargets = remember(batchQueue.size) {
                        if (batchQueue.isEmpty()) emptyList()
                        else batchQueue.map { it.availableTargets.toSet() }
                            .reduceOrNull { acc, set -> acc.intersect(set) }
                            ?.toList()
                            .orEmpty()
                    }
                    val allAreImages = remember(batchQueue.size) {
                        batchQueue.isNotEmpty() && batchQueue.all { it.category == ConversionCategory.IMAGE }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showBatchFilePicker = true },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Files (${batchQueue.size})")
                                    }
                                    if (batchQueue.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { batchQueue.clear() },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }

                                if (batchQueue.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (allAreImages && batchQueue.size >= 2) {
                                            FilledTonalButton(
                                                onClick = {
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
                                                enabled = !isBatchRunning,
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Combine into 1 PDF")
                                            }
                                        }

                                        Button(
                                            onClick = {
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
                                            enabled = !isBatchRunning,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isBatchRunning) "Converting..." else "Convert All")
                                        }
                                    }
                                }
                            }
                        }

                        if (commonTargets.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Convert All To:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    commonTargets.forEach { target ->
                                        FilterChip(
                                            selected = globalTargetFormat == target,
                                            onClick = {
                                                globalTargetFormat = target
                                                batchQueue.forEach { it.targetFormat = target }
                                            },
                                            label = { Text(".${target.uppercase()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                        )
                                    }
                                }
                            }
                        }

                        if (batchQueue.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showBatchFilePicker = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.LibraryAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                        Text("Batch Queue is Empty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Tap 'Add Files' to queue multiple images, documents, audio/video files, or archives for batch conversion or multi-image PDF merging.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(batchQueue, key = { it.id }) { item ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.sourceFile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "${Formatter.formatFileSize(context, item.sourceFile.length())} • ${item.status.name}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { batchQueue.remove(item) }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            item.availableTargets.forEach { fmt ->
                                                FilterChip(
                                                    selected = item.targetFormat.equals(fmt, true),
                                                    onClick = { item.targetFormat = fmt },
                                                    label = { Text(".${fmt.uppercase()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                                )
                                            }
                                        }

                                        item.outputFile?.let { out ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Saved: ${out.name} (${Formatter.formatFileSize(context, out.length())})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                TextButton(onClick = { appState?.open(out.toUniversal()) }) {
                                                    Text("Open", style = MaterialTheme.typography.labelSmall)
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
}
