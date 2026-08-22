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
                            .stringResource(dev.auriya.app.R.string.record_session_actions),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_session_actions_sub),
                )
            }

            // 1. Export CSV directly to Download/auriya (No popup share sheet)
            item {
                RichSelectionCard(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_save_csv),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_save_csv_sub),
                    description = "Generates and saves the full second-by-second CSV dataset directly to your Download/auriya directory without opening share dialogs.",
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
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_share_csv),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_share_csv_sub),
                    description = "Open Android's sharing sheet to send the CSV file directly to Google Sheets, Microsoft Excel, Drive, or messaging apps.",
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
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_copy_markdown),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_copy_markdown_sub),
                    description = "Formats benchmark stats into clean Markdown text and copies it to your clipboard for quick sharing in chats and forums.",
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
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_delete_session),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.record_delete_session_sub),
                    description = "Permanently remove this recording file from your device local storage.",
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
