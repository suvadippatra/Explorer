package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint

/**
 * Utility for applying ColorMatrix photo filters to Bitmaps.
 */
object PhotoFilterHelper {

    fun applyFilter(source: Bitmap, filterType: PhotoFilterType): Bitmap {
        if (filterType == PhotoFilterType.NONE) return source
        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(result)
        val paint = AndroidPaint()

        val matrix = ColorMatrix()
        when (filterType) {
            PhotoFilterType.SEPIA -> matrix.set(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterType.GRAYSCALE, PhotoFilterType.BLACK_WHITE -> matrix.setSaturation(0f)
            PhotoFilterType.BRIGHTNESS -> matrix.set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 40f,
                    0f, 1f, 0f, 0f, 40f,
                    0f, 0f, 1f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterType.CONTRAST -> {
                val scale = 1.35f
                val translate = (-0.5f * scale + 0.5f) * 255f
                matrix.set(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.AUTO_FIX -> {
                matrix.set(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 10f,
                        0f, 1.1f, 0f, 0f, 10f,
                        0f, 0f, 1.1f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.CROSS_PROCESS -> {
                matrix.set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 20f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 0.8f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.DOCUMENT -> {
                matrix.set(
                    floatArrayOf(
                        1.4f, 0f, 0f, 0f, -20f,
                        0f, 1.4f, 0f, 0f, -20f,
                        0f, 0f, 1.4f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.FILL_LIGHT -> {
                matrix.set(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 50f,
                        0f, 1f, 0f, 0f, 50f,
                        0f, 0f, 1f, 0f, 50f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.FISHEYE -> {
                matrix.set(
                    floatArrayOf(
                        1.1f, 0.1f, 0f, 0f, 0f,
                        0.1f, 1.1f, 0f, 0f, 0f,
                        0f, 0f, 1.0f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.GRAIN -> {
                matrix.set(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 15f,
                        0f, 0.9f, 0f, 0f, 15f,
                        0f, 0f, 0.9f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.LOMOISH -> {
                matrix.set(
                    floatArrayOf(
                        1.3f, 0f, 0f, 0f, -10f,
                        0f, 1.1f, 0f, 0f, -10f,
                        0f, 0f, 0.9f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.NEGATIVE -> {
                matrix.set(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.POSTERIZE -> {
                matrix.set(
                    floatArrayOf(
                        1.4f, 0f, 0f, 0f, -30f,
                        0f, 1.4f, 0f, 0f, -30f,
                        0f, 0f, 1.4f, 0f, -30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.ROTATE -> {
                matrix.set(
                    floatArrayOf(
                        0f, 1f, 0f, 0f, 0f,
                        1f, 0f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.SATURATE -> matrix.setSaturation(2.0f)
            PhotoFilterType.SHARPEN -> matrix.setSaturation(1.6f)
            PhotoFilterType.TEMPERATURE -> {
                matrix.set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 25f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 0.8f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.TINT -> {
                matrix.set(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 30f,
                        0f, 0.9f, 0f, 0f, 0f,
                        0f, 0f, 1.2f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            PhotoFilterType.VIGNETTE -> {
                matrix.set(
                    floatArrayOf(
                        0.8f, 0f, 0f, 0f, 0f,
                        0f, 0.8f, 0f, 0f, 0f,
                        0f, 0f, 0.8f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            else -> {}
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
