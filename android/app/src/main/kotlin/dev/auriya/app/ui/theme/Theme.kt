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

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> rememberDynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = darkTheme,
            isAmoled = false,
            style = PaletteStyle.TonalSpot,
        )
    }

    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

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
