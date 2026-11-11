package com.troikoss.continuum_explorer.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.troikoss.continuum_explorer.R
import com.troikoss.continuum_explorer.managers.AppFontFamily

/**
 * Fallback FontFamily definitions using OptionalLocal/Async loading strategies
 * so font resolution never blocks touch events on the main UI thread.
 */
private val FallbackPlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.OptionalLocal)
)

private val FallbackJetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.jetbrains_mono, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.jetbrains_mono, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.OptionalLocal)
)

@Volatile
private var cachedPlusJakartaSansFamily: FontFamily? = null

@Volatile
private var cachedJetBrainsMonoFamily: FontFamily? = null

@Volatile
private var cachedPlusJakartaTypography: Typography? = null

@Volatile
private var cachedJetBrainsMonoTypography: Typography? = null

val PlusJakartaSansFontFamily: FontFamily
    get() = cachedPlusJakartaSansFamily ?: FallbackPlusJakartaSansFontFamily

val JetBrainsMonoFontFamily: FontFamily
    get() = cachedJetBrainsMonoFamily ?: FallbackJetBrainsMonoFontFamily

/**
 * Preloads and caches the bundled variable TTF fonts off the main thread at application startup
 * so switching fonts in Settings is instantaneous with zero main-thread disk I/O or touch lag.
 */
fun preloadAppFonts(context: Context) {
    val appContext = context.applicationContext
    try {
        if (cachedPlusJakartaSansFamily == null) {
            ResourcesCompat.getFont(appContext, R.font.plus_jakarta_sans)?.let { tf ->
                val family = FontFamily(tf)
                cachedPlusJakartaSansFamily = family
                cachedPlusJakartaTypography = createTypographyForFamily(family)
            }
        }
    } catch (_: Throwable) {}

    try {
        if (cachedJetBrainsMonoFamily == null) {
            ResourcesCompat.getFont(appContext, R.font.jetbrains_mono)?.let { tf ->
                val family = FontFamily(tf)
                cachedJetBrainsMonoFamily = family
                cachedJetBrainsMonoTypography = createTypographyForFamily(family)
            }
        }
    } catch (_: Throwable) {}
}

private fun createTypographyForFamily(activeFontFamily: FontFamily): Typography {
    return Typography(
        titleLarge = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.2.sp
        ),
        bodySmall = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp
        ),
        labelLarge = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp
        ),
        labelSmall = TextStyle(
            fontFamily = activeFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.3.sp
        )
    )
}

private val SystemDefaultTypography: Typography = createTypographyForFamily(FontFamily.Default)
private val DefaultPlusJakartaTypography: Typography by lazy { createTypographyForFamily(PlusJakartaSansFontFamily) }
private val DefaultJetBrainsMonoTypography: Typography by lazy { createTypographyForFamily(JetBrainsMonoFontFamily) }

fun buildAppTypography(selectedFont: AppFontFamily): Typography {
    return when (selectedFont) {
        AppFontFamily.SYSTEM_DEFAULT -> SystemDefaultTypography
        AppFontFamily.PLUS_JAKARTA_SANS -> cachedPlusJakartaTypography ?: DefaultPlusJakartaTypography
        AppFontFamily.JETBRAINS_MONO -> cachedJetBrainsMonoTypography ?: DefaultJetBrainsMonoTypography
    }
}

val Typography: Typography = SystemDefaultTypography