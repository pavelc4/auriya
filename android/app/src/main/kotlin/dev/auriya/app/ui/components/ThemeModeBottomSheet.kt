package dev.auriya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.ui.config.components.BottomSheetHeader

data class ThemeModeItem(
    val mode: DarkThemeMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeModeBottomSheet(
    selectedMode: DarkThemeMode,
    onModeSelected: (DarkThemeMode) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val items =
        listOf(
            ThemeModeItem(
                mode = DarkThemeMode.FOLLOW_SYSTEM,
                title = stringResource(R.string.appearance_follow_system),
                subtitle = "Dynamically match device system settings",
                icon = Icons.Rounded.BrightnessAuto,
            ),
            ThemeModeItem(
                mode = DarkThemeMode.LIGHT,
                title = stringResource(R.string.appearance_light_theme),
                subtitle = "Clean and crisp daytime brightness",
                icon = Icons.Rounded.LightMode,
            ),
            ThemeModeItem(
                mode = DarkThemeMode.DARK,
                title = stringResource(R.string.appearance_dark_theme),
                subtitle = "Battery-efficient, high contrast dark theme",
                icon = Icons.Rounded.DarkMode,
            ),
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { AuriyaDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            BottomSheetHeader(
                title = stringResource(R.string.appearance_theme_mode),
                subtitle = "Choose your preferred visual appearance and interface style",
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedMode == item.mode
                    val itemShape = animatedItemShapeFor(index, items.size, isSelected = isSelected)

                    ExpressiveSelectableCard(
                        selected = isSelected,
                        onClick = {
                            onModeSelected(item.mode)
                            onDismiss()
                        },
                        title = item.title,
                        subtitle = item.subtitle,
                        leadingIcon = item.icon,
                        shape = itemShape,
                    )
                }
            }
        }
    }
}
