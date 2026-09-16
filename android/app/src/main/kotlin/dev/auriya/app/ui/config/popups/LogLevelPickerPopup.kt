package dev.auriya.app.ui.config.popups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.config.components.BottomSheetHeader
import dev.auriya.app.ui.config.components.RichSelectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogLevelPickerPopup(
    logLevel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val levels =
        listOf(
            Triple("trace", "Verbose Trace Logging", "Logs every internal function call and micro-event. High verbosity for debugging."),
            Triple("debug", "Detailed Debug Output", "Logs frame statistics, socket commands, and kernel events."),
            Triple("info", "Standard Operational Info", "Logs game detection, profile transitions, and thermal warnings. (Recommended)"),
            Triple("warn", "Warnings Only", "Logs only potential issues or unexpected states."),
            Triple("error", "Critical Errors Only", "Logs only critical failures and exceptions."),
        )

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
                            .stringResource(dev.auriya.app.R.string.config_log_verbosity),
                    subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.common_active)}: ${logLevel.uppercase()}",
                )
            }

            items(levels.size) { index ->
                val (key, sub, desc) = levels[index]
                val isSelected = key.equals(logLevel, ignoreCase = true)
                val icon =
                    when (key) {
                        "trace" -> Icons.Outlined.BugReport
                        "debug" -> Icons.Outlined.Terminal
                        "info" -> Icons.Outlined.Info
                        "warn" -> Icons.Outlined.WarningAmber
                        "error" -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.Description
                    }

                RichSelectionCard(
                    title = key.uppercase(),
                    subtitle = sub,
                    description = desc,
                    icon = icon,
                    selected = isSelected,
                    onClick = { onSelect(key) },
                )
            }
        }
    }
}
