package dev.auriya.app.ui.config.popups

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.auriya.app.R
import dev.auriya.app.ui.components.ProfilePickerBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerPopup(
    defaultMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ProfilePickerBottomSheet(
        selectedProfile = defaultMode,
        onSelect = onSelect,
        onDismiss = onDismiss,
        title = stringResource(R.string.config_default_profile),
        subtitle = "${stringResource(R.string.common_active)}: ${defaultMode.replaceFirstChar { it.uppercase() }}",
    )
}
