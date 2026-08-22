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
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_dnd_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_dnd_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_dnd_auto),
        ) {
            val total = 1
            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_dnd_auto_game),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.config_dnd_auto_game_desc),
                checked = dndEnabled,
                onCheckedChange = onDndEnabledChange,
                icon = Icons.Outlined.DoNotDisturbOn,
                shape = itemShapeFor(0, total),
            )
        }
    }
}
