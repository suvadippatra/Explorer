package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Aspect ratio options for photo cropping.
 */
enum class CropAspectRatio(val label: String, val ratio: Float?, val icon: String) {
    FREE("Free", null, "🔲"),
    SQUARE_1_1("1:1 Square", 1.0f, "⏹️"),
    RATIO_4_3("4:3", 4f / 3f, "📺"),
    RATIO_16_9("16:9", 16f / 9f, "🖥️"),
    RATIO_9_16("9:16 Story/Reel", 9f / 16f, "📱"),
    RATIO_3_2("3:2", 3f / 2f, "📷")
}

/**
 * State controller for crop, rotation, and mirror transformations.
 */
class CropTransformState {
    var selectedAspectRatio by mutableStateOf(CropAspectRatio.FREE)
    var isFlipHorizontal by mutableStateOf(false)
    var isFlipVertical by mutableStateOf(false)
    var rotationStep90 by mutableIntStateOf(0) // 0, 90, 180, 270
    var fineAngleRotation by mutableFloatStateOf(0f) // -45f to +45f

    // Normalized crop box (values 0.0f to 1.0f)
    var cropLeft by mutableFloatStateOf(0.05f)
    var cropTop by mutableFloatStateOf(0.05f)
    var cropRight by mutableFloatStateOf(0.95f)
    var cropBottom by mutableFloatStateOf(0.95f)

    val totalRotation: Float
        get() = (rotationStep90 + fineAngleRotation) % 360f

    fun resetCrop() {
        cropLeft = 0.05f
        cropTop = 0.05f
        cropRight = 0.95f
        cropBottom = 0.95f
        selectedAspectRatio = CropAspectRatio.FREE
    }

    fun resetAll() {
        resetCrop()
        isFlipHorizontal = false
        isFlipVertical = false
        rotationStep90 = 0
        fineAngleRotation = 0f
    }

    fun rotate90Clockwise() {
        rotationStep90 = (rotationStep90 + 90) % 360
    }

    fun rotate90CounterClockwise() {
        rotationStep90 = (rotationStep90 - 90 + 360) % 360
    }

    fun applyAspectRatio(aspectRatio: CropAspectRatio) {
        selectedAspectRatio = aspectRatio
        val targetRatio = aspectRatio.ratio ?: return

        val currentW = cropRight - cropLeft
        val currentH = cropBottom - cropTop
        val centerX = (cropLeft + cropRight) / 2f
        val centerY = (cropTop + cropBottom) / 2f

        // Adjust dimensions to fit ratio
        var newW = currentW
        var newH = currentW / targetRatio

        if (newH > 0.9f) {
            newH = 0.9f
            newW = newH * targetRatio
        }
        if (newW > 0.9f) {
            newW = 0.9f
            newH = newW / targetRatio
        }

        cropLeft = (centerX - newW / 2f).coerceIn(0.02f, 0.98f)
        cropRight = (centerX + newW / 2f).coerceIn(0.02f, 0.98f)
        cropTop = (centerY - newH / 2f).coerceIn(0.02f, 0.98f)
        cropBottom = (centerY + newH / 2f).coerceIn(0.02f, 0.98f)
    }
}

@Composable
fun rememberCropTransformState(): CropTransformState = remember { CropTransformState() }

/**
 * Interactive Crop overlay with Rule of Thirds grid, corner handles, and dimension guides.
 */
@Composable
fun CropOverlay(
    cropState: CropTransformState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, cropState.cropLeft, cropState.cropTop, cropState.cropRight, cropState.cropBottom) {
                    if (!enabled) return@pointerInput

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val pxLeft = cropState.cropLeft * width
                            val pxTop = cropState.cropTop * height
                            val pxRight = cropState.cropRight * width
                            val pxBottom = cropState.cropBottom * height

                            val handleRadius = 40f

                            // Detect which handle was touched
                            activeHandle = when {
                                (startOffset - Offset(pxLeft, pxTop)).getDistance() < handleRadius -> CropHandle.TOP_LEFT
                                (startOffset - Offset(pxRight, pxTop)).getDistance() < handleRadius -> CropHandle.TOP_RIGHT
                                (startOffset - Offset(pxLeft, pxBottom)).getDistance() < handleRadius -> CropHandle.BOTTOM_LEFT
                                (startOffset - Offset(pxRight, pxBottom)).getDistance() < handleRadius -> CropHandle.BOTTOM_RIGHT
                                (startOffset.x in pxLeft..pxRight && (startOffset.y - pxTop) in -handleRadius..handleRadius) -> CropHandle.TOP_EDGE
                                (startOffset.x in pxLeft..pxRight && (startOffset.y - pxBottom) in -handleRadius..handleRadius) -> CropHandle.BOTTOM_EDGE
                                (startOffset.y in pxTop..pxBottom && (startOffset.x - pxLeft) in -handleRadius..handleRadius) -> CropHandle.LEFT_EDGE
                                (startOffset.y in pxTop..pxBottom && (startOffset.x - pxRight) in -handleRadius..handleRadius) -> CropHandle.RIGHT_EDGE
                                (startOffset.x in pxLeft..pxRight && startOffset.y in pxTop..pxBottom) -> CropHandle.INSIDE_PAN
                                else -> null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            if (width <= 0 || height <= 0) return@detectDragGestures

                            val dxNorm = dragAmount.x / width
                            val dyNorm = dragAmount.y / height
                            val minSizeNorm = 0.12f

                            when (activeHandle) {
                                CropHandle.TOP_LEFT -> {
                                    cropState.cropLeft = (cropState.cropLeft + dxNorm).coerceIn(0.01f, cropState.cropRight - minSizeNorm)
                                    cropState.cropTop = (cropState.cropTop + dyNorm).coerceIn(0.01f, cropState.cropBottom - minSizeNorm)
                                }
                                CropHandle.TOP_RIGHT -> {
                                    cropState.cropRight = (cropState.cropRight + dxNorm).coerceIn(cropState.cropLeft + minSizeNorm, 0.99f)
                                    cropState.cropTop = (cropState.cropTop + dyNorm).coerceIn(0.01f, cropState.cropBottom - minSizeNorm)
                                }
                                CropHandle.BOTTOM_LEFT -> {
                                    cropState.cropLeft = (cropState.cropLeft + dxNorm).coerceIn(0.01f, cropState.cropRight - minSizeNorm)
                                    cropState.cropBottom = (cropState.cropBottom + dyNorm).coerceIn(cropState.cropTop + minSizeNorm, 0.99f)
                                }
                                CropHandle.BOTTOM_RIGHT -> {
                                    cropState.cropRight = (cropState.cropRight + dxNorm).coerceIn(cropState.cropLeft + minSizeNorm, 0.99f)
                                    cropState.cropBottom = (cropState.cropBottom + dyNorm).coerceIn(cropState.cropTop + minSizeNorm, 0.99f)
                                }
                                CropHandle.TOP_EDGE -> {
                                    cropState.cropTop = (cropState.cropTop + dyNorm).coerceIn(0.01f, cropState.cropBottom - minSizeNorm)
                                }
                                CropHandle.BOTTOM_EDGE -> {
                                    cropState.cropBottom = (cropState.cropBottom + dyNorm).coerceIn(cropState.cropTop + minSizeNorm, 0.99f)
                                }
                                CropHandle.LEFT_EDGE -> {
                                    cropState.cropLeft = (cropState.cropLeft + dxNorm).coerceIn(0.01f, cropState.cropRight - minSizeNorm)
                                }
                                CropHandle.RIGHT_EDGE -> {
                                    cropState.cropRight = (cropState.cropRight + dxNorm).coerceIn(cropState.cropLeft + minSizeNorm, 0.99f)
                                }
                                CropHandle.INSIDE_PAN -> {
                                    val currentW = cropState.cropRight - cropState.cropLeft
                                    val currentH = cropState.cropBottom - cropState.cropTop
                                    var newL = cropState.cropLeft + dxNorm
                                    var newT = cropState.cropTop + dyNorm

                                    if (newL < 0.01f) newL = 0.01f
                                    if (newL + currentW > 0.99f) newL = 0.99f - currentW
                                    if (newT < 0.01f) newT = 0.01f
                                    if (newT + currentH > 0.99f) newT = 0.99f - currentH

                                    cropState.cropLeft = newL
                                    cropState.cropRight = newL + currentW
                                    cropState.cropTop = newT
                                    cropState.cropBottom = newT + currentH
                                }
                                null -> {}
                            }

                            // If aspect ratio is locked, enforce it
                            cropState.selectedAspectRatio.ratio?.let { ratio ->
                                val curW = (cropState.cropRight - cropState.cropLeft) * width
                                val curH = (cropState.cropBottom - cropState.cropTop) * height
                                val adjustedH = (curW / ratio) / height
                                cropState.cropBottom = (cropState.cropTop + adjustedH).coerceAtMost(0.99f)
                            }
                        },
                        onDragEnd = { activeHandle = null },
                        onDragCancel = { activeHandle = null }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val pxLeft = cropState.cropLeft * width
            val pxTop = cropState.cropTop * height
            val pxRight = cropState.cropRight * width
            val pxBottom = cropState.cropBottom * height
            val cropRect = Rect(pxLeft, pxTop, pxRight, pxBottom)

            // 1. Darkened outer shroud
            val shroudPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, width, height))
                addRect(cropRect)
            }
            drawPath(shroudPath, color = Color.Black.copy(alpha = 0.55f))

            // 2. White border around crop area
            drawRect(
                color = Color.White,
                topLeft = Offset(pxLeft, pxTop),
                size = Size(pxRight - pxLeft, pxBottom - pxTop),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Rule of Thirds grid lines
            val thirdW = (pxRight - pxLeft) / 3f
            val thirdH = (pxBottom - pxTop) / 3f
            val gridColor = Color.White.copy(alpha = 0.45f)
            val gridStroke = Stroke(width = 1.dp.toPx())

            // Vertical thirds
            drawLine(gridColor, Offset(pxLeft + thirdW, pxTop), Offset(pxLeft + thirdW, pxBottom), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(pxLeft + 2 * thirdW, pxTop), Offset(pxLeft + 2 * thirdW, pxBottom), strokeWidth = 1.dp.toPx())

            // Horizontal thirds
            drawLine(gridColor, Offset(pxLeft, pxTop + thirdH), Offset(pxRight, pxTop + thirdH), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(pxLeft, pxTop + 2 * thirdH), Offset(pxRight, pxTop + 2 * thirdH), strokeWidth = 1.dp.toPx())

            // 4. Corner Handles
            val handleLen = 22.dp.toPx()
            val handleThick = 4.dp.toPx()
            val handleColor = Color.White

            // Top-Left corner
            drawLine(handleColor, Offset(pxLeft - 2f, pxTop), Offset(pxLeft + handleLen, pxTop), strokeWidth = handleThick, cap = StrokeCap.Square)
            drawLine(handleColor, Offset(pxLeft, pxTop - 2f), Offset(pxLeft, pxTop + handleLen), strokeWidth = handleThick, cap = StrokeCap.Square)

            // Top-Right corner
            drawLine(handleColor, Offset(pxRight + 2f, pxTop), Offset(pxRight - handleLen, pxTop), strokeWidth = handleThick, cap = StrokeCap.Square)
            drawLine(handleColor, Offset(pxRight, pxTop - 2f), Offset(pxRight, pxTop + handleLen), strokeWidth = handleThick, cap = StrokeCap.Square)

            // Bottom-Left corner
            drawLine(handleColor, Offset(pxLeft - 2f, pxBottom), Offset(pxLeft + handleLen, pxBottom), strokeWidth = handleThick, cap = StrokeCap.Square)
            drawLine(handleColor, Offset(pxLeft, pxBottom + 2f), Offset(pxLeft, pxBottom - handleLen), strokeWidth = handleThick, cap = StrokeCap.Square)

            // Bottom-Right corner
            drawLine(handleColor, Offset(pxRight + 2f, pxBottom), Offset(pxRight - handleLen, pxBottom), strokeWidth = handleThick, cap = StrokeCap.Square)
            drawLine(handleColor, Offset(pxRight, pxBottom + 2f), Offset(pxRight, pxBottom - handleLen), strokeWidth = handleThick, cap = StrokeCap.Square)
        }
    }
}

private enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP_EDGE, BOTTOM_EDGE, LEFT_EDGE, RIGHT_EDGE,
    INSIDE_PAN
}

/**
 * Helper object executing bitmap crops and transforms at native image resolution.
 */
object CropTransformer {

    fun applyCropAndTransform(
        source: Bitmap,
        cropState: CropTransformState
    ): Bitmap {
        val matrix = Matrix()

        // 1. Flip
        val sx = if (cropState.isFlipHorizontal) -1f else 1f
        val sy = if (cropState.isFlipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            matrix.postScale(sx, sy, source.width / 2f, source.height / 2f)
        }

        // 2. Rotation (90-step + fine angle)
        val totalRot = cropState.totalRotation
        if (totalRot != 0f) {
            matrix.postRotate(totalRot, source.width / 2f, source.height / 2f)
        }

        val transformedSource = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        // 3. Crop bounds
        val startX = (cropState.cropLeft * transformedSource.width).toInt().coerceIn(0, transformedSource.width - 1)
        val startY = (cropState.cropTop * transformedSource.height).toInt().coerceIn(0, transformedSource.height - 1)
        val endX = (cropState.cropRight * transformedSource.width).toInt().coerceIn(startX + 1, transformedSource.width)
        val endY = (cropState.cropBottom * transformedSource.height).toInt().coerceIn(startY + 1, transformedSource.height)

        val cropWidth = max(1, endX - startX)
        val cropHeight = max(1, endY - startY)

        return Bitmap.createBitmap(transformedSource, startX, startY, cropWidth, cropHeight)
    }
}
