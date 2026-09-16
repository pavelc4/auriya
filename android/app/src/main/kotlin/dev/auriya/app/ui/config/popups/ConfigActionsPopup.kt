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
    settings: Settings,
    onImportRequest: () -> Unit,
    onResetRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                            .stringResource(dev.auriya.app.R.string.config_actions_title),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_actions_subtitle),
                )
            }

            item {
                RichSelectionCard(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_export),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_export_sub),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_export_desc),
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
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_import),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_import_sub),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_import_desc),
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
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_reset_factory),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_reset_factory_sub),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_reset_factory_desc),
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
