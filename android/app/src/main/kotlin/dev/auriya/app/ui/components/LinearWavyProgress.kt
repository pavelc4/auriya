package dev.auriya.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp


@Composable
fun LinearWavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    waveLength: Float = 16f,
    amplitude: Float = 2.5f,
    strokeWidth: Float = 4f,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "wavy",
    )
    Canvas(modifier = modifier.height(12.dp)) {
        val centerY = size.height / 2f
        val totalWidth = size.width
        val activeWidth = totalWidth * animated

        // Track (flat line)
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(totalWidth, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        if (activeWidth <= 0f) return@Canvas

        // Wavy indicator
        val path = Path().apply {
            moveTo(0f, centerY)
            var x = 0f
            val step = 1f
            while (x < activeWidth) {
                val phase = (x / waveLength) * 2f * Math.PI.toFloat()
                val y = centerY + kotlin.math.sin(phase.toDouble()).toFloat() * amplitude
                lineTo(x, y)
                x += step
            }
        }
        drawPath(
            path = path,
            color = indicatorColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
