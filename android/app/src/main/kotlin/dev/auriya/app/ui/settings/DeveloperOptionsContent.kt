package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.components.ActionSettingItem
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.InfoSettingItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.viewmodel.UiViewModel

@Composable
fun DeveloperOptionsContent(
    viewModel: UiViewModel,
    onResetOobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)

    // In-memory StateFlows scoped to viewModelScope: collectAsState avoids Lifecycle observer overhead.
    val hasRoot by viewModel.hasRoot.collectAsState()
    val daemonActive by viewModel.daemonActive.collectAsState()
    val debugMode by viewModel.debugLogsEnabled.collectAsState()
    val isExportingLogs by viewModel.isExportingLogs.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.exportLogsResult.collect { result ->
            val msg =
                result.fold(
                    onSuccess = { "Logs exported to Downloads/AuriyaLogs.tar.gz" },
                    onFailure = { err ->
                        "Export failed: ${err.message ?: "check root grant"}"
                    },
                )
            Toast.makeText(currentContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    val onRestartDaemon =
        remember(viewModel, currentContext) {
            {
                viewModel.restartDaemon()
                Toast.makeText(currentContext, "Restarting Auriya daemon...", Toast.LENGTH_SHORT).show()
            }
        }
    val onToggleDebugLogs: (Boolean) -> Unit =
        remember(viewModel, currentContext) {
            { enabled ->
                viewModel.setDebugLogs(enabled)
                Toast
                    .makeText(
                        currentContext,
                        "Debug logs ${if (enabled) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    val onResetClick =
        remember(onResetOobe, currentContext) {
            {
                onResetOobe()
                Toast.makeText(currentContext, "OOBE State reset. Showing setup...", Toast.LENGTH_SHORT).show()
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DaemonOperationsSection(
            debugMode = debugMode,
            isExportingLogs = isExportingLogs,
            onRestartDaemon = onRestartDaemon,
            onToggleDebugLogs = onToggleDebugLogs,
            onExportLogs = viewModel::exportLogs,
        )

        PrivilegesDiagnosticsSection(
            hasRoot = hasRoot,
            daemonActive = daemonActive,
        )

        ApplicationStateSection(
            onResetOobe = onResetClick,
        )
    }
}

@Composable
private fun DaemonOperationsSection(
    debugMode: Boolean,
    isExportingLogs: Boolean,
    onRestartDaemon: () -> Unit,
    onToggleDebugLogs: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
) {
    SettingsSubsection(
        title = stringResource(R.string.dev_daemon_ops),
    ) {
        val totalOps = 3

        ActionSettingItem(
            title = stringResource(R.string.dev_restart_daemon),
            subtitle = stringResource(R.string.dev_restart_daemon_desc),
            actionText = stringResource(R.string.common_restart),
            onAction = onRestartDaemon,
            icon = Icons.Rounded.Refresh,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            actionContainerColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.onPrimary,
            shape = itemShapeFor(0, totalOps),
        )

        SwitchSettingItem(
            title = stringResource(R.string.dev_debug_logs),
            subtitle = stringResource(R.string.dev_debug_logs_desc),
            checked = debugMode,
            onCheckedChange = onToggleDebugLogs,
            icon = Icons.Rounded.BugReport,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = itemShapeFor(1, totalOps),
        )

        ClickableSettingItem(
            title = stringResource(R.string.dev_export_logs),
            subtitle =
                if (isExportingLogs) {
                    "Collecting daemon, KernelSU, kernel, and system logs..."
                } else {
                    stringResource(R.string.dev_export_logs_desc)
                },
            onClick = onExportLogs,
            enabled = !isExportingLogs,
            icon = Icons.Rounded.UploadFile,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = itemShapeFor(2, totalOps),
        )
    }
}

@Composable
private fun PrivilegesDiagnosticsSection(
    hasRoot: Boolean,
    daemonActive: Boolean,
) {
    SettingsSubsection(
        title = stringResource(R.string.dev_root_diagnostics),
    ) {
        val totalDiag = 2
        val colorScheme = MaterialTheme.colorScheme

        InfoSettingItem(
            title = stringResource(R.string.dev_root_verified),
            subtitle = if (hasRoot) "Privileged su access active" else "Non-root restricted execution",
            valueText = if (hasRoot) stringResource(R.string.common_active) else "DENIED",
            valueBadge = true,
            valueContainerColor = if (hasRoot) colorScheme.primaryContainer else colorScheme.errorContainer,
            valueContentColor = if (hasRoot) colorScheme.onPrimaryContainer else colorScheme.onErrorContainer,
            icon = Icons.Rounded.Shield,
            iconContainerColor = if (hasRoot) colorScheme.primaryContainer else colorScheme.errorContainer,
            iconTint = if (hasRoot) colorScheme.onPrimaryContainer else colorScheme.onErrorContainer,
            shape = itemShapeFor(0, totalDiag),
        )

        InfoSettingItem(
            title = stringResource(R.string.dev_daemon_status),
            subtitle = stringResource(R.string.dev_daemon_status_sub),
            valueText = if (daemonActive) "WORKING" else "STOPPED",
            valueBadge = true,
            valueContainerColor = if (daemonActive) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest,
            valueContentColor = if (daemonActive) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
            icon = Icons.Rounded.Info,
            iconContainerColor = colorScheme.primaryContainer,
            iconTint = colorScheme.onPrimaryContainer,
            shape = itemShapeFor(1, totalDiag),
        )
    }
}

@Composable
private fun ApplicationStateSection(
    onResetOobe: () -> Unit,
) {
    SettingsSubsection(
        title = stringResource(R.string.dev_sec_app_state),
    ) {
        ActionSettingItem(
            title = stringResource(R.string.dev_reset_oobe),
            subtitle = stringResource(R.string.dev_reset_oobe_desc),
            actionText = stringResource(R.string.common_reset),
            onAction = onResetOobe,
            icon = Icons.Rounded.RestartAlt,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            actionContainerColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.onPrimary,
            shape = itemShapeFor(0, 1),
        )
    }
}
