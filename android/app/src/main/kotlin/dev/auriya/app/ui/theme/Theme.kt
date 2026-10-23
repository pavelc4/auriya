package dev.auriya.app.ui.theme

import android.os.Build
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.ThemePrefs

@Composable
fun AuriyaTheme(
    prefs: ThemePrefs? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentPrefs = prefs ?: ThemePrefs()
    val isDark =
        when (currentPrefs.darkThemeMode) {
            DarkThemeMode.FOLLOW_SYSTEM -> darkTheme
            DarkThemeMode.LIGHT -> false
            DarkThemeMode.DARK -> true
        }

    // Fully dynamic color on Android 12+ (SDK >= 31)
    val baseScheme: ColorScheme =
        remember(isDark) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) defaultDarkColorScheme else defaultLightColorScheme
            }
        }

    val colorScheme =
        remember(baseScheme, isDark, currentPrefs.isAmoled) {
            if (isDark) {
                val lifted =
                    baseScheme.copy(
                        surfaceContainerLowest = baseScheme.surfaceContainer,
                        surfaceContainerLow = baseScheme.surfaceContainerHigh,
                        surfaceContainer = baseScheme.surfaceContainerHighest,
                        surfaceContainerHigh = baseScheme.surfaceBright,
                    )
                if (currentPrefs.isAmoled) {
                    lifted.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = Color(0xFF0E100F),
                        surfaceContainer = Color(0xFF161817),
                        surfaceContainerHigh = Color(0xFF202221),
                        surfaceContainerHighest = Color(0xFF2B2D2C),
                    )
                } else {
                    lifted
                }
            } else {
                baseScheme.copy(
                    surfaceContainer = baseScheme.surfaceContainerLowest,
                )
            }
        }

    val semanticColors = if (isDark) DarkSemanticColors else LightSemanticColors

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val activity = view.context as? androidx.activity.ComponentActivity
            activity?.enableEdgeToEdge(
                statusBarStyle =
                    androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
                navigationBarStyle =
                    androidx.activity.SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDark },
            )
        }
    }

    CompositionLocalProvider(
        LocalAuriyaSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AuriyaTypography,
            shapes = Shapes,
            content = content,
        )
    }
}

private val defaultDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF81C784),
        onPrimary = Color(0xFF00391C),
        primaryContainer = Color(0xFF2E7D32).copy(alpha = 0.55f),
        onPrimaryContainer = Color(0xFFC8E6C9),
        secondary = Color(0xFF81C784),
        onSecondary = Color(0xFF00391C),
        secondaryContainer = Color(0xFF2E7D32).copy(alpha = 0.4f),
        onSecondaryContainer = Color(0xFFC8E6C9),
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

private val defaultLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF00210E),
        secondary = Color(0xFF2E7D32),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC8E6C9).copy(alpha = 0.7f),
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

object AuriyaTheme {
    val semantic: AuriyaSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAuriyaSemanticColors.current
}
