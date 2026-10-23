package dev.auriya.app.ui.games.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.theme.AuriyaTokens

@Composable
fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AuriyaTokens.padding.small, top = 4.dp),
    )
}

@Composable
fun ProfileModeRow(
    selected: String,
    onClick: () -> Unit,
) {
    val displayMode =
        when (selected.lowercase()) {
            "fas", "fast" -> "FAS"
            "balance" -> "BALANCE"
            "powersave" -> "POWERSAVE"
            else -> "PERFORMANCE"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.games_profile_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.games_profile_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayMode,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun GovernorRow(
    selected: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.games_cpu_governor),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.games_cpu_governor_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selected.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun CeilingRow(
    selected: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.games_cpu_ceiling),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.games_cpu_ceiling_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selected.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun ThermalRow(
    selected: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.games_thermal_preset),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.games_thermal_preset_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selected.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusBadge(label = valueLabel, tone = StatusTone.PRIMARY)
        }
        val safeValue = if (value.isNaN()) range.start else value.coerceIn(range.start, range.endInclusive)
        Slider(
            value = safeValue,
            onValueChange = onChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheck,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                ),
        )
    }
}

fun getGovernorInfo(gov: String): Pair<ImageVector, String> {
    val name = gov.lowercase().trim()
    return when (name) {
        "performance" -> {
            Icons.Outlined.Speed to "Locks CPU clusters to maximum operating frequencies for maximum throughput."
        }

        "powersave" -> {
            Icons.Outlined.BatterySaver to "Locks CPU to lowest frequencies to conserve battery and minimize thermals."
        }

        "schedutil" -> {
            Icons.Outlined.Tune to "Energy-Aware Scheduling governor scaling frequencies dynamically via task load."
        }

        "walt" -> {
            Icons.Outlined.Analytics to "Qualcomm Window-Assisted Load Tracking predicting workload demand history."
        }

        "conservative" -> {
            Icons.AutoMirrored.Filled.TrendingDown to "Gradual step-by-step frequency scaling prioritizing battery longevity."
        }

        "ondemand" -> {
            Icons.Outlined.Bolt to "Rapidly jumps to maximum frequency on CPU load spikes, then steps down."
        }

        "interactive" -> {
            Icons.Outlined.TouchApp to "Responsive scaling tailored for low latency and smooth UI touch response."
        }

        "userspace" -> {
            Icons.Outlined.Tune to "Allows manual frequency control by userspace daemons and external tools."
        }

        "blu_schedutil", "blu_active" -> {
            Icons.Outlined.Tune to "Tuned EAS governor by eng.stk balancing smooth frametimes and efficiency."
        }

        "helix_schedutil" -> {
            Icons.Outlined.Tune to "Energy-Aware Scheduling governor tuned for responsive UI and reduced power spikes."
        }

        "electroutil" -> {
            Icons.Outlined.EnergySavingsLeaf to "Schedutil tuning designed for battery efficiency and low frametime jitter."
        }

        "pwrutilx", "pwrutil" -> {
            Icons.Outlined.BatterySaver to "Power-focused schedutil variant designed for extended battery endurance."
        }

        "elementalx" -> {
            Icons.Outlined.Speed to "ElementalX custom governor balancing rapid touch response with battery preservation."
        }

        "alucard" -> {
            Icons.Outlined.Speed to "Custom governor with aggressive frequency ramp-up on high computational loads."
        }

        "darkness", "nightmare" -> {
            Icons.Outlined.Speed to "Aggressive scaling governor prioritizing rapid task completion and fast idle."
        }

        "impulse" -> {
            Icons.Outlined.Bolt to "Tuned interactive governor delivering instant frequency bursts for responsiveness."
        }

        "ironactive" -> {
            Icons.Outlined.EnergySavingsLeaf to
                "Interactive variant modified for aggressive power conservation and smooth scaling."
        }

        "zzmoove" -> {
            Icons.Outlined.Tune to "Dynamic multi-profile governor that adapts frequency scaling based on load patterns."
        }

        "smartmax", "smartmax_eps" -> {
            Icons.Outlined.EnergySavingsLeaf to
                "Custom governor tuned for UI smoothness with strict battery caps."
        }

        "wheatley" -> {
            Icons.Outlined.EnergySavingsLeaf to "Governor designed to maximize CPU C-state sleep duration during active sessions."
        }

        "pegasusq" -> {
            Icons.Outlined.Tune to "Multi-core aware governor that dynamically manages core hotplugging and scaling."
        }

        "cultivation", "cultivation_schedutil" -> {
            Icons.Outlined.SportsEsports to
                "Gaming-focused governor optimized for stable 3D framerates and frametime pacing."
        }

        "bioshock" -> {
            Icons.Outlined.Speed to "Custom multi-tiered governor designed for snappy responsiveness under gaming loads."
        }

        "yankactive", "yankbattery" -> {
            Icons.Outlined.BatterySaver to
                "Battery-centric governor tuned for endurance and conservative frequency bumps."
        }

        "smartass", "smartassv2" -> {
            Icons.Outlined.TouchApp to "Smart interactive governor with dedicated screen-on and deep-sleep states."
        }

        else -> {
            when {
                "sched" in name || "util" in name -> {
                    Icons.Outlined.Tune to "Scheduler-based frequency scaling policy."
                }

                "save" in name || "eco" in name || "pwr" in name || "batt" in name -> {
                    Icons.Outlined.BatterySaver to
                        "Power-saving frequency policy designed to minimize battery drain."
                }

                "perf" in name || "boost" in name || "turbo" in name || "max" in name -> {
                    Icons.Outlined.Speed to
                        "High-performance frequency scaling policy."
                }

                "active" in name || "interact" in name || "touch" in name -> {
                    Icons.Outlined.TouchApp to
                        "Interactive scaling policy responsive to UI and input events."
                }

                "game" in name || "gaming" in name -> {
                    Icons.Outlined.SportsEsports to
                        "Gaming-tuned governor policy optimized for steady frametimes."
                }

                "chill" in name || "cool" in name || "thermal" in name -> {
                    Icons.Outlined.EnergySavingsLeaf to
                        "Thermal-focused frequency policy prioritizing cool operation."
                }

                else -> {
                    Icons.Outlined.Tune to "Kernel CPU governor scheduling policy."
                }
            }
        }
    }
}

fun getCeilingInfo(ceiling: String): Pair<ImageVector, String> {
    val name = ceiling.lowercase().trim()
    return when (name) {
        "default" -> Icons.Outlined.Tune to "Vendor kernel default power capping and thermal limits."
        "low" -> Icons.Outlined.EnergySavingsLeaf to "Strict thermal cap for maximum battery conservation and cool thermals."
        "balance" -> Icons.Outlined.Balance to "Balanced power limit balancing sustained FPS and thermal dissipation."
        "high" -> Icons.Outlined.LocalFireDepartment to "High burst power ceiling allowing sustained peak graphical performance."
        else -> Icons.Outlined.Tune to "Custom cluster frequency ceiling cap."
    }
}

fun getProfileModeInfo(mode: String): Pair<ImageVector, String> =
    when (mode.lowercase()) {
        "fas", "fast" -> {
            Pair(
                Icons.Outlined.AutoAwesome,
                "Dynamic frequency scaling based on frame latency",
            )
        }

        "balance" -> {
            Pair(
                Icons.Outlined.Balance,
                "Balanced performance and energy efficiency",
            )
        }

        "powersave" -> {
            Pair(
                Icons.Outlined.EnergySavingsLeaf,
                "Battery saving with lowest sustainable clocks",
            )
        }

        else -> {
            Pair(
                Icons.Outlined.Bolt,
                "Maximum performance and sustained clocks",
            )
        }
    }
