package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard

fun LazyListScope.cpuConfigPane(
    defaultGov: String,
    onOpenGovPicker: () -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.Speed,
            title = "CPU Frequency Governor",
            description = "Governors dictate how the Linux kernel scales CPU clock speeds between idle and load. Tap below to choose or inspect active scaling governors."
        )
    }
    item {
        SettingsSubsection(title = "SCALING SETTINGS") {
            val total = 1
            ClickableSettingItem(
                title = "CPU Governor",
                subtitle = "Current governor: $defaultGov (Tap to switch)",
                onClick = onOpenGovPicker,
                icon = Icons.Outlined.Speed,
                shape = itemShapeFor(0, total),
            )
        }
    }
}
