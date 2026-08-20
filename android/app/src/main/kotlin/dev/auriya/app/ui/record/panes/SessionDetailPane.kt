package dev.auriya.app.ui.record.panes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.auriya.app.data.AppIconCache
import dev.auriya.app.data.stats.BenchmarkSample
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.record.components.formatDuration
import dev.auriya.app.ui.theme.ExpTitleTypography
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private enum class MetricGraphTab(
    val label: String,
    val icon: ImageVector,
) {
    OVERVIEW("Overview", Icons.Outlined.Layers),
    FPS("FPS", Icons.Outlined.Speed),
    THERMALS("Thermals", Icons.Outlined.DeviceThermostat),
    CPU_LOAD("CPU Load", Icons.Outlined.Memory),
}

@Composable
fun SessionDetailPane(
    session: BenchmarkSession,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MetricGraphTab.OVERVIEW) }

    val dateStr =
        remember(session.startTimeEpoch) {
            val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy • HH:mm", Locale.getDefault())
            sdf.format(Date(session.startTimeEpoch))
        }

    val displayJank =
        remember(session) {
            if (session.samples.isNotEmpty()) {
                session.samples.maxOfOrNull { it.jank } ?: session.totalJank
            } else {
                session.totalJank
            }
        }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Info Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val icon =
                            remember(session.packageName) {
                                AppIconCache.load(context.packageManager, session.packageName)
                            }

                        if (icon != null) {
                            androidx.compose.foundation.Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.SportsEsports,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.appLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = session.profile.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(
                                    text = "• ${formatDuration(session.durationSeconds)} • ${session.samplesCount} samples",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            // Hero Metric Card (Avg, 1% Low, Max, Jank)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "PERFORMANCE METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "%.1f".format(session.avgFps),
                                    style =
                                        ExpTitleTypography.titleLarge.copy(
                                            fontSize = 44.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                                Text(
                                    text = "Average Frame Rate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "%.1f FPS".format(session.minLow1Pct),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    text = "1% Low Stability",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            DetailStatItem(
                                title = "Peak FPS",
                                value = "%.1f".format(session.maxFps),
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                            )
                            DetailStatItem(
                                title = "Jank Stutter",
                                value = "$displayJank frames",
                                icon = Icons.Outlined.WarningAmber,
                            )
                            DetailStatItem(
                                title = "Avg CPU Load",
                                value = "%.1f%%".format(session.avgCpuLoad),
                                icon = Icons.Outlined.Memory,
                            )
                        }
                    }
                }
            }

            // Timeline Graph Card (Graph on top, Tabs at BOTTOM)
            if (session.samples.size > 1) {
                item {
                    Text(
                        text = "TIMELINE METRICS GRAPH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
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
                                                "Avg %.1f FPS • 1%% Low %.1f FPS".format(session.avgFps, session.minLow1Pct)
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
            }

            // Thermal Peak Section
            item {
                Text(
                    text = "THERMAL & HARDWARE STATS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SessionCharacteristicCard(
                        title = "Peak CPU Temp",
                        value = if (session.maxCpuTemp != null) "%.1f°C".format(session.maxCpuTemp) else "--",
                        desc = "Max junction silicon heat",
                        icon = Icons.Outlined.Memory,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    SessionCharacteristicCard(
                        title = "Peak Battery Temp",
                        value = if (session.maxBatteryTemp != null) "%.1f°C".format(session.maxBatteryTemp) else "--",
                        desc = "Max cell pack thermal",
                        icon = Icons.Outlined.BatteryChargingFull,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStatItem(
    title: String,
    value: String,
    icon: ImageVector,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SessionCharacteristicCard(
    title: String,
    value: String,
    desc: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Overview / All-Merged Timeline Canvas Graph (Default)
 */
@Composable
private fun OverviewTimelineCanvas(
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
private fun FpsTimelineCanvas(
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
private fun ThermalTimelineCanvas(
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
private fun CpuLoadTimelineCanvas(
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

/**
 * Direct CSV Export to Download/auriya/ without popping up the share sheet
 */
fun exportSessionCsvDirect(
    context: Context,
    session: BenchmarkSession,
) {
    runCatching {
        val safeName = session.appLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "auriya_benchmark_${safeName}_${session.startTimeEpoch}.csv"

        dev.auriya.app.data.stats.BenchmarkRecorder
            .saveSessionToDownloadAuriya(context, session)
        Toast.makeText(context, "Saved to Download/auriya/$fileName", Toast.LENGTH_LONG).show()
    }.onFailure { e ->
        e.printStackTrace()
        Toast.makeText(context, "Failed to export: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Open Android system share sheet for sharing CSV file to other apps
 */
fun shareSessionCsv(
    context: Context,
    session: BenchmarkSession,
) {
    runCatching {
        val safeName = session.appLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "auriya_benchmark_${safeName}_${session.startTimeEpoch}.csv"

        val exportFile = File(context.cacheDir, fileName)
        val savedFile =
            dev.auriya.app.data.stats.BenchmarkRecorder
                .saveSessionToDownloadAuriya(context, session)
        if (!exportFile.exists() && savedFile != null && savedFile.exists()) {
            savedFile.copyTo(exportFile, overwrite = true)
        }

        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile,
            )

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Auriya Benchmark Report: ${session.appLabel}")
                putExtra(Intent.EXTRA_TEXT, "Auriya Benchmark Report for ${session.appLabel}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        context.startActivity(Intent.createChooser(intent, "Share CSV Report with..."))
    }.onFailure { e ->
        e.printStackTrace()
        Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copies a formatted Markdown benchmark report to the clipboard
 */
fun copyMarkdownReport(
    context: Context,
    session: BenchmarkSession,
) {
    val displayJank =
        if (session.samples.isNotEmpty()) {
            session.samples.maxOfOrNull { it.jank } ?: session.totalJank
        } else {
            session.totalJank
        }

    val report =
        buildString {
            appendLine("### 🎮 Auriya Benchmark Report: **${session.appLabel}**")
            appendLine("- **Package:** `${session.packageName}`")
            appendLine("- **Profile:** `${session.profile}`")
            appendLine("- **Duration:** ${formatDuration(session.durationSeconds)} (${session.samplesCount} samples)")
            appendLine("- **Average FPS:** **%.1f FPS**".format(session.avgFps))
            appendLine("- **1% Low FPS:** **%.1f FPS**".format(session.minLow1Pct))
            appendLine("- **Peak FPS:** **%.1f FPS**".format(session.maxFps))
            appendLine("- **Jank Stutter:** $displayJank frames")
            appendLine("- **Avg CPU Load:** %.1f%%".format(session.avgCpuLoad))
            if (session.maxCpuTemp != null) appendLine("- **Peak CPU Temp:** %.1f°C".format(session.maxCpuTemp))
            if (session.maxBatteryTemp != null) appendLine("- **Peak Battery Temp:** %.1f°C".format(session.maxBatteryTemp))
            appendLine()
            appendLine("*Captured with Auriya Daemon v2.0*")
        }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Auriya Benchmark Report", report))
    Toast.makeText(context, "Benchmark report copied to clipboard!", Toast.LENGTH_SHORT).show()
}
