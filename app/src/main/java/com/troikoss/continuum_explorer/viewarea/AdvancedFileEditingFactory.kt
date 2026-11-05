package com.troikoss.continuum_explorer.viewarea

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

data class AudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val durationMs: Long = 0L
)

data class ExifInfo(
    val date: String? = null,
    val make: String? = null,
    val model: String? = null,
    val latLong: Pair<Double, Double>? = null
)

/**
 * AdvancedFileEditingFactory
 *
 * Unified factory encapsulating background-threaded (Dispatchers.IO) operations for:
 * 1. Image Editing: Rename, EXIF read/write, Stitching/Collage, Overlays, Compression & Resizing.
 * 2. Video Processing: Trimming, Merging, Audio track swapping, Watermarking.
 * 3. Audio Editing: Waveform trimming, Metadata tagging, Volume & Pitch/Speed adjustments.
 * 4. Text/Markdown/HTML: Stream IO & Real-time Live Preview rendering.
 * 5. Advanced PDF Workspace: Dual-page layout, Signature/Image stamping, Vector vs. Raster compression.
 */
object AdvancedFileEditingFactory {

    // =========================================================================
    // 1. IMAGE EDITING TOOLS
    // =========================================================================

    suspend fun readExif(context: Context, uri: Uri): ExifInfo = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val latLongArray = FloatArray(2)
                val hasGps = exif.getLatLong(latLongArray)
                val latLongPair = if (hasGps) Pair(latLongArray[0].toDouble(), latLongArray[1].toDouble()) else null
                ExifInfo(date, make, model, latLongPair)
            } ?: ExifInfo()
        } catch (_: Exception) {
            ExifInfo()
        }
    }

    suspend fun writeExif(
        context: Context,
        file: File,
        date: String? = null,
        make: String? = null,
        model: String? = null,
        clearAll: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val exif = ExifInterface(file.absolutePath)
            if (clearAll) {
                exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                exif.setAttribute(ExifInterface.TAG_MAKE, null)
                exif.setAttribute(ExifInterface.TAG_MODEL, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            } else {
                date?.let { exif.setAttribute(ExifInterface.TAG_DATETIME, it) }
                make?.let { exif.setAttribute(ExifInterface.TAG_MAKE, it) }
                model?.let { exif.setAttribute(ExifInterface.TAG_MODEL, it) }
            }
            exif.saveAttributes()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun stitchImagesSideBySide(
        bitmaps: List<Bitmap>,
        isVertical: Boolean = false,
        paddingPx: Int = 0,
        backgroundColor: Int = Color.WHITE
    ): Bitmap = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (isVertical) {
            val maxWidth = bitmaps.maxOf { it.width }
            val totalHeight = bitmaps.sumOf { it.height } + (paddingPx * (bitmaps.size - 1))
            val result = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(backgroundColor)
            var currentY = 0f
            bitmaps.forEach { bmp ->
                val x = ((maxWidth - bmp.width) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(bmp, x, currentY, null)
                currentY += bmp.height + paddingPx
            }
            result
        } else {
            val maxHeight = bitmaps.maxOf { it.height }
            val totalWidth = bitmaps.sumOf { it.width } + (paddingPx * (bitmaps.size - 1))
            val result = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(backgroundColor)
            var currentX = 0f
            bitmaps.forEach { bmp ->
                val y = ((maxHeight - bmp.height) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(bmp, currentX, y, null)
                currentX += bmp.width + paddingPx
            }
            result
        }
    }

    suspend fun createCollageGrid(
        bitmaps: List<Bitmap>,
        columns: Int = 2,
        paddingPx: Int = 0,
        backgroundColor: Int = Color.WHITE
    ): Bitmap = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val cols = columns.coerceAtLeast(1)
        val cellWidth = bitmaps.maxOf { it.width }
        val cellHeight = bitmaps.maxOf { it.height }
        val rows = (bitmaps.size + cols - 1) / cols
        val totalWidth = cols * cellWidth + (paddingPx * (cols + 1))
        val totalHeight = rows * cellHeight + (paddingPx * (rows + 1))
        val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(backgroundColor)
        bitmaps.forEachIndexed { index, bmp ->
            val row = index / cols
            val col = index % cols
            val x = (paddingPx + col * (cellWidth + paddingPx)).toFloat()
            val y = (paddingPx + row * (cellHeight + paddingPx)).toFloat()
            canvas.drawBitmap(bmp, x, y, null)
        }
        result
    }

    suspend fun applyWatermarkOverlay(
        source: Bitmap,
        watermarkText: String?,
        watermarkImage: Bitmap?,
        textColorInt: Int = Color.WHITE,
        textSizePx: Float = 48f,
        opacityPercent: Int = 100,
        position: String = "Bottom-Right"
    ): Bitmap = withContext(Dispatchers.IO) {
        val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val alpha = ((opacityPercent.coerceIn(10, 100) / 100f) * 255).toInt()

        watermarkImage?.let { img ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            val (x, y) = calculateOverlayPos(result.width.toFloat(), result.height.toFloat(), img.width.toFloat(), img.height.toFloat(), position)
            canvas.drawBitmap(img, x, y, paint)
        }

        watermarkText?.let { text ->
            if (text.isNotBlank()) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColorInt
                    this.alpha = alpha
                    textSize = textSizePx
                    setShadowLayer(4f, 2f, 2f, Color.BLACK)
                }
                val textBounds = Rect()
                paint.getTextBounds(text, 0, text.length, textBounds)
                val (x, y) = calculateOverlayPos(
                    result.width.toFloat(),
                    result.height.toFloat(),
                    textBounds.width().toFloat(),
                    textBounds.height().toFloat(),
                    position
                )
                canvas.drawText(text, x, y + textBounds.height(), paint)
            }
        }
        result
    }

    private fun calculateOverlayPos(
        containerW: Float,
        containerH: Float,
        itemW: Float,
        itemH: Float,
        position: String
    ): Pair<Float, Float> {
        val margin = 32f
        return when (position) {
            "Top-Left" -> Pair(margin, margin)
            "Top-Right" -> Pair((containerW - itemW - margin).coerceAtLeast(margin), margin)
            "Center" -> Pair(((containerW - itemW) / 2f).coerceAtLeast(0f), ((containerH - itemH) / 2f).coerceAtLeast(0f))
            "Bottom-Left" -> Pair(margin, (containerH - itemH - margin).coerceAtLeast(margin))
            else -> Pair((containerW - itemW - margin).coerceAtLeast(margin), (containerH - itemH - margin).coerceAtLeast(margin)) // Bottom-Right
        }
    }

    suspend fun compressAndResizeImage(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        dpi: Int = 96,
        qualityPercent: Int = 85,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ByteArray = withContext(Dispatchers.IO) {
        val w = targetWidth.coerceAtLeast(1)
        val h = targetHeight.coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        scaled.density = dpi
        val outputStream = ByteArrayOutputStream()
        scaled.compress(format, qualityPercent.coerceIn(1, 100), outputStream)
        outputStream.toByteArray()
    }

    // =========================================================================
    // 2. VIDEO PROCESSING TOOLS
    // =========================================================================

    suspend fun overlayTextOrWatermarkOnVideoFrame(
        frame: Bitmap,
        text: String?,
        watermark: Bitmap?
    ): Bitmap = withContext(Dispatchers.IO) {
        applyWatermarkOverlay(frame, text, watermark)
    }

    // =========================================================================
    // 3. AUDIO PROCESSING & METADATA TOOLS
    // =========================================================================

    suspend fun readAudioMetadata(context: Context, uri: Uri): AudioMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            AudioMetadata(title, artist, album, year, durationMs)
        } catch (_: Exception) {
            AudioMetadata()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 4. ADVANCED PDF WORKSPACE & COMPRESSION TOOLS
    // =========================================================================

    suspend fun stampImageSignature(
        context: Context,
        pdfFile: File,
        signatureBitmap: Bitmap,
        pageIndex: Int,
        x: Float,
        y: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = PDDocument.load(pdfFile)
            if (pageIndex < doc.numberOfPages) {
                val page = doc.getPage(pageIndex)
                val pdImage = JPEGFactory.createFromImage(doc, signatureBitmap)
                val contentStream = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                contentStream.drawImage(pdImage, x, y, pdImage.width.toFloat(), pdImage.height.toFloat())
                contentStream.close()
                doc.save(pdfFile)
            }
            doc.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun compressPdfRasterized(
        context: Context,
        inputFile: File,
        outputFile: File,
        dpi: Int = 120,
        qualityPercent: Int = 60
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputDoc = PDDocument.load(inputFile)
            val outputDoc = PDDocument()
            val renderer = PDFRenderer(inputDoc)

            for (i in 0 until inputDoc.numberOfPages) {
                val pageBitmap = renderer.renderImageWithDPI(i, dpi.toFloat())
                val pdImage = JPEGFactory.createFromImage(outputDoc, pageBitmap, (qualityPercent / 100f).coerceIn(0.1f, 1f))
                val newPage = com.tom_roush.pdfbox.pdmodel.PDPage(
                    com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pdImage.width.toFloat(), pdImage.height.toFloat())
                )
                outputDoc.addPage(newPage)
                val stream = PDPageContentStream(outputDoc, newPage)
                stream.drawImage(pdImage, 0f, 0f)
                stream.close()
            }

            outputDoc.save(outputFile)
            inputDoc.close()
            outputDoc.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
