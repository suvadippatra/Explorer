package com.troikoss.continuum_explorer.ui.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 100% On-Device Image & Graphics Conversion Engine.
 * Supports PNG, JPG/JPEG, WEBP, BMP, ICO, SVG rasterization, and single-page PDF generation
 * with quality tuning, max-dimension scaling, alpha-to-solid background compositing, and EXIF control.
 */
object ImageConversionEngine {

    suspend fun convertImage(
        context: Context,
        sourceFile: File,
        targetExtension: String,
        outputFile: File,
        tuning: ConversionTuningConfig,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var sourceBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            onProgress(0.15f, "Decoding ${sourceFile.name}...")
            val srcExt = ConversionFormatMatrix.resolveSourceExtension(sourceFile)

            sourceBitmap = when (srcExt) {
                "svg" -> decodeSvgToBitmap(context, sourceFile, tuning.maxDimensionPx)
                "ico" -> decodeIcoToBitmap(sourceFile)
                else -> decodeRasterWithExifOrientation(sourceFile, tuning.maxDimensionPx)
            } ?: return@withContext Result.failure(
                IllegalArgumentException("Unable to decode image '${sourceFile.name}'.")
            )

            onProgress(0.45f, "Applying resolution & color tuning...")
            val workingBitmap = if (tuning.maxDimensionPx > 0) {
                val maxSide = max(sourceBitmap.width, sourceBitmap.height)
                if (maxSide > tuning.maxDimensionPx) {
                    val ratio = tuning.maxDimensionPx.toFloat() / maxSide.toFloat()
                    val targetW = (sourceBitmap.width * ratio).roundToInt().coerceAtLeast(1)
                    val targetH = (sourceBitmap.height * ratio).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true).also {
                        if (it !== sourceBitmap) scaledBitmap = it
                    }
                } else {
                    sourceBitmap
                }
            } else {
                sourceBitmap
            }

            onProgress(0.70f, "Encoding to ${targetExtension.uppercase()}...")
            val cleanTarget = targetExtension.lowercase().trimStart('.')
            when (cleanTarget) {
                "png" -> {
                    FileOutputStream(outputFile).use { fos ->
                        workingBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                }
                "jpg", "jpeg" -> {
                    val flattened = flattenAlpha onto tuning.jpgBackgroundColorHex.toInt() from workingBitmap
                    try {
                        FileOutputStream(outputFile).use { fos ->
                            flattened.compress(
                                Bitmap.CompressFormat.JPEG,
                                tuning.imageQuality.coerceIn(10, 100),
                                fos
                            )
                        }
                    } finally {
                        if (flattened !== workingBitmap) flattened.recycle()
                    }
                    if (!tuning.stripExifMetadata && srcExt in listOf("jpg", "jpeg")) {
                        copySafeExifTags(sourceFile, outputFile)
                    }
                }
                "webp" -> {
                    FileOutputStream(outputFile).use { fos ->
                        val q = tuning.imageQuality.coerceIn(10, 100)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val format = if (q >= 100) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            }
                            workingBitmap.compress(format, q, fos)
                        } else {
                            @Suppress("DEPRECATION")
                            workingBitmap.compress(Bitmap.CompressFormat.WEBP, q, fos)
                        }
                    }
                }
                "bmp" -> {
                    writeBitmapAsBmp(workingBitmap, outputFile)
                }
                "ico" -> {
                    writeBitmapAsIco(workingBitmap, outputFile, tuning.icoSizePx.coerceIn(16, 256))
                }
                "pdf" -> {
                    writeBitmapAsPdf(workingBitmap, outputFile)
                }
                else -> {
                    return@withContext Result.failure(
                        UnsupportedOperationException("Unsupported image target format: .$cleanTarget")
                    )
                }
            }

            onProgress(1.0f, "Converted to ${outputFile.name}")
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                val sb = scaledBitmap
                val src = sourceBitmap
                if (sb != null && sb !== src && !sb.isRecycled) {
                    sb.recycle()
                }
                if (src != null && !src.isRecycled) {
                    src.recycle()
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun decodeSvgToBitmap(
        context: Context,
        svgFile: File,
        maxDimensionPx: Int
    ): Bitmap? {
        return try {
            val targetDim = if (maxDimensionPx > 0) maxDimensionPx else 1024
            val loader = ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .build()
            val request = ImageRequest.Builder(context)
                .data(svgFile)
                .size(Size(targetDim, targetDim))
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val drawable = result.drawable ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: targetDim
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: targetDim
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeIcoToBitmap(icoFile: File): Bitmap? {
        return try {
            // Try direct BitmapFactory first (Android supports many ICO files natively)
            BitmapFactory.decodeFile(icoFile.absolutePath)?.let { return it }
            // Fallback: scan for embedded PNG signature inside ICO container
            val bytes = icoFile.readBytes()
            val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            for (i in 0..(bytes.size - pngMagic.size)) {
                var match = true
                for (j in pngMagic.indices) {
                    if (bytes[i + j] != pngMagic[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    return BitmapFactory.decodeByteArray(bytes, i, bytes.size - i)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeRasterWithExifOrientation(file: File, maxDimensionPx: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        val capDimension = if (maxDimensionPx > 0) maxDimensionPx else 4096
        var sampleSize = 1
        while ((rawW / sampleSize) > capDimension * 2 || (rawH / sampleSize) > capDimension * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (_: Exception) {
            decoded
        }
    }

    private object flattenAlpha {
        infix fun onto(bgColor: Int) = BgHolder(bgColor)
        class BgHolder(val bgColor: Int) {
            infix fun from(source: Bitmap): Bitmap {
                if (!source.hasAlpha()) return source
                val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(bgColor or -0x1000000)
                canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                return out
            }
        }
    }

    private fun copySafeExifTags(source: File, dest: File) {
        try {
            val srcExif = ExifInterface(source.absolutePath)
            val dstExif = ExifInterface(dest.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_SOFTWARE
            )
            for (tag in tags) {
                val value = srcExif.getAttribute(tag)
                if (!value.isNullOrBlank()) {
                    dstExif.setAttribute(tag, value)
                }
            }
            dstExif.saveAttributes()
        } catch (_: Exception) {}
    }

    /**
     * Writes a standard 32-bit BGRA Windows BMP (BITMAPFILEHEADER + BITMAPINFOHEADER) file.
     */
    private fun writeBitmapAsBmp(bitmap: Bitmap, outputFile: File) {
        val width = bitmap.width
        val height = bitmap.height
        val rowBytes = width * 4
        val imageSize = rowBytes * height
        val fileSize = 54 + imageSize

        FileOutputStream(outputFile).buffered().use { out ->
            val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
            // BITMAPFILEHEADER (14 bytes)
            header.put(0x42.toByte()) // 'B'
            header.put(0x4D.toByte()) // 'M'
            header.putInt(fileSize)
            header.putShort(0)
            header.putShort(0)
            header.putInt(54)

            // BITMAPINFOHEADER (40 bytes)
            header.putInt(40)
            header.putInt(width)
            header.putInt(height) // Bottom-up DIB
            header.putShort(1)    // Planes
            header.putShort(32)   // 32-bit BGRA
            header.putInt(0)      // BI_RGB
            header.putInt(imageSize)
            header.putInt(2835)   // 72 DPI X
            header.putInt(2835)   // 72 DPI Y
            header.putInt(0)
            header.putInt(0)
            out.write(header.array())

            val pixels = IntArray(width)
            val rowBuffer = ByteBuffer.allocate(rowBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (y in (height - 1) downTo 0) {
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                rowBuffer.clear()
                for (x in 0 until width) {
                    val argb = pixels[x]
                    val a = (argb ushr 24) and 0xFF
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    rowBuffer.put(b.toByte())
                    rowBuffer.put(g.toByte())
                    rowBuffer.put(r.toByte())
                    rowBuffer.put(a.toByte())
                }
                out.write(rowBuffer.array())
            }
        }
    }

    /**
     * Writes a standard Windows .ico file containing a PNG-encoded icon entry (up to 256x256).
     */
    private fun writeBitmapAsIco(bitmap: Bitmap, outputFile: File, targetSize: Int) {
        val dim = targetSize.coerceIn(16, 256)
        val iconBitmap = if (bitmap.width != dim || bitmap.height != dim) {
            Bitmap.createScaledBitmap(bitmap, dim, dim, true)
        } else {
            bitmap
        }
        try {
            val pngBytes = ByteArrayOutputStream().use { baos ->
                iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
            FileOutputStream(outputFile).use { fos ->
                val header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
                // ICONDIR (6 bytes)
                header.putShort(0) // Reserved
                header.putShort(1) // Type 1 = ICO
                header.putShort(1) // 1 image
                // ICONDIRENTRY (16 bytes)
                header.put((if (dim >= 256) 0 else dim).toByte()) // Width (0 means 256)
                header.put((if (dim >= 256) 0 else dim).toByte()) // Height (0 means 256)
                header.put(0.toByte()) // Color palette
                header.put(0.toByte()) // Reserved
                header.putShort(1)     // Color planes
                header.putShort(32)    // Bits per pixel
                header.putInt(pngBytes.size) // Payload size
                header.putInt(22)      // Offset to PNG data
                fos.write(header.array())
                fos.write(pngBytes)
            }
        } finally {
            if (iconBitmap !== bitmap && !iconBitmap.isRecycled) {
                iconBitmap.recycle()
            }
        }
    }

    /**
     * Writes the Bitmap onto a clean single-page PDF document using Android's native PdfDocument.
     */
    private fun writeBitmapAsPdf(bitmap: Bitmap, outputFile: File) {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = bitmap.width.coerceAtLeast(1)
            val pageHeight = bitmap.height.coerceAtLeast(1)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            pdfDocument.finishPage(page)
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
    }
}
