package dev.auriya.app.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard

/**
 * A profile preset declaration.
 *
 * Text is stored as string resource ids (not resolved strings) because `stringResource(...)`
 * is a @Composable call and cannot be evaluated in a top-level initializer. Resolution happens
 * at render time inside [ProfilePresetItem].
 */
data class ProfilePreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

/** Plain top-level constant: no composition involved, allocated once. */
val DEFAULT_PROFILE_PRESETS =
    listOf(
        ProfilePreset(
            id = "powersave",
            titleRes = R.string.profile_preset_powersave_title,
            subtitleRes = R.string.profile_preset_powersave_subtitle,
            descriptionRes = R.string.profile_preset_powersave_description,
            icon = Icons.Outlined.Eco,
        ),
        ProfilePreset(
            id = "balance",
            titleRes = R.string.profile_preset_balance_title,
            subtitleRes = R.string.profile_preset_balance_subtitle,
            descriptionRes = R.string.profile_preset_balance_description,
            icon = Icons.Outlined.Speed,
        ),
        ProfilePreset(
            id = "performance",
            titleRes = R.string.profile_preset_performance_title,
            subtitleRes = R.string.profile_preset_performance_subtitle,
            descriptionRes = R.string.profile_preset_performance_description,
            icon = Icons.Outlined.Bolt,
        ),
        ProfilePreset(
            id = "fas",
            titleRes = R.string.profile_preset_fas_title,
            subtitleRes = R.string.profile_preset_fas_subtitle,
            descriptionRes = R.string.profile_preset_fas_description,
            icon = Icons.Outlined.RocketLaunch,
        ),
    )

/** Resolves the preset's strings at render time. Only called from composition. */
@Composable
private fun ProfilePresetItem(
    preset: ProfilePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RichSelectionCard(
        title = stringResource(preset.titleRes),
        subtitle = stringResource(preset.subtitleRes),
        description = stringResource(preset.descriptionRes),
        icon = preset.icon,
        selected = selected,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerBottomSheet(
    selectedProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.config_performance_profile),
    subtitle: String = stringResource(R.string.config_performance_profile_sub),
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
                    ProfilePresetItem(
                        preset = p,
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
