package dev.auriya.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Top-level theme wrapper. On Android 12+ we use the platform's
 * dynamic color (wallpaper-derived) so Auriya blends with the rest of
 * the system UI; on older platforms we fall back to a fixed brand
 * palette pending the final UI design pass.
 */
@Composable
fun AuriyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = AuriyaPrimaryDark,
            onPrimary = AuriyaOnPrimaryDark,
            primaryContainer = AuriyaPrimaryContainerDark,
            onPrimaryContainer = AuriyaOnPrimaryContainerDark,
            secondary = AuriyaSecondaryDark,
            onSecondary = AuriyaOnSecondaryDark,
            secondaryContainer = AuriyaSecondaryContainerDark,
            onSecondaryContainer = AuriyaOnSecondaryContainerDark,
            tertiary = AuriyaTertiaryDark,
            onTertiary = AuriyaOnTertiaryDark,
            tertiaryContainer = AuriyaTertiaryContainerDark,
            onTertiaryContainer = AuriyaOnTertiaryContainerDark,
            background = AuriyaBackgroundDark,
            onBackground = AuriyaOnBackgroundDark,
            surface = AuriyaSurfaceDark,
            onSurface = AuriyaOnSurfaceDark,
        )
        else -> lightColorScheme(
            primary = AuriyaPrimaryLight,
            onPrimary = AuriyaOnPrimaryLight,
            primaryContainer = AuriyaPrimaryContainerLight,
            onPrimaryContainer = AuriyaOnPrimaryContainerLight,
            secondary = AuriyaSecondaryLight,
            onSecondary = AuriyaOnSecondaryLight,
            secondaryContainer = AuriyaSecondaryContainerLight,
            onSecondaryContainer = AuriyaOnSecondaryContainerLight,
            tertiary = AuriyaTertiaryLight,
            onTertiary = AuriyaOnTertiaryLight,
            tertiaryContainer = AuriyaTertiaryContainerLight,
            onTertiaryContainer = AuriyaOnTertiaryContainerLight,
            background = AuriyaBackgroundLight,
            onBackground = AuriyaOnBackgroundLight,
            surface = AuriyaSurfaceLight,
            onSurface = AuriyaOnSurfaceLight,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuriyaTypography,
        content = content,
    )
}
