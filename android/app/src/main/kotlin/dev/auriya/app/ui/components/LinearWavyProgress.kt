package dev.auriya.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LinearWavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    waveLength: Float = 72f,
    amplitude: Float = 6f,
    strokeWidth: Float = 10f,
) {
    val animated =
        animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            label = "wavy",
        )
    val wavePath = remember { Path() }

    Spacer(
        modifier =
            modifier
                .height(12.dp)
                .drawWithCache {
                    val centerY = size.height / 2f
                    val totalWidth = size.width
                    val trackStart = Offset(0f, centerY)
                    val trackEnd = Offset(totalWidth, centerY)
                    val indicatorStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    val twoPiOverWave = (2.0 * PI) / waveLength

                    onDrawBehind {
                        drawLine(
                            color = trackColor,
                            start = trackStart,
                            end = trackEnd,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )

                        val activeWidth = totalWidth * animated.value
                        if (activeWidth <= 0f) return@onDrawBehind

                        wavePath.rewind()
                        wavePath.moveTo(0f, centerY)
                        var x = 0f
                        while (x < activeWidth) {
                            val y = centerY + sin(x * twoPiOverWave).toFloat() * amplitude
                            wavePath.lineTo(x, y)
                            x += 2f
                        }
                        drawPath(
                            path = wavePath,
                            color = indicatorColor,
                            style = indicatorStroke,
                        )
                    }
                },
    )
}
