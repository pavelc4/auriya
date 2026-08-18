package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DynamicForm
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ShowChart
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.dynamicGovernorConfigPane(
    dgEnabled: Boolean,
    dgCvThreshold: Float,
    dgDebounceFrames: Float,
    onDgEnabledChange: (Boolean) -> Unit,
    onCvThresholdChange: (Float) -> Unit,
    onCvThresholdFinished: () -> Unit,
    onDebounceFramesChange: (Float) -> Unit,
    onDebounceFramesFinished: () -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.DynamicForm,
            title = "Dynamic Governor Switching",
            description = "Detects micro-stutter and frame time variance. When jitter exceeds the CV threshold for consecutive debounce frames, Auriya temporarily switches to an aggressive governor to smooth out spikes."
        )
    }
    item {
        SettingsSubsection(title = "DYNAMIC GOVERNOR CONTROLS") {
            val total = 3
            SwitchSettingItem(
                title = "Adaptive Governor",
                subtitle = "Dynamically switch governor on frame jitter",
                checked = dgEnabled,
                onCheckedChange = onDgEnabledChange,
                icon = Icons.Outlined.DynamicForm,
                shape = itemShapeFor(0, total),
            )
            SliderSettingItem(
                title = "CV Jitter Threshold",
                description = "Coefficient of variation sensitivity trigger",
                icon = Icons.Outlined.ShowChart,
                value = dgCvThreshold,
                onValueChange = onCvThresholdChange,
                onValueChangeFinished = onCvThresholdFinished,
                valueRange = 0.05f..0.50f,
                steps = 8,
                displayValueFormatter = { String.format("%.2f", it) },
                shape = itemShapeFor(1, total),
                enabled = dgEnabled
            )
            SliderSettingItem(
                title = "Debounce Frames",
                description = "Consecutive jitter frames required to switch",
                icon = Icons.Outlined.Layers,
                value = dgDebounceFrames,
                onValueChange = onDebounceFramesChange,
                onValueChangeFinished = onDebounceFramesFinished,
                valueRange = 1f..10f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} frames" },
                shape = itemShapeFor(2, total),
                enabled = dgEnabled
            )
        }
    }
}
