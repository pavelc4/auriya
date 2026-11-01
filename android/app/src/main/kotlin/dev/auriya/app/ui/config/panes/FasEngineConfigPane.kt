package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import dev.auriya.app.R
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.fasEngineConfigPane(
    // FAS Controls
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
    // Dynamic Governor Controls
    dgEnabled: Boolean,
    dgCvThreshold: Float,
    dgDebounceFrames: Float,
    onDgEnabledChange: (Boolean) -> Unit,
    onCvThresholdChange: (Float) -> Unit,
    onCvThresholdFinished: () -> Unit,
    onDebounceFramesChange: (Float) -> Unit,
    onDebounceFramesFinished: () -> Unit,
    // Profile Tuning Controls
    selectedModeKey: String,
    modeMargin: Float,
    modeThermal: Float,
    onOpenTuneProfilePicker: () -> Unit,
    onMarginChange: (Float) -> Unit,
    onMarginFinished: () -> Unit,
    onThermalChange: (Float) -> Unit,
    onThermalFinished: () -> Unit,
) {
    // 1. Info Card
    item {
        PopupInfoCard(
            icon = Icons.Outlined.AutoGraph,
            title = androidx.compose.ui.res.stringResource(R.string.config_fas_info_title),
            description = androidx.compose.ui.res.stringResource(R.string.config_fas_info_desc),
        )
    }

    // 2. FAS Configuration Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_fas),
        ) {
            val total = 4
            SwitchSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_fas_enable),
                subtitle = androidx.compose.ui.res.stringResource(R.string.config_fas_enable_desc),
                checked = fasEnabled,
                onCheckedChange = onFasEnabledChange,
                icon = Icons.Outlined.AutoGraph,
                shape = itemShapeFor(0, total),
            )
            SliderSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_fallback_fps),
                description = androidx.compose.ui.res.stringResource(R.string.config_fallback_fps_desc),
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
                title = androidx.compose.ui.res.stringResource(R.string.config_poll_interval),
                description = androidx.compose.ui.res.stringResource(R.string.config_poll_interval_desc),
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
                title = androidx.compose.ui.res.stringResource(R.string.config_thermal_limit),
                description = androidx.compose.ui.res.stringResource(R.string.config_thermal_limit_desc),
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

    // 3. Dynamic Governor Controls Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_dynamic_gov),
        ) {
            val total = 3
            SwitchSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_adaptive_gov),
                subtitle = androidx.compose.ui.res.stringResource(R.string.config_adaptive_gov_desc),
                checked = dgEnabled,
                onCheckedChange = onDgEnabledChange,
                icon = Icons.Outlined.DynamicForm,
                shape = itemShapeFor(0, total),
                enabled = fasEnabled,
            )
            SliderSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_cv_jitter),
                description = androidx.compose.ui.res.stringResource(R.string.config_cv_jitter_desc),
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                value = dgCvThreshold,
                onValueChange = onCvThresholdChange,
                onValueChangeFinished = onCvThresholdFinished,
                valueRange = 0.05f..0.50f,
                steps = 8,
                displayValueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                shape = itemShapeFor(1, total),
                enabled = fasEnabled && dgEnabled,
            )
            SliderSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_debounce_frames),
                description = androidx.compose.ui.res.stringResource(R.string.config_debounce_frames_desc),
                icon = Icons.Outlined.Layers,
                value = dgDebounceFrames,
                onValueChange = onDebounceFramesChange,
                onValueChangeFinished = onDebounceFramesFinished,
                valueRange = 1f..10f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} frames" },
                shape = itemShapeFor(2, total),
                enabled = fasEnabled && dgEnabled,
            )
        }
    }

    // 4. Preset Tuning Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_preset_tuning),
        ) {
            val total = 3
            val icon =
                when (selectedModeKey) {
                    "powersave" -> Icons.Outlined.Eco
                    "balance" -> Icons.Outlined.Tune
                    "performance" -> Icons.Outlined.Bolt
                    "fast", "fas" -> Icons.Outlined.RocketLaunch
                    else -> Icons.Outlined.Tune
                }

            val displayModeKey =
                if (selectedModeKey.equals("fast", ignoreCase = true) || selectedModeKey.equals("fas", ignoreCase = true)) {
                    "FAS"
                } else {
                    selectedModeKey.replaceFirstChar { it.uppercase() }
                }

            ClickableSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_profile_to_tune),
                subtitle = displayModeKey,
                onClick = onOpenTuneProfilePicker,
                icon = icon,
                shape = itemShapeFor(0, total),
            )

            SliderSettingItem(
                title = "$displayModeKey ${androidx.compose.ui.res.stringResource(R.string.config_fps_margin)}",
                description = androidx.compose.ui.res.stringResource(R.string.config_fps_margin_desc),
                icon = Icons.Outlined.Speed,
                value = modeMargin,
                onValueChange = onMarginChange,
                onValueChangeFinished = onMarginFinished,
                valueRange = 0f..10f,
                steps = 19,
                displayValueFormatter = { String.format(java.util.Locale.US, "%.1f FPS", it) },
                shape = itemShapeFor(1, total),
            )

            SliderSettingItem(
                title = "$displayModeKey ${androidx.compose.ui.res.stringResource(R.string.config_thermal_limit)}",
                description = androidx.compose.ui.res.stringResource(R.string.config_thermal_limit_desc),
                icon = Icons.Outlined.DeviceThermostat,
                value = modeThermal,
                onValueChange = onThermalChange,
                onValueChangeFinished = onThermalFinished,
                valueRange = 60f..105f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} °C" },
                shape = itemShapeFor(2, total),
            )
        }
    }
}
