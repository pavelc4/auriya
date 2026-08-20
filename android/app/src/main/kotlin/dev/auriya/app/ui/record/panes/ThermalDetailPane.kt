package dev.auriya.app.ui.record.panes

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.Thermal
import dev.auriya.app.ui.theme.ExpTitleTypography

@Composable
fun ThermalDetailPane(
    thermal: Thermal,
    modifier: Modifier = Modifier,
) {
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
            // 1. Thermal Insight Card (XKM Wavy Circle Style)
            item {
                ThermalInsightCard(thermal = thermal)
            }

            // 2. Thermal Headroom & Characteristics (Compact & Uniform)
            item {
                Text(
                    text = "THERMAL CHARACTERISTICS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                val maxTemp = listOfNotNull(thermal.cpu_c, thermal.battery_c).maxOrNull() ?: 0f
                val headroom = (85f - maxTemp).coerceAtLeast(0f)

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThermalCharacteristicCard(
                        title = "Headroom",
                        value = if (maxTemp > 0f) "+%.1f°C".format(headroom) else "--",
                        desc = "Safe limit margin",
                        icon = Icons.Outlined.Shield,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    ThermalCharacteristicCard(
                        title = "Cooling Profile",
                        value =
                            if (maxTemp >= 70f) {
                                "Throttled"
                            } else if (maxTemp >= 50f) {
                                "Active Load"
                            } else {
                                "Nominal"
                            },
                        desc = "Governor state",
                        icon = Icons.Outlined.Air,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            // 3. Sensor Zones Breakdown
            item {
                Text(
                    text = "SENSOR ZONES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            if (thermal.cpu_c != null) {
                item {
                    SensorDetailCard(
                        title = "CPU Processor",
                        temp = thermal.cpu_c,
                        icon = Icons.Outlined.Memory,
                        desc = "Primary core cluster junction • Target < 75°C",
                    )
                }
            }

            if (thermal.battery_c != null) {
                item {
                    SensorDetailCard(
                        title = "Battery Cell",
                        temp = thermal.battery_c,
                        icon = Icons.Outlined.BatteryChargingFull,
                        desc = "Lithium cell core pack • Safe range < 45°C",
                    )
                }
            }
        }
    }
}

/**
 * Material Design card displaying thermal insight information with wavy circular gauge
 */
@Composable
fun ThermalInsightCard(
    thermal: Thermal,
    modifier: Modifier = Modifier,
) {
    val maxTemp = listOfNotNull(thermal.cpu_c, thermal.battery_c).maxOrNull() ?: 0f

    val (badgeText, badgeContainerColor, badgeContentColor) =
        when {
            maxTemp <= 0f -> {
                Triple(
                    "STANDBY",
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            maxTemp >= 70f -> {
                Triple("THROTTLING", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }

            maxTemp >= 50f -> {
                Triple("ELEVATED", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }

            else -> {
                Triple("OPTIMAL", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

    val primaryColor =
        when {
            maxTemp >= 70f -> MaterialTheme.colorScheme.error
            maxTemp >= 50f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header: Icon + Title + Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeviceThermostat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "Thermal Insight",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Badge (Pushed to Right)
                Surface(
                    color = badgeContainerColor,
                    shape = CircleShape,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                    )
                }
            }

            // Content: Wavy Circular Gauge + Stats Column
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Wavy Circular Temperature Gauge (Tighter wave)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(136.dp),
                ) {
                    // Background Track
                    WavyCircularProgressIndicator(
                        progress = 1f,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        strokeWidth = 12.dp,
                        amplitude = 2.dp,
                        frequency = 14,
                    )

                    // Active Progress (scaled to 0-85°C max)
                    val progressFraction = (maxTemp / 85f).coerceIn(0.05f, 1f)
                    WavyCircularProgressIndicator(
                        progress = if (maxTemp > 0f) progressFraction else 0f,
                        modifier = Modifier.fillMaxSize(),
                        color = primaryColor,
                        strokeWidth = 12.dp,
                        amplitude = 2.dp,
                        frequency = 14,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (maxTemp > 0f) "%.1f°C".format(maxTemp) else "--",
                            style =
                                ExpTitleTypography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = primaryColor,
                                ),
                        )
                        Text(
                            text = "Peak Sensor",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Stats Column (CPU Junction, Battery Cell, Thermal State)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    ThermalInsightItem(
                        label = "CPU Junction",
                        value = if (thermal.cpu_c != null) "%.1f°C".format(thermal.cpu_c) else "--",
                        icon = Icons.Outlined.Memory,
                        color =
                            if (thermal.cpu_c != null &&
                                thermal.cpu_c >= 70f
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    ThermalInsightItem(
                        label = "Battery Cell",
                        value = if (thermal.battery_c != null) "%.1f°C".format(thermal.battery_c) else "--",
                        icon = Icons.Outlined.BatteryChargingFull,
                        color =
                            if (thermal.battery_c != null &&
                                thermal.battery_c >= 45f
                            ) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    ThermalInsightItem(
                        label = "Thermal State",
                        value =
                            when {
                                maxTemp >= 70f -> "Throttling"
                                maxTemp >= 50f -> "Elevated Load"
                                maxTemp > 0f -> "Optimal"
                                else -> "Standby"
                            },
                        icon = Icons.Outlined.DeviceThermostat,
                        color = primaryColor,
                    )
                }
            }

            // Flavor Insight Context Note
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text =
                            when {
                                maxTemp >= 70f -> "High thermal load detected. Silicon governors are regulating clock frequencies."
                                maxTemp >= 50f -> "Moderate core temperature under gaming load. Heat dissipation is working normally."
                                maxTemp > 0f -> "All sensor zones are running in optimal range. Zero thermal throttling active."
                                else -> "Polling thermal telemetry directly from kernel sysfs sensor zones."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

/**
 * Wavy circular progress indicator with animated progress
 */
@Composable
fun WavyCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    strokeWidth: Dp,
    amplitude: Dp = 2.dp,
    frequency: Int = 14,
) {
    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    val amplitudePx = with(LocalDensity.current) { amplitude.toPx() }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec =
            tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing,
            ),
        label = "thermal_wavy_progress",
    )

    Canvas(modifier = modifier) {
        val radius = (size.minDimension - strokeWidthPx - amplitudePx * 2) / 2
        val center = Offset(size.width / 2, size.height / 2)
        val path = Path()

        val startAngle = -90f
        val sweepAngle = 360f * animatedProgress

        for (angle in 0..sweepAngle.toInt()) {
            val currentAngle = startAngle + angle
            val rad = Math.toRadians(currentAngle.toDouble())

            val wavePhase = Math.toRadians((angle * frequency).toDouble())
            val r = radius + amplitudePx * kotlin.math.sin(wavePhase)

            val x = center.x + r * kotlin.math.cos(rad)
            val y = center.y + r * kotlin.math.sin(rad)

            if (angle == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }

        drawPath(
            path = path,
            color = color,
            style =
                Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                ),
        )
    }
}

/**
 * Material Design 3 Expressive Wavy Linear Progress Indicator (Tighter, refined wave)
 */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 1.6.dp,
    wavelength: Dp = 10.dp,
) {
    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    val amplitudePx = with(LocalDensity.current) { amplitude.toPx() }
    val wavelengthPx = with(LocalDensity.current) { wavelength.toPx() }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec =
            tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing,
            ),
        label = "wavy_linear_progress",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(12.dp),
    ) {
        val totalWidth = size.width
        val midY = size.height / 2f
        val activeWidth = totalWidth * animatedProgress

        // 1. Draw flat track for remaining segment
        if (activeWidth < totalWidth) {
            val trackPath =
                Path().apply {
                    moveTo(activeWidth, midY)
                    lineTo(totalWidth, midY)
                }
            drawPath(
                path = trackPath,
                color = trackColor,
                style =
                    Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                    ),
            )
        }

        // 2. Draw wavy path for active segment
        if (activeWidth > 0f) {
            val wavyPath =
                Path().apply {
                    var x = 0f
                    moveTo(0f, midY)
                    val step = 2f
                    while (x <= activeWidth) {
                        val angle = (x / wavelengthPx) * 2 * Math.PI
                        val y = midY + (amplitudePx * kotlin.math.sin(angle)).toFloat()
                        lineTo(x, y)
                        x += step
                    }
                }
            drawPath(
                path = wavyPath,
                color = color,
                style =
                    Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                    ),
            )
        }
    }
}

@Composable
fun ThermalInsightItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .background(
                        color.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ThermalCharacteristicCard(
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
            // Icon with Badge
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

@Composable
private fun SensorDetailCard(
    title: String,
    temp: Float,
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
) {
    val tempColor =
        when {
            temp >= 70f -> MaterialTheme.colorScheme.error
            temp >= 50f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tempColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tempColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
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

                Text(
                    text = "%.1f°C".format(temp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = tempColor,
                )
            }

            // Material Design 3 Wavy Linear Progress Bar
            WavyLinearProgressIndicator(
                progress = (temp / 85f).coerceIn(0.05f, 1f),
                color = tempColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                strokeWidth = 4.dp,
                amplitude = 1.6.dp,
                wavelength = 10.dp,
            )
        }
    }
}
