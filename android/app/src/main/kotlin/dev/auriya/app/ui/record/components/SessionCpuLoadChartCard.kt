package dev.auriya.app.ui.record.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.R
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.theme.GoogleSansRounded

/**
 * Dedicated CPU Load Timeline Chart Card with area gradient and scrubbing.
 */
@Composable
fun SessionCpuLoadChartCard(
    session: BenchmarkSession,
    modifier: Modifier = Modifier,
) {
    val samples = session.samples
    if (samples.size < 2) return

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val scrubSample = scrubIndex?.let { samples.getOrNull(it) }

    val loadColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    val guidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)

    val peakLoad =
        remember(samples) {
            samples.maxOfOrNull { it.cpuLoad } ?: session.avgCpuLoad
        }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Header Title
            Text(
                text = stringResource(R.string.record_chart_cpu_title),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 2. Interactive Tooltip Pill
            AnimatedVisibility(
                visible = scrubSample != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (scrubSample != null) {
                    val timeSec = scrubSample.timestampOffsetMs / 1000
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatDuration(timeSec),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "CPU Load: %.1f%%".format(scrubSample.cpuLoad),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = loadColor,
                            )
                        }
                    }
                }
            }

            // 3. High-Fidelity Canvas
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f))
                        .pointerInput(samples.size) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    scrubIndex = (offset.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    scrubIndex = (change.position.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                },
                                onDragEnd = { scrubIndex = null },
                                onDragCancel = { scrubIndex = null },
                            )
                        }.pointerInput(samples.size) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    val idx = (offset.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                    scrubIndex = if (scrubIndex == idx) null else idx
                                },
                                onDoubleTap = { scrubIndex = null },
                            )
                        },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    // Reference grid lines for 25%, 50%, 75%
                    val guideLoads = listOf(0.25f, 0.50f, 0.75f)
                    for (gl in guideLoads) {
                        val y = h - gl * h
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Area Gradient Fill under Load Curve
                    val fillPath =
                        Path().apply {
                            moveTo(0f, h)
                            samples.forEachIndexed { i, s ->
                                val x = i * stepX
                                val y = h - (s.cpuLoad / 100f).coerceIn(0f, 1f) * h
                                lineTo(x, y)
                            }
                            lineTo(w, h)
                            close()
                        }
                    drawPath(
                        path = fillPath,
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        loadColor.copy(alpha = 0.22f),
                                        loadColor.copy(alpha = 0.02f),
                                    ),
                                startY = 0f,
                                endY = h,
                            ),
                    )

                    // CPU Load Stroke Line
                    val loadPath =
                        Path().apply {
                            samples.forEachIndexed { i, s ->
                                val x = i * stepX
                                val y = h - (s.cpuLoad / 100f).coerceIn(0f, 1f) * h
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                    drawPath(
                        path = loadPath,
                        color = loadColor,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Scrub Line & Dots
                    scrubIndex?.let { idx ->
                        val scrubX = idx * stepX
                        val currentSample = samples[idx]
                        val sy = h - (currentSample.cpuLoad / 100f).coerceIn(0f, 1f) * h

                        drawLine(
                            color = guidelineColor,
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )

                        drawCircle(color = loadColor, radius = 5.dp.toPx(), center = Offset(scrubX, sy))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(scrubX, sy))
                    }
                }
            }

            // 4. Time Range & Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "00:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )

                Text(
                    text = stringResource(R.string.record_stat_avg_peak_cpu, session.avgCpuLoad, peakLoad),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )

                Text(
                    text = formatDuration(session.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
