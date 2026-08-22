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
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_cpu_info_title),
            description =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_cpu_info_desc),
        )
    }
    item {
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.config_sec_scaling),
        ) {
            val total = 1
            ClickableSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.games_cpu_governor),
                subtitle = "Governor: $defaultGov",
                onClick = onOpenGovPicker,
                icon = Icons.Outlined.Speed,
                shape = itemShapeFor(0, total),
            )
        }
    }
}
