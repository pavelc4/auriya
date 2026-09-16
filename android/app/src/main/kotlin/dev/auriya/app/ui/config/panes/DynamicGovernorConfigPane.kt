package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.DynamicForm
import androidx.compose.material.icons.outlined.Layers
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
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_dynamic_gov_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_dynamic_gov_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_dynamic_gov),
        ) {
            val total = 3
            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_adaptive_gov),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_adaptive_gov_desc),
                checked = dgEnabled,
                onCheckedChange = onDgEnabledChange,
                icon = Icons.Outlined.DynamicForm,
                shape = itemShapeFor(0, total),
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_cv_jitter),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_cv_jitter_desc),
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                value = dgCvThreshold,
                onValueChange = onCvThresholdChange,
                onValueChangeFinished = onCvThresholdFinished,
                valueRange = 0.05f..0.50f,
                steps = 8,
                displayValueFormatter = { String.format("%.2f", it) },
                shape = itemShapeFor(1, total),
                enabled = dgEnabled,
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_debounce_frames),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_debounce_frames_desc),
                icon = Icons.Outlined.Layers,
                value = dgDebounceFrames,
                onValueChange = onDebounceFramesChange,
                onValueChangeFinished = onDebounceFramesFinished,
                valueRange = 1f..10f,
                steps = 8,
                displayValueFormatter = { "${it.roundToInt()} frames" },
                shape = itemShapeFor(2, total),
                enabled = dgEnabled,
            )
        }
    }
}
