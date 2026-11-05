package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brush drawing styles supported in DrawingView.
 */
enum class BrushType(val label: String, val iconEmoji: String) {
    PEN("Pen", "✏️"),
    HIGHLIGHTER("Highlighter", "🖍️"),
    ERASER("Eraser", "🧹")
}

/**
 * Represents a single drawn vector stroke or shape.
 */
data class DrawingPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val brushType: BrushType,
    val shapeType: ShapeType = ShapeType.FREEHAND,
    val startPoint: Offset = Offset.Zero,
    val endPoint: Offset = Offset.Zero,
    val alpha: Float = if (brushType == BrushType.HIGHLIGHTER) 0.38f else 1.0f
)

/**
 * State controller for DrawingView with live pen, highlighter, eraser, and vector shapes.
 */
class DrawingState {
    var brushType by mutableStateOf(BrushType.PEN)
    var shapeType by mutableStateOf(ShapeType.FREEHAND)
    var strokeColor by mutableStateOf(Color(0xFFE53935))
    var strokeWidth by mutableFloatStateOf(10f)

    val drawingPaths = mutableStateListOf<DrawingPath>()
    val undoPaths = mutableStateListOf<DrawingPath>()

    // Current stroke while user is dragging
    var activePath by mutableStateOf<Path?>(null)
    var activeStartPoint by mutableStateOf(Offset.Zero)
    var activeEndPoint by mutableStateOf(Offset.Zero)
    var isDrawingActive by mutableStateOf(false)

    fun undo() {
        if (drawingPaths.isNotEmpty()) {
            val last = drawingPaths.removeAt(drawingPaths.size - 1)
            undoPaths.add(last)
        }
    }

    fun redo() {
        if (undoPaths.isNotEmpty()) {
            val last = undoPaths.removeAt(undoPaths.size - 1)
            drawingPaths.add(last)
        }
    }

    fun clear() {
        drawingPaths.clear()
        undoPaths.clear()
        activePath = null
        isDrawingActive = false
    }

    fun createSnapshot(): List<DrawingPath> {
        return drawingPaths.toList()
    }

    fun restoreSnapshot(snapshot: List<DrawingPath>) {
        drawingPaths.clear()
        drawingPaths.addAll(snapshot)
        activePath = null
        isDrawingActive = false
    }
}

@Composable
fun rememberDrawingState(): DrawingState = remember { DrawingState() }

/**
 * DrawingView canvas component handling live touch gestures and vector shape rendering.
 */
@Composable
fun DrawingView(
    state: DrawingState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onStrokeCompleted: (() -> Unit)? = null
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, state.brushType, state.shapeType, state.strokeColor, state.strokeWidth) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { startOffset ->
                        state.isDrawingActive = true
                        state.activeStartPoint = startOffset
                        state.activeEndPoint = startOffset

                        if (state.shapeType == ShapeType.FREEHAND) {
                            val newPath = Path().apply { moveTo(startOffset.x, startOffset.y) }
                            state.activePath = newPath
                        } else {
                            state.activePath = null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.activeEndPoint += dragAmount

                        if (state.shapeType == ShapeType.FREEHAND) {
                            state.activePath?.lineTo(state.activeEndPoint.x, state.activeEndPoint.y)
                        }
                    },
                    onDragEnd = {
                        val finalPath = state.activePath ?: Path()
                        val newDrawing = DrawingPath(
                            path = finalPath,
                            color = state.strokeColor,
                            strokeWidth = state.strokeWidth,
                            brushType = state.brushType,
                            shapeType = state.shapeType,
                            startPoint = state.activeStartPoint,
                            endPoint = state.activeEndPoint,
                            alpha = if (state.brushType == BrushType.HIGHLIGHTER) 0.38f else 1.0f
                        )
                        state.drawingPaths.add(newDrawing)
                        state.undoPaths.clear()
                        state.activePath = null
                        state.isDrawingActive = false
                        onStrokeCompleted?.invoke()
                    },
                    onDragCancel = {
                        state.activePath = null
                        state.isDrawingActive = false
                    }
                )
            }
    ) {
        // Render completed paths
        state.drawingPaths.forEach { stroke ->
            drawVectorStroke(stroke)
        }

        // Render in-progress active stroke
        if (state.isDrawingActive) {
            val previewStroke = DrawingPath(
                path = state.activePath ?: Path(),
                color = state.strokeColor,
                strokeWidth = state.strokeWidth,
                brushType = state.brushType,
                shapeType = state.shapeType,
                startPoint = state.activeStartPoint,
                endPoint = state.activeEndPoint,
                alpha = if (state.brushType == BrushType.HIGHLIGHTER) 0.38f else 1.0f
            )
            drawVectorStroke(previewStroke)
        }
    }
}

/**
 * Draws a single vector stroke or geometric shape with correct blend mode and caps.
 */
fun DrawScope.drawVectorStroke(stroke: DrawingPath) {
    val blendMode = when (stroke.brushType) {
        BrushType.ERASER -> BlendMode.Clear
        BrushType.HIGHLIGHTER -> BlendMode.SrcOver
        BrushType.PEN -> BlendMode.SrcOver
    }

    val drawColor = if (stroke.brushType == BrushType.ERASER) {
        Color.Transparent
    } else {
        stroke.color.copy(alpha = stroke.alpha)
    }

    val strokeCap = when (stroke.brushType) {
        BrushType.HIGHLIGHTER -> StrokeCap.Square
        else -> StrokeCap.Round
    }

    when (stroke.shapeType) {
        ShapeType.FREEHAND -> {
            drawPath(
                path = stroke.path,
                color = drawColor,
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = strokeCap,
                    join = StrokeJoin.Round
                ),
                blendMode = blendMode
            )
        }

        ShapeType.LINE -> {
            drawLine(
                color = drawColor,
                start = stroke.startPoint,
                end = stroke.endPoint,
                strokeWidth = stroke.strokeWidth,
                cap = strokeCap,
                blendMode = blendMode
            )
        }

        ShapeType.RECTANGLE -> {
            val left = kotlin.math.min(stroke.startPoint.x, stroke.endPoint.x)
            val top = kotlin.math.min(stroke.startPoint.y, stroke.endPoint.y)
            val width = abs(stroke.endPoint.x - stroke.startPoint.x)
            val height = abs(stroke.endPoint.y - stroke.startPoint.y)

            drawRect(
                color = drawColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = stroke.strokeWidth, join = StrokeJoin.Miter),
                blendMode = blendMode
            )
        }

        ShapeType.CIRCLE -> { // Oval / Ellipse
            val left = kotlin.math.min(stroke.startPoint.x, stroke.endPoint.x)
            val top = kotlin.math.min(stroke.startPoint.y, stroke.endPoint.y)
            val width = abs(stroke.endPoint.x - stroke.startPoint.x)
            val height = abs(stroke.endPoint.y - stroke.startPoint.y)

            drawOval(
                color = drawColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = stroke.strokeWidth),
                blendMode = blendMode
            )
        }

        ShapeType.ARROW -> {
            // Main stem
            drawLine(
                color = drawColor,
                start = stroke.startPoint,
                end = stroke.endPoint,
                strokeWidth = stroke.strokeWidth,
                cap = StrokeCap.Round,
                blendMode = blendMode
            )

            // Arrowhead calculations
            val dx = stroke.endPoint.x - stroke.startPoint.x
            val dy = stroke.endPoint.y - stroke.startPoint.y
            val angle = atan2(dy.toDouble(), dx.toDouble())
            val arrowLength = (stroke.strokeWidth * 3.5f).coerceIn(24f, 60f)
            val arrowAngle = PI / 6.0 // 30 degrees

            val x1 = stroke.endPoint.x - (arrowLength * cos(angle - arrowAngle)).toFloat()
            val y1 = stroke.endPoint.y - (arrowLength * sin(angle - arrowAngle)).toFloat()

            val x2 = stroke.endPoint.x - (arrowLength * cos(angle + arrowAngle)).toFloat()
            val y2 = stroke.endPoint.y - (arrowLength * sin(angle + arrowAngle)).toFloat()

            val arrowHeadPath = Path().apply {
                moveTo(stroke.endPoint.x, stroke.endPoint.y)
                lineTo(x1, y1)
                moveTo(stroke.endPoint.x, stroke.endPoint.y)
                lineTo(x2, y2)
            }

            drawPath(
                path = arrowHeadPath,
                color = drawColor,
                style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode
            )
        }
    }
}
