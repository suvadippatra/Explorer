package com.troikoss.continuum_explorer.ui.converter

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.troikoss.continuum_explorer.data.db.SuiteActivityType
import com.troikoss.continuum_explorer.data.db.SuiteRecentDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class StudentPresetType(
    val title: String,
    val subtitle: String,
    val minKb: Int,
    val maxKb: Int,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val highContrastSignature: Boolean = false
) {
    PASSPORT_PHOTO_STANDARD(
        title = "Exam Passport Photo (20–50 KB)",
        subtitle = "200×230 px • 3.5×4.5 cm ratio • Strict 20–50 KB",
        minKb = 20,
        maxKb = 48,
        targetWidthPx = 200,
        targetHeightPx = 230
    ),
    PASSPORT_PHOTO_HD(
        title = "High-DPI Passport Photo (30–80 KB)",
        subtitle = "413×531 px (300 DPI) • Strict 30–80 KB",
        minKb = 30,
        maxKb = 78,
        targetWidthPx = 413,
        targetHeightPx = 531
    ),
    SIGNATURE_STANDARD(
        title = "Student Exam Signature (10–20 KB)",
        subtitle = "140×60 px • Auto white paper cleanup • 10–20 KB",
        minKb = 10,
        maxKb = 19,
        targetWidthPx = 140,
        targetHeightPx = 60,
        highContrastSignature = true
    ),
    SIGNATURE_WIDE(
        title = "Wide Signature (15–30 KB)",
        subtitle = "300×100 px • Auto white paper cleanup • 15–30 KB",
        minKb = 15,
        maxKb = 29,
        targetWidthPx = 300,
        targetHeightPx = 100,
        highContrastSignature = true
    ),
    CUSTOM_KB(
        title = "Custom KB & Pixel Resizer",
        subtitle = "Set exact max KB slider & custom pixel dimensions",
        minKb = 5,
        maxKb = 50,
        targetWidthPx = 0,
        targetHeightPx = 0
    )
}

enum class PdfCompressionLevel(
    val label: String,
    val description: String,
    val scaleFactor: Float,
    val jpegQuality: Int
) {
    EXTREME("Extreme Compression", "Smallest KB (~72 DPI, 45% JPEG)", 1.0f, 45),
    RECOMMENDED("Recommended Balanced", "Sharp text & small size (~110 DPI, 68% JPEG)", 1.45f, 68),
    HIGH_QUALITY("High Quality", "Print-ready clarity (~150 DPI, 82% JPEG)", 2.0f, 82)
}

/**
 * Inspired by GoogleChromeLabs/squoosh:
 * Iterative binary-search image quantizer, student exam photo/signature KB target engine,
 * and native PDF page stream compressor.
 */
object SquooshImageCompressor {

    suspend fun compressImageToTargetKb(
        context: Context,
        sourceFile: File,
        outputFile: File,
        minTargetKb: Int = 0,
        maxTargetKb: Int = 50,
        exactWidthPx: Int = 0,
        exactHeightPx: Int = 0,
        enhanceSignatureContrast: Boolean = false,
        outputWebp: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        var rawBitmap: Bitmap? = null
        var workingBitmap: Bitmap? = null
        try {
            onProgress(0.1f, "Decoding source image...")
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            rawBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot decode image: ${sourceFile.name}"))

            onProgress(0.25f, "Normalizing dimensions & background...")
            val targetW = if (exactWidthPx > 0) exactWidthPx else rawBitmap.width
            val targetH = if (exactHeightPx > 0) exactHeightPx else rawBitmap.height

            workingBitmap = createWhiteBackedBitmap(
                source = rawBitmap,
                targetWidth = targetW,
                targetHeight = targetH,
                enhanceSignature = enhanceSignatureContrast
            )

            val maxBytes = (maxTargetKb.coerceAtLeast(5)) * 1024
            val minBytes = (minTargetKb.coerceAtLeast(0)) * 1024
            val compressFormat = if (outputWebp) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            } else {
                Bitmap.CompressFormat.JPEG
            }

            onProgress(0.45f, "Running Squoosh binary-search quantization...")
            var bestBytes: ByteArray? = null
            var scaleStep = 1.0f

            // Up to 5 dimension scale steps if even quality=10 exceeds maxBytes
            for (step in 0..5) {
                val scaledBmp = if (scaleStep < 0.99f && exactWidthPx <= 0 && exactHeightPx <= 0) {
                    val sw = (workingBitmap.width * scaleStep).roundToInt().coerceAtLeast(64)
                    val sh = (workingBitmap.height * scaleStep).roundToInt().coerceAtLeast(40)
                    Bitmap.createScaledBitmap(workingBitmap, sw, sh, true)
                } else {
                    workingBitmap
                }

                var lowQ = 8
                var highQ = 98
                var candidateUnderMax: ByteArray? = null

                while (lowQ <= highQ) {
                    val midQ = (lowQ + highQ) / 2
                    val baos = ByteArrayOutputStream()
                    scaledBmp.compress(compressFormat, midQ, baos)
                    val bytes = baos.toByteArray()
                    if (bytes.size <= maxBytes) {
                        candidateUnderMax = bytes
                        lowQ = midQ + 1 // Try higher quality while staying <= maxBytes
                    } else {
                        highQ = midQ - 1
                    }
                }

                if (scaledBmp !== workingBitmap) {
                    scaledBmp.recycle()
                }

                if (candidateUnderMax != null) {
                    bestBytes = candidateUnderMax
                    break
                }
                scaleStep *= 0.78f
            }

            // If still null (e.g. fixed dimensions with very low KB limit), encode at minimum quality
            if (bestBytes == null) {
                val baos = ByteArrayOutputStream()
                workingBitmap.compress(compressFormat, 8, baos)
                bestBytes = baos.toByteArray()
            }

            // If exam portal requires a strict minimum KB (e.g., 20 KB minimum for passport photo)
            // and the quantized image is smaller than minBytes, first try quality 100, then pad safely in JPEG comment/APP marker or trailing bytes
            if (minBytes > 0 && bestBytes.size < minBytes && minBytes < maxBytes) {
                val hqBaos = ByteArrayOutputStream()
                workingBitmap.compress(compressFormat, 100, hqBaos)
                val hqBytes = hqBaos.toByteArray()
                if (hqBytes.size in minBytes..maxBytes) {
                    bestBytes = hqBytes
                } else if (bestBytes.size < minBytes && !outputWebp) {
                    bestBytes = padJpegToMinimumBytes(bestBytes, minBytes)
                }
            }

            onProgress(0.85f, "Saving optimized image...")
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                fos.write(bestBytes)
                fos.flush()
            }

            SuiteRecentDatabase.recordFileActivity(
                context = context,
                file = outputFile,
                editorToolId = "file_converter",
                editorToolName = "File Converter (Squoosh KB)",
                activityType = SuiteActivityType.CREATED
            )
            onProgress(1.0f, "Saved ${outputFile.name} (${outputFile.length() / 1024} KB)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            rawBitmap?.recycle()
            workingBitmap?.recycle()
        }
    }

    /**
     * Compresses an existing PDF file using native PdfRenderer + JPEG stream quantization + PdfDocument.
     */
    suspend fun compressPdfToTarget(
        context: Context,
        sourcePdf: File,
        outputFile: File,
        level: PdfCompressionLevel,
        targetMaxKb: Int = 0,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            onProgress(0.08f, "Opening PDF document...")
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount.coerceAtLeast(1)

            // Adjust quality if user specified a strict targetMaxKb
            val perPageBudgetBytes = if (targetMaxKb > 0) {
                ((targetMaxKb * 1024) / pageCount).coerceAtLeast(12 * 1024)
            } else 0

            val outPdf = PdfDocument()
            try {
                for (i in 0 until pageCount) {
                    onProgress(
                        0.1f + (0.8f * i / pageCount.toFloat()),
                        "Compressing page ${i + 1} of $pageCount..."
                    )
                    val page = renderer.openPage(i)
                    val origW = page.width.coerceAtLeast(1)
                    val origH = page.height.coerceAtLeast(1)

                    val renderW = (origW * level.scaleFactor).roundToInt().coerceIn(300, 1800)
                    val renderH = (origH * level.scaleFactor).roundToInt().coerceIn(400, 2400)

                    val renderBmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(renderBmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(renderBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    // Quantize page via JPEG stream to shrink embedded bitmap weight
                    var q = level.jpegQuality
                    var jpegBytes: ByteArray
                    do {
                        val baos = ByteArrayOutputStream()
                        renderBmp.compress(Bitmap.CompressFormat.JPEG, q, baos)
                        jpegBytes = baos.toByteArray()
                        q -= 12
                    } while (perPageBudgetBytes > 0 && jpegBytes.size > perPageBudgetBytes && q >= 20)

                    renderBmp.recycle()

                    val quantizedBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                    val outPage = outPdf.startPage(pageInfo)
                    outPage.canvas.drawColor(Color.WHITE)
                    if (quantizedBmp != null) {
                        val dstRect = Rect(0, 0, origW, origH)
                        outPage.canvas.drawBitmap(quantizedBmp, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        quantizedBmp.recycle()
                    }
                    outPdf.finishPage(outPage)
                }

                onProgress(0.92f, "Writing compressed PDF...")
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    outPdf.writeTo(fos)
                }
            } finally {
                outPdf.close()
            }

            SuiteRecentDatabase.recordFileActivity(
                context = context,
                file = outputFile,
                editorToolId = "file_converter",
                editorToolName = "PDF Compressor",
                activityType = SuiteActivityType.CREATED
            )
            onProgress(1.0f, "Compressed PDF to ${outputFile.length() / 1024} KB")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Combines multiple images into a single A4 PDF with optional JPEG quantization.
     */
    suspend fun combineImagesToPdf(
        context: Context,
        imageFiles: List<File>,
        outputFile: File,
        jpegQuality: Int = 78,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        if (imageFiles.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images selected"))
        }
        val pdfDoc = PdfDocument()
        try {
            val total = imageFiles.size
            imageFiles.forEachIndexed { idx, imgFile ->
                onProgress(
                    (idx.toFloat() / total.toFloat()) * 0.9f,
                    "Adding image ${idx + 1} of $total..."
                )
                val rawBmp = BitmapFactory.decodeFile(imgFile.absolutePath) ?: return@forEachIndexed
                val maxDim = 1600
                val scale = min(1f, maxDim.toFloat() / max(rawBmp.width, rawBmp.height).coerceAtLeast(1))
                val scaledW = (rawBmp.width * scale).roundToInt().coerceAtLeast(1)
                val scaledH = (rawBmp.height * scale).roundToInt().coerceAtLeast(1)
                val scaledBmp = if (scale < 0.99f) {
                    Bitmap.createScaledBitmap(rawBmp, scaledW, scaledH, true).also { rawBmp.recycle() }
                } else rawBmp

                val baos = ByteArrayOutputStream()
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(25, 95), baos)
                scaledBmp.recycle()
                val jpegBytes = baos.toByteArray()
                val finalBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return@forEachIndexed

                // A4 Page (595 x 842 pt) or landscape if image is wide
                val pageW = if (finalBmp.width > finalBmp.height) 842 else 595
                val pageH = if (finalBmp.width > finalBmp.height) 595 else 842
                val margin = 24f
                val availW = pageW - margin * 2
                val availH = pageH - margin * 2
                val fitScale = min(availW / finalBmp.width.toFloat(), availH / finalBmp.height.toFloat())
                val drawW = finalBmp.width * fitScale
                val drawH = finalBmp.height * fitScale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(
                    finalBmp,
                    null,
                    RectF(left, top, left + drawW, top + drawH),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                pdfDoc.finishPage(page)
                finalBmp.recycle()
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
            SuiteRecentDatabase.recordFileActivity(
                context = context,
                file = outputFile,
                editorToolId = "file_converter",
                editorToolName = "Image to PDF Combiner",
                activityType = SuiteActivityType.CREATED
            )
            onProgress(1.0f, "Created ${outputFile.name} (${outputFile.length() / 1024} KB)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pdfDoc.close()
        }
    }

    private fun createWhiteBackedBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        enhanceSignature: Boolean
    ): Bitmap {
        val w = targetWidth.coerceIn(32, 4096)
        val h = targetHeight.coerceIn(32, 4096)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        // Center-crop / fit nicely inside target box
        val scale = min(w.toFloat() / source.width.coerceAtLeast(1), h.toFloat() / source.height.coerceAtLeast(1))
        val drawW = source.width * scale
        val drawH = source.height * scale
        val left = (w - drawW) / 2f
        val top = (h - drawH) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (enhanceSignature) {
            // High-contrast ink filter: boosts white paper to pure white and darkens ink strokes
            val contrast = 1.85f
            val brightness = 35f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, null, RectF(left, top, left + drawW, top + drawH), paint)
        return out
    }

    private fun padJpegToMinimumBytes(jpegBytes: ByteArray, minBytes: Int): ByteArray {
        val needed = minBytes - jpegBytes.size
        if (needed <= 0) return jpegBytes
        // Insert a standard JPEG COM (0xFF 0xFE) comment segment before EOI (0xFF 0xD9)
        if (jpegBytes.size >= 4 &&
            jpegBytes[jpegBytes.size - 2] == 0xFF.toByte() &&
            jpegBytes[jpegBytes.size - 1] == 0xD9.toByte()
        ) {
            val out = ByteArrayOutputStream(minBytes + 16)
            out.write(jpegBytes, 0, jpegBytes.size - 2)
            var remaining = needed
            while (remaining > 0) {
                val payloadLen = min(remaining, 32000)
                val segLen = payloadLen + 2
                out.write(0xFF)
                out.write(0xFE)
                out.write((segLen shr 8) and 0xFF)
                out.write(segLen and 0xFF)
                out.write(ByteArray(payloadLen) { ' '.code.toByte() })
                remaining -= payloadLen
            }
            out.write(0xFF)
            out.write(0xD9)
            return out.toByteArray()
        }
        return jpegBytes
    }
}
