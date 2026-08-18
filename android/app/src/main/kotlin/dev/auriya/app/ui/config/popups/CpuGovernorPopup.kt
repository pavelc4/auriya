package dev.auriya.app.ui.config.popups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Speed
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
fun CpuGovernorPopup(
    show: Boolean,
    defaultGov: String,
    availableGovernors: List<String>,
    sheetState: SheetState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                BottomSheetHeader(
                    title = "CPU Governor",
                    subtitle = "Active: $defaultGov"
                )
            }

            items(availableGovernors.size) { index ->
                val gov = availableGovernors[index]
                val isSelected = gov.equals(defaultGov, ignoreCase = true)
                val icon = when (gov.lowercase()) {
                    "schedutil" -> Icons.Outlined.AutoMode
                    "performance" -> Icons.Outlined.Bolt
                    "powersave" -> Icons.Outlined.Eco
                    else -> Icons.Outlined.Speed
                }
                val subtitle = when (gov.lowercase()) {
                    "schedutil" -> "Energy-Aware Dynamic Tuning"
                    "performance" -> "Maximum Clocks Locked"
                    "powersave" -> "Battery Preservation"
                    else -> "Kernel Scaling Driver"
                }
                val description = when (gov.lowercase()) {
                    "schedutil" -> "Calculates CPU frequency dynamically based on scheduler load and EAS energy models. Best balance for gaming."
                    "performance" -> "Locks all CPU cores to maximum frequency without downclocking. Highest FPS stability at high power."
                    "powersave" -> "Locks CPU to minimum frequencies to maximize battery life."
                    else -> "Standard Linux kernel governor for frequency management."
                }

                RichSelectionCard(
                    title = gov.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    subtitle = subtitle,
                    description = description,
                    icon = icon,
                    selected = isSelected,
                    onClick = { onSelect(gov) }
                )
            }
        }
    }
}
