package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun PhotoEditorCanvas(
    baseBitmap: Bitmap,
    fileName: String,
    stateController: PhotoEditorStateController,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Base Image with filter, rotation, and flip (cached to avoid re-filtering on touch drag)
        val filteredBitmap = remember(baseBitmap, stateController.activeFilter) {
            PhotoFilterHelper.applyFilter(baseBitmap, stateController.activeFilter)
        }

        Image(
            bitmap = filteredBitmap.asImageBitmap(),
            contentDescription = fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    rotationZ = stateController.rotationAngle.toFloat(),
                    scaleX = if (stateController.isFlipHorizontal) -1f else 1f,
                    scaleY = if (stateController.isFlipVertical) -1f else 1f
                )
        )

        // Interactive Touch Drawing Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    stateController.activeTool,
                    stateController.brushColor,
                    stateController.brushSize,
                    stateController.isEraser,
                    stateController.selectedShapeType
                ) {
                    detectDragGestures(
                        onDragStart = { start ->
                            stateController.currentStartPoint = start
                            stateController.currentEndPoint = start
                            if (stateController.selectedShapeType == ShapeType.FREEHAND) {
                                val p = Path().apply { moveTo(start.x, start.y) }
                                stateController.currentPath = p
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            stateController.currentEndPoint += dragAmount
                            if (stateController.selectedShapeType == ShapeType.FREEHAND && stateController.currentPath != null) {
                                stateController.currentPath!!.lineTo(stateController.currentEndPoint.x, stateController.currentEndPoint.y)
                            }
                        },
                        onDragEnd = {
                            val finalPath = stateController.currentPath ?: Path()
                            stateController.strokes.add(
                                DrawingStroke(
                                    path = finalPath,
                                    color = stateController.brushColor,
                                    strokeWidth = stateController.brushSize,
                                    isEraser = stateController.isEraser,
                                    shapeType = stateController.selectedShapeType,
                                    startPoint = stateController.currentStartPoint,
                                    endPoint = stateController.currentEndPoint
                                )
                            )
                            stateController.currentPath = null
                            stateController.undoStrokes.clear()
                        }
                    )
                }
        ) {
            // Render strokes
            stateController.strokes.forEach { stroke ->
                val drawColor = if (stroke.isEraser) Color.Black else stroke.color
                when (stroke.shapeType) {
                    ShapeType.FREEHAND -> {
                        drawPath(
                            path = stroke.path,
                            color = drawColor,
                            style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    ShapeType.LINE -> {
                        drawLine(
                            color = drawColor,
                            start = stroke.startPoint,
                            end = stroke.endPoint,
                            strokeWidth = stroke.strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    ShapeType.RECTANGLE -> {
                        val left = Math.min(stroke.startPoint.x, stroke.endPoint.x)
                        val top = Math.min(stroke.startPoint.y, stroke.endPoint.y)
                        val width = abs(stroke.endPoint.x - stroke.startPoint.x)
                        val height = abs(stroke.endPoint.y - stroke.startPoint.y)
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = stroke.strokeWidth)
                        )
                    }
                    ShapeType.CIRCLE -> {
                        val radius = (stroke.endPoint - stroke.startPoint).getDistance() / 2f
                        val center = (stroke.startPoint + stroke.endPoint) / 2f
                        drawCircle(
                            color = drawColor,
                            radius = radius,
                            center = center,
                            style = Stroke(width = stroke.strokeWidth)
                        )
                    }
                    ShapeType.ARROW -> {
                        drawLine(
                            color = drawColor,
                            start = stroke.startPoint,
                            end = stroke.endPoint,
                            strokeWidth = stroke.strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Draw current active stroke during drag
            stateController.currentPath?.let { p ->
                drawPath(
                    path = p,
                    color = if (stateController.isEraser) Color.Gray else stateController.brushColor,
                    style = Stroke(width = stateController.brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Render Text Overlays
        stateController.textOverlays.forEachIndexed { index, txt ->
            Box(
                modifier = Modifier
                    .offset(txt.position.x.dp, txt.position.y.dp)
                    .graphicsLayer(
                        rotationZ = txt.rotation,
                        scaleX = txt.scale,
                        scaleY = txt.scale
                    )
                    .pointerInput(index) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            stateController.textOverlays[index] = txt.copy(
                                position = Offset(
                                    txt.position.x + dragAmount.x,
                                    txt.position.y + dragAmount.y
                                )
                            )
                        }
                    }
                    .background(txt.backgroundColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = txt.text,
                    color = txt.color,
                    fontSize = txt.fontSize.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Render Sticker Overlays
        stateController.stickerOverlays.forEachIndexed { index, stk ->
            Box(
                modifier = Modifier
                    .offset(stk.position.x.dp, stk.position.y.dp)
                    .graphicsLayer(
                        rotationZ = stk.rotation,
                        scaleX = stk.scale,
                        scaleY = stk.scale
                    )
                    .pointerInput(index) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            stateController.stickerOverlays[index] = stk.copy(
                                position = Offset(
                                    stk.position.x + dragAmount.x,
                                    stk.position.y + dragAmount.y
                                )
                            )
                        }
                    }
            ) {
                Text(
                    text = stk.emojiOrSticker,
                    fontSize = 42.sp
                )
            }
        }
    }
}
