package dev.auriya.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.dp

object AuriyaTokens {
    val padding = Padding()
    val rounding = Rounding()
    val motion = Motion()
    val iconSize = IconSize()

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
        val extraSmall = 4.dp
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val largeIncreased = 20.dp
        val xl = 24.dp
        val extraLarge = 28.dp
        val extraLargeIncreased = 32.dp
        val full = 999.dp
    }

    class IconSize {
        val small = 16.dp
        val medium = 20.dp
        val normal = 24.dp
        val large = 32.dp
        val xl = 48.dp
    }

    class Motion {
        val emphasized = SpringSpec<Float>(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        )
        val standard = SpringSpec<Float>(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium
        )
    }
}
