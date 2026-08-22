package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.Modifier
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.daemonConfigPane(
    defaultMode: String,
    logLevel: String,
    checkIntervalMs: Float,
    onOpenPresetPicker: () -> Unit,
    onOpenLogLevelPicker: () -> Unit,
    onCheckIntervalChange: (Float) -> Unit,
    onCheckIntervalFinished: () -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.Dns,
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_daemon_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_daemon_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_daemon_controls),
        ) {
            val total = 3
            ClickableSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_default_profile),
                subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.home_active_profile)}: ${defaultMode.replaceFirstChar { it.uppercase() }}",
                onClick = onOpenPresetPicker,
                icon = Icons.Outlined.Tune,
                shape = itemShapeFor(0, total),
            )
            ClickableSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_log_verbosity),
                subtitle = "Level: ${logLevel.uppercase()}",
                onClick = onOpenLogLevelPicker,
                icon = Icons.AutoMirrored.Outlined.Article,
                shape = itemShapeFor(1, total),
            )
            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_check_interval),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_check_interval_desc),
                icon = Icons.Outlined.Timer,
                value = checkIntervalMs,
                onValueChange = onCheckIntervalChange,
                onValueChangeFinished = onCheckIntervalFinished,
                valueRange = 500f..10000f,
                steps = 18,
                displayValueFormatter = { "${it.roundToInt()} ms" },
                shape = itemShapeFor(2, total),
            )
        }
    }
}
