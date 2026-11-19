package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class PhotoToolTab(val label: String, val icon: ImageVector) {
    FILTER("Filters", Icons.Default.AutoFixHigh),
    ADJUST("Adjust", Icons.Default.Tune),
    CROP("Crop", Icons.Default.Crop),
    CANVAS("Canvas", Icons.Default.AspectRatio),
    DRAW("Draw", Icons.Default.Brush),
    TEXT("Text", Icons.Default.TextFields)
}

/**
 * Left 68.dp Vertical Tonal Tool Rail for Desktop Photo Studio.
 */
@Composable
internal fun PhotoStudioToolRail(
    activeTab: PhotoToolTab,
    onSelectTab: (PhotoToolTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = modifier
            .width(68.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhotoToolTab.entries.forEach { tab ->
                val selected = activeTab == tab
                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Borderless tonal selectable pill used inside the Photo Studio Inspector & mobile tool tray.
 */
@Composable
private fun TonalInspectorPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(34.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Right-hand 268.dp Desktop Properties Inspector (or compact mobile bottom sheet content).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoStudioInspectorContent(
    activeTab: PhotoToolTab,
    isDesktopRail: Boolean,
    selectedPreset: FilterPreset,
    onSelectPreset: (FilterPreset) -> Unit,
    colorAdjustments: ColorAdjustments,
    onAdjustmentsChange: (ColorAdjustments) -> Unit,
    cropState: CropTransformState,
    onApplyCrop: () -> Unit,
    canvasBgColor: Color,
    onSelectCanvasBg: (Color) -> Unit,
    onApplyCanvasFrame: (Int, Int, Float, Color) -> Unit,
    onImportImageLayer: () -> Unit,
    onResetCanvas: () -> Unit,
    drawingState: DrawingState,
    onAddTextClick: () -> Unit,
    onAddEmoji: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerModifier = if (isDesktopRail) {
        modifier
            .width(268.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }

    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isDesktopRail) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = activeTab.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = activeTab.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        when (activeTab) {
            PhotoToolTab.FILTER -> {
                if (isDesktopRail) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterPreset.entries.forEach { preset ->
                            TonalInspectorPill(
                                label = "${preset.icon} ${preset.displayName}",
                                selected = selectedPreset == preset,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterPreset.entries.forEach { preset ->
                            TonalInspectorPill(
                                label = "${preset.icon} ${preset.displayName}",
                                selected = selectedPreset == preset,
                                onClick = { onSelectPreset(preset) }
                            )
                        }
                    }
                }
            }

            PhotoToolTab.ADJUST -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdjustSliderRow(
                        label = "Brightness",
                        value = colorAdjustments.brightness,
                        range = -80f..80f,
                        onValueChange = { onAdjustmentsChange(colorAdjustments.copy(brightness = it)) }
                    )
                    AdjustSliderRow(
                        label = "Contrast",
                        value = colorAdjustments.contrast,
                        range = 0.5f..1.8f,
                        onValueChange = { onAdjustmentsChange(colorAdjustments.copy(contrast = it)) }
                    )
                    AdjustSliderRow(
                        label = "Saturation",
                        value = colorAdjustments.saturation,
                        range = 0f..2.0f,
                        onValueChange = { onAdjustmentsChange(colorAdjustments.copy(saturation = it)) }
                    )
                    if (isDesktopRail) {
                        FilledTonalButton(
                            onClick = { onAdjustmentsChange(ColorAdjustments()) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset Adjustments", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            PhotoToolTab.CROP -> {
                if (isDesktopRail) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CropAspectRatio.entries.forEach { ratio ->
                            TonalInspectorPill(
                                label = ratio.label,
                                selected = cropState.selectedAspectRatio == ratio,
                                onClick = { cropState.applyAspectRatio(ratio) }
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { cropState.rotate90Clockwise() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rotate 90°", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = onApplyCrop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply Crop", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CropAspectRatio.entries.forEach { ratio ->
                            TonalInspectorPill(
                                label = ratio.label,
                                selected = cropState.selectedAspectRatio == ratio,
                                onClick = { cropState.applyAspectRatio(ratio) }
                            )
                        }
                        TonalInspectorPill("Rotate 90°", false, { cropState.rotate90Clockwise() })
                        Button(
                            onClick = onApplyCrop,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Apply", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            PhotoToolTab.CANVAS -> {
                val bgChoices = listOf(
                    Color.White to "White",
                    Color(0xFF181A20) to "Dark",
                    Color.Black to "Black",
                    Color(0xFFF5F0E6) to "Cream"
                )
                if (isDesktopRail) {
                    Text(
                        text = "Backdrop",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bgChoices.forEach { (bgCol, label) ->
                            TonalInspectorPill(label, canvasBgColor == bgCol, { onSelectCanvasBg(bgCol) })
                        }
                    }
                    Text(
                        text = "Frame Ratio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TonalInspectorPill("+Matte", false, { onApplyCanvasFrame(0, 0, 0.14f, canvasBgColor) })
                        TonalInspectorPill("1:1 Square", false, { onApplyCanvasFrame(1, 1, 0.08f, canvasBgColor) })
                        TonalInspectorPill("16:9 Wide", false, { onApplyCanvasFrame(16, 9, 0.08f, canvasBgColor) })
                    }
                    FilledTonalButton(
                        onClick = onImportImageLayer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Image Layer", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onResetCanvas,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Canvas", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bgChoices.forEach { (bgCol, label) ->
                            TonalInspectorPill(label, canvasBgColor == bgCol, { onSelectCanvasBg(bgCol) })
                        }
                        TonalInspectorPill("+Matte", false, { onApplyCanvasFrame(0, 0, 0.14f, canvasBgColor) })
                        TonalInspectorPill("1:1", false, { onApplyCanvasFrame(1, 1, 0.08f, canvasBgColor) })
                        TonalInspectorPill("16:9", false, { onApplyCanvasFrame(16, 9, 0.08f, canvasBgColor) })
                        FilledTonalButton(
                            onClick = onImportImageLayer,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Layer", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = onResetCanvas, modifier = Modifier.height(34.dp)) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            PhotoToolTab.DRAW -> {
                val palette = listOf(
                    Color(0xFFE53935),
                    Color(0xFF1E88E5),
                    Color(0xFF43A047),
                    Color(0xFFFDD835),
                    Color(0xFFAB47BC),
                    Color.White,
                    Color.Black
                )
                if (isDesktopRail) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShapeType.entries.forEach { shape ->
                            TonalInspectorPill(
                                label = shape.label,
                                selected = drawingState.shapeType == shape,
                                onClick = { drawingState.shapeType = shape }
                            )
                        }
                    }
                    Text(
                        text = "Stroke Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        palette.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (drawingState.strokeColor == col) 2.5.dp else 1.dp,
                                        color = if (drawingState.strokeColor == col) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { drawingState.strokeColor = col }
                            )
                        }
                    }
                    AdjustSliderRow(
                        label = "Size",
                        value = drawingState.strokeWidth,
                        range = 2f..42f,
                        onValueChange = { drawingState.strokeWidth = it }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShapeType.entries.forEach { shape ->
                            TonalInspectorPill(shape.label, drawingState.shapeType == shape, { drawingState.shapeType = shape })
                        }
                        palette.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (drawingState.strokeColor == col) 2.dp else 1.dp,
                                        color = if (drawingState.strokeColor == col) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { drawingState.strokeColor = col }
                            )
                        }
                    }
                }
            }

            PhotoToolTab.TEXT -> {
                val emojis = listOf("⭐", "🔥", "✅", "❤️", "📌", "🎯", "✨")
                if (isDesktopRail) {
                    Button(
                        onClick = onAddTextClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Text Layer", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = "Stickers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojis.forEach { emoji ->
                            TonalInspectorPill(emoji, false, { onAddEmoji(emoji) })
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onAddTextClick,
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Text", style = MaterialTheme.typography.labelSmall)
                        }
                        emojis.forEach { emoji ->
                            TonalInspectorPill(emoji, false, { onAddEmoji(emoji) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f", value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
