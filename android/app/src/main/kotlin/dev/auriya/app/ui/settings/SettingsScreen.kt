package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.components.*
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsSubScreen {
    NONE,
    APP,
    APPEARANCE,
    FLOATING_OVERLAY,
    DEVELOPER_OPTIONS,
}

@Composable
fun SettingsScreen(
    viewModel: UiViewModel,
    themePrefs: ThemePrefs?,
    onSeedChange: (Int) -> Unit,
    onDynamicToggle: (Boolean) -> Unit,
    onNavModeChange: (NavMode) -> Unit,
    onNavTypeChange: (NavType) -> Unit,
    onCornerRadiusChange: (Int) -> Unit,
    onDarkModeChange: (DarkThemeMode) -> Unit,
    onAmoledToggle: (Boolean) -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetOobe: () -> Unit,
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

    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.NONE) }

    androidx.activity.compose.BackHandler(enabled = activeSubScreen != SettingsSubScreen.NONE) {
        activeSubScreen = SettingsSubScreen.NONE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuriyaTokens.padding.normal),
    ) {
        if (activeSubScreen != SettingsSubScreen.NONE) {
            val title = when (activeSubScreen) {
                SettingsSubScreen.APP -> "App Settings"
                SettingsSubScreen.APPEARANCE -> "Appearance"
                SettingsSubScreen.FLOATING_OVERLAY -> "Floating Overlay"
                SettingsSubScreen.DEVELOPER_OPTIONS -> "Developer Options"
                else -> ""
            }
            SubScreenHeader(title = title, onBack = { activeSubScreen = SettingsSubScreen.NONE })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
            contentPadding = PaddingValues(top = AuriyaTokens.padding.smaller, bottom = 80.dp),
        ) {
            when (activeSubScreen) {
                SettingsSubScreen.NONE -> {
                    item {
                        Column(modifier = Modifier.padding(vertical = AuriyaTokens.padding.small)) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "Manage profiles, performance tuning, appearance, and monitoring options",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Build,
                            title = "App",
                            subtitle = "General application and performance settings",
                            onClick = { activeSubScreen = SettingsSubScreen.APP },
                        )
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Palette,
                            title = "Appearance",
                            subtitle = "Theme, seed colors, and navigation style",
                            onClick = { activeSubScreen = SettingsSubScreen.APPEARANCE },
                        )
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Layers,
                            title = "Floating Overlay",
                            subtitle = "Global system monitor floating overlay settings",
                            onClick = { activeSubScreen = SettingsSubScreen.FLOATING_OVERLAY },
                        )
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Info,
                            title = "About",
                            subtitle = "Developer information and project specs",
                            onClick = onNavigateToAbout,
                        )
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Code,
                            title = "Developer Options",
                            subtitle = "App reset, diagnostics, and debugging tools",
                            onClick = { activeSubScreen = SettingsSubScreen.DEVELOPER_OPTIONS },
                        )
                    }
                }

                SettingsSubScreen.APP -> {
                    item {
                        SectionCard(title = "Performance Tuning") {
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
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 1.dp,
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
                                        },
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SectionCard(title = "Language Options") {
                            SettingRow(
                                icon = Icons.Filled.Translate,
                                title = "App Language",
                                subtitle = "English (System Default)",
                                onClick = onNavigateToLanguage,
                            )
                        }
                    }

                    item {
                        SectionCard(title = "System & Operations") {
                            SettingRow(
                                icon = Icons.AutoMirrored.Filled.Send,
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
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 1.dp,
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
                                            Toast.makeText(
                                                context,
                                                "Debug logs ${if (debugMode) "enabled" else "disabled"}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                thickness = 1.dp,
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
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(AuriyaTokens.rounding.full),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ),
                                        contentPadding = PaddingValues(horizontal = AuriyaTokens.padding.normal, vertical = 6.dp),
                                        modifier = Modifier.wrapContentSize(),
                                    ) {
                                        Text(
                                            text = "Restart",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                SettingsSubScreen.APPEARANCE -> {
                    item {
                        ThemePickerCard(
                            seedColor = themePrefs?.seedColor ?: 0xFFFFB68E.toInt(),
                            useDynamicColor = themePrefs?.useDynamicColor ?: true,
                            navMode = themePrefs?.navMode ?: NavMode.STANDARD,
                            navType = themePrefs?.navType ?: NavType.LEGACY,
                            cornerRadius = themePrefs?.cornerRadius ?: 24,
                            darkThemeMode = themePrefs?.darkThemeMode ?: DarkThemeMode.FOLLOW_SYSTEM,
                            isAmoled = themePrefs?.isAmoled ?: false,
                            onSeedChange = onSeedChange,
                            onDynamicToggle = onDynamicToggle,
                            onNavModeChange = onNavModeChange,
                            onNavTypeChange = onNavTypeChange,
                            onCornerRadiusChange = onCornerRadiusChange,
                            onDarkModeChange = onDarkModeChange,
                            onAmoledToggle = onAmoledToggle,
                        )
                    }
                }

                SettingsSubScreen.FLOATING_OVERLAY -> {
                    item {
                        FloatingOverlayContent()
                    }
                }

                SettingsSubScreen.DEVELOPER_OPTIONS -> {
                    item {
                        DeveloperOptionsContent(
                            viewModel = viewModel,
                            onResetOobe = onResetOobe
                        )
                    }
                }
            }
        }
    }
}

private fun saveSettingsChange(
    viewModel: UiViewModel,
    settings: Settings,
    defaultGov: String,
    globalPreset: String,
) {
    val updated = settings.copy(
        cpu = settings.cpu.copy(defaultGovernor = defaultGov),
        daemon = settings.daemon.copy(defaultMode = globalPreset),
    )
    viewModel.saveSettings(updated)
}
