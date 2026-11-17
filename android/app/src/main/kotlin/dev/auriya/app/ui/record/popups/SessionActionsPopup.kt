package dev.auriya.app.ui.record.popups

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard
import dev.auriya.app.ui.record.panes.copyMarkdownReport
import dev.auriya.app.ui.record.panes.exportSessionCsvDirect
import dev.auriya.app.ui.record.panes.shareSessionCsv

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionActionsPopup(
    show: Boolean,
    session: BenchmarkSession?,
    sheetState: SheetState,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show || session == null) return
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    title = stringResource(dev.auriya.app.R.string.record_session_actions),
                    subtitle = stringResource(dev.auriya.app.R.string.record_session_actions_sub),
                )
            }

            // 1. Export CSV directly to Download/auriya (No popup share sheet)
            item {
                RichSelectionCard(
                    title = stringResource(dev.auriya.app.R.string.record_save_csv),
                    subtitle = stringResource(dev.auriya.app.R.string.record_save_csv_sub),
                    description = stringResource(dev.auriya.app.R.string.record_save_csv_desc),
                    icon = Icons.Outlined.TableChart,
                    selected = false,
                    onClick = {
                        exportSessionCsvDirect(context, session)
                        onDismiss()
                    },
                )
            }

            // 2. Share CSV File via App Chooser (Optional sharing)
            item {
                RichSelectionCard(
                    title = stringResource(dev.auriya.app.R.string.record_share_csv),
                    subtitle =stringResource(dev.auriya.app.R.string.record_share_csv_sub),
                    description = stringResource(dev.auriya.app.R.string.record_share_csv_desc),
                    icon = Icons.Outlined.Share,
                    selected = false,
                    onClick = {
                        shareSessionCsv(context, session)
                        onDismiss()
                    },
                )
            }

            // 3. Copy Markdown Summary
            item {
                RichSelectionCard(
                    title = stringResource(dev.auriya.app.R.string.record_copy_markdown),
                    subtitle = stringResource(dev.auriya.app.R.string.record_copy_markdown_sub),
                    description = stringResource(dev.auriya.app.R.string.record_copy_markdown_desc),
                    icon = Icons.Outlined.ContentCopy,
                    selected = false,
                    onClick = {
                        copyMarkdownReport(context, session)
                        onDismiss()
                    },
                )
            }

            // 4. Delete Session
            item {
                RichSelectionCard(
                    title = stringResource(dev.auriya.app.R.string.record_delete_session),
                    subtitle = stringResource(dev.auriya.app.R.string.record_delete_session_sub),
                    description = stringResource(dev.auriya.app.R.string.record_delete_session_desc),
                    icon = Icons.Outlined.DeleteOutline,
                    selected = false,
                    onClick = {
                        onDeleteRequest()
                        onDismiss()
                    },
                )
            }
        }
    }
}
