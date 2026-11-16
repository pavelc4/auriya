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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard

/**
 * A raw, context-free declaration of a profile preset.
 *
 * Text is kept as string resources (or plain literals) instead of resolved strings, because
 * `stringResource(...)` is a @Composable call and therefore cannot be evaluated at top level.
 */
data class ProfilePresetSpec(
    val id: String,
    @StringRes val titleRes: Int = 0,
    val title: String = "",
    @StringRes val subtitleRes: Int = 0,
    val subtitle: String = "",
    @StringRes val descriptionRes: Int = 0,
    val description: String = "",
    val icon: ImageVector,
)

/** A fully resolved preset, safe to pass around as plain data. */
data class ProfilePreset(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String,
    val icon: ImageVector,
)

val DEFAULT_PROFILE_PRESET_SPECS =
    listOf(
        ProfilePresetSpec(
            id = "powersave",
            titleRes = R.string.Performance_Powerasve,
            subtitleRes = R.string.Performance_Powerasve_sub,
            descriptionRes = R.string.Performance_Powerasve_des,
            icon = Icons.Outlined.Eco,
        ),
        ProfilePresetSpec(
            id = "balance",
            titleRes = R.string.Performance_Balance,
            subtitleRes = R.string.Performance_Balance_sub,
            descriptionRes = R.string.Performance_Balance_des,
            icon = Icons.Outlined.Speed,
        ),
        ProfilePresetSpec(
            id = "performance",
            titleRes = R.string.Performance_Performance,
            subtitleRes = R.string.Performance_Performance_sub,
            descriptionRes = R.string.Performance_Performance_des,
            icon = Icons.Outlined.Bolt,
        ),
        ProfilePresetSpec(
            id = "fas",
            titleRes = R.string.Performance_FAS,
            subtitleRes = R.string.Performance_FAS_sub,
            descriptionRes = R.string.Performance_FAS_des,
            icon = Icons.Outlined.RocketLaunch,
        ),
    )

/** Resolves a spec into a [ProfilePreset]. Must be called from a @Composable scope. */
@Composable
fun ProfilePresetSpec.resolve(): ProfilePreset =
    ProfilePreset(
        id = id,
        title = if (titleRes != 0) stringResource(titleRes) else title,
        subtitle = if (subtitleRes != 0) stringResource(subtitleRes) else subtitle,
        description = if (descriptionRes != 0) stringResource(descriptionRes) else description,
        icon = icon,
    )

/** Resolves the built-in presets. Must be called from a @Composable scope. */
@Composable
fun rememberDefaultProfilePresets(): List<ProfilePreset> {
    val specs = DEFAULT_PROFILE_PRESET_SPECS
    return remember(specs) { specs.map { it.id } }.let { ids ->
        ids.zip(specs).map { (_, spec) -> spec.resolve() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerBottomSheet(
    selectedProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.config_performance_profile),
    subtitle: String = stringResource(R.string.config_performance_profile_sub),
    presets: List<ProfilePreset>? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val resolvedPresets = presets ?: rememberDefaultProfilePresets()
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
                items(resolvedPresets.size) { i ->
                    val p = resolvedPresets[i]
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
