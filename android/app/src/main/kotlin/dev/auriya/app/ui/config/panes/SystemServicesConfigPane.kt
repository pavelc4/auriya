package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import dev.auriya.app.R
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SliderSettingItem
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard
import kotlin.math.roundToInt

fun LazyListScope.systemServicesConfigPane(
    // Daemon Controls
    defaultMode: String,
    logLevel: String,
    checkIntervalMs: Float,
    onOpenPresetPicker: () -> Unit,
    onOpenLogLevelPicker: () -> Unit,
    onCheckIntervalChange: (Float) -> Unit,
    onCheckIntervalFinished: () -> Unit,
    // CPU Governor
    defaultGov: String,
    onOpenGovPicker: () -> Unit,
    // DND
    dndEnabled: Boolean,
    onDndEnabledChange: (Boolean) -> Unit,
) {
    // 1. Info Card
    item {
        PopupInfoCard(
            icon = Icons.Outlined.Dns,
            title = androidx.compose.ui.res.stringResource(R.string.config_daemon_info_title),
            description = androidx.compose.ui.res.stringResource(R.string.config_daemon_info_desc),
        )
    }

    // 2. Daemon Controls Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_daemon_controls),
        ) {
            val total = 3
            val displayDefaultMode =
                if (defaultMode.equals("fast", ignoreCase = true) || defaultMode.equals("fas", ignoreCase = true)) {
                    "FAS"
                } else {
                    defaultMode.replaceFirstChar { it.uppercase() }
                }
            ClickableSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_default_profile),
                subtitle = "${androidx.compose.ui.res.stringResource(R.string.home_active_profile)}: $displayDefaultMode",
                onClick = onOpenPresetPicker,
                icon = Icons.Outlined.Tune,
                shape = itemShapeFor(0, total),
            )
            ClickableSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_log_verbosity),
                subtitle = "Level: ${logLevel.uppercase()}",
                onClick = onOpenLogLevelPicker,
                icon = Icons.AutoMirrored.Outlined.Article,
                shape = itemShapeFor(1, total),
            )
            SliderSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_check_interval),
                description = androidx.compose.ui.res.stringResource(R.string.config_check_interval_desc),
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

    // 3. Scaling Settings Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_scaling),
        ) {
            val total = 1
            ClickableSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.games_cpu_governor),
                subtitle = "Governor: $defaultGov",
                onClick = onOpenGovPicker,
                icon = Icons.Outlined.Speed,
                shape = itemShapeFor(0, total),
            )
        }
    }

    // 4. DND Automation Subsection
    item {
        SettingsSubsection(
            title = androidx.compose.ui.res.stringResource(R.string.config_sec_dnd_auto),
        ) {
            val total = 1
            SwitchSettingItem(
                title = androidx.compose.ui.res.stringResource(R.string.config_dnd_auto_game),
                subtitle = androidx.compose.ui.res.stringResource(R.string.config_dnd_auto_game_desc),
                checked = dndEnabled,
                onCheckedChange = onDndEnabledChange,
                icon = Icons.Outlined.DoNotDisturbOn,
                shape = itemShapeFor(0, total),
            )
        }
    }
}
