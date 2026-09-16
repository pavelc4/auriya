package dev.auriya.app.ui.record.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.Battery
import dev.auriya.app.ui.theme.ExpTitleTypography
import kotlin.math.abs

@Composable
fun BatteryDetailPane(
    battery: Battery,
    modifier: Modifier = Modifier,
) {
    val isCharging = (battery.current_ma ?: 0) > 0
    val wattage =
        if (battery.voltage_v != null && battery.current_ma != null) {
            abs(battery.voltage_v * battery.current_ma / 1000.0)
        } else {
            null
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
            // Hero Battery Level Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text =
                                    androidx.compose.ui.res
                                        .stringResource(dev.auriya.app.R.string.record_battery_power_state),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color =
                                    if (isCharging) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                            ) {
                                Text(
                                    text = (battery.status ?: if (isCharging) "Charging" else "Discharging").uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (isCharging) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (battery.pct != null) "${battery.pct}%" else "--",
                                style =
                                    ExpTitleTypography.titleLarge.copy(
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                            if (wattage != null) {
                                Text(
                                    text = "• %.2f W".format(wattage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }

                        if (battery.pct != null) {
                            WavyLinearProgressIndicator(
                                progress = (battery.pct.toFloat() / 100f).coerceIn(0.05f, 1f),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                strokeWidth = 4.dp,
                                amplitude = 2.2.dp,
                                wavelength = 28.dp,
                            )
                        }
                    }
                }
            }

            // Power Metrics Section
            item {
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_sec_electrical),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            // Power Metrics Grid Row 1
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BatteryMetricCard(
                        title =
                            if (isCharging) {
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_charge_rate)
                            } else {
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_discharge)
                            },
                        value = if (battery.current_ma != null) "${abs(battery.current_ma)} mA" else "--",
                        desc =
                            if (isCharging) {
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_input_current)
                            } else {
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_power_drain)
                            },
                        icon = if (isCharging) Icons.Outlined.Bolt else Icons.Outlined.ElectricBolt,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    BatteryMetricCard(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.record_voltage),
                        value = if (battery.voltage_v != null) "%.2f V".format(battery.voltage_v) else "--",
                        desc =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.record_terminal_potential),
                        icon = Icons.Outlined.ElectricalServices,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            // Power Metrics Grid Row 2
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BatteryMetricCard(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.record_health),
                        value = battery.health ?: "Good",
                        desc =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.record_pack_integrity),
                        icon = Icons.Outlined.HealthAndSafety,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    BatteryMetricCard(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.record_chemistry),
                        value = "Li-Po / Li-ion",
                        desc = "Cell chemistry",
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
private fun BatteryMetricCard(
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
