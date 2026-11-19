package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
 * Single-Line 48.dp Top Bar for Image Viewer default mode (no stacked 2-line subtitle).
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
        containerColor = Color(0xD9121418),
        showBottomHairline = false,
        navigationContent = {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        },
        titleContent = {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
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

            IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Info (${imageWidth}×${imageHeight} • $fileSizeFormatted)") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { showMenu = false; onShowExifDialog() }
                    )
                    DropdownMenuItem(
                        text = { Text("Print") },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                        onClick = { showMenu = false; onPrint() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                        onClick = { showMenu = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy") },
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

/**
 * Desktop-First Ultra-Clean Tonal Flat Photo Studio.
 * - Single 48.dp Command Bar (☰, filename, Undo/Redo, Save).
 * - Desktop (>= 760.dp): Left 68.dp Vertical Tool Rail + Center Full-Bleed Canvas + Right 268.dp Tonal Properties Inspector.
 * - Mobile (< 760.dp): Full-Bleed Canvas + Compact Bottom Tool Controls + Segmented Switcher.
 * - Quiet 26.dp SlimStatusStrip at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    androidx.activity.compose.BackHandler(enabled = true) {
        onExit()
    }

    var workingBitmap by remember(baseBitmap) { mutableStateOf(baseBitmap) }
    var activeTab by remember { mutableStateOf(PhotoToolTab.FILTER) }
    var selectedPreset by remember { mutableStateOf(FilterPreset.ORIGINAL) }
    var colorAdjustments by remember { mutableStateOf(ColorAdjustments()) }
    var canvasBgColor by remember { mutableStateOf(Color.White) }
    var showImportImagePicker by remember { mutableStateOf(false) }

    val drawingState = rememberDrawingState()
    val cropState = rememberCropTransformState()
    val graphicManager = remember { GraphicManager() }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textDialogInput by remember { mutableStateOf("") }

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
            title = "Select Image Layer",
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
            title = { Text("Add Text Layer", fontWeight = FontWeight.Bold) },
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
    fun PhotoCanvasStage(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.background(Color(0xFF101216)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = filteredPreviewBitmap.asImageBitmap(),
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

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
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val isDesktop = maxWidth >= 760.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Strict Single 48.dp Command Bar
            SingleRowCommandBar(
                navigationContent = {
                    IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar", modifier = Modifier.size(20.dp))
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
                    FilledTonalButton(
                        onClick = { showSaveDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            )

            // Main Workspace
            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PhotoStudioToolRail(
                        activeTab = activeTab,
                        onSelectTab = { activeTab = it }
                    )
                    TonalVerticalPaneDivider()
                    PhotoCanvasStage(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    TonalVerticalPaneDivider()
                    PhotoStudioInspectorContent(
                        activeTab = activeTab,
                        isDesktopRail = true,
                        selectedPreset = selectedPreset,
                        onSelectPreset = { selectedPreset = it },
                        colorAdjustments = colorAdjustments,
                        onAdjustmentsChange = { colorAdjustments = it },
                        cropState = cropState,
                        onApplyCrop = {
                            workingBitmap = CropTransformer.applyCropAndTransform(workingBitmap, cropState)
                            cropState.resetAll()
                            activeTab = PhotoToolTab.FILTER
                        },
                        canvasBgColor = canvasBgColor,
                        onSelectCanvasBg = { canvasBgColor = it },
                        onApplyCanvasFrame = { aw, ah, pad, bg -> applyCanvasFrame(aw, ah, pad, bg) },
                        onImportImageLayer = { showImportImagePicker = true },
                        onResetCanvas = { workingBitmap = baseBitmap },
                        drawingState = drawingState,
                        onAddTextClick = { showAddTextDialog = true },
                        onAddEmoji = { emoji -> graphicManager.addGraphic(EmojiGraphic(emoji = emoji)) }
                    )
                }
            } else {
                PhotoCanvasStage(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PhotoStudioInspectorContent(
                            activeTab = activeTab,
                            isDesktopRail = false,
                            selectedPreset = selectedPreset,
                            onSelectPreset = { selectedPreset = it },
                            colorAdjustments = colorAdjustments,
                            onAdjustmentsChange = { colorAdjustments = it },
                            cropState = cropState,
                            onApplyCrop = {
                                workingBitmap = CropTransformer.applyCropAndTransform(workingBitmap, cropState)
                                cropState.resetAll()
                                activeTab = PhotoToolTab.FILTER
                            },
                            canvasBgColor = canvasBgColor,
                            onSelectCanvasBg = { canvasBgColor = it },
                            onApplyCanvasFrame = { aw, ah, pad, bg -> applyCanvasFrame(aw, ah, pad, bg) },
                            onImportImageLayer = { showImportImagePicker = true },
                            onResetCanvas = { workingBitmap = baseBitmap },
                            drawingState = drawingState,
                            onAddTextClick = { showAddTextDialog = true },
                            onAddEmoji = { emoji -> graphicManager.addGraphic(EmojiGraphic(emoji = emoji)) }
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .height(34.dp)
                        ) {
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
                leftText = "${workingBitmap.width} × ${workingBitmap.height} px • ${selectedPreset.displayName}",
                rightText = activeTab.label
            )
        }
    }
}
