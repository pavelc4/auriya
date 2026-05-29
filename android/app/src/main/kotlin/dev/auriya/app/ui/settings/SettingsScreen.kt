package dev.auriya.app.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: UiViewModel,
    themePrefs: ThemePrefs?,
    onSeedChange: (Int) -> Unit,
    onDynamicToggle: (Boolean) -> Unit,
    onNavModeChange: (NavMode) -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var defaultGov by remember(settings) { mutableStateOf(settings.cpu.defaultGovernor) }
    var globalPreset by remember(settings) { mutableStateOf(settings.daemon.defaultMode) }
    var debugMode by remember { mutableStateOf(false) }

    val availableGovernors = remember {
        try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
            if (file.exists()) {
                file.readText().split(Regex("\\s+")).filter { it.isNotEmpty() }
            } else {
                listOf("schedutil", "performance", "powersave")
            }
        } catch (e: Exception) {
            listOf("schedutil", "performance", "powersave")
        }
    }

    val availablePresets = listOf("powersave", "balance", "performance")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuriyaTokens.padding.normal),
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
        contentPadding = PaddingValues(top = AuriyaTokens.padding.normal, bottom = 80.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = AuriyaTokens.padding.small)) {
                Text(
                    text = "Global Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure global parameters, tuner modes, and system operations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ThemePickerCard(
                seedColor = themePrefs?.seedColor ?: 0xFFFFB68E.toInt(),
                useDynamicColor = themePrefs?.useDynamicColor ?: true,
                navMode = themePrefs?.navMode ?: NavMode.STANDARD,
                onSeedChange = onSeedChange,
                onDynamicToggle = onDynamicToggle,
                onNavModeChange = onNavModeChange,
            )
        }

        item {
            SectionCard(title = "Performance") {
                SettingRow(
                    icon = Icons.Filled.Settings,
                    title = "CPU Governor",
                    subtitle = "Global CPU scaling governor",
                    control = {
                        SettingsDropdown(
                            value = defaultGov,
                            options = availableGovernors,
                            onValueChange = {
                                defaultGov = it
                                saveSettingsChange(viewModel, settings, defaultGov, globalPreset)
                                Toast.makeText(context, "Governor set to $it", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                SettingRow(
                    icon = Icons.Filled.Star,
                    title = "Global Preset",
                    subtitle = "Default performance profile when idle",
                    control = {
                        SettingsDropdown(
                            value = globalPreset,
                            options = availablePresets,
                            onValueChange = {
                                globalPreset = it
                                saveSettingsChange(viewModel, settings, defaultGov, globalPreset)
                                Toast.makeText(context, "Preset set to $it", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }
        }

        item {
            SectionCard(title = "Language") {
                SettingRow(
                    icon = Icons.Filled.Translate,
                    title = "App Language",
                    subtitle = "English (System Default)",
                    onClick = onNavigateToLanguage
                )
            }
        }

        item {
            SectionCard(title = "System") {
                SettingRow(
                    icon = Icons.Filled.Send,
                    title = "Export System Logs",
                    subtitle = "Saves logs to Download/AuriyaLogs.tar.gz",
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val cmd = """
                                mkdir -p /sdcard/Download/AuriyaLogs &&
                                cp /data/adb/auriya/daemon.log /sdcard/Download/AuriyaLogs/auriya.log 2>/dev/null;
                                dmesg > /sdcard/Download/AuriyaLogs/kernel.log 2>/dev/null;
                                tar -czf /sdcard/Download/AuriyaLogs.tar.gz -C /sdcard/Download AuriyaLogs
                            """.trimIndent()
                            val rc = RootShell.exec(cmd)
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (rc == 0) "Logs exported to Downloads/AuriyaLogs.tar.gz"
                                    else "Export failed (rc=$rc); check root grant",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                DndAccessRow()
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                SettingRow(
                    icon = Icons.Filled.BugReport,
                    title = "Debug Logs Mode",
                    subtitle = "Increase log verbosity for troubleshooting",
                    control = {
                        Switch(
                            checked = debugMode,
                            onCheckedChange = {
                                debugMode = it
                                coroutineScope.launch(Dispatchers.IO) {
                                    val cmd = if (debugMode) "SETLOG DEBUG" else "SETLOG INFO"
                                    RootShell.exec("echo \"$cmd\" | nc -U /dev/socket/auriya.sock")
                                }
                                Toast.makeText(context, "Debug logs ${if (debugMode) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                            },
                        )
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                SettingRow(
                    icon = Icons.Filled.Refresh,
                    title = "Restart Tuner Daemon",
                    subtitle = "Force restart background eBPF daemon",
                    control = {
                        Button(
                            onClick = {
                                viewModel.restartDaemon()
                                Toast.makeText(context, "Restarting Auriya daemon...", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = AuriyaTokens.padding.normal, vertical = 6.dp),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(
                                text = "Restart",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }

        item {
            SectionCard(title = "About") {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = "About Auriya",
                    subtitle = "Version, owner, and contributors",
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AuriyaTokens.padding.smaller)
            )
            content()
        }
    }
}

@Composable
fun SettingsDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            contentPadding = PaddingValues(horizontal = AuriyaTokens.padding.normal, vertical = 6.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest)
            ) {
                Text(
                    text = value.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(AuriyaTokens.rounding.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuriyaTokens.iconSize.medium)
            )
        }

        Spacer(modifier = Modifier.width(AuriyaTokens.padding.normal))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if (control != null) {
            Spacer(modifier = Modifier.width(AuriyaTokens.padding.small))
            control()
        } else if (onClick != null) {
            Spacer(modifier = Modifier.width(AuriyaTokens.padding.small))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DndAccessRow() {
    val context = LocalContext.current
    val nm = remember { context.getSystemService(NotificationManager::class.java) }
    val isGranted = remember { nm?.isNotificationPolicyAccessGranted ?: false }

    SettingRow(
        icon = Icons.Filled.DoNotDisturbOn,
        title = "Do Not Disturb Access",
        subtitle = if (isGranted) "Granted — companion can set game-mode DnD"
                   else "Required for auto DnD during gaming",
        control = {
            if (isGranted) {
                Text(
                    text = "GRANTED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AuriyaTokens.rounding.full))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            } else {
                Button(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                ) {
                    Text(
                        text = "GRANT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}

private fun saveSettingsChange(
    viewModel: UiViewModel,
    settings: Settings,
    defaultGov: String,
    globalPreset: String
) {
    val updated = settings.copy(
        cpu = settings.cpu.copy(defaultGovernor = defaultGov),
        daemon = settings.daemon.copy(defaultMode = globalPreset)
    )
    viewModel.saveSettings(updated)
}
