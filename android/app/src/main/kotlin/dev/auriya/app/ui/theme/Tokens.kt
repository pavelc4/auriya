package dev.auriya.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Centralised spacing, corner-radius, and motion tokens. Components
 * pull from here instead of hard-coding magic numbers so the eventual
 * Material 3 Expressive theme switch only has to touch one file.
 */
object AuriyaTokens {
    val padding = Padding()
    val rounding = Rounding()

    class Padding {
        val smallest = 4.dp
        val smaller = 8.dp
        val small = 12.dp
        val normal = 16.dp
        val larger = 24.dp
        val largest = 32.dp
    }

    class Rounding {
        val none = 0.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val extraLarge = 28.dp
    }
}
