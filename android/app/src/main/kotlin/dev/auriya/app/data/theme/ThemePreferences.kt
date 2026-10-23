package dev.auriya.app.data.theme

import androidx.compose.ui.graphics.Color
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.ThemePrefs

data class PaletteItem(
    val id: String = "",
    val name: String = "",
    val seed: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
)

val PALETTE_PRESETS =
    listOf(
        PaletteItem(
            id = "emerald",
            name = "Emerald",
            seed = 0xFF2E7D32.toInt(),
            primary = Color(0xFF2E7D32),
            secondary = Color(0xFF81C784),
            tertiary = Color(0xFFC8E6C9),
            neutral = Color(0xFFE8F5E9),
        ),
        PaletteItem(
            id = "blue",
            name = "Ocean",
            seed = 0xFF1976D2.toInt(),
            primary = Color(0xFF1976D2),
            secondary = Color(0xFF64B5F6),
            tertiary = Color(0xFFBBDEFB),
            neutral = Color(0xFFE3F2FD),
        ),
        PaletteItem(
            id = "purple",
            name = "Twilight",
            seed = 0xFF7B1FA2.toInt(),
            primary = Color(0xFF7B1FA2),
            secondary = Color(0xFFBA68C8),
            tertiary = Color(0xFFE1BEE7),
            neutral = Color(0xFFF3E5F5),
        ),
        PaletteItem(
            id = "orange",
            name = "Sunset",
            seed = 0xFFE64A19.toInt(),
            primary = Color(0xFFE64A19),
            secondary = Color(0xFFFF8A65),
            tertiary = Color(0xFFFFCCBC),
            neutral = Color(0xFFFBE9E7),
        ),
        PaletteItem(
            id = "amber",
            name = "Amber",
            seed = 0xFFFBC02D.toInt(),
            primary = Color(0xFFFBC02D),
            secondary = Color(0xFFFFF176),
            tertiary = Color(0xFFFFF9C4),
            neutral = Color(0xFFFFFDE7),
        ),
        PaletteItem(
            id = "rose",
            name = "Sakura",
            seed = 0xFFC2185B.toInt(),
            primary = Color(0xFFC2185B),
            secondary = Color(0xFFF06292),
            tertiary = Color(0xFFF8BBD0),
            neutral = Color(0xFFFCE4EC),
        ),
        PaletteItem(
            id = "slate",
            name = "Slate",
            seed = 0xFF455A64.toInt(),
            primary = Color(0xFF455A64),
            secondary = Color(0xFF90A4AE),
            tertiary = Color(0xFFCFD8DC),
            neutral = Color(0xFFECEFF1),
        ),
    )

val PALETTE_ITEMS = PALETTE_PRESETS
