package com.troikoss.continuum_explorer.ui.editor.photo

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

enum class ShapeType {
    FREEHAND, LINE, RECTANGLE, CIRCLE, ARROW
}

enum class PhotoTool(val label: String, val iconEmoji: String) {
    BRUSH("Brush", "🎨"),
    FILTER("Filters", "🪄"),
    TEXT("Text", "🔤"),
    STICKER("Stickers", "😊"),
    CROP_ROTATE("Crop & Rotate", "✂️"),
    WATERMARK("Watermark", "💧"),
    RESIZE("Resize", "📐")
}

enum class PhotoFilterType(val displayName: String) {
    NONE("Normal"),
    AUTO_FIX("Auto Fix"),
    BLACK_WHITE("B&W"),
    BRIGHTNESS("Bright"),
    CONTRAST("Contrast"),
    CROSS_PROCESS("Cross Proc"),
    DOCUMENT("Document"),
    FILL_LIGHT("Fill Light"),
    FISHEYE("Fisheye"),
    GRAIN("Grain"),
    GRAYSCALE("Grayscale"),
    LOMOISH("Lomoish"),
    NEGATIVE("Invert"),
    POSTERIZE("Posterize"),
    ROTATE("Rotate"),
    SATURATE("Saturate"),
    SEPIA("Sepia"),
    SHARPEN("Sharpen"),
    TEMPERATURE("Warm/Cool"),
    TINT("Tint"),
    VIGNETTE("Vignette")
}

data class DrawingStroke(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val shapeType: ShapeType = ShapeType.FREEHAND,
    val startPoint: Offset = Offset.Zero,
    val endPoint: Offset = Offset.Zero
)

data class TextOverlayItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var text: String,
    var color: Color = Color.White,
    var backgroundColor: Color = Color.Transparent,
    var fontSize: Float = 24f,
    var position: Offset = Offset(100f, 100f),
    var rotation: Float = 0f,
    var scale: Float = 1.0f,
    var hasShadow: Boolean = false,
    var shadowColor: Color = Color.Black,
    var shadowRadius: Float = 4f,
    var hasBorder: Boolean = false,
    var borderColor: Color = Color.Black,
    var borderWidth: Float = 2f
)

data class StickerOverlayItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val emojiOrSticker: String,
    var position: Offset = Offset(150f, 150f),
    var scale: Float = 1.0f,
    var rotation: Float = 0f
)

/**
 * Main State controller class for Photo Editing session.
 */
class PhotoEditorStateController {
    var activeTool by mutableStateOf(PhotoTool.BRUSH)

    // Brush state
    var brushColor by mutableStateOf(Color.Red)
    var brushSize by mutableFloatStateOf(12f)
    var isEraser by mutableStateOf(false)
    var selectedShapeType by mutableStateOf(ShapeType.FREEHAND)

    val strokes = mutableStateListOf<DrawingStroke>()
    val undoStrokes = mutableStateListOf<DrawingStroke>()

    // Active stroke during drag
    var currentPath by mutableStateOf<Path?>(null)
    var currentStartPoint by mutableStateOf(Offset.Zero)
    var currentEndPoint by mutableStateOf(Offset.Zero)

    // Filter
    var activeFilter by mutableStateOf(PhotoFilterType.NONE)

    // Overlays
    val textOverlays = mutableStateListOf<TextOverlayItem>()
    val stickerOverlays = mutableStateListOf<StickerOverlayItem>()

    // Transform
    var rotationAngle by mutableIntStateOf(0)
    var isFlipHorizontal by mutableStateOf(false)
    var isFlipVertical by mutableStateOf(false)

    // Watermark
    var watermarkText by mutableStateOf("")
    var watermarkOpacity by mutableFloatStateOf(80f)
    var watermarkPosition by mutableStateOf("Bottom-Right")

    // Resize
    var targetWidthInput by mutableStateOf("")
    var targetHeightInput by mutableStateOf("")
    var qualityPercent by mutableFloatStateOf(85f)

    fun undo() {
        if (strokes.isNotEmpty()) {
            val last = strokes.removeAt(strokes.size - 1)
            undoStrokes.add(last)
        }
    }

    fun redo() {
        if (undoStrokes.isNotEmpty()) {
            val last = undoStrokes.removeAt(undoStrokes.size - 1)
            strokes.add(last)
        }
    }

    fun resetAll(originalWidth: Int, originalHeight: Int) {
        strokes.clear()
        undoStrokes.clear()
        currentPath = null
        activeFilter = PhotoFilterType.NONE
        textOverlays.clear()
        stickerOverlays.clear()
        rotationAngle = 0
        isFlipHorizontal = false
        isFlipVertical = false
        watermarkText = ""
        targetWidthInput = originalWidth.toString()
        targetHeightInput = originalHeight.toString()
    }
}

@Composable
fun rememberPhotoEditorStateController(): PhotoEditorStateController {
    return remember { PhotoEditorStateController() }
}
