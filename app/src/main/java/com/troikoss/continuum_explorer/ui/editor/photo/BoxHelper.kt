package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Focus border overlay (BoxHelper) showing transformation handles (Scale, Rotate, Delete)
 * when a Graphic layer is focused/tapped.
 */
@Composable
fun BoxHelperOverlay(
    graphic: Graphic,
    onDelete: () -> Unit,
    onRotateScale: (scaleDelta: Float, rotationDelta: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val handleSize = 28.dp

    Box(
        modifier = modifier
            .graphicsLayer(
                rotationZ = graphic.rotation,
                scaleX = graphic.scale,
                scaleY = graphic.scale
            )
    ) {
        // Dashed bounding rectangle
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = Color(0xFF2196F3),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )
        }

        // 1. Delete Handle (Top-Left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset((-14).dp, (-14).dp)
                .size(handleSize)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete Layer",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // 2. Rotate & Scale Handle (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(14.dp, 14.dp)
                .size(handleSize)
                .clip(CircleShape)
                .background(Color(0xFF2196F3))
                .pointerInput(graphic.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Dragging outward increases scale, sideways rotates
                        val scaleChange = 1f + (dragAmount.x + dragAmount.y) / 250f
                        val rotChange = dragAmount.x * 0.4f
                        onRotateScale(scaleChange, rotChange)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Scale & Rotate Layer",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Snapping guides rendering lines at center horizontal and vertical when layers snap.
 */
@Composable
fun SnappingGuidesOverlay(
    isSnappingH: Boolean,
    isSnappingV: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isSnappingH && !isSnappingV) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val guideColor = Color(0xFFFF9800)
        val stroke = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        )

        if (isSnappingH) {
            // Vertical center guideline
            drawLine(
                color = guideColor,
                start = Offset(width / 2f, 0f),
                end = Offset(width / 2f, height),
                strokeWidth = stroke.width,
                pathEffect = stroke.pathEffect
            )
        }

        if (isSnappingV) {
            // Horizontal center guideline
            drawLine(
                color = guideColor,
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = stroke.width,
                pathEffect = stroke.pathEffect
            )
        }
    }
}
