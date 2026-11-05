package com.troikoss.continuum_explorer.ui.editor.photo

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Filter presets available in the FilterEngine.
 */
enum class FilterPreset(val displayName: String, val icon: String) {
    ORIGINAL("Original", "🖼️"),
    GRAYSCALE("Grayscale", "⚪"),
    SEPIA("Sepia", "📜"),
    INVERT("Invert", "🔄"),
    VINTAGE("Vintage", "🎞️"),
    VIGNETTE("Vignette", "🔘"),
    WARM("Warm", "☀️"),
    COOL("Cool", "❄️"),
    NOIR("Noir", "🎬"),
    BRIGHT("Bright", "✨")
}

/**
 * Color adjustment parameters (Brightness, Contrast, Saturation, Hue, Vignette).
 */
data class ColorAdjustments(
    val brightness: Float = 0f,      // -100f to +100f
    val contrast: Float = 1.0f,      // 0.5f to 2.0f
    val saturation: Float = 1.0f,    // 0f to 2.0f
    val hueRotation: Float = 0f,     // -180f to +180f
    val vignetteIntensity: Float = 0f // 0f to 1.0f
) {
    fun isDefault(): Boolean {
        return brightness == 0f && contrast == 1.0f && saturation == 1.0f &&
                hueRotation == 0f && vignetteIntensity == 0f
    }
}

/**
 * Complete engine for applying presets and fine-grained color adjustments to Bitmaps.
 */
object FilterEngine {

    /**
     * Builds the ColorMatrix for a specific preset.
     */
    fun getPresetMatrix(preset: FilterPreset): ColorMatrix {
        val matrix = ColorMatrix()
        when (preset) {
            FilterPreset.ORIGINAL -> {
                // Identity matrix
                matrix.reset()
            }
            FilterPreset.GRAYSCALE -> {
                matrix.setSaturation(0f)
            }
            FilterPreset.SEPIA -> {
                matrix.set(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FilterPreset.INVERT -> {
                matrix.set(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FilterPreset.VINTAGE -> {
                matrix.set(
                    floatArrayOf(
                        0.9f, 0.1f, 0.1f, 0f, 30f,
                        0.1f, 0.8f, 0.1f, 0f, 20f,
                        0.1f, 0.1f, 0.6f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FilterPreset.VIGNETTE -> {
                // High contrast base for vignette
                val scale = 1.15f
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
            FilterPreset.WARM -> {
                matrix.set(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 25f,
                        0f, 1.05f, 0f, 0f, 10f,
                        0f, 0f, 0.85f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FilterPreset.COOL -> {
                matrix.set(
                    floatArrayOf(
                        0.85f, 0f, 0f, 0f, -15f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.25f, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            FilterPreset.NOIR -> {
                // High contrast black & white
                val bwMatrix = ColorMatrix()
                bwMatrix.setSaturation(0f)
                val contrastMatrix = ColorMatrix()
                val scale = 1.45f
                val translate = (-0.5f * scale + 0.5f) * 255f
                contrastMatrix.set(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate - 15f,
                        0f, scale, 0f, 0f, translate - 15f,
                        0f, 0f, scale, 0f, translate - 15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                matrix.setConcat(contrastMatrix, bwMatrix)
            }
            FilterPreset.BRIGHT -> {
                matrix.set(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 45f,
                        0f, 1f, 0f, 0f, 45f,
                        0f, 0f, 1f, 0f, 45f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }
        return matrix
    }

    /**
     * Builds the ColorMatrix combining Brightness, Contrast, Saturation, and Hue Rotation.
     */
    fun getAdjustmentMatrix(adjustments: ColorAdjustments): ColorMatrix {
        val result = ColorMatrix()

        // 1. Brightness
        val brightnessMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, adjustments.brightness,
                    0f, 1f, 0f, 0f, adjustments.brightness,
                    0f, 0f, 1f, 0f, adjustments.brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        // 2. Contrast
        val contrastMatrix = ColorMatrix().apply {
            val scale = adjustments.contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            set(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        // 3. Saturation
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(adjustments.saturation)
        }

        // 4. Hue Rotation (axis 0=Red, 1=Green, 2=Blue; or custom 3D rotation)
        val hueMatrix = ColorMatrix()
        if (adjustments.hueRotation != 0f) {
            val radians = Math.toRadians(adjustments.hueRotation.toDouble())
            val cosVal = cos(radians).toFloat()
            val sinVal = sin(radians).toFloat()
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f

            hueMatrix.set(
                floatArrayOf(
                    lumR + cosVal * (1 - lumR) + sinVal * (-lumR),
                    lumG + cosVal * (-lumG) + sinVal * (-lumG),
                    lumB + cosVal * (-lumB) + sinVal * (1 - lumB),
                    0f, 0f,

                    lumR + cosVal * (-lumR) + sinVal * (0.143f),
                    lumG + cosVal * (1 - lumG) + sinVal * (0.140f),
                    lumB + cosVal * (-lumB) + sinVal * (-0.283f),
                    0f, 0f,

                    lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)),
                    lumG + cosVal * (-lumG) + sinVal * (lumG),
                    lumB + cosVal * (1 - lumB) + sinVal * (lumB),
                    0f, 0f,

                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        // Concatenate all adjustment matrices
        result.setConcat(contrastMatrix, brightnessMatrix)
        result.setConcat(saturationMatrix, result)
        result.setConcat(hueMatrix, result)

        return result
    }

    /**
     * Applies preset and fine adjustments to a Bitmap, returning a new filtered Bitmap.
     */
    fun applyFilter(
        source: Bitmap,
        preset: FilterPreset = FilterPreset.ORIGINAL,
        adjustments: ColorAdjustments = ColorAdjustments()
    ): Bitmap {
        if (preset == FilterPreset.ORIGINAL && adjustments.isDefault()) {
            return source
        }

        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(result)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

        // Combine preset and adjustments
        val finalMatrix = ColorMatrix()
        val presetMatrix = getPresetMatrix(preset)
        val adjMatrix = getAdjustmentMatrix(adjustments)

        finalMatrix.setConcat(adjMatrix, presetMatrix)
        paint.colorFilter = ColorMatrixColorFilter(finalMatrix)

        canvas.drawBitmap(source, 0f, 0f, paint)

        // Apply Vignette overlay if requested (either through preset or custom slider)
        val effectiveVignette = when {
            adjustments.vignetteIntensity > 0f -> adjustments.vignetteIntensity
            preset == FilterPreset.VIGNETTE -> 0.75f
            else -> 0f
        }

        if (effectiveVignette > 0f) {
            val cx = source.width / 2f
            val cy = source.height / 2f
            val radius = kotlin.math.max(cx, cy) * 1.2f

            val alpha = (effectiveVignette * 220).toInt().coerceIn(0, 255)
            val colors = intArrayOf(
                AndroidColor.TRANSPARENT,
                AndroidColor.argb(alpha / 3, 0, 0, 0),
                AndroidColor.argb(alpha, 0, 0, 0)
            )
            val stops = floatArrayOf(0f, 0.6f, 1.0f)

            val vignetteShader = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)
            val vignettePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                shader = vignetteShader
            }
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), vignettePaint)
        }

        return result
    }
}
