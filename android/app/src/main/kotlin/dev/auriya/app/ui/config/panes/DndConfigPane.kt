package dev.auriya.app.ui.config.panes

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.components.PopupInfoCard

fun LazyListScope.dndConfigPane(
    dndEnabled: Boolean,
    onDndEnabledChange: (Boolean) -> Unit,
) {
    item {
        PopupInfoCard(
            icon = Icons.Outlined.DoNotDisturbOn,
            title = "Do Not Disturb (DND)",
            description = "Automatically silences notifications, pop-up banners, and call rings whenever a listed game enters the foreground, restoring normal state on exit.",
        )
    }
    item {
        SettingsSubsection(title = "DND AUTOMATION") {
            val total = 1
            SwitchSettingItem(
                title = "Auto Game DND",
                subtitle = "Mute notifications automatically when games launch",
                checked = dndEnabled,
                onCheckedChange = onDndEnabledChange,
                icon = Icons.Outlined.DoNotDisturbOn,
                shape = itemShapeFor(0, total),
            )
        }
    }
}
