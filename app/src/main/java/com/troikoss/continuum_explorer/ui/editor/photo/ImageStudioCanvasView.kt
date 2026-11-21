package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troikoss.continuum_explorer.ui.components.suite.SingleRowCommandBar
import com.troikoss.continuum_explorer.ui.components.suite.SlimStatusStrip
import com.troikoss.continuum_explorer.ui.components.suite.TonalVerticalPaneDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Vector geometric shapes supported by DrawingView and Photo Studio.
 */
enum class ShapeType(val label: String) {
    FREEHAND("Brush"),
    LINE("Line"),
    RECTANGLE("Rect"),
    CIRCLE("Circle"),
    ARROW("Arrow")
}

/**
 * Photo filter types supported by PhotoFilterHelper and Photo Studio.
 */
enum class PhotoFilterType(val displayName: String) {
    NONE("Original"),
    AUTO_FIX("Auto Fix"),
    BRIGHTNESS("Bright"),
    CONTRAST("Contrast"),
    SEPIA("Sepia"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W"),
    SATURATE("Saturate"),
    TEMPERATURE("Warm"),
    TINT("Tint"),
    VIGNETTE("Vignette"),
    SHARPEN("Sharpen"),
    LOMOISH("Lomo"),
    CROSS_PROCESS("Cross"),
    DOCUMENT("Doc"),
    FILL_LIGHT("Fill Light"),
    FISHEYE("Fisheye"),
    GRAIN("Grain"),
    NEGATIVE("Invert"),
    POSTERIZE("Posterize"),
    ROTATE("Swap RGB")
}

data class StrokeItem(
    val path: Path = Path(),
    val color: Color = Color(0xFFE53935),
    val strokeWidth: Float = 10f,
    val isEraser: Boolean = false,
    val shapeType: ShapeType = ShapeType.FREEHAND,
    val startPoint: Offset = Offset.Zero,
    val endPoint: Offset = Offset.Zero
)

data class TextOverlayItem(
    val text: String,
    val color: Color = Color.White,
    val backgroundColor: Color = Color.Transparent,
    val fontSize: Float = 26f,
    var position: Offset = Offset(160f, 220f)
)

data class StickerOverlayItem(
    val emojiOrSticker: String,
    val scale: Float = 1.0f,
    var position: Offset = Offset(200f, 240f)
)

class PhotoEditorStateController {
    var rotationAngle by mutableIntStateOf(0)
    var isFlipHorizontal by mutableStateOf(false)
    var isFlipVertical by mutableStateOf(false)
    var activeFilter by mutableStateOf(PhotoFilterType.NONE)

    val strokes = mutableStateListOf<StrokeItem>()
    val textOverlays = mutableStateListOf<TextOverlayItem>()
    val stickerOverlays = mutableStateListOf<StickerOverlayItem>()

    var watermarkText by mutableStateOf("")
    var watermarkOpacity by mutableFloatStateOf(60f)
    var watermarkPosition by mutableStateOf("BOTTOM_RIGHT")

    var targetWidthInput by mutableStateOf("")
    var targetHeightInput by mutableStateOf("")
}

@Composable
fun rememberPhotoEditorStateController(): PhotoEditorStateController {
    return remember { PhotoEditorStateController() }
}

/**
 * Single-Line 48.dp Top Bar for Image Viewer default mode (zero stacked subtitle rows).
 */
@Composable
fun PhotoEditorViewTopBar(
    fileName: String,
    imageWidth: Int,
    imageHeight: Int,
    fileSizeFormatted: String,
    onMenuClick: () -> Unit,
    onEnterEditMode: () -> Unit,
    onShowExifDialog: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    SingleRowCommandBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        navigationContent = {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        titleContent = {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actionsContent = {
            FilledTonalButton(
                onClick = onEnterEditMode,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }

            IconButton(onClick = onShowExifDialog, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info ($imageWidth×$imageHeight • $fileSizeFormatted)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Image Info & EXIF") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { showMenu = false; onShowExifDialog() }
                    )
                    DropdownMenuItem(
                        text = { Text("Print") },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                        onClick = { showMenu = false; onPrint() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move To...") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = { showMenu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy To...") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; onCopy() }
                    )
                    DropdownMenuItem(
                        text = { Text("Open External") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                        onClick = { showMenu = false; onOpenExternal() }
                    )
                }
            }
        }
    )
}


@Composable
private fun TonalPhotoPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * Desktop-First Ultra-Clean Tonal Flat Photo Studio.
 * - Strict Single 48.dp Command Bar (Undo/Redo, Compare Original, Reset, Save).
 * - Desktop (>= 760.dp): Left 68.dp Vertical M3 Tool Rail + Center Full-Bleed Canvas + Right 276.dp Tonal Inspector.
 * - Mobile (< 760.dp): Clean compact bottom tool bar.
 * - Quiet 26.dp bottom status strip with live canvas dimensions & active filter.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoEditorView(
    baseBitmap: Bitmap,
    fileName: String,
    targetSaveFile: File?,
    onExit: () -> Unit,
    onSaveSuccess: (File) -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.activity.compose.BackHandler(enabled = true) { onExit() }

    var workingBitmap by remember(baseBitmap) { mutableStateOf(baseBitmap) }
    var activeTab by remember { mutableStateOf(PhotoToolTab.FILTER) }
    var selectedPreset by remember { mutableStateOf(FilterPreset.ORIGINAL) }
    var colorAdjustments by remember { mutableStateOf(ColorAdjustments()) }
    var canvasBgColor by remember { mutableStateOf(Color.White) }
    var showImportImagePicker by remember { mutableStateOf(false) }
    var compareOriginal by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val drawingState = rememberDrawingState()
    val cropState = rememberCropTransformState()
    val graphicManager = remember { GraphicManager() }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textDialogInput by remember { mutableStateOf("") }

    fun resetAllEdits() {
        workingBitmap = baseBitmap
        selectedPreset = FilterPreset.ORIGINAL
        colorAdjustments = ColorAdjustments()
        cropState.resetAll()
        Toast.makeText(context, "Edits reset", Toast.LENGTH_SHORT).show()
    }

    fun applyCanvasFrame(aspectW: Int, aspectH: Int, padRatio: Float, bgColor: Color) {
        val src = workingBitmap
        val baseW = (src.width * (1f + padRatio)).toInt().coerceAtLeast(320)
        val baseH = (src.height * (1f + padRatio)).toInt().coerceAtLeast(320)
        val targetW: Int
        val targetH: Int
        if (aspectW > 0 && aspectH > 0) {
            val targetRatio = aspectW.toFloat() / aspectH.toFloat()
            val currentRatio = baseW.toFloat() / baseH.toFloat()
            if (currentRatio > targetRatio) {
                targetW = baseW
                targetH = (baseW / targetRatio).toInt()
            } else {
                targetH = baseH
                targetW = (baseH * targetRatio).toInt()
            }
        } else {
            targetW = baseW
            targetH = baseH
        }
        val out = Bitmap.createBitmap(targetW.coerceAtMost(3840), targetH.coerceAtMost(3840), Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(out)
        c.drawColor(bgColor.toArgb())
        val left = ((out.width - src.width) / 2f)
        val top = ((out.height - src.height) / 2f)
        c.drawBitmap(src, left, top, AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG))
        workingBitmap = out
    }

    if (showImportImagePicker) {
        com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog(
            title = "Add Image Layer",
            selectFolders = false,
            allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp"),
            onDismiss = { showImportImagePicker = false },
            onSelected = { pickedFile ->
                showImportImagePicker = false
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val overlayBmp = android.graphics.BitmapFactory.decodeFile(pickedFile.absolutePath)
                        if (overlayBmp != null) {
                            val baseCopy = workingBitmap.copy(Bitmap.Config.ARGB_8888, true)
                            val c = AndroidCanvas(baseCopy)
                            val maxOverlayW = (baseCopy.width * 0.65f).toInt().coerceAtLeast(160)
                            val scale = (maxOverlayW.toFloat() / overlayBmp.width.toFloat()).coerceAtMost(1f)
                            val scaledW = (overlayBmp.width * scale).toInt().coerceAtLeast(1)
                            val scaledH = (overlayBmp.height * scale).toInt().coerceAtLeast(1)
                            val scaledOverlay = Bitmap.createScaledBitmap(overlayBmp, scaledW, scaledH, true)
                            val left = (baseCopy.width - scaledW) / 2f
                            val top = (baseCopy.height - scaledH) / 2f
                            c.drawBitmap(scaledOverlay, left, top, AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG))
                            withContext(Dispatchers.Main) {
                                workingBitmap = baseCopy
                                Toast.makeText(context, "Added ${pickedFile.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        )
    }

    val filteredPreviewBitmap by remember(workingBitmap, selectedPreset, colorAdjustments) {
        derivedStateOf {
            FilterEngine.applyFilter(workingBitmap, selectedPreset, colorAdjustments)
        }
    }

    fun renderAndSave(saveAsNew: Boolean, customFileName: String, qualityPercent: Float) {
        scope.launch(Dispatchers.IO) {
            try {
                val baseFiltered = filteredPreviewBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = AndroidCanvas(baseFiltered)

                drawingState.drawingPaths.forEach { dPath ->
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = dPath.color.toArgb()
                        strokeWidth = dPath.strokeWidth
                        style = AndroidPaint.Style.STROKE
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                        alpha = (dPath.alpha * 255).toInt().coerceIn(0, 255)
                    }
                    when (dPath.shapeType) {
                        ShapeType.FREEHAND -> canvas.drawPath(dPath.path.asAndroidPath(), paint)
                        ShapeType.LINE, ShapeType.ARROW -> canvas.drawLine(
                            dPath.startPoint.x, dPath.startPoint.y, dPath.endPoint.x, dPath.endPoint.y, paint
                        )
                        ShapeType.RECTANGLE -> canvas.drawRect(
                            dPath.startPoint.x, dPath.startPoint.y, dPath.endPoint.x, dPath.endPoint.y, paint
                        )
                        ShapeType.CIRCLE -> canvas.drawOval(
                            android.graphics.RectF(dPath.startPoint.x, dPath.startPoint.y, dPath.endPoint.x, dPath.endPoint.y),
                            paint
                        )
                    }
                }

                graphicManager.graphics.forEach { g ->
                    when (g) {
                        is TextGraphic -> {
                            val tp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = g.textColor.toArgb()
                                textSize = g.fontSize * g.scale * 1.4f
                                isFakeBoldText = true
                            }
                            canvas.drawText(g.text, g.position.x, g.position.y, tp)
                        }
                        is EmojiGraphic -> {
                            val ep = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                textSize = g.fontSize * g.scale
                            }
                            canvas.drawText(g.emoji, g.position.x, g.position.y, ep)
                        }
                        else -> {}
                    }
                }

                val parentDir = targetSaveFile?.parentFile
                    ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!parentDir.exists()) parentDir.mkdirs()

                val outName = when {
                    customFileName.isNotBlank() -> customFileName
                    saveAsNew -> {
                        val base = fileName.substringBeforeLast('.')
                        val ext = fileName.substringAfterLast('.', "jpg")
                        "${base}_edited.$ext"
                    }
                    else -> fileName
                }
                val outFile = File(parentDir, outName)
                FileOutputStream(outFile).use { fos ->
                    baseFiltered.compress(Bitmap.CompressFormat.JPEG, qualityPercent.toInt().coerceIn(20, 100), fos)
                }

                withContext(Dispatchers.Main) {
                    showSaveDialog = false
                    Toast.makeText(context, "Saved ${outFile.name}", Toast.LENGTH_SHORT).show()
                    onSaveSuccess(outFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("Add Text", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textDialogInput,
                    onValueChange = { textDialogInput = it },
                    label = { Text("Text") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textDialogInput.isNotBlank()) {
                        graphicManager.addGraphic(TextGraphic(text = textDialogInput.trim()))
                        textDialogInput = ""
                    }
                    showAddTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveDialog) {
        PhotoSaveDialog(
            originalFileName = fileName,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { saveAsNew, customName, quality ->
                renderAndSave(saveAsNew, customName, quality)
            }
        )
    }

    @Composable
    fun ToolPropertiesContent(isDesktopInspector: Boolean) {
        when (activeTab) {
            PhotoToolTab.FILTER -> {
                if (isDesktopInspector) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPreset.entries.forEach { preset ->
                            TonalPhotoPill(
                                label = "${preset.icon} ${preset.displayName}",
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset }
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterPreset.entries.forEach { preset ->
                            TonalPhotoPill(
                                label = "${preset.icon} ${preset.displayName}",
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset }
                            )
                        }
                    }
                }
            }

            PhotoToolTab.ADJUST -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Brightness", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(68.dp))
                        Slider(
                            value = colorAdjustments.brightness,
                            onValueChange = { colorAdjustments = colorAdjustments.copy(brightness = it) },
                            valueRange = -80f..80f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Contrast", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(68.dp))
                        Slider(
                            value = colorAdjustments.contrast,
                            onValueChange = { colorAdjustments = colorAdjustments.copy(contrast = it) },
                            valueRange = 0.5f..1.8f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saturation", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(68.dp))
                        Slider(
                            value = colorAdjustments.saturation,
                            onValueChange = { colorAdjustments = colorAdjustments.copy(saturation = it) },
                            valueRange = 0f..2.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isDesktopInspector) {
                        TextButton(
                            onClick = { colorAdjustments = ColorAdjustments() },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Reset Sliders") }
                    }
                }
            }

            PhotoToolTab.CROP -> {
                if (isDesktopInspector) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CropAspectRatio.entries.forEach { ratio ->
                                TonalPhotoPill(
                                    label = ratio.label,
                                    selected = cropState.selectedAspectRatio == ratio,
                                    onClick = { cropState.applyAspectRatio(ratio) }
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { cropState.rotate90Clockwise() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rotate 90°")
                        }
                        Button(
                            onClick = {
                                workingBitmap = CropTransformer.applyCropAndTransform(workingBitmap, cropState)
                                cropState.resetAll()
                                activeTab = PhotoToolTab.FILTER
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Crop")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CropAspectRatio.entries.forEach { ratio ->
                            TonalPhotoPill(
                                label = ratio.label,
                                selected = cropState.selectedAspectRatio == ratio,
                                onClick = { cropState.applyAspectRatio(ratio) }
                            )
                        }
                        TonalPhotoPill("Rotate 90°", false) { cropState.rotate90Clockwise() }
                        Button(
                            onClick = {
                                workingBitmap = CropTransformer.applyCropAndTransform(workingBitmap, cropState)
                                cropState.resetAll()
                                activeTab = PhotoToolTab.FILTER
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) { Text("Apply", style = MaterialTheme.typography.labelSmall) }
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
                if (isDesktopInspector) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Backdrop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bgChoices.forEach { (bgCol, label) ->
                                TonalPhotoPill(
                                    label = label,
                                    selected = canvasBgColor == bgCol,
                                    onClick = { canvasBgColor = bgCol }
                                )
                            }
                        }
                        Text("Frame", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TonalPhotoPill("+ Matte", false) { applyCanvasFrame(0, 0, 0.14f, canvasBgColor) }
                            TonalPhotoPill("1:1 Square", false) { applyCanvasFrame(1, 1, 0.08f, canvasBgColor) }
                            TonalPhotoPill("16:9 Wide", false) { applyCanvasFrame(16, 9, 0.08f, canvasBgColor) }
                        }
                        FilledTonalButton(
                            onClick = { showImportImagePicker = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Image Layer")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bgChoices.forEach { (bgCol, label) ->
                            TonalPhotoPill(label, canvasBgColor == bgCol) { canvasBgColor = bgCol }
                        }
                        TonalPhotoPill("+ Matte", false) { applyCanvasFrame(0, 0, 0.14f, canvasBgColor) }
                        TonalPhotoPill("1:1", false) { applyCanvasFrame(1, 1, 0.08f, canvasBgColor) }
                        TonalPhotoPill("16:9", false) { applyCanvasFrame(16, 9, 0.08f, canvasBgColor) }
                        FilledTonalButton(
                            onClick = { showImportImagePicker = true },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Layer", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            PhotoToolTab.DRAW -> {
                val swatches = listOf(
                    Color(0xFFE53935),
                    Color(0xFF1E88E5),
                    Color(0xFF43A047),
                    Color(0xFFFDD835),
                    Color.White,
                    Color.Black
                )
                if (isDesktopInspector) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ShapeType.entries.forEach { shape ->
                                TonalPhotoPill(
                                    label = shape.label,
                                    selected = drawingState.shapeType == shape,
                                    onClick = { drawingState.shapeType = shape }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            swatches.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (drawingState.strokeColor == col) 2.dp else 1.dp,
                                            color = if (drawingState.strokeColor == col) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { drawingState.strokeColor = col }
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShapeType.entries.forEach { shape ->
                            TonalPhotoPill(shape.label, drawingState.shapeType == shape) { drawingState.shapeType = shape }
                        }
                        swatches.forEach { col ->
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
                if (isDesktopInspector) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showAddTextDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Text Layer")
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            emojis.forEach { emoji ->
                                TonalPhotoPill(emoji, false) { graphicManager.addGraphic(EmojiGraphic(emoji = emoji)) }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showAddTextDialog = true },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Text", style = MaterialTheme.typography.labelSmall)
                        }
                        emojis.forEach { emoji ->
                            TonalPhotoPill(emoji, false) { graphicManager.addGraphic(EmojiGraphic(emoji = emoji)) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CanvasStage(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.background(Color(0xFF101216)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = (if (compareOriginal) baseBitmap else filteredPreviewBitmap).asImageBitmap(),
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if (!compareOriginal) {
                DrawingView(
                    state = drawingState,
                    enabled = activeTab == PhotoToolTab.DRAW,
                    modifier = Modifier.fillMaxSize()
                )

                if (activeTab == PhotoToolTab.CROP) {
                    CropOverlay(
                        cropState = cropState,
                        enabled = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                graphicManager.graphics.forEach { graphic ->
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(graphic.position.x.roundToInt(), graphic.position.y.roundToInt()) }
                            .pointerInput(graphic.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    graphicManager.updateGraphicTranslation(
                                        graphic = graphic,
                                        dragDelta = dragAmount,
                                        canvasWidth = size.width.toFloat().coerceAtLeast(600f),
                                        canvasHeight = size.height.toFloat().coerceAtLeast(600f)
                                    )
                                }
                            }
                            .clickable { graphicManager.selectGraphic(graphic.id) }
                    ) {
                        when (graphic) {
                            is TextGraphic -> {
                                Text(
                                    text = graphic.text,
                                    color = graphic.textColor,
                                    fontSize = (graphic.fontSize * graphic.scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            is EmojiGraphic -> {
                                Text(
                                    text = graphic.emoji,
                                    fontSize = (graphic.fontSize * graphic.scale).sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            else -> {}
                        }
                        if (graphic.isSelected) {
                            BoxHelperOverlay(
                                graphic = graphic,
                                onDelete = { graphicManager.removeGraphic(graphic) },
                                onRotateScale = { scaleDelta, rotDelta ->
                                    graphicManager.updateGraphicTransform(graphic, scaleDelta, rotDelta)
                                },
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                }

                SnappingGuidesOverlay(
                    isSnappingH = graphicManager.isSnappingCenterH,
                    isSnappingV = graphicManager.isSnappingCenterV,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
        val isDesktopLayout = maxWidth >= 760.dp
        val isFullScreen by com.troikoss.continuum_explorer.managers.SettingsManager.isFullScreenTabBarHidden

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar
            SingleRowCommandBar(
                navigationContent = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("photo_editor_menu_button")
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                titleContent = {
                    Text(
                        text = fileName.ifBlank { "Photo Studio" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actionsContent = {
                    IconButton(onClick = { drawingState.undo() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { drawingState.redo() }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { compareOriginal = !compareOriginal },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = "Compare Original",
                            tint = if (compareOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (isDesktopLayout) {
                        IconButton(onClick = { resetAllEdits() }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showSaveDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("photo_editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset All") },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                                onClick = { showMoreMenu = false; resetAllEdits() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isFullScreen) "Show Tab Bar" else "Full Screen") },
                                leadingIcon = {
                                    Icon(
                                        if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    com.troikoss.continuum_explorer.managers.SettingsManager.toggleFullScreenTabBar()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Studio", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { showMoreMenu = false; onExit() }
                            )
                        }
                    }
                }
            )

            // Main Body: 3-Pane Desktop Studio vs Compact Mobile Bottom Dock
            if (isDesktopLayout) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Left 68.dp Vertical M3 Tool Rail
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .width(68.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PhotoToolTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                Surface(
                                    onClick = { activeTab = tab },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("photo_rail_${tab.name.lowercase()}")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(19.dp))
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TonalVerticalPaneDivider()

                    // Center Full-Bleed Canvas
                    CanvasStage(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    TonalVerticalPaneDivider()

                    // Right 276.dp Tonal Properties Inspector
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .width(276.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = activeTab.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ToolPropertiesContent(isDesktopInspector = true)
                        }
                    }
                }
            } else {
                CanvasStage(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolPropertiesContent(isDesktopInspector = false)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PhotoToolTab.entries.forEachIndexed { idx, tab ->
                                SegmentedButton(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = PhotoToolTab.entries.size),
                                    icon = {}
                                ) {
                                    Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Quiet 26.dp Desktop Status Strip
            SlimStatusStrip(
                leftText = "${workingBitmap.width}×${workingBitmap.height} px • ${activeTab.label}",
                rightText = selectedPreset.displayName
            )
        }
    }
}
