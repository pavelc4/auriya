package dev.auriya.app.ui.config.popups

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard
import dev.auriya.shared.config.TomlParser
import dev.auriya.shared.model.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigActionsPopup(
    show: Boolean,
    settings: Settings,
    sheetState: SheetState,
    onImportRequest: () -> Unit,
    onResetRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current

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
                    title = "Config Actions",
                    subtitle = "Backup, import, or reset settings.toml",
                )
            }

            item {
                RichSelectionCard(
                    title = "Export Configuration",
                    subtitle = "Share & Backup TOML",
                    description = "Export and share your active settings.toml to device storage, cloud, or messaging apps.",
                    icon = Icons.Outlined.Share,
                    selected = false,
                    onClick = {
                        val content = TomlParser.serializeSettings(settings)
                        val sendIntent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, content)
                                putExtra(Intent.EXTRA_TITLE, "settings.toml")
                                type = "text/plain"
                            }
                        context.startActivity(Intent.createChooser(sendIntent, "Export settings.toml"))
                        onDismiss()
                    },
                )
            }

            item {
                RichSelectionCard(
                    title = "Import Configuration",
                    subtitle = "Load from File",
                    description = "Select a valid settings.toml file from your phone storage to import and apply tuning immediately.",
                    icon = Icons.Outlined.FileUpload,
                    selected = false,
                    onClick = {
                        onImportRequest()
                        onDismiss()
                    },
                )
            }

            item {
                RichSelectionCard(
                    title = "Reset to Default",
                    subtitle = "Restore Factory Preset",
                    description = "Revert all daemon health intervals, CPU scaling governors, FAS thresholds, and profile margins back to default factory values.",
                    icon = Icons.Outlined.RestartAlt,
                    selected = false,
                    onClick = {
                        onResetRequest()
                        onDismiss()
                    },
                )
            }
        }
    }
}
