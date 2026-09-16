package dev.auriya.app.ui.config.popups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerPopup(
    defaultMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val presets =
        listOf(
            Triple(
                "powersave",
                "Battery Preservation",
                "Limits clock frequencies and aggressively throttles background tasks to maximize battery life.",
            ),
            Triple(
                "balance",
                "Daily Dynamic Tuning",
                "Dynamic optimization and adaptive frequency scaling for smooth responsiveness and efficiency.",
            ),
            Triple(
                "performance",
                "Maximum Power",
                "Unlocks high clock frequencies and unthrottled rendering for demanding gaming sessions.",
            ),
            Triple("fast", "Ultra Low Latency", "Zero-margin frame delivery with rapid clock ramp-up for competitive response."),
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item {
                BottomSheetHeader(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_default_profile),
                    subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.common_active)}: ${defaultMode.replaceFirstChar { it.uppercase() }}",
                )
            }

            items(presets.size) { index ->
                val (key, sub, desc) = presets[index]
                val isSelected = key.equals(defaultMode, ignoreCase = true)
                val icon =
                    when (key) {
                        "powersave" -> Icons.Outlined.Eco
                        "balance" -> Icons.Outlined.Tune
                        "performance" -> Icons.Outlined.Bolt
                        "fast" -> Icons.Outlined.RocketLaunch
                        else -> Icons.Outlined.Speed
                    }

                RichSelectionCard(
                    title = key.replaceFirstChar { it.uppercase() },
                    subtitle = sub,
                    description = desc,
                    icon = icon,
                    selected = isSelected,
                    onClick = { onSelect(key) },
                )
            }
        }
    }
}
