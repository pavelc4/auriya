package dev.auriya.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard

data class ProfilePreset(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String,
    val icon: ImageVector,
)

val DEFAULT_PROFILE_PRESETS =
    listOf(
        ProfilePreset(
            id = "powersave",
            title = "Powersave",
            subtitle = "Battery Preservation",
            description = "Limits clock frequencies and aggressively throttles background tasks to maximize battery life.",
            icon = Icons.Outlined.Eco,
        ),
        ProfilePreset(
            id = "balance",
            title = "Balance",
            subtitle = "Balanced Daily",
            description = "Intelligently balances power efficiency with smooth responsiveness for day-to-day apps.",
            icon = Icons.Outlined.Speed,
        ),
        ProfilePreset(
            id = "performance",
            title = "Performance",
            subtitle = "High Performance",
            description = "Unleashes CPU & GPU power limits for intense gaming sessions and complex tasks.",
            icon = Icons.Outlined.Bolt,
        ),
        ProfilePreset(
            id = "fas",
            title = "FAS",
            subtitle = "Dynamic FAS / Turbo",
            description = "Zero-margin frame delivery with rapid dynamic clock ramp-up for competitive response.",
            icon = Icons.Outlined.RocketLaunch,
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerBottomSheet(
    selectedProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Performance Profile",
    subtitle: String = "Choose an optimization profile for CPU, GPU, and frame pacing",
    presets: List<ProfilePreset> = DEFAULT_PROFILE_PRESETS,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
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
                title = title,
                subtitle = subtitle,
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets.size) { i ->
                    val p = presets[i]
                    val isSelected =
                        selectedProfile.equals(p.id, ignoreCase = true) ||
                            (
                                (selectedProfile.equals("fas", ignoreCase = true) || selectedProfile.equals("fast", ignoreCase = true)) &&
                                    (p.id.equals("fas", ignoreCase = true) || p.id.equals("fast", ignoreCase = true))
                            )
                    RichSelectionCard(
                        title = p.title,
                        subtitle = p.subtitle,
                        description = p.description,
                        icon = p.icon,
                        selected = isSelected,
                        onClick = {
                            onSelect(p.id)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}
