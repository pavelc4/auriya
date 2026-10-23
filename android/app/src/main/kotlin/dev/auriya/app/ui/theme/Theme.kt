package dev.auriya.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.data.theme.PALETTE_PRESETS

@Composable
fun AuriyaTheme(
    prefs: ThemePrefs?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val seedColor = prefs?.seedColor ?: PALETTE_PRESETS.first().seed
    val useDynamic = prefs?.useDynamicColor ?: true
    val darkThemeMode = prefs?.darkThemeMode ?: DarkThemeMode.FOLLOW_SYSTEM
    val isAmoled = prefs?.isAmoled ?: false

    val isDark =
        when (darkThemeMode) {
            DarkThemeMode.FOLLOW_SYSTEM -> darkTheme
            DarkThemeMode.LIGHT -> false
            DarkThemeMode.DARK -> true
        }

    val baseScheme: ColorScheme =
        if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            getCustomColorScheme(seedColor, isDark)
        }

    val colorScheme =
        if (isDark) {
            val lifted =
                baseScheme.copy(
                    surfaceContainerLowest = baseScheme.surfaceContainer,
                    surfaceContainerLow = baseScheme.surfaceContainerHigh,
                    surfaceContainer = baseScheme.surfaceContainerHighest,
                    surfaceContainerHigh = baseScheme.surfaceBright,
                )
            if (isAmoled) {
                lifted.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0E100F),
                    surfaceContainer = Color(0xFF161817),
                )
            } else {
                lifted
            }
        } else {
            baseScheme.copy(
                surfaceContainer = baseScheme.surfaceContainerLowest,
            )
        }

    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalAuriyaSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuriyaTypography,
            shapes = Shapes,
            content = content,
        )
    }
}

private fun getCustomColorScheme(
    seedColor: Int,
    isDark: Boolean,
): ColorScheme {
    val palette = PALETTE_PRESETS.firstOrNull { it.seed == seedColor } ?: PALETTE_PRESETS.first()
    return if (isDark) {
        darkColorScheme(
            primary = palette.secondary,
            onPrimary = Color(0xFF00391C),
            primaryContainer = palette.primary.copy(alpha = 0.55f),
            onPrimaryContainer = palette.tertiary,
            secondary = palette.secondary,
            onSecondary = Color(0xFF00391C),
            secondaryContainer = palette.primary.copy(alpha = 0.4f),
            onSecondaryContainer = palette.tertiary,
            background = Color(0xFF111413),
            onBackground = Color(0xFFE1E3DF),
            surface = Color(0xFF111413),
            onSurface = Color(0xFFE1E3DF),
            surfaceVariant = Color(0xFF414943),
            onSurfaceVariant = Color(0xFFC1C9C1),
            surfaceContainerLowest = Color(0xFF0C0F0E),
            surfaceContainerLow = Color(0xFF191C1B),
            surfaceContainer = Color(0xFF1D201F),
            surfaceContainerHigh = Color(0xFF282B29),
            surfaceContainerHighest = Color(0xFF333634),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.tertiary,
            onPrimaryContainer = Color(0xFF00210E),
            secondary = palette.primary,
            onSecondary = Color.White,
            secondaryContainer = palette.tertiary.copy(alpha = 0.7f),
            onSecondaryContainer = Color(0xFF00210E),
            background = Color(0xFFF6FAF5),
            onBackground = Color(0xFF191C1A),
            surface = Color(0xFFF6FAF5),
            onSurface = Color(0xFF191C1A),
            surfaceVariant = Color(0xFFDCE5DD),
            onSurfaceVariant = Color(0xFF414943),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF0F5F0),
            surfaceContainer = Color(0xFFEBF0EB),
            surfaceContainerHigh = Color(0xFFE5EAE5),
            surfaceContainerHighest = Color(0xFFDFE4DF),
        )
    }
}

object AuriyaTheme {
    val semantic: AuriyaSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAuriyaSemanticColors.current
}
