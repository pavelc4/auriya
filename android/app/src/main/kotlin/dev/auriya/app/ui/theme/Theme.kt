package dev.auriya.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import dev.auriya.app.data.ThemePrefs

@Composable
fun AuriyaTheme(
    prefs: ThemePrefs?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val seedColor = prefs?.seedColor ?: 0xFFFFB68E.toInt()
    val useDynamic = prefs?.useDynamicColor ?: true
    val darkThemeMode = prefs?.darkThemeMode ?: dev.auriya.app.data.DarkThemeMode.FOLLOW_SYSTEM
    val isAmoled = prefs?.isAmoled ?: false

    val isDark = when (darkThemeMode) {
        dev.auriya.app.data.DarkThemeMode.FOLLOW_SYSTEM -> darkTheme
        dev.auriya.app.data.DarkThemeMode.LIGHT -> false
        dev.auriya.app.data.DarkThemeMode.DARK -> true
    }

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (isDark) dynamicDarkColorScheme(context)
                             else dynamicLightColorScheme(context)
            if (isDark && isAmoled) {
                baseScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainer = Color.Black,
                    surfaceContainerLow = Color(0xFF0F0F0F),
                    surfaceContainerHigh = Color(0xFF1F1F1F),
                    surfaceContainerLowest = Color.Black,
                )
            } else {
                baseScheme
            }
        }
        else -> rememberDynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = isDark,
            isAmoled = isDark && isAmoled,
            style = PaletteStyle.TonalSpot,
        )
    }

    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalAuriyaSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuriyaTypography,
            content = content,
        )
    }
}

object AuriyaTheme {
    val semantic: AuriyaSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAuriyaSemanticColors.current
}
