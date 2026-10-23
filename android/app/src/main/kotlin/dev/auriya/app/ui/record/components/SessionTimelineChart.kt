package dev.auriya.app.ui.record.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.AppPrefs
import dev.auriya.app.data.stats.BenchmarkSample
import dev.auriya.app.data.stats.BenchmarkSession

enum class MetricGraphTab(
    val label: String,
    val icon: ImageVector,
) {
    OVERVIEW("Overview", Icons.Outlined.Layers),
    FPS("FPS", Icons.Outlined.Speed),
    THERMALS("Thermals", Icons.Outlined.DeviceThermostat),
    CPU_LOAD("CPU Load", Icons.Outlined.Memory),
}

@Composable
fun SessionTimelineChartCard(
    session: BenchmarkSession,
    roundFps: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(MetricGraphTab.OVERVIEW) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. High-Fidelity Canvas Graph (Double tap to reset to Overview)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    selectedTab = MetricGraphTab.OVERVIEW
                                },
                            )
                        },
            ) {
                when (selectedTab) {
                    MetricGraphTab.OVERVIEW -> {
                        OverviewTimelineCanvas(
                            samples = session.samples,
                            maxFps = (session.maxFps.toFloat() * 1.1f).coerceAtLeast(60f),
                            avgFps = session.avgFps.toFloat(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MetricGraphTab.FPS -> {
                        FpsTimelineCanvas(
                            samples = session.samples,
                            maxFps = (session.maxFps.toFloat() * 1.1f).coerceAtLeast(60f),
                            avgFps = session.avgFps.toFloat(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MetricGraphTab.THERMALS -> {
                        ThermalTimelineCanvas(
                            samples = session.samples,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    MetricGraphTab.CPU_LOAD -> {
                        CpuLoadTimelineCanvas(
                            samples = session.samples,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // 2. Timeline Time and Metric Values Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "00:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Text(
                    text =
                        when (selectedTab) {
                            MetricGraphTab.OVERVIEW -> {
                                "Avg %.1f FPS • Peak %.1f°C • %.1f%% Load".format(
                                    session.avgFps,
                                    session.maxCpuTemp ?: 0f,
                                    session.avgCpuLoad,
                                )
                            }

                            MetricGraphTab.FPS -> {
                                "Avg ${AppPrefs.formatFps(session.avgFps, roundFps)} FPS • 1% Low ${AppPrefs.formatFps(session.minLow1Pct, roundFps)} FPS"
                            }

                            MetricGraphTab.THERMALS -> {
                                "Peak CPU: %.1f°C • Peak Bat: %.1f°C".format(
                                    session.maxCpuTemp ?: 0f,
                                    session.maxBatteryTemp ?: 0f,
                                )
                            }

                            MetricGraphTab.CPU_LOAD -> {
                                "Avg CPU Load: %.1f%%".format(session.avgCpuLoad)
                            }
                        },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                )
                Text(
                    text = formatDuration(session.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }

            // 3. Metric Selector Tabs AT THE BOTTOM
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetricGraphTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "tab_color",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        label = "tab_text_color",
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = containerColor,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedTab =
                                        if (isSelected && tab != MetricGraphTab.OVERVIEW) {
                                            MetricGraphTab.OVERVIEW
                                        } else {
                                            tab
                                        }
                                },
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = contentColor,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overview / All-Merged Timeline Canvas Graph (Default)
 */
@Composable
fun OverviewTimelineCanvas(
    samples: List<BenchmarkSample>,
    maxFps: Float,
    avgFps: Float,
    modifier: Modifier = Modifier,
) {
    val fpsColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.error
    val loadColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Grid Guidelines (30, 60, 90, 120 FPS reference)
        val guideFps = listOf(30f, 60f, 90f, 120f).filter { it <= maxFps }
        for (g in guideFps) {
            val y = h - (g / maxFps) * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (samples.size < 2) return@Canvas
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        // 2. CPU Load Line (0..100%)
        val loadPath =
            Path().apply {
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.cpuLoad / 100f).coerceIn(0f, 1f) * h
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        drawPath(
            path = loadPath,
            color = loadColor.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // 3. CPU Temp Line (30..90°C)
        val hasTemps = samples.any { it.cpuTemp != null }
        if (hasTemps) {
            val tempPath =
                Path().apply {
                    samples.forEachIndexed { index, s ->
                        val temp = s.cpuTemp ?: 40f
                        val x = index * stepX
                        val normalizedTemp = ((temp - 30f) / 60f).coerceIn(0f, 1f)
                        val y = h - normalizedTemp * h
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            drawPath(
                path = tempPath,
                color = tempColor.copy(alpha = 0.70f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 4. Area Gradient Fill under FPS
        val fillPath =
            Path().apply {
                moveTo(0f, h)
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.fps.toFloat() / maxFps).coerceIn(0f, 1f) * h
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
                            fpsColor.copy(alpha = 0.25f),
                            fpsColor.copy(alpha = 0.02f),
                        ),
                    startY = 0f,
                    endY = h,
                ),
        )

        // 5. Bold FPS Stroke Line
        val fpsPath =
            Path().apply {
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.fps.toFloat() / maxFps).coerceIn(0f, 1f) * h
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        drawPath(
            path = fpsPath,
            color = fpsColor,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Clean FPS Canvas Graph with smooth area gradient fill
 */
@Composable
fun FpsTimelineCanvas(
    samples: List<BenchmarkSample>,
    maxFps: Float,
    avgFps: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    val avgLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Grid Guidelines (30, 60, 90, 120 FPS)
        val guideFps = listOf(30f, 60f, 90f, 120f).filter { it <= maxFps }
        for (g in guideFps) {
            val y = h - (g / maxFps) * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 2. Average FPS horizontal line
        if (avgFps > 0f) {
            val avgY = h - (avgFps / maxFps).coerceIn(0f, 1f) * h
            drawLine(
                color = avgLineColor,
                start = Offset(0f, avgY),
                end = Offset(w, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
            )
        }

        if (samples.size < 2) return@Canvas

        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        // 3. Fill Area Gradient Path
        val fillPath =
            Path().apply {
                moveTo(0f, h)
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.fps.toFloat() / maxFps).coerceIn(0f, 1f) * h
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
                            primaryColor.copy(alpha = 0.28f),
                            primaryColor.copy(alpha = 0.02f),
                        ),
                    startY = 0f,
                    endY = h,
                ),
        )

        // 4. Smooth Stroke Line Path
        val strokePath =
            Path().apply {
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.fps.toFloat() / maxFps).coerceIn(0f, 1f) * h
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

        drawPath(
            path = strokePath,
            color = primaryColor,
            style =
                Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
        )
    }
}

/**
 * Thermal Timeline Canvas Graph for CPU & Battery Temperature
 */
@Composable
fun ThermalTimelineCanvas(
    samples: List<BenchmarkSample>,
    modifier: Modifier = Modifier,
) {
    val cpuColor = MaterialTheme.colorScheme.error
    val batteryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Temp scale: 25°C to 85°C
        val minTemp = 25f
        val maxTemp = 85f
        val tempRange = maxTemp - minTemp

        // Guidelines (40°C, 60°C, 80°C)
        listOf(40f, 60f, 80f).forEach { t ->
            val y = h - ((t - minTemp) / tempRange).coerceIn(0f, 1f) * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (samples.size < 2) return@Canvas
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        // 1. Battery Temp Curve
        val hasBat = samples.any { it.batteryTemp != null }
        if (hasBat) {
            val batPath =
                Path().apply {
                    samples.forEachIndexed { index, s ->
                        val temp = s.batteryTemp ?: 30f
                        val x = index * stepX
                        val y = h - ((temp - minTemp) / tempRange).coerceIn(0f, 1f) * h
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            drawPath(
                path = batPath,
                color = batteryColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 2. CPU Temp Curve
        val hasCpu = samples.any { it.cpuTemp != null }
        if (hasCpu) {
            val cpuPath =
                Path().apply {
                    samples.forEachIndexed { index, s ->
                        val temp = s.cpuTemp ?: 40f
                        val x = index * stepX
                        val y = h - ((temp - minTemp) / tempRange).coerceIn(0f, 1f) * h
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            drawPath(
                path = cpuPath,
                color = cpuColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * CPU Load Timeline Canvas Graph
 */
@Composable
fun CpuLoadTimelineCanvas(
    samples: List<BenchmarkSample>,
    modifier: Modifier = Modifier,
) {
    val loadColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Guides (25%, 50%, 75%)
        listOf(25f, 50f, 75f).forEach { pct ->
            val y = h - (pct / 100f) * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (samples.size < 2) return@Canvas
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        // Gradient Fill
        val fillPath =
            Path().apply {
                moveTo(0f, h)
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
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
                            loadColor.copy(alpha = 0.25f),
                            loadColor.copy(alpha = 0.02f),
                        ),
                    startY = 0f,
                    endY = h,
                ),
        )

        // Stroke Line
        val strokePath =
            Path().apply {
                samples.forEachIndexed { index, s ->
                    val x = index * stepX
                    val y = h - (s.cpuLoad / 100f).coerceIn(0f, 1f) * h
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

        drawPath(
            path = strokePath,
            color = loadColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
