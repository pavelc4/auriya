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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.AppPrefs
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.theme.GoogleSansRounded

/**
 * Dedicated FPS Timeline Chart Card with interactive scrubbing and 1% Low reference curve.
 * Conforms to Material 3 Data Visualization guidelines.
 */
@Composable
fun SessionFpsChartCard(
    session: BenchmarkSession,
    roundFps: Boolean,
    modifier: Modifier = Modifier,
) {
    val samples = session.samples
    if (samples.size < 2) return

    val maxFpsScale =
        remember(session.maxFps) {
            val rawMax = session.maxFps.toFloat()
            when {
                rawMax > 120f -> 144f
                rawMax > 90f -> 120f
                rawMax > 60f -> 90f
                else -> 60f
            }
        }

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val scrubSample = scrubIndex?.let { samples.getOrNull(it) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Header with Title & Stat Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Frame Rate Timeline",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Real-time FPS and 1% Low consistency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.height(30.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Avg ${AppPrefs.formatFps(session.avgFps, roundFps)} FPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // 2. Interactive Tooltip Pill (Shows when user scrubs across timeline)
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
                                text = "FPS: ${AppPrefs.formatFps(scrubSample.fps, roundFps)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "1% Low: ${AppPrefs.formatFps(scrubSample.low1Pct, roundFps)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 3. High-Fidelity Canvas
            val fpsColor = MaterialTheme.colorScheme.primary
            val low1PctColor = MaterialTheme.colorScheme.tertiary
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
            val guidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f))
                        .pointerInput(samples.size) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    val idx = (offset.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                    scrubIndex = idx
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    val idx = (change.position.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                    scrubIndex = idx
                                },
                                onDragEnd = {
                                    scrubIndex = null
                                },
                                onDragCancel = {
                                    scrubIndex = null
                                },
                            )
                        }.pointerInput(samples.size) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                                    val idx = (offset.x / stepX).toInt().coerceIn(0, samples.size - 1)
                                    scrubIndex = if (scrubIndex == idx) null else idx
                                },
                                onDoubleTap = {
                                    scrubIndex = null
                                },
                            )
                        },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    // Guidelines for 30, 60, 90, 120 FPS
                    val guideValues = listOf(30f, 60f, 90f, 120f, 144f).filter { it <= maxFpsScale }
                    for (g in guideValues) {
                        val y = h - (g / maxFpsScale) * h
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // 1% Low Curve (Dashed line)
                    val lowPath =
                        Path().apply {
                            samples.forEachIndexed { i, s ->
                                val x = i * stepX
                                val y = h - (s.low1Pct.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                    drawPath(
                        path = lowPath,
                        color = low1PctColor.copy(alpha = 0.70f),
                        style =
                            Stroke(
                                width = 1.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                            ),
                    )

                    // Area Gradient Fill under FPS Curve
                    val fillPath =
                        Path().apply {
                            moveTo(0f, h)
                            samples.forEachIndexed { i, s ->
                                val x = i * stepX
                                val y = h - (s.fps.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h
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
                                        fpsColor.copy(alpha = 0.28f),
                                        fpsColor.copy(alpha = 0.02f),
                                    ),
                                startY = 0f,
                                endY = h,
                            ),
                    )

                    // Bold FPS Stroke Line
                    val fpsPath =
                        Path().apply {
                            samples.forEachIndexed { i, s ->
                                val x = i * stepX
                                val y = h - (s.fps.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                    drawPath(
                        path = fpsPath,
                        color = fpsColor,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Scrub Line & Dots
                    scrubIndex?.let { idx ->
                        val scrubX = idx * stepX
                        val currentSample = samples[idx]
                        val scrubFpsY = h - (currentSample.fps.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h
                        val scrubLowY = h - (currentSample.low1Pct.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h

                        // Vertical guide line
                        drawLine(
                            color = guidelineColor,
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )

                        // 1% Low dot
                        drawCircle(
                            color = low1PctColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(scrubX, scrubLowY),
                        )

                        // Main FPS dot with white center
                        drawCircle(
                            color = fpsColor,
                            radius = 6.dp.toPx(),
                            center = Offset(scrubX, scrubFpsY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(scrubX, scrubFpsY),
                        )
                    }
                }
            }

            // 4. Time Range & Legend Row
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Solid FPS legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 12.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(fpsColor),
                        )
                        Text(
                            text = "FPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }

                    // Dashed 1% Low legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 12.dp, height = 2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(low1PctColor),
                        )
                        Text(
                            text = "1% Low",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }

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
