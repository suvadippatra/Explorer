package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import java.util.UUID

/**
 * Visual graphic objects supported in PhotoEditor overlay layers.
 */
sealed class Graphic {
    abstract val id: String
    abstract var position: Offset
    abstract var scale: Float
    abstract var rotation: Float
    abstract var isSelected: Boolean

    abstract fun copyGraphic(): Graphic
}

/**
 * Sticker layer containing an image bitmap with scaling and rotation handles.
 */
data class StickerGraphic(
    override val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap? = null,
    val stickerName: String = "Sticker",
    override var position: Offset = Offset(200f, 200f),
    override var scale: Float = 1.0f,
    override var rotation: Float = 0f,
    override var isSelected: Boolean = false,
    val width: Float = 120f,
    val height: Float = 120f
) : Graphic() {
    override fun copyGraphic(): Graphic = copy()
}

/**
 * Rich text graphic with customizable fonts, shadow, border, background color, and alignment.
 */
data class TextGraphic(
    override val id: String = UUID.randomUUID().toString(),
    var text: String = "Double tap to edit",
    var textColor: Color = Color.White,
    var backgroundColor: Color = Color.Transparent,
    var fontSize: Float = 26f,
    var fontFamilyName: String = "Default",
    var hasShadow: Boolean = false,
    var shadowColor: Color = Color.Black,
    var shadowRadius: Float = 6f,
    var hasBorder: Boolean = false,
    var borderColor: Color = Color.Black,
    var borderWidth: Float = 2f,
    var textAlign: TextAlign = TextAlign.Center,
    override var position: Offset = Offset(150f, 250f),
    override var scale: Float = 1.0f,
    override var rotation: Float = 0f,
    override var isSelected: Boolean = false
) : Graphic() {
    override fun copyGraphic(): Graphic = copy()
}

/**
 * Scalable vector/font-based emoji layer.
 */
data class EmojiGraphic(
    override val id: String = UUID.randomUUID().toString(),
    var emoji: String = "⭐",
    var fontSize: Float = 56f,
    override var position: Offset = Offset(200f, 200f),
    override var scale: Float = 1.0f,
    override var rotation: Float = 0f,
    override var isSelected: Boolean = false
) : Graphic() {
    override fun copyGraphic(): Graphic = copy()
}

/**
 * Vector paths and shapes drawn using DrawingView.
 */
data class DrawingGraphic(
    override val id: String = UUID.randomUUID().toString(),
    val path: Path = Path(),
    val strokeColor: Color = Color.Red,
    val strokeWidth: Float = 10f,
    val brushType: BrushType = BrushType.PEN,
    val shapeType: ShapeType = ShapeType.FREEHAND,
    val startPoint: Offset = Offset.Zero,
    val endPoint: Offset = Offset.Zero,
    val alpha: Float = 1.0f,
    val isEraser: Boolean = false,
    override var position: Offset = Offset.Zero,
    override var scale: Float = 1.0f,
    override var rotation: Float = 0f,
    override var isSelected: Boolean = false
) : Graphic() {
    override fun copyGraphic(): Graphic = copy()
}

/**
 * Central overlay stack manager for distinct visual graphic layers.
 * Handles layer hierarchy, selection, reordering, snapping guides, and boundary constraints.
 */
class GraphicManager {
    val graphics = mutableStateListOf<Graphic>()
    var selectedGraphicId by mutableStateOf<String?>(null)

    // Snapping guides state
    var isSnappingCenterH by mutableStateOf(false)
    var isSnappingCenterV by mutableStateOf(false)

    val selectedGraphic: Graphic?
        get() = graphics.firstOrNull { it.id == selectedGraphicId }

    fun addGraphic(graphic: Graphic) {
        // Deselect others and select the new one
        clearSelection()
        graphic.isSelected = true
        graphics.add(graphic)
        selectedGraphicId = graphic.id
    }

    fun removeGraphic(graphic: Graphic) {
        graphics.remove(graphic)
        if (selectedGraphicId == graphic.id) {
            selectedGraphicId = null
        }
    }

    fun removeSelected() {
        val target = selectedGraphic ?: return
        removeGraphic(target)
    }

    fun selectGraphic(id: String?) {
        selectedGraphicId = id
        graphics.forEach { it.isSelected = (it.id == id) }
    }

    fun clearSelection() {
        selectedGraphicId = null
        graphics.forEach { it.isSelected = false }
        isSnappingCenterH = false
        isSnappingCenterV = false
    }

    fun bringToFront(id: String) {
        val index = graphics.indexOfFirst { it.id == id }
        if (index >= 0 && index < graphics.size - 1) {
            val item = graphics.removeAt(index)
            graphics.add(item)
        }
    }

    fun sendToBack(id: String) {
        val index = graphics.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = graphics.removeAt(index)
            graphics.add(0, item)
        }
    }

    fun clearAll() {
        graphics.clear()
        selectedGraphicId = null
        isSnappingCenterH = false
        isSnappingCenterV = false
    }

    /**
     * Applies translation with auto-center snapping guides and boundary constraints.
     */
    fun updateGraphicTranslation(
        graphic: Graphic,
        dragDelta: Offset,
        canvasWidth: Float,
        canvasHeight: Float,
        snapThreshold: Float = 18f
    ) {
        var newX = graphic.position.x + dragDelta.x
        var newY = graphic.position.y + dragDelta.y

        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f

        // Check horizontal center snap
        if (kotlin.math.abs(newX - centerX) < snapThreshold) {
            newX = centerX
            isSnappingCenterH = true
        } else {
            isSnappingCenterH = false
        }

        // Check vertical center snap
        if (kotlin.math.abs(newY - centerY) < snapThreshold) {
            newY = centerY
            isSnappingCenterV = true
        } else {
            isSnappingCenterV = false
        }

        // Boundary constraints (allow reasonable offscreen margin)
        val clampedX = newX.coerceIn(-50f, canvasWidth + 50f)
        val clampedY = newY.coerceIn(-50f, canvasHeight + 50f)

        graphic.position = Offset(clampedX, clampedY)
    }

    /**
     * Applies scale and rotation updates from multi-touch gesture.
     */
    fun updateGraphicTransform(
        graphic: Graphic,
        scaleFactor: Float,
        rotationDelta: Float
    ) {
        val newScale = (graphic.scale * scaleFactor).coerceIn(0.2f, 8.0f)
        val newRotation = (graphic.rotation + rotationDelta) % 360f

        graphic.scale = newScale
        graphic.rotation = if (newRotation < 0) newRotation + 360f else newRotation
    }

    /**
     * Creates a deep snapshot copy of all graphics for undo/redo.
     */
    fun createSnapshot(): List<Graphic> {
        return graphics.map { it.copyGraphic() }
    }

    /**
     * Restores state from a snapshot.
     */
    fun restoreSnapshot(snapshot: List<Graphic>) {
        graphics.clear()
        graphics.addAll(snapshot.map { it.copyGraphic() })
        selectedGraphicId = null
        isSnappingCenterH = false
        isSnappingCenterV = false
    }
}
