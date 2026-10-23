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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.theme.GoogleSansRounded

/**
 * Dedicated Thermals Timeline Chart Card.
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
    val scrubSample = scrubIndex?.let { samples.getOrNull(it) }

    val cpuTempColor = MaterialTheme.colorScheme.error
    val batTempColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    val guidelineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Thermals Timeline",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Hardware temperatures over time in °C",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                session.maxCpuTemp?.let { peak ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.height(30.dp),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Peak: %.1f°C".format(peak),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

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
                                    text = "Battery: %.1f°C".format(scrubSample.batteryTemp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = batTempColor,
                                )
                            }
                        }
                    }
                }
            }

            // 3. High-Fidelity Canvas
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
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

                    // Scrub Line & Dots
                    scrubIndex?.let { idx ->
                        val scrubX = idx * stepX
                        val currentSample = samples[idx]

                        drawLine(
                            color = guidelineColor,
                            start = Offset(scrubX, 0f),
                            end = Offset(scrubX, h),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                        )

                        currentSample.batteryTemp?.let { bt ->
                            val by = h - ((bt - minTemp) / tempRange).coerceIn(0f, 1f) * h
                            drawCircle(color = batTempColor, radius = 5.dp.toPx(), center = Offset(scrubX, by))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(scrubX, by))
                        }

                        currentSample.cpuTemp?.let { ct ->
                            val cy = h - ((ct - minTemp) / tempRange).coerceIn(0f, 1f) * h
                            drawCircle(color = cpuTempColor, radius = 5.dp.toPx(), center = Offset(scrubX, cy))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(scrubX, cy))
                        }
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
                                text = "CPU Temp",
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
                                text = "Battery Temp",
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
