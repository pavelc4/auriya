package dev.auriya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tag
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
import dev.auriya.app.ui.config.components.BottomSheetHeader

data class CpuMetricStyleItem(
    val style: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuMetricStyleBottomSheet(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val items =
        listOf(
            CpuMetricStyleItem(
                style = "tags",
                title = "Tags",
                subtitle = "Inline cluster frequency badges (e.g. L 1.1 · M 1.7 · P 0.8)",
                icon = Icons.Rounded.Tag,
            ),
            CpuMetricStyleItem(
                style = "clusters",
                title = "Clusters",
                subtitle = "Frequency and load percentage for each cluster (little, big, prime)",
                icon = Icons.Rounded.Hub,
            ),
            CpuMetricStyleItem(
                style = "cores",
                title = "Cores",
                subtitle = "Individual per-core frequencies and utilization (cpu0..cpu7)",
                icon = Icons.Rounded.DeveloperBoard,
            ),
            CpuMetricStyleItem(
                style = "overview",
                title = "Overview",
                subtitle = "Total CPU utilization percentage and maximum core clock",
                icon = Icons.Rounded.Speed,
            ),
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { AuriyaDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            BottomSheetHeader(
                title = stringResource(R.string.overlay_cpu_style),
                subtitle = "Choose how CPU telemetry metrics are formatted on the HUD",
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedStyle == item.style
                    val itemShape = animatedItemShapeFor(index, items.size, isSelected = isSelected)

                    ExpressiveSelectableCard(
                        selected = isSelected,
                        onClick = {
                            onStyleSelected(item.style)
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
