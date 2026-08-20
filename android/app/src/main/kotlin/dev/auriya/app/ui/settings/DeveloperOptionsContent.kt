package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.RootShell
import dev.auriya.app.ui.components.*
import dev.auriya.app.viewmodel.UiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DeveloperOptionsContent(
    viewModel: UiViewModel,
    onResetOobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val systemInfo by viewModel.systemInfo.collectAsState()
    var debugMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. DAEMON & OPERATIONS ---
        SettingsSubsection(title = "DAEMON & OPERATIONS") {
            val totalOps = 3

            ActionSettingItem(
                title = "Restart Tuner Daemon",
                subtitle = "Force restart background eBPF daemon",
                actionText = "Restart",
                onAction = {
                    viewModel.restartDaemon()
                    Toast.makeText(context, "Restarting Auriya daemon...", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Rounded.Refresh,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                actionContainerColor = MaterialTheme.colorScheme.errorContainer,
                actionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = itemShapeFor(0, totalOps),
            )

            SwitchSettingItem(
                title = "Debug Logs Mode",
                subtitle = "Increase log verbosity for troubleshooting",
                checked = debugMode,
                onCheckedChange = {
                    debugMode = it
                    coroutineScope.launch(Dispatchers.IO) {
                        val cmd = if (debugMode) "SETLOG DEBUG" else "SETLOG INFO"
                        RootShell.exec("echo \"$cmd\" | nc -U /dev/socket/auriya.sock")
                    }
                    Toast
                        .makeText(
                            context,
                            "Debug logs ${if (debugMode) "enabled" else "disabled"}",
                            Toast.LENGTH_SHORT,
                        ).show()
                },
                icon = Icons.Rounded.BugReport,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = itemShapeFor(1, totalOps),
            )

            ClickableSettingItem(
                title = "Export System Logs",
                subtitle = "Saves logs to Downloads/AuriyaLogs.tar.gz",
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val cmd =
                            """
                            mkdir -p /sdcard/Download/AuriyaLogs &&
                            cp /data/adb/auriya/daemon.log /sdcard/Download/AuriyaLogs/auriya.log 2>/dev/null;
                            dmesg > /sdcard/Download/AuriyaLogs/kernel.log 2>/dev/null;
                            tar -czf /sdcard/Download/AuriyaLogs.tar.gz -C /sdcard/Download AuriyaLogs
                            """.trimIndent()
                        val rc = RootShell.exec(cmd)
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    if (rc == 0) {
                                        "Logs exported to Downloads/AuriyaLogs.tar.gz"
                                    } else {
                                        "Export failed (rc=$rc); check root grant"
                                    },
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                },
                icon = Icons.Rounded.UploadFile,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = itemShapeFor(2, totalOps),
            )
        }

        // --- 2. PRIVILEGES & DIAGNOSTICS ---
        SettingsSubsection(title = "PRIVILEGES & DIAGNOSTICS") {
            val totalDiag = 2

            InfoSettingItem(
                title = "Root Verified",
                subtitle = if (hasRoot) "Privileged su access active" else "Non-root restricted execution",
                valueText = if (hasRoot) "ACTIVE" else "DENIED",
                valueBadge = true,
                valueContainerColor = if (hasRoot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                valueContentColor = if (hasRoot) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                icon = Icons.Rounded.Shield,
                iconContainerColor = if (hasRoot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                iconTint = if (hasRoot) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                shape = itemShapeFor(0, totalDiag),
            )

            InfoSettingItem(
                title = "Daemon Status",
                subtitle = "Current active daemon state",
                valueText = systemInfo.daemonStatus.uppercase(),
                valueBadge = true,
                valueContainerColor =
                    if (systemInfo.daemonStatus ==
                        "working"
                    ) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                valueContentColor =
                    if (systemInfo.daemonStatus ==
                        "working"
                    ) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                icon = Icons.Rounded.Info,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = itemShapeFor(1, totalDiag),
            )
        }

        // --- 3. APPLICATION STATE ---
        SettingsSubsection(title = "APPLICATION STATE") {
            ActionSettingItem(
                title = "Reset Setup Wizard",
                subtitle = "Re-enable OOBE setup flow next launch",
                actionText = "Reset",
                onAction = {
                    onResetOobe()
                    Toast.makeText(context, "OOBE State reset. Showing setup...", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Rounded.RestartAlt,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                actionContainerColor = MaterialTheme.colorScheme.errorContainer,
                actionContentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = itemShapeFor(0, 1),
            )
        }
    }
}
