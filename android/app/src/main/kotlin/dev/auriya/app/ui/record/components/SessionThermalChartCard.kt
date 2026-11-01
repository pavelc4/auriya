package dev.auriya.app.ui.record.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlin.math.roundToInt

/**
 * Dedicated Thermals Timeline Chart Card with smooth scrubbing and peak metrics.
 * Modeled after the Google Material 3 "Average Temperature" visualization reference.
 */
@Composable
fun SessionThermalChartCard(
    session: BenchmarkSession,
    modifier: Modifier = Modifier,
) {
    val samples = session.samples
    if (samples.size < 2) return

    val hasCpuTemp = samples.any { it.cpuTemp != null }
    val hasBatTemp = samples.any { it.batteryTemp != null }
    if (!hasCpuTemp && !hasBatTemp) return

    val minTemp = 30f
    val maxTemp =
        remember(session.maxCpuTemp, session.maxBatteryTemp) {
            val peak = maxOf(session.maxCpuTemp ?: 0f, session.maxBatteryTemp ?: 0f)
            (peak + 8f).coerceAtLeast(85f)
        }

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var scrubPixelX by remember { mutableStateOf<Float?>(null) }
    val scrubSample = scrubIndex?.let { samples.getOrNull(it) }

    val cpuTempColor = MaterialTheme.colorScheme.error
    val batTempColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    val guidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Header Title & Peak Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.record_chart_thermal_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.maxCpuTemp != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cpuTempColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "CPU %.1f°C".format(session.maxCpuTemp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cpuTempColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (session.maxBatteryTemp != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = batTempColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "Bat %.1f°C".format(session.maxBatteryTemp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = batTempColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            // 2. High-Fidelity Canvas with Fixed Floating Tooltip Overlay (Prevents Height Shifting)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
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
                    val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

                    // Reference grid lines for 45°, 60°, 75°C
                    val guideTemps = listOf(45f, 60f, 75f, 90f).filter { it in minTemp..maxTemp }
                    for (gt in guideTemps) {
                        val y = h - ((gt - minTemp) / tempRange) * h
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Battery Temperature Line
                    if (hasBatTemp) {
                        val batPath =
                            Path().apply {
                                samples.forEachIndexed { i, s ->
                                    val t = s.batteryTemp ?: 35f
                                    val x = i * stepX
                                    val y = h - ((t - minTemp) / tempRange).coerceIn(0f, 1f) * h
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                            }
                        drawPath(
                            path = batPath,
                            color = batTempColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }

                    // CPU Temperature Line
                    if (hasCpuTemp) {
                        val cpuPath =
                            Path().apply {
                                samples.forEachIndexed { i, s ->
                                    val t = s.cpuTemp ?: 40f
                                    val x = i * stepX
                                    val y = h - ((t - minTemp) / tempRange).coerceIn(0f, 1f) * h
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                            }
                        drawPath(
                            path = cpuPath,
                            color = cpuTempColor,
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }

                    // Scrub Line & Dots at exact pixel coordinate
                    scrubIndex?.let { idx ->
                        val targetX = scrubPixelX ?: (idx * stepX)
                        val currentSample = samples[idx]

                        drawLine(
                            color = guidelineColor,
                            start = Offset(targetX, 0f),
                            end = Offset(targetX, h),
                            strokeWidth = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )

                        currentSample.batteryTemp?.let { bt ->
                            val by = h - ((bt - minTemp) / tempRange).coerceIn(0f, 1f) * h
                            drawCircle(color = batTempColor, radius = 5.dp.toPx(), center = Offset(targetX, by))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(targetX, by))
                        }

                        currentSample.cpuTemp?.let { ct ->
                            val cy = h - ((ct - minTemp) / tempRange).coerceIn(0f, 1f) * h
                            drawCircle(color = cpuTempColor, radius = 5.dp.toPx(), center = Offset(targetX, cy))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(targetX, cy))
                        }
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
                            if (scrubSample.cpuTemp != null) {
                                Text(
                                    text = "CPU: %.1f°C".format(scrubSample.cpuTemp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cpuTempColor,
                                )
                            }
                            if (scrubSample.batteryTemp != null) {
                                Text(
                                    text = "Bat: %.1f°C".format(scrubSample.batteryTemp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = batTempColor,
                                )
                            }
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
                    if (hasCpuTemp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 12.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(cpuTempColor),
                            )
                            Text(
                                text = stringResource(R.string.record_legend_cpu_temp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (hasBatTemp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 12.dp, height = 3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(batTempColor),
                            )
                            Text(
                                text = stringResource(R.string.record_legend_battery_temp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
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
