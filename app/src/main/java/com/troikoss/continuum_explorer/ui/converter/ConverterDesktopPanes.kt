package com.troikoss.continuum_explorer.ui.converter

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import com.troikoss.continuum_explorer.utils.IconHelper
import com.troikoss.continuum_explorer.utils.toUniversal
import java.io.File
import kotlin.math.roundToInt

/**
 * 2-Pane Desktop & Mobile Workbenches for File Converter (Ultra-Clean Tonal Flat M3):
 * 1. [ConverterPresetsWorkbench]: Left controls (2x2 preset tiles + sliders) + Right Live Before/After Stage.
 * 2. [ConverterSingleFileWorkbench]: Left Source Card & Quality + Right wrapping FlowRow Format Matrix.
 * 3. [ConverterBatchWorkbench]: Left dense tonal Queue Table + Right Batch Inspector.
 */
@Composable
fun ConverterPresetsWorkbench(
    isWide: Boolean,
    presetSubMode: Int,
    onSubModeChange: (Int) -> Unit,
    selectedStudentPreset: StudentPresetType,
    onSelectStudentPreset: (StudentPresetType) -> Unit,
    studentSourceFile: File?,
    onPickStudentImage: () -> Unit,
    customMinKb: Float,
    onMinKbChange: (Float) -> Unit,
    customMaxKb: Float,
    onMaxKbChange: (Float) -> Unit,
    customWidthInput: String,
    onWidthInputChange: (String) -> Unit,
    customHeightInput: String,
    onHeightInputChange: (String) -> Unit,
    enhanceSignatureInk: Boolean,
    onEnhanceInkChange: (Boolean) -> Unit,
    pdfSourceFile: File?,
    onPickPdf: () -> Unit,
    selectedPdfCompression: PdfCompressionLevel,
    onSelectPdfCompression: (PdfCompressionLevel) -> Unit,
    pdfTargetMaxKb: Float,
    onPdfTargetMaxKbChange: (Float) -> Unit,
    presetProcessing: Boolean,
    presetProgressText: String,
    presetOutputResult: File?,
    presetErrorText: String?,
    onRunImagePreset: () -> Unit,
    onRunPdfCompress: () -> Unit,
    onOpenOutput: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val controlsPane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(
            modifier = paneMod
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sub-mode pill switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TonalModeChip(
                    label = "Photo & Sign",
                    icon = Icons.Default.Badge,
                    selected = presetSubMode == 0,
                    onClick = { onSubModeChange(0) },
                    modifier = Modifier.weight(1f)
                )
                TonalModeChip(
                    label = "PDF Size",
                    icon = Icons.Default.PictureAsPdf,
                    selected = presetSubMode == 1,
                    onClick = { onSubModeChange(1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (presetSubMode == 0) {
                // Source Image Picker Strip
                TonalSourceSelectorRow(
                    label = studentSourceFile?.name ?: "Select Image",
                    file = studentSourceFile,
                    buttonText = if (studentSourceFile == null) "Pick" else "Change",
                    onPick = onPickStudentImage
                )

                // 2x2 Tonal Preset Grid
                val presets = StudentPresetType.entries
                val rows = presets.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { preset ->
                                val selected = selectedStudentPreset == preset
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectStudentPreset(preset) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(11.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = when (preset) {
                                                StudentPresetType.PASSPORT_PHOTO_STANDARD -> "Passport Photo"
                                                StudentPresetType.PASSPORT_PHOTO_HD -> "Passport HD"
                                                StudentPresetType.SIGNATURE_STANDARD -> "Signature"
                                                StudentPresetType.SIGNATURE_WIDE -> "Wide Signature"
                                                StudentPresetType.CUSTOM_KB -> "Custom KB"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (preset == StudentPresetType.CUSTOM_KB) "Custom Range"
                                            else "${preset.minKb}–${preset.maxKb} KB • ${preset.targetWidthPx}×${preset.targetHeightPx}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Target KB Sliders inside tonal surface
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Size", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${customMinKb.roundToInt()}–${customMaxKb.roundToInt()} KB",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Min", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            Slider(
                                value = customMinKb,
                                onValueChange = {
                                    onMinKbChange(it)
                                    if (it >= customMaxKb) onMaxKbChange((it + 5f).coerceAtMost(500f))
                                },
                                valueRange = 0f..300f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Max", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            Slider(
                                value = customMaxKb,
                                onValueChange = {
                                    onMaxKbChange(it)
                                    if (it <= customMinKb) onMinKbChange((it - 5f).coerceAtLeast(0f))
                                },
                                valueRange = 5f..500f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customWidthInput,
                                onValueChange = { onWidthInputChange(it.filter { ch -> ch.isDigit() }) },
                                label = { Text("Width px") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customHeightInput,
                                onValueChange = { onHeightInputChange(it.filter { ch -> ch.isDigit() }) },
                                label = { Text("Height px") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ink Cleanup", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Switch(checked = enhanceSignatureInk, onCheckedChange = onEnhanceInkChange)
                        }
                    }
                }

                Button(
                    onClick = onRunImagePreset,
                    enabled = !presetProcessing && studentSourceFile != null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("converter_run_preset_button")
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (presetProcessing) presetProgressText.ifBlank { "Compressing..." }
                        else "Generate ${customMinKb.roundToInt()}–${customMaxKb.roundToInt()} KB",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // PDF Size Compressor
                TonalSourceSelectorRow(
                    label = pdfSourceFile?.name ?: "Select PDF",
                    file = pdfSourceFile,
                    buttonText = if (pdfSourceFile == null) "Pick" else "Change",
                    onPick = onPickPdf
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfCompressionLevel.entries.forEach { level ->
                        val selected = selectedPdfCompression == level
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPdfCompression(level) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = level.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${level.jpegQuality}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Budget", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (pdfTargetMaxKb <= 0f) "Auto" else "${pdfTargetMaxKb.roundToInt()} KB",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = pdfTargetMaxKb,
                            onValueChange = onPdfTargetMaxKbChange,
                            valueRange = 0f..2000f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Button(
                    onClick = onRunPdfCompress,
                    enabled = !presetProcessing && pdfSourceFile != null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (presetProcessing) presetProgressText.ifBlank { "Compressing..." } else "Compress PDF",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (presetErrorText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = presetErrorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    val liveStagePane: @Composable (Modifier) -> Unit = { paneMod ->
        PresetsLiveStage(
            presetSubMode = presetSubMode,
            sourceFile = if (presetSubMode == 0) studentSourceFile else pdfSourceFile,
            outputFile = presetOutputResult,
            customMinKb = customMinKb.roundToInt(),
            customMaxKb = customMaxKb.roundToInt(),
            customWidthInput = customWidthInput,
            customHeightInput = customHeightInput,
            pdfTargetMaxKb = pdfTargetMaxKb.roundToInt(),
            selectedPdfCompression = selectedPdfCompression,
            onOpenOutput = onOpenOutput,
            modifier = paneMod
        )
    }

    if (isWide) {
        Row(modifier = modifier.fillMaxSize()) {
            controlsPane(Modifier.weight(0.55f).fillMaxHeight())
            TonalVerticalPaneDivider()
            liveStagePane(Modifier.weight(0.45f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            controlsPane(Modifier.fillMaxWidth())
            liveStagePane(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PresetsLiveStage(
    presetSubMode: Int,
    sourceFile: File?,
    outputFile: File?,
    customMinKb: Int,
    customMaxKb: Int,
    customWidthInput: String,
    customHeightInput: String,
    pdfTargetMaxKb: Int,
    selectedPdfCompression: PdfCompressionLevel,
    onOpenOutput: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (sourceFile == null || !sourceFile.exists()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (presetSubMode == 0) Icons.Default.Image else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Live Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val srcDims = remember(sourceFile, presetSubMode) {
                if (presetSubMode == 0) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
                    if (opts.outWidth > 0) "${opts.outWidth}×${opts.outHeight}" else "Image"
                } else "PDF"
            }
            val outDims = remember(outputFile, presetSubMode) {
                if (outputFile != null && outputFile.exists() && presetSubMode == 0) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(outputFile.absolutePath, opts)
                    if (opts.outWidth > 0) "${opts.outWidth}×${opts.outHeight}" else "JPG"
                } else if (outputFile != null) "PDF" else null
            }
            val srcKb = (sourceFile.length() / 1024.0).coerceAtLeast(0.1)
            val outKb = outputFile?.let { (it.length() / 1024.0).coerceAtLeast(0.1) }
            val savedPct = if (outKb != null) (((srcKb - outKb) / srcKb) * 100.0).roundToInt() else null

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comparison",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (savedPct != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (savedPct >= 0) "-$savedPct%" else "+${-savedPct}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Before card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(56.dp)) {
                            IconHelper.FileThumbnail(file = sourceFile.toUniversal(), modifier = Modifier.fillMaxSize())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = Formatter.formatFileSize(context, sourceFile.length()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = srcDims,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // After / Target card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (outputFile != null) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (outputFile != null && outputFile.exists()) {
                            Box(modifier = Modifier.size(56.dp)) {
                                IconHelper.FileThumbnail(file = outputFile.toUniversal(), modifier = Modifier.fillMaxSize())
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = Formatter.formatFileSize(context, outputFile.length()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = outDims ?: "Ready",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { onOpenOutput(outputFile) },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text("Open")
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (presetSubMode == 0) "$customMinKb–$customMaxKb KB"
                                    else if (pdfTargetMaxKb > 0) "≤ $pdfTargetMaxKb KB"
                                    else selectedPdfCompression.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (presetSubMode == 0) {
                                    Text(
                                        text = "${customWidthInput.ifBlank { "Auto" }}×${customHeightInput.ifBlank { "Auto" }} px",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterSingleFileWorkbench(
    isWide: Boolean,
    singleItem: ConverterQueueItem?,
    singleConverting: Boolean,
    onPickFile: () -> Unit,
    onTargetFormatChange: (String) -> Unit,
    onImageQualityChange: (Int) -> Unit,
    onConvert: () -> Unit,
    onOpenOutput: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val sourcePane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(
            modifier = paneMod
                .background(if (isWide) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Source",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onPickFile)
                    .testTag("converter_single_pick_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (singleItem != null) {
                        Box(modifier = Modifier.size(52.dp)) {
                            IconHelper.FileThumbnail(
                                file = singleItem.sourceFile.toUniversal(),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = singleItem.sourceFile.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ".${singleItem.sourceExtension.uppercase()} • ${Formatter.formatFileSize(context, singleItem.sourceFile.length())}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Pick File",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FilledTonalButton(
                        onClick = onPickFile,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (singleItem == null) "Browse" else "Change")
                    }
                }
            }

            if (singleItem != null && singleItem.category == ConversionCategory.IMAGE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Quality", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${singleItem.tuning.imageQuality}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = singleItem.tuning.imageQuality.toFloat(),
                            onValueChange = { onImageQualityChange(it.roundToInt()) },
                            valueRange = 15f..100f
                        )
                    }
                }
            }

            if (singleItem != null) {
                Button(
                    onClick = onConvert,
                    enabled = !singleConverting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("converter_single_run_button")
                ) {
                    Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (singleConverting) singleItem.statusDetail.ifBlank { "Converting..." }
                        else "Convert → .${singleItem.targetFormat.uppercase()}",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    val targetsPane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(
            modifier = paneMod
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (singleItem == null) {
                Text(
                    text = "Supported Formats",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ConversionFormatMatrix.getFormatSummaryPairs(ConversionCategory.ALL).forEach { (src, dst) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = src,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(0.42f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = dst,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.58f)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    singleItem.availableTargets.forEach { fmt ->
                        val selected = singleItem.targetFormat.equals(fmt, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onTargetFormatChange(fmt) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = ".${fmt.uppercase()}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                singleItem.outputFile?.let { out ->
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
                                    text = out.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = Formatter.formatFileSize(context, out.length()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { onOpenOutput(out) },
                                shape = RoundedCornerShape(999.dp)
                            ) { Text("Open") }
                        }
                    }
                }

                singleItem.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
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

    if (isWide) {
        Row(modifier = modifier.fillMaxSize()) {
            sourcePane(Modifier.width(310.dp).fillMaxHeight())
            TonalVerticalPaneDivider()
            targetsPane(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            sourcePane(Modifier.fillMaxWidth())
            targetsPane(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterBatchWorkbench(
    isWide: Boolean,
    batchQueue: List<ConverterQueueItem>,
    commonTargets: List<String>,
    allAreImages: Boolean,
    globalTargetFormat: String?,
    isBatchRunning: Boolean,
    onAddFiles: () -> Unit,
    onClearQueue: () -> Unit,
    onSelectGlobalTarget: (String) -> Unit,
    onItemTargetChange: (ConverterQueueItem, String) -> Unit,
    onRemoveItem: (ConverterQueueItem) -> Unit,
    onCombineToPdf: () -> Unit,
    onConvertAll: () -> Unit,
    onOpenOutput: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val queuePane: @Composable (Modifier) -> Unit = { paneMod ->
        LazyColumn(
            modifier = paneMod.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (batchQueue.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onAddFiles)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Queue Empty",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            FilledTonalButton(onClick = onAddFiles, shape = RoundedCornerShape(999.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Files")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(batchQueue, key = { _, it -> it.id }) { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.sourceFile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = Formatter.formatFileSize(context, item.sourceFile.length()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (item.status) {
                                        ConversionStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                                        ConversionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                                        ConversionStatus.CONVERTING -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Text(
                                        text = ".${item.targetFormat.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveItem(item) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(15.dp))
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                item.availableTargets.forEach { fmt ->
                                    val chosen = item.targetFormat.equals(fmt, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (chosen) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onItemTargetChange(item, fmt) }
                                    ) {
                                        Text(
                                            text = ".${fmt.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (chosen) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            item.outputFile?.let { out ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${out.name} (${Formatter.formatFileSize(context, out.length())})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { onOpenOutput(out) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("Open", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val inspectorPane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(
            modifier = paneMod
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Batch (${batchQueue.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onAddFiles,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
                if (batchQueue.isNotEmpty()) {
                    TextButton(
                        onClick = onClearQueue,
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Clear") }
                }
            }

            if (commonTargets.isNotEmpty()) {
                Text(
                    text = "Apply Format to All",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonTargets.forEach { target ->
                        val selected = globalTargetFormat == target
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectGlobalTarget(target) }
                        ) {
                            Text(
                                text = ".${target.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (allAreImages && batchQueue.size >= 2) {
                FilledTonalButton(
                    onClick = onCombineToPdf,
                    enabled = !isBatchRunning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Combine to PDF")
                }
            }

            if (batchQueue.isNotEmpty()) {
                Button(
                    onClick = onConvertAll,
                    enabled = !isBatchRunning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("converter_batch_run_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBatchRunning) "Converting..." else "Convert All",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (isWide) {
        Row(modifier = modifier.fillMaxSize()) {
            queuePane(Modifier.weight(1f).fillMaxHeight())
            TonalVerticalPaneDivider()
            inspectorPane(Modifier.width(290.dp).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            inspectorPane(Modifier.fillMaxWidth())
            queuePane(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun TonalModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TonalSourceSelectorRow(
    label: String,
    file: File?,
    buttonText: String,
    onPick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (file != null && file.exists()) {
                    Text(
                        text = Formatter.formatFileSize(context, file.length()),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onPick,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
