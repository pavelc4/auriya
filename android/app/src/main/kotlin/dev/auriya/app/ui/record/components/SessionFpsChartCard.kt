package dev.auriya.app.ui.record.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import dev.auriya.app.data.AppPrefs
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlin.math.roundToInt

/**
 * Dedicated FPS Timeline Chart Card with interactive smooth scrubbing and statistical reference metrics.
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

    // Statistical stability calculation: frames within +-5 FPS of target/avg
    val stabilityPct =
        remember(samples, session.avgFps) {
            val target = session.avgFps
            if (target > 0 && samples.isNotEmpty()) {
                (samples.count { kotlin.math.abs(it.fps - target) <= 5.0 }.toFloat() / samples.size * 100f).coerceIn(0f, 100f)
            } else {
                100f
            }
        }

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var scrubPixelX by remember { mutableStateOf<Float?>(null) }
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
            // 1. Header Title & Target Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.record_chart_fps_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "Avg ${AppPrefs.formatFps(session.avgFps, roundFps)} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // 2. High-Fidelity Canvas with Fixed Floating Tooltip Overlay (Prevents Height Shifting)
            val fpsColor = MaterialTheme.colorScheme.primary
            val low1PctColor = MaterialTheme.colorScheme.tertiary
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
            val guidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f))
                        .pointerInput(samples) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat()
                                    val stepX = w / (samples.size - 1).coerceAtLeast(1)
                                    val startX = offset.x.coerceIn(0f, w)
                                    scrubPixelX = startX
                                    scrubIndex = (startX / stepX).roundToInt().coerceIn(0, samples.size - 1)
                                },
                                onDragEnd = {
                                    scrubIndex = null
                                    scrubPixelX = null
                                },
                                onDragCancel = {
                                    scrubIndex = null
                                    scrubPixelX = null
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat()
                                    val stepX = w / (samples.size - 1).coerceAtLeast(1)
                                    val currentX = change.position.x.coerceIn(0f, w)
                                    scrubPixelX = currentX
                                    val idx = (currentX / stepX).roundToInt().coerceIn(0, samples.size - 1)
                                    if (scrubIndex != idx) {
                                        scrubIndex = idx
                                    }
                                },
                            )
                        },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (samples.size - 1).coerceAtLeast(1)

                    // Reference guidelines for 30, 60, 90, 120 FPS
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

                    // Scrub Line & Dots at exact pixel coordinate
                    scrubIndex?.let { idx ->
                        val targetX = scrubPixelX ?: (idx * stepX)
                        val currentSample = samples[idx]
                        val scrubFpsY = h - (currentSample.fps.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h
                        val scrubLowY = h - (currentSample.low1Pct.toFloat() / maxFpsScale).coerceIn(0f, 1f) * h

                        // Vertical guide line
                        drawLine(
                            color = guidelineColor,
                            start = Offset(targetX, 0f),
                            end = Offset(targetX, h),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )

                        // 1% Low dot
                        drawCircle(
                            color = low1PctColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(targetX, scrubLowY),
                        )

                        // Main FPS dot with white center
                        drawCircle(
                            color = fpsColor,
                            radius = 6.dp.toPx(),
                            center = Offset(targetX, scrubFpsY),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(targetX, scrubFpsY),
                        )
                    }
                }

                // Smooth Floating Tooltip (Inside Box so layout never jumps)
                if (scrubSample != null) {
                    val timeSec = scrubSample.timestampOffsetMs / 1000
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                        shadowElevation = 4.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            // 3. Time Range & Legend Row
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
                            text = stringResource(R.string.record_legend_fps),
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
                            text = stringResource(R.string.record_legend_1pct_low),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // 4. Statistical Data Summary Row (Always visible underneath the chart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FpsStatPill(
                    label = "Avg",
                    value = "${AppPrefs.formatFps(session.avgFps, roundFps)} FPS",
                    color = MaterialTheme.colorScheme.primary,
                )
                FpsStatPill(
                    label = "1% Low",
                    value = "${AppPrefs.formatFps(session.minLow1Pct, roundFps)} FPS",
                    color = low1PctColor,
                )
                FpsStatPill(
                    label = "Peak",
                    value = "${AppPrefs.formatFps(session.maxFps, roundFps)} FPS",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FpsStatPill(
                    label = "Stability",
                    value = "%.1f%%".format(stabilityPct),
                    color = if (stabilityPct >= 90f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FpsStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}
