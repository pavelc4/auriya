package dev.auriya.app.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.config.components.BottomSheetHeader

/**
 * A CPU metric style option.
 *
 * Text is stored as string resource ids rather than resolved strings so this can stay a
 * plain top-level constant; the labels are resolved at render time in
 * [CpuMetricStyleCard].
 */
data class CpuMetricStyleItem(
    val style: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
)

val DEFAULT_CPU_METRIC_STYLES =
    listOf(
        CpuMetricStyleItem(
            style = "tags",
            titleRes = R.string.cpu_style_tags_title,
            subtitleRes = R.string.cpu_style_tags_subtitle,
            icon = Icons.Rounded.Tag,
        ),
        CpuMetricStyleItem(
            style = "clusters",
            titleRes = R.string.cpu_style_clusters_title,
            subtitleRes = R.string.cpu_style_clusters_subtitle,
            icon = Icons.Rounded.Hub,
        ),
        CpuMetricStyleItem(
            style = "cores",
            titleRes = R.string.cpu_style_cores_title,
            subtitleRes = R.string.cpu_style_cores_subtitle,
            icon = Icons.Rounded.DeveloperBoard,
        ),
        CpuMetricStyleItem(
            style = "overview",
            titleRes = R.string.cpu_style_overview_title,
            subtitleRes = R.string.cpu_style_overview_subtitle,
            icon = Icons.Rounded.Speed,
        ),
    )

/** Resolves the style's strings at render time. Only called from composition. */
@Composable
private fun CpuMetricStyleCard(
    item: CpuMetricStyleItem,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    ExpressiveSelectableCard(
        selected = selected,
        onClick = onClick,
        title = stringResource(item.titleRes),
        subtitle = stringResource(item.subtitleRes),
        leadingIcon = item.icon,
        shape = shape,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuMetricStyleBottomSheet(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val items = DEFAULT_CPU_METRIC_STYLES

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
                subtitle = stringResource(R.string.overlay_cpu_style_sub),
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

                    CpuMetricStyleCard(
                        item = item,
                        selected = isSelected,
                        shape = itemShape,
                        onClick = {
                            onStyleSelected(item.style)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}
