package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
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
            title = "Auriya Daemon Engine",
            description = "The Auriya background daemon monitors foreground games, manages CPU frequencies via Unix sockets, and enforces scheduling policies."
        )
    }
    item {
        SettingsSubsection(title = "DAEMON CONTROLS") {
            val total = 3
            ClickableSettingItem(
                title = "Default Profile",
                subtitle = "Base system profile: ${defaultMode.replaceFirstChar { it.uppercase() }}",
                onClick = onOpenPresetPicker,
                icon = Icons.Outlined.Tune,
                shape = itemShapeFor(0, total),
            )
            ClickableSettingItem(
                title = "Log Verbosity",
                subtitle = "Logging level: ${logLevel.uppercase()}",
                onClick = onOpenLogLevelPicker,
                icon = Icons.Outlined.Article,
                shape = itemShapeFor(1, total),
            )
            SliderSettingItem(
                title = "Check Interval",
                description = "Daemon health status poll frequency",
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
