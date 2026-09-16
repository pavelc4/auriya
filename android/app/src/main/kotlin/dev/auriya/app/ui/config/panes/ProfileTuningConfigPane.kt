package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.profileTuningConfigPane(
    selectedModeKey: String,
    modeMargin: Float,
    modeThermal: Float,
    onOpenTuneProfilePicker: () -> Unit,
    onMarginChange: (Float) -> Unit,
    onMarginFinished: () -> Unit,
    onThermalChange: (Float) -> Unit,
    onThermalFinished: () -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.Tune,
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_preset_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_preset_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_preset_tuning),
        ) {
            val icon =
                when (selectedModeKey) {
                    "powersave" -> Icons.Outlined.Eco
                    "balance" -> Icons.Outlined.Tune
                    "performance" -> Icons.Outlined.Bolt
                    "fast" -> Icons.Outlined.RocketLaunch
                    else -> Icons.Outlined.Tune
                }

            ClickableSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_profile_to_tune),
                subtitle = "${selectedModeKey.replaceFirstChar { it.uppercase() }}",
                onClick = onOpenTuneProfilePicker,
                icon = icon,
                shape = itemShapeFor(0, 3),
            )

            SliderSettingItem(
                title = "${selectedModeKey.replaceFirstChar { it.uppercase() }} ${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.config_fps_margin)}",
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_fps_margin_desc),
                icon = Icons.Outlined.Speed,
                value = modeMargin,
                onValueChange = onMarginChange,
                onValueChangeFinished = onMarginFinished,
                valueRange = 0f..10f,
                steps = 19,
                displayValueFormatter = { String.format("%.1f FPS", it) },
                shape = itemShapeFor(1, 3),
            )

            SliderSettingItem(
                title = "${selectedModeKey.replaceFirstChar { it.uppercase() }} ${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.config_thermal_limit)}",
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_thermal_limit_desc),
                icon = Icons.Outlined.DeviceThermostat,
                value = modeThermal,
                onValueChange = onThermalChange,
                onValueChangeFinished = onThermalFinished,
                valueRange = 60f..105f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} °C" },
                shape = itemShapeFor(2, 3),
            )
        }
    }
}
