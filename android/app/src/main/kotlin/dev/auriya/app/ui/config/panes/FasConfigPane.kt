package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.VideoSettings
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.fasConfigPane(
    fasEnabled: Boolean,
    fasTargetFps: Float,
    fasPollIntervalMs: Float,
    fasThermalThreshold: Float,
    onFasEnabledChange: (Boolean) -> Unit,
    onTargetFpsChange: (Float) -> Unit,
    onTargetFpsFinished: () -> Unit,
    onPollIntervalChange: (Float) -> Unit,
    onPollIntervalFinished: () -> Unit,
    onThermalThresholdChange: (Float) -> Unit,
    onThermalThresholdFinished: () -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.AutoGraph,
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_fas_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_fas_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_fas),
        ) {
            val total = 4
            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_fas_enable),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_fas_enable_desc),
                checked = fasEnabled,
                onCheckedChange = onFasEnabledChange,
                icon = Icons.Outlined.AutoGraph,
                shape = itemShapeFor(0, total),
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_fallback_fps),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_fallback_fps_desc),
                icon = Icons.Outlined.VideoSettings,
                value = fasTargetFps,
                onValueChange = onTargetFpsChange,
                onValueChangeFinished = onTargetFpsFinished,
                valueRange = 30f..165f,
                steps = 26,
                displayValueFormatter = { "${it.roundToInt()} FPS" },
                shape = itemShapeFor(1, total),
                enabled = fasEnabled,
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_poll_interval),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_poll_interval_desc),
                icon = Icons.Outlined.HourglassEmpty,
                value = fasPollIntervalMs,
                onValueChange = onPollIntervalChange,
                onValueChangeFinished = onPollIntervalFinished,
                valueRange = 50f..1000f,
                steps = 18,
                displayValueFormatter = { "${it.roundToInt()} ms" },
                shape = itemShapeFor(2, total),
                enabled = fasEnabled,
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_thermal_limit),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_thermal_limit_desc),
                icon = Icons.Outlined.DeviceThermostat,
                value = fasThermalThreshold,
                onValueChange = onThermalThresholdChange,
                onValueChangeFinished = onThermalThresholdFinished,
                valueRange = 60f..105f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} °C" },
                shape = itemShapeFor(3, total),
                enabled = fasEnabled,
            )
        }
    }
}
