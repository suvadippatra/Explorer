package com.troikoss.continuum_explorer.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.managers.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val MaterialUiDarkColorScheme = darkColorScheme(
    primary = MuiPrimaryDark,
    onPrimary = MuiOnPrimaryDark,
    primaryContainer = MuiPrimaryContainerDark,
    onPrimaryContainer = MuiOnPrimaryContainerDark,
    secondary = MuiSecondaryDark,
    secondaryContainer = MuiSecondaryContainerDark,
    tertiary = MuiTertiaryDark,
    background = MuiBackgroundDark,
    surface = MuiSurfaceDark,
    surfaceContainerLow = MuiSurfaceContainerLowDark,
    surfaceContainer = MuiSurfaceContainerDark,
    surfaceContainerHigh = MuiSurfaceContainerHighDark,
    outline = MuiOutlineDark,
    outlineVariant = MuiOutlineVariantDark
)

private val MaterialUiLightColorScheme = lightColorScheme(
    primary = MuiPrimaryLight,
    onPrimary = MuiOnPrimaryLight,
    primaryContainer = MuiPrimaryContainerLight,
    onPrimaryContainer = MuiOnPrimaryContainerLight,
    secondary = MuiSecondaryLight,
    secondaryContainer = MuiSecondaryContainerLight,
    tertiary = MuiTertiaryLight,
    background = MuiBackgroundLight,
    surface = MuiSurfaceLight,
    surfaceContainerLow = MuiSurfaceContainerLowLight,
    surfaceContainer = MuiSurfaceContainerLight,
    surfaceContainerHigh = MuiSurfaceContainerHighLight,
    outline = MuiOutlineLight,
    outlineVariant = MuiOutlineVariantLight
)

@Composable
fun FileExplorerTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeMode = SettingsManager.themeMode.value
    val selectedFont = SettingsManager.appFontFamily.value
    val isSystemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.MATERIAL_UI -> isSystemDark
    }

    val colorScheme = when {
        themeMode == ThemeMode.MATERIAL_UI -> {
            if (darkTheme) MaterialUiDarkColorScheme else MaterialUiLightColorScheme
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val activeTypography = remember(selectedFont) {
        buildAppTypography(selectedFont)
    }

    val activeShapes = remember(themeMode) {
        if (themeMode == ThemeMode.MATERIAL_UI) {
            Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(20.dp)
            )
        } else {
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(24.dp)
            )
        }
    }

    val context = LocalContext.current
    DisposableEffect(darkTheme) {
        if (context is ComponentActivity) {
            context.enableEdgeToEdge(
                statusBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
                navigationBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
            )
        }
        onDispose {}
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = activeTypography,
        shapes = activeShapes,
        content = content
    )
}
