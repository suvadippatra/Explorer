package com.troikoss.continuum_explorer.ui.editor.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Image export formats supported by PhotoEditorEngine.
 */
enum class ExportFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

/**
 * Snapshot representing the state of the editor at a point in time for undo/redo.
 */
data class EditorHistorySnapshot(
    val graphics: List<Graphic>,
    val drawingPaths: List<DrawingPath>,
    val filterPreset: FilterPreset,
    val colorAdjustments: ColorAdjustments,
    val rotationStep90: Int,
    val fineAngleRotation: Float,
    val isFlipHorizontal: Boolean,
    val isFlipVertical: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float
)

/**
 * Native PhotoEditorEngine managing multi-layer graphic stacks, vector drawing,
 * color matrices & filters, crop/transforms, deep undo/redo history, and high-resolution rendering.
 */
class PhotoEditorEngine(
    val baseBitmap: Bitmap
) {
    // Sub-systems
    val graphicManager = GraphicManager()
    val drawingState = DrawingState()
    val cropTransformState = CropTransformState()

    // Filters & Adjustments
    var activeFilterPreset by mutableStateOf(FilterPreset.ORIGINAL)
    var colorAdjustments by mutableStateOf(ColorAdjustments())

    // Active tool mode
    var activeTool by mutableStateOf(PhotoTool.BRUSH)

    // Deep Undo / Redo history stacks
    private val undoStack = mutableListOf<EditorHistorySnapshot>()
    private val redoStack = mutableListOf<EditorHistorySnapshot>()

    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    init {
        // Record initial pristine state
        pushHistorySnapshot()
    }

    /**
     * Captures current editor state into the undo stack.
     */
    fun pushHistorySnapshot() {
        val snapshot = EditorHistorySnapshot(
            graphics = graphicManager.createSnapshot(),
            drawingPaths = drawingState.createSnapshot(),
            filterPreset = activeFilterPreset,
            colorAdjustments = colorAdjustments.copy(),
            rotationStep90 = cropTransformState.rotationStep90,
            fineAngleRotation = cropTransformState.fineAngleRotation,
            isFlipHorizontal = cropTransformState.isFlipHorizontal,
            isFlipVertical = cropTransformState.isFlipVertical,
            cropLeft = cropTransformState.cropLeft,
            cropTop = cropTransformState.cropTop,
            cropRight = cropTransformState.cropRight,
            cropBottom = cropTransformState.cropBottom
        )

        undoStack.add(snapshot)
        redoStack.clear()
        updateUndoRedoStatus()
    }

    fun undo() {
        if (undoStack.size > 1) {
            val currentState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(currentState)
            val prevState = undoStack.last()
            restoreFromSnapshot(prevState)
            updateUndoRedoStatus()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(nextState)
            restoreFromSnapshot(nextState)
            updateUndoRedoStatus()
        }
    }

    private fun restoreFromSnapshot(snapshot: EditorHistorySnapshot) {
        graphicManager.restoreSnapshot(snapshot.graphics)
        drawingState.restoreSnapshot(snapshot.drawingPaths)
        activeFilterPreset = snapshot.filterPreset
        colorAdjustments = snapshot.colorAdjustments.copy()

        cropTransformState.rotationStep90 = snapshot.rotationStep90
        cropTransformState.fineAngleRotation = snapshot.fineAngleRotation
        cropTransformState.isFlipHorizontal = snapshot.isFlipHorizontal
        cropTransformState.isFlipVertical = snapshot.isFlipVertical
        cropTransformState.cropLeft = snapshot.cropLeft
        cropTransformState.cropTop = snapshot.cropTop
        cropTransformState.cropRight = snapshot.cropRight
        cropTransformState.cropBottom = snapshot.cropBottom
    }

    private fun updateUndoRedoStatus() {
        canUndo = undoStack.size > 1
        canRedo = redoStack.isNotEmpty()
    }

    fun resetAll() {
        pushHistorySnapshot()
        graphicManager.clearAll()
        drawingState.clear()
        cropTransformState.resetAll()
        activeFilterPreset = FilterPreset.ORIGINAL
        colorAdjustments = ColorAdjustments()
        pushHistorySnapshot()
    }

    /**
     * High-Resolution Output Renderer.
     * Takes the original source bitmap, applies crops & rotations, color adjustments & filters,
     * scales all vector strokes and graphic layers proportionally to native image dimensions,
     * and produces the final high-resolution Bitmap.
     */
    suspend fun renderHighResolutionBitmap(
        previewCanvasWidth: Float,
        previewCanvasHeight: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Apply Transforms & Crops to original high-res bitmap
        val transformedBase = CropTransformer.applyCropAndTransform(baseBitmap, cropTransformState)

        // 2. Apply Filters & Color Adjustments
        val filteredBitmap = FilterEngine.applyFilter(transformedBase, activeFilterPreset, colorAdjustments)

        // 3. Create mutable canvas at native filtered bitmap resolution
        val outBitmap = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(outBitmap)

        val nativeW = outBitmap.width.toFloat()
        val nativeH = outBitmap.height.toFloat()

        // Calculate scaling factors from preview UI canvas coordinates to native image pixels
        val scaleX = if (previewCanvasWidth > 0) nativeW / previewCanvasWidth else 1.0f
        val scaleY = if (previewCanvasHeight > 0) nativeH / previewCanvasHeight else 1.0f
        val uniformScale = (scaleX + scaleY) / 2f

        // 4. Render Vector Drawing Paths at native resolution
        drawingState.drawingPaths.forEach { stroke ->
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth * uniformScale
                style = AndroidPaint.Style.STROKE
                strokeCap = when (stroke.brushType) {
                    BrushType.HIGHLIGHTER -> AndroidPaint.Cap.SQUARE
                    else -> AndroidPaint.Cap.ROUND
                }
                strokeJoin = AndroidPaint.Join.ROUND
                if (stroke.brushType == BrushType.ERASER) {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                } else if (stroke.brushType == BrushType.HIGHLIGHTER) {
                    alpha = (0.38f * 255).toInt()
                }
            }

            when (stroke.shapeType) {
                ShapeType.FREEHAND -> {
                    // Scale path to native resolution
                    val matrix = Matrix().apply { postScale(scaleX, scaleY) }
                    val nativePath = android.graphics.Path(stroke.path.asAndroidPath()).apply {
                        transform(matrix)
                    }
                    canvas.drawPath(nativePath, paint)
                }
                ShapeType.LINE -> {
                    canvas.drawLine(
                        stroke.startPoint.x * scaleX,
                        stroke.startPoint.y * scaleY,
                        stroke.endPoint.x * scaleX,
                        stroke.endPoint.y * scaleY,
                        paint
                    )
                }
                ShapeType.RECTANGLE -> {
                    val l = kotlin.math.min(stroke.startPoint.x, stroke.endPoint.x) * scaleX
                    val t = kotlin.math.min(stroke.startPoint.y, stroke.endPoint.y) * scaleY
                    val r = kotlin.math.max(stroke.startPoint.x, stroke.endPoint.x) * scaleX
                    val b = kotlin.math.max(stroke.startPoint.y, stroke.endPoint.y) * scaleY
                    canvas.drawRect(l, t, r, b, paint)
                }
                ShapeType.CIRCLE -> {
                    val l = kotlin.math.min(stroke.startPoint.x, stroke.endPoint.x) * scaleX
                    val t = kotlin.math.min(stroke.startPoint.y, stroke.endPoint.y) * scaleY
                    val r = kotlin.math.max(stroke.startPoint.x, stroke.endPoint.x) * scaleX
                    val b = kotlin.math.max(stroke.startPoint.y, stroke.endPoint.y) * scaleY
                    canvas.drawOval(RectF(l, t, r, b), paint)
                }
                ShapeType.ARROW -> {
                    val sx = stroke.startPoint.x * scaleX
                    val sy = stroke.startPoint.y * scaleY
                    val ex = stroke.endPoint.x * scaleX
                    val ey = stroke.endPoint.y * scaleY
                    canvas.drawLine(sx, sy, ex, ey, paint)
                }
            }
        }

        // 5. Render Graphic Overlay Layers (Text, Sticker, Emoji)
        graphicManager.graphics.forEach { graphic ->
            canvas.save()
            val nativePosX = graphic.position.x * scaleX
            val nativePosY = graphic.position.y * scaleY

            canvas.translate(nativePosX, nativePosY)
            canvas.rotate(graphic.rotation)
            canvas.scale(graphic.scale * uniformScale, graphic.scale * uniformScale)

            when (graphic) {
                is TextGraphic -> {
                    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = graphic.textColor.toArgb()
                        textSize = graphic.fontSize
                        typeface = Typeface.DEFAULT_BOLD
                    }

                    if (graphic.hasShadow) {
                        textPaint.setShadowLayer(
                            graphic.shadowRadius,
                            2f,
                            2f,
                            graphic.shadowColor.toArgb()
                        )
                    }

                    val bounds = Rect()
                    textPaint.getTextBounds(graphic.text, 0, graphic.text.length, bounds)

                    // Draw background pill if not transparent
                    if (graphic.backgroundColor != Color.Transparent) {
                        val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            color = graphic.backgroundColor.toArgb()
                        }
                        val padX = 12f
                        val padY = 8f
                        canvas.drawRoundRect(
                            RectF(
                                -bounds.width() / 2f - padX,
                                -bounds.height() / 2f - padY,
                                bounds.width() / 2f + padX,
                                bounds.height() / 2f + padY
                            ),
                            8f,
                            8f,
                            bgPaint
                        )
                    }

                    // Draw border if requested
                    if (graphic.hasBorder) {
                        val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                            color = graphic.borderColor.toArgb()
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = graphic.borderWidth
                        }
                        val padX = 12f
                        val padY = 8f
                        canvas.drawRoundRect(
                            RectF(
                                -bounds.width() / 2f - padX,
                                -bounds.height() / 2f - padY,
                                bounds.width() / 2f + padX,
                                bounds.height() / 2f + padY
                            ),
                            8f,
                            8f,
                            borderPaint
                        )
                    }

                    canvas.drawText(
                        graphic.text,
                        -bounds.width() / 2f,
                        bounds.height() / 2f,
                        textPaint
                    )
                }

                is EmojiGraphic -> {
                    val emojiPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        textSize = graphic.fontSize
                    }
                    val bounds = Rect()
                    emojiPaint.getTextBounds(graphic.emoji, 0, graphic.emoji.length, bounds)
                    canvas.drawText(
                        graphic.emoji,
                        -bounds.width() / 2f,
                        bounds.height() / 2f,
                        emojiPaint
                    )
                }

                is StickerGraphic -> {
                    graphic.bitmap?.let { stickerBmp ->
                        canvas.drawBitmap(
                            stickerBmp,
                            -stickerBmp.width / 2f,
                            -stickerBmp.height / 2f,
                            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
                        )
                    }
                }

                is DrawingGraphic -> {
                    // Handled above in drawing paths
                }
            }

            canvas.restore()
        }

        outBitmap
    }

    /**
     * Exports rendered image to file destination with quality selector.
     */
    suspend fun exportImageToFile(
        targetFile: File,
        format: ExportFormat,
        qualityPercent: Int,
        previewCanvasWidth: Float,
        previewCanvasHeight: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val highResBitmap = renderHighResolutionBitmap(previewCanvasWidth, previewCanvasHeight)
            val compressFormat = when (format) {
                ExportFormat.JPEG -> CompressFormat.JPEG
                ExportFormat.PNG -> CompressFormat.PNG
                ExportFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        CompressFormat.WEBP
                    }
                }
            }

            FileOutputStream(targetFile).use { outStream ->
                highResBitmap.compress(compressFormat, qualityPercent.coerceIn(1, 100), outStream)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun rememberPhotoEditorEngine(baseBitmap: Bitmap): PhotoEditorEngine {
    return remember(baseBitmap) { PhotoEditorEngine(baseBitmap) }
}
