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
            title = "Mode Presets Tuning",
            description = "Each performance mode (Powersave, Balance, Performance, Fast) balances power consumption and smoothness through FPS Margin and Thermal Limits.",
        )
    }
    item {
        SettingsSubsection(title = "PRESET SELECTION & TUNING") {
            val icon =
                when (selectedModeKey) {
                    "powersave" -> Icons.Outlined.Eco
                    "balance" -> Icons.Outlined.Tune
                    "performance" -> Icons.Outlined.Bolt
                    "fast" -> Icons.Outlined.RocketLaunch
                    else -> Icons.Outlined.Tune
                }

            ClickableSettingItem(
                title = "Profile to Tune",
                subtitle = "Currently customizing: ${selectedModeKey.replaceFirstChar { it.uppercase() }} mode",
                onClick = onOpenTuneProfilePicker,
                icon = icon,
                shape = itemShapeFor(0, 3),
            )

            SliderSettingItem(
                title = "${selectedModeKey.replaceFirstChar { it.uppercase() }} Margin",
                description = "FPS drop margin before frequency ramp-up",
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
                title = "${selectedModeKey.replaceFirstChar { it.uppercase() }} Thermal Limit",
                description = "Maximum temperature threshold for $selectedModeKey mode",
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
