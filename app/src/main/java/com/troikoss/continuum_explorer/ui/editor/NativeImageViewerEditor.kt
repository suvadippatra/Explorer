package com.troikoss.continuum_explorer.ui.editor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper
import coil.compose.AsyncImage
import com.troikoss.continuum_explorer.model.UniversalFile
import com.troikoss.continuum_explorer.ui.components.InAppFilePickerDialog
import com.troikoss.continuum_explorer.ui.editor.photo.*
import com.troikoss.continuum_explorer.utils.FileExplorerState
import com.troikoss.continuum_explorer.utils.GlobalEvents
import com.troikoss.continuum_explorer.utils.getUriForUniversalFile
import com.troikoss.continuum_explorer.utils.toUniversal
import com.troikoss.continuum_explorer.viewarea.AdvancedFileEditingFactory
import com.troikoss.continuum_explorer.viewarea.ExifInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Universal Native Image Viewer & Modular Photo Editor.
 * Features:
 * 1. Default View Mode:
 *    - Pinch to zoom & pan
 *    - Double tap zoom reset
 *    - File details & EXIF metadata popup
 *    - Share, Print, Move, Copy, Open External
 *    - Prominent Edit button & Edit option in triple-dot menu
 *
 * 2. In-Tab Interactive Photo Editing Mode (using ui.editor.photo package):
 *    - Interactive Canvas with Brush, Lines, Shapes, Colors, Sizes, Eraser
 *    - Photo Filters (Sepia, B&W, Brightness, Contrast, Sharpen, Vignette, Invert, Warm, Cool, Vintage)
 *    - Text & Sticker Overlays (draggable on canvas)
 *    - Rotate & Flip controls
 *    - Watermarks
 *    - Resize & Quality Compression
 *    - Undo / Redo / Reset history
 *    - Save options: "Save as New File" (default) or "Replace Original File"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeImageViewerEditor(
    file: UniversalFile,
    appState: FileExplorerState,
    onMenuClick: () -> Unit,
    onExitEditor: (() -> Unit)? = null,
    startInEditMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fileRef = remember(file) { file.fileRef ?: (file.absolutePath.takeIf { it.startsWith("/") }?.let { File(it) }) }
    val uri = remember(file) { file.documentFileRef?.uri ?: getUriForUniversalFile(context, file) }

    // State Controller
    val photoState = rememberPhotoEditorStateController()

    // Mode & Dialog Toggles
    var isEditMode by remember(file, startInEditMode) { mutableStateOf(startInEditMode) }
    var showBars by remember { mutableStateOf(true) }
    var showExifDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var newOverlayText by remember { mutableStateOf("") }

    // File Action Pickers
    var showMovePicker by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }

    // View Mode Pan & Zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Base Bitmap & EXIF
    var baseBitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var exifInfo by remember(file) { mutableStateOf<ExifInfo?>(null) }
    var isLoadingImage by remember(file) { mutableStateOf(true) }

    // Load Base Image & EXIF
    LaunchedEffect(file) {
        isLoadingImage = true
        withContext(Dispatchers.IO) {
            try {
                if (fileRef != null && fileRef.exists()) {
                    baseBitmap = BitmapFactory.decodeFile(fileRef.absolutePath)
                } else if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        baseBitmap = BitmapFactory.decodeStream(stream)
                    }
                }
                if (uri != null) {
                    exifInfo = AdvancedFileEditingFactory.readExif(context, uri)
                }
            } catch (_: Exception) {
                baseBitmap = null
            }
        }
        isLoadingImage = false
    }

    val imageWidth = remember(baseBitmap) { baseBitmap?.width ?: 0 }
    val imageHeight = remember(baseBitmap) { baseBitmap?.height ?: 0 }
    val fileSizeFormatted = remember(file, baseBitmap) {
        val len = fileRef?.length() ?: 0L
        if (len > 0) Formatter.formatFileSize(context, len) else "Unknown Size"
    }

    // Initialize dimensions in state controller when bitmap loads
    LaunchedEffect(baseBitmap) {
        baseBitmap?.let { bmp ->
            photoState.targetWidthInput = bmp.width.toString()
            photoState.targetHeightInput = bmp.height.toString()
        }
    }

    // View Mode Actions
    fun printImage() {
        val printBitmap = baseBitmap ?: return
        val printHelper = PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
        printHelper.printBitmap("Image_${file.name}", printBitmap)
    }

    fun shareImage() {
        val shareUri = uri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    }

    fun openExternal() {
        val targetUri = uri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No external image viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    // Execute Image Save
    fun executeSaveImage(saveAsNew: Boolean, customFileName: String, qualityPercent: Float) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val original = baseBitmap ?: return@launch

                // 1. Rotation & Flip
                val matrix = Matrix()
                if (photoState.rotationAngle != 0) matrix.postRotate(photoState.rotationAngle.toFloat())
                if (photoState.isFlipHorizontal || photoState.isFlipVertical) {
                    matrix.postScale(if (photoState.isFlipHorizontal) -1f else 1f, if (photoState.isFlipVertical) -1f else 1f)
                }
                var processedBmp = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

                // 2. Filter
                processedBmp = PhotoFilterHelper.applyFilter(processedBmp, photoState.activeFilter)

                // 3. Render Canvas Strokes, Text, Stickers
                val finalCanvas = AndroidCanvas(processedBmp)

                photoState.strokes.forEach { stroke ->
                    val p = AndroidPaint().apply {
                        color = stroke.color.toArgb()
                        strokeWidth = stroke.strokeWidth
                        style = AndroidPaint.Style.STROKE
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                        isAntiAlias = true
                        if (stroke.isEraser) {
                            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                        }
                    }

                    when (stroke.shapeType) {
                        ShapeType.FREEHAND -> finalCanvas.drawPath(stroke.path.asAndroidPath(), p)
                        ShapeType.LINE -> finalCanvas.drawLine(stroke.startPoint.x, stroke.startPoint.y, stroke.endPoint.x, stroke.endPoint.y, p)
                        ShapeType.RECTANGLE -> finalCanvas.drawRect(stroke.startPoint.x, stroke.startPoint.y, stroke.endPoint.x, stroke.endPoint.y, p)
                        ShapeType.CIRCLE -> {
                            val rx = Math.abs(stroke.endPoint.x - stroke.startPoint.x) / 2f
                            val ry = Math.abs(stroke.endPoint.y - stroke.startPoint.y) / 2f
                            val cx = (stroke.startPoint.x + stroke.endPoint.x) / 2f
                            val cy = (stroke.startPoint.y + stroke.endPoint.y) / 2f
                            finalCanvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
                        }
                        ShapeType.ARROW -> finalCanvas.drawLine(stroke.startPoint.x, stroke.startPoint.y, stroke.endPoint.x, stroke.endPoint.y, p)
                    }
                }

                // Draw Text Overlays
                photoState.textOverlays.forEach { txt ->
                    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = txt.color.toArgb()
                        textSize = txt.fontSize * 1.5f
                        isFakeBoldText = true
                    }
                    if (txt.backgroundColor != Color.Transparent) {
                        val bgPaint = AndroidPaint().apply { color = txt.backgroundColor.toArgb() }
                        val bounds = android.graphics.Rect()
                        textPaint.getTextBounds(txt.text, 0, txt.text.length, bounds)
                        finalCanvas.drawRect(
                            txt.position.x - 8f,
                            txt.position.y - bounds.height() - 8f,
                            txt.position.x + bounds.width() + 8f,
                            txt.position.y + 8f,
                            bgPaint
                        )
                    }
                    finalCanvas.drawText(txt.text, txt.position.x, txt.position.y, textPaint)
                }

                // Draw Stickers
                photoState.stickerOverlays.forEach { stk ->
                    val stkPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        textSize = 48f * stk.scale
                    }
                    finalCanvas.drawText(stk.emojiOrSticker, stk.position.x, stk.position.y, stkPaint)
                }

                // 4. Watermark
                if (photoState.watermarkText.isNotBlank()) {
                    processedBmp = AdvancedFileEditingFactory.applyWatermarkOverlay(
                        source = processedBmp,
                        watermarkText = photoState.watermarkText,
                        watermarkImage = null,
                        opacityPercent = photoState.watermarkOpacity.toInt(),
                        position = photoState.watermarkPosition
                    )
                }

                // 5. Resize & Compress
                val targetW = photoState.targetWidthInput.toIntOrNull() ?: processedBmp.width
                val targetH = photoState.targetHeightInput.toIntOrNull() ?: processedBmp.height

                val compressedBytes = AdvancedFileEditingFactory.compressAndResizeImage(
                    source = processedBmp,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    qualityPercent = qualityPercent.toInt()
                )

                // Save to file destination
                val parentDir = file.fileRef?.parentFile ?: Environment.getExternalStorageDirectory()
                val targetFileName = if (customFileName.isNotBlank()) {
                    customFileName
                } else if (saveAsNew) {
                    val nameNoExt = file.name.substringBeforeLast('.')
                    val ext = file.name.substringAfterLast('.', "jpg")
                    "${nameNoExt}_edited.$ext"
                } else {
                    file.name
                }

                val finalFile = File(parentDir, targetFileName)
                FileOutputStream(finalFile).use { out -> out.write(compressedBytes) }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved image: ${finalFile.name}", Toast.LENGTH_SHORT).show()
                    showSaveDialog = false
                    isEditMode = false
                    appState.viewingFile = finalFile.toUniversal()
                    appState.refresh()
                    GlobalEvents.triggerRefresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        if (isLoadingImage) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (baseBitmap == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = uri ?: fileRef,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (!isEditMode) {
            // =========================================================================
            // --- 1. DEFAULT VIEW MODE ---
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showBars = !showBars },
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5.0f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = baseBitmap!!.asImageBitmap(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // View Top Bar
            if (showBars) {
                PhotoEditorViewTopBar(
                    fileName = file.name,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    fileSizeFormatted = fileSizeFormatted,
                    onMenuClick = { (onExitEditor ?: onMenuClick).invoke() },
                    onEnterEditMode = { isEditMode = true },
                    onShowExifDialog = { showExifDialog = true },
                    onShare = { shareImage() },
                    onPrint = { printImage() },
                    onMove = { showMovePicker = true },
                    onCopy = { showCopyPicker = true },
                    onOpenExternal = { openExternal() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        } else {
            // =========================================================================
            // --- 2. IN-TAB INTERACTIVE PHOTO EDITOR MODE (PhotoEditorView) ---
            // =========================================================================
            PhotoEditorView(
                baseBitmap = baseBitmap!!,
                fileName = file.name,
                targetSaveFile = fileRef,
                onExit = {
                    if (startInEditMode) {
                        (onExitEditor ?: onMenuClick).invoke()
                    } else {
                        isEditMode = false
                    }
                },
                onSaveSuccess = { savedFile ->
                    com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase.recordFileActivity(
                        context = context,
                        file = savedFile,
                        activityType = com.troikoss.continuum_explorer.data.db.SuiteActivityType.EDITED
                    )
                    appState.viewingFile = savedFile.toUniversal()
                    appState.refresh()
                    GlobalEvents.triggerRefresh()
                    if (startInEditMode) {
                        (onExitEditor ?: onMenuClick).invoke()
                    } else {
                        isEditMode = false
                    }
                },
                onMenuClick = onMenuClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // --- DIALOGS & MODALS ---

    // EXIF Properties Dialog
    if (showExifDialog) {
        AlertDialog(
            onDismissRequest = { showExifDialog = false },
            title = { Text("Image Properties & EXIF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filename: ${file.name}", style = MaterialTheme.typography.bodySmall)
                    Text("Dimensions: ${imageWidth} x ${imageHeight} px", style = MaterialTheme.typography.bodySmall)
                    Text("File Size: $fileSizeFormatted", style = MaterialTheme.typography.bodySmall)
                    Text("Camera Make: ${exifInfo?.make ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    Text("Camera Model: ${exifInfo?.model ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    Text("Date Taken: ${exifInfo?.date ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showExifDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Add Text Overlay Dialog
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("Add Text Overlay", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newOverlayText,
                    onValueChange = { newOverlayText = it },
                    label = { Text("Overlay Text") },
                    placeholder = { Text("Type caption or watermark...") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    trailingIcon = {
                        if (newOverlayText.isNotEmpty()) {
                            IconButton(onClick = { newOverlayText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newOverlayText.isNotBlank()) {
                            photoState.textOverlays.add(TextOverlayItem(text = newOverlayText))
                            newOverlayText = ""
                        }
                        showAddTextDialog = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Save Image Dialog
    if (showSaveDialog) {
        PhotoSaveDialog(
            originalFileName = file.name,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { saveAsNew, customName, quality ->
                executeSaveImage(saveAsNew, customName, quality)
            }
        )
    }

    // Move File Picker
    if (showMovePicker) {
        InAppFilePickerDialog(
            title = "Select Destination Folder to Move Image",
            selectFolders = true,
            initialDirectory = fileRef?.parentFile ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showMovePicker = false },
            onSelected = { targetFolder ->
                showMovePicker = false
                if (fileRef != null && fileRef.exists() && targetFolder.isDirectory) {
                    val newFile = File(targetFolder, fileRef.name)
                    if (fileRef.renameTo(newFile)) {
                        Toast.makeText(context, "Moved to ${newFile.parent}", Toast.LENGTH_SHORT).show()
                        appState.viewingFile = newFile.toUniversal()
                        appState.refresh()
                        GlobalEvents.triggerRefresh()
                    } else {
                        Toast.makeText(context, "Failed to move file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Copy File Picker
    if (showCopyPicker) {
        InAppFilePickerDialog(
            title = "Select Destination Folder to Copy Image",
            selectFolders = true,
            initialDirectory = fileRef?.parentFile ?: Environment.getExternalStorageDirectory(),
            onDismiss = { showCopyPicker = false },
            onSelected = { targetFolder ->
                showCopyPicker = false
                if (fileRef != null && fileRef.exists() && targetFolder.isDirectory) {
                    val destFile = File(targetFolder, fileRef.name)
                    try {
                        fileRef.copyTo(destFile, overwrite = true)
                        Toast.makeText(context, "Copied to ${destFile.parent}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
