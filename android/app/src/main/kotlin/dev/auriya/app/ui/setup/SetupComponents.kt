package dev.auriya.app.ui.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.setup.components.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated sine wave line canvas element used for setup transitions.
 */
@Composable
fun SineWaveLine(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 1f,
    strokeWidth: Dp = 2.dp,
    amplitude: Dp = 8.dp,
    waves: Float = 2f,
    phase: Float = 0f,
    animate: Boolean? = false,
    animationDurationMillis: Int = 2000,
    samples: Int = 400,
    cap: StrokeCap = StrokeCap.Round,
) {
    val density = LocalDensity.current

    val currentPhase =
        if (animate == true) {
            val infiniteTransition = rememberInfiniteTransition(label = "SineWaveAnimation")
            val animatedPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2f * PI.toFloat(),
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = animationDurationMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "phaseAnimation",
            )
            animatedPhase
        } else {
            phase
        }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val strokePx = with(density) { strokeWidth.toPx() }
        val ampPx = with(density) { amplitude.toPx() }

        if (w <= 0f || samples < 2) return@Canvas

        val path =
            Path().apply {
                val step = w / (samples - 1)
                moveTo(0f, centerY + (ampPx * sin(currentPhase)))
                for (i in 1 until samples) {
                    val x = i * step
                    val theta = (x / w) * (2f * PI.toFloat() * waves) + currentPhase
                    val y = centerY + ampPx * sin(theta)
                    lineTo(x, y)
                }
            }

        drawPath(
            path = path,
            color = color,
            style =
                Stroke(
                    width = strokePx,
                    cap = cap,
                    join = StrokeJoin.Round,
                ),
            alpha = alpha,
        )
    }
}

/**
 * Helper to determine if we should render light or dark text based on theme preferences.
 */
@Composable
fun isThemeDark(prefs: ThemePrefs?): Boolean {
    if (prefs == null) return true
    return when (prefs.darkThemeMode) {
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
}

val SineBow =
    Easing { fraction ->
        val t = fraction * 2f * Math.PI.toFloat()
        (1f - cos(t)) / 2f
    }
