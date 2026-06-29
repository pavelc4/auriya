package dev.auriya.app.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import dev.auriya.app.data.NavType
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.ThemePrefs
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

    val availableGovernors =
        remember {
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
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = AuriyaTokens.padding.normal),
    ) {
        if (activeSubScreen != SettingsSubScreen.NONE) {
            val title =
                when (activeSubScreen) {
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
                                            Toast
                                                .makeText(
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
                                        shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                                        colors =
                                            ButtonDefaults.buttonColors(
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
                        FloatingOverlayContent(context)
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

@Composable
private fun DeveloperOptionsContent(
    viewModel: UiViewModel,
    onResetOobe: () -> Unit
) {
    val context = LocalContext.current
    val hasRoot by viewModel.hasRoot.collectAsState()
    val systemInfo by viewModel.systemInfo.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
    ) {
        SectionCard(title = "Application State") {
            SettingRow(
                icon = Icons.Filled.Refresh,
                title = "Reset Setup Wizard",
                subtitle = "Re-enable OOBE setup flow next launch",
                control = {
                    Button(
                        onClick = {
                            onResetOobe()
                            Toast.makeText(context, "OOBE State reset. Showing setup...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(AuriyaTokens.rounding.medium)
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        SectionCard(title = "Privileges & Diagnostics") {
            SettingRow(
                icon = Icons.Filled.Shield,
                title = "Root Verified",
                subtitle = if (hasRoot) "Privileged su access active" else "Non-root restricted execution",
                control = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuriyaTokens.rounding.small))
                            .background(
                                if (hasRoot) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasRoot) "ACTIVE" else "DENIED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRoot) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )

            SettingRow(
                icon = Icons.Filled.Info,
                title = "Daemon Status",
                subtitle = "Current active daemon state",
                control = {
                    Text(
                        text = systemInfo.daemonStatus.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (systemInfo.daemonStatus == "working") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@Composable
private fun FloatingOverlayContent(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("auriya_overlay", Context.MODE_PRIVATE) }
    var enableOverlay by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var showFps by remember { mutableStateOf(prefs.getBoolean("show_fps", true)) }
    var showCpu by remember { mutableStateOf(prefs.getBoolean("show_cpu", true)) }
    var showGpu by remember { mutableStateOf(prefs.getBoolean("show_gpu", true)) }
    var showTemp by remember { mutableStateOf(prefs.getBoolean("show_temp", true)) }
    var showBattery by remember { mutableStateOf(prefs.getBoolean("show_battery", true)) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    
    var layoutStyle by remember { mutableStateOf(prefs.getString("layout_style", "Horizontal") ?: "Horizontal") }
    var updateIntervalMs by remember { mutableStateOf(prefs.getLong("update_interval_ms", 1000L)) }
    
    var textSizeSp by remember { mutableStateOf(prefs.getFloat("text_size_sp", 12f)) }
    var bgOpacity by remember { mutableStateOf(prefs.getFloat("bg_opacity", 0.7f)) }
    var paddingDp by remember { mutableStateOf(prefs.getFloat("padding_dp", 12f)) }
    var cornerRadiusDp by remember { mutableStateOf(prefs.getFloat("corner_radius_dp", 16f)) }

    val hasOverlayPermission =
        remember {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

    LaunchedEffect(enableOverlay) {
        if (enableOverlay) {
            if (!hasOverlayPermission) {
                val intent =
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                prefs.edit().putBoolean("enabled", true).apply()
                context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
            }
        } else {
            prefs.edit().putBoolean("enabled", false).apply()
            context.stopService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
        }
    }

    LaunchedEffect(hasOverlayPermission) {
        if (hasOverlayPermission && enableOverlay) {
            context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)) {
        SectionCard(title = "Overlay Activation") {
            SettingRow(
                icon = Icons.Filled.Layers,
                title = "Show Floating Overlay",
                subtitle =
                    if (hasOverlayPermission) {
                        "Granted — overlay can display on top of apps"
                    } else {
                        "Tap to grant overlay permission"
                    },
                control = {
                    Switch(
                        checked = enableOverlay,
                        onCheckedChange = { enableOverlay = it },
                    )
                },
            )
        }
        
        SectionCard(title = "Telemetry Metrics") {
            SettingRow(
                icon = Icons.Filled.Speed,
                title = "Show FPS Counter",
                subtitle = "Display active frame rate monitoring",
                control = {
                    Switch(
                        checked = showFps,
                        onCheckedChange = {
                            showFps = it
                            prefs.edit().putBoolean("show_fps", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.Memory,
                title = "Show CPU Clusters",
                subtitle = "Monitor GHz speeds for Little, Medium, Big cores",
                control = {
                    Switch(
                        checked = showCpu,
                        onCheckedChange = {
                            showCpu = it
                            prefs.edit().putBoolean("show_cpu", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.DeveloperBoard,
                title = "Show GPU Metrics",
                subtitle = "Display GPU frequency & load percentage",
                control = {
                    Switch(
                        checked = showGpu,
                        onCheckedChange = {
                            showGpu = it
                            prefs.edit().putBoolean("show_gpu", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.Thermostat,
                title = "Show CPU Temperature",
                subtitle = "Monitor core thermal metrics in real-time",
                control = {
                    Switch(
                        checked = showTemp,
                        onCheckedChange = {
                            showTemp = it
                            prefs.edit().putBoolean("show_temp", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Show Battery Temperature",
                subtitle = "Display current battery thermal metrics",
                control = {
                    Switch(
                        checked = showBattery,
                        onCheckedChange = {
                            showBattery = it
                            prefs.edit().putBoolean("show_battery", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
        }

        SectionCard(title = "Visual Customization & Layout") {
            SettingRow(
                icon = Icons.Filled.ColorLens,
                title = "Use Monet Theme Colors",
                subtitle = "Match overlay colors with device style",
                control = {
                    Switch(
                        checked = monetEnabled,
                        onCheckedChange = {
                            monetEnabled = it
                            prefs.edit().putBoolean("monet_enabled", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.MenuOpen,
                title = "Layout Orientation",
                subtitle = "Grid orientation of telemetry elements",
                control = {
                    SettingsDropdown(
                        value = layoutStyle,
                        options = listOf("Horizontal", "Vertical"),
                        onValueChange = {
                            layoutStyle = it
                            prefs.edit().putString("layout_style", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )
            SettingRow(
                icon = Icons.Filled.HourglassEmpty,
                title = "Refresh Interval",
                subtitle = "Select query telemetry speed",
                control = {
                    val currentText = when (updateIntervalMs) {
                        500L -> "500ms (Fast)"
                        1000L -> "1s (Balanced)"
                        2000L -> "2s (Battery Save)"
                        5000L -> "5s (Minimal)"
                        else -> "1s"
                    }
                    SettingsDropdown(
                        value = currentText,
                        options = listOf("500ms (Fast)", "1s (Balanced)", "2s (Battery Save)", "5s (Minimal)"),
                        onValueChange = { selected ->
                            val value = when (selected) {
                                "500ms (Fast)" -> 500L
                                "1s (Balanced)" -> 1000L
                                "2s (Battery Save)" -> 2000L
                                "5s (Minimal)" -> 5000L
                                else -> 1000L
                            }
                            updateIntervalMs = value
                            prefs.edit().putLong("update_interval_ms", value).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
        }

        // Standalone RV-style Slider Cards
        SettingsSliderCard(
            title = "Text Size",
            description = "Scale of the floating overlay text",
            icon = Icons.Filled.TextFields,
            value = textSizeSp,
            onValueChange = {
                textSizeSp = it
                prefs.edit().putFloat("text_size_sp", it).apply()
            },
            onValueChangeFinished = { restartOverlay(context) },
            valueRange = 8f..20f,
            displayValueFormatter = { "${it.toInt()} sp" },
            valueLabel = "Font Size",
            steps = 11
        )
        
        SettingsSliderCard(
            title = "Background Opacity",
            description = "Opacity level of the backing block",
            icon = Icons.Filled.Opacity,
            value = bgOpacity,
            onValueChange = {
                bgOpacity = it
                prefs.edit().putFloat("bg_opacity", it).apply()
            },
            onValueChangeFinished = { restartOverlay(context) },
            valueRange = 0f..1f,
            displayValueFormatter = { "${(it * 100).toInt()}%" },
            valueLabel = "Opacity Level"
        )

        SettingsSliderCard(
            title = "Container Padding",
            description = "Thickness of internal margins",
            icon = Icons.Filled.AspectRatio,
            value = paddingDp,
            onValueChange = {
                paddingDp = it
                prefs.edit().putFloat("padding_dp", it).apply()
            },
            onValueChangeFinished = { restartOverlay(context) },
            valueRange = 4f..24f,
            displayValueFormatter = { "${it.toInt()} dp" },
            valueLabel = "Internal Margin"
        )

        SettingsSliderCard(
            title = "Corner Radius",
            description = "Rounding index of the overlay",
            icon = Icons.Filled.RoundedCorner,
            value = cornerRadiusDp,
            onValueChange = {
                cornerRadiusDp = it
                prefs.edit().putFloat("corner_radius_dp", it).apply()
            },
            onValueChangeFinished = { restartOverlay(context) },
            valueRange = 0f..32f,
            displayValueFormatter = { "${it.toInt()} dp" },
            valueLabel = "Edge Rounding"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSliderCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValueFormatter: (Float) -> String,
    valueLabel: String = "Current Value",
    steps: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = displayValueFormatter(value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth(),
                    track = { _ ->
                        val fraction = if (valueRange.endInclusive > valueRange.start) {
                            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    },
                    thumb = {
                        Spacer(modifier = Modifier.size(0.dp))
                    }
                )
            }
        }
    }
}

private fun restartOverlay(context: android.content.Context) {
    val prefs = context.getSharedPreferences("auriya_overlay", android.content.Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean("enabled", false)
    val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)

    context.stopService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    if (enabled && hasPermission) {
        context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    }
}

@Composable
private fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AuriyaTokens.padding.normal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AuriyaTokens.rounding.medium))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(AuriyaTokens.iconSize.medium),
                )
            }

            Spacer(modifier = Modifier.width(AuriyaTokens.padding.normal))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.width(AuriyaTokens.padding.small))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(AuriyaTokens.padding.small))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AuriyaTokens.padding.smaller),
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
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = accentColor ?: MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.wrapContentSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AuriyaTokens.padding.small, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest),
            ) {
                Text(
                    text = value.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (accentColor !=
                            null
                        ) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint =
                        if (accentColor !=
                            null
                        ) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clip(RoundedCornerShape(AuriyaTokens.rounding.medium)),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
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
    control: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AuriyaTokens.rounding.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuriyaTokens.iconSize.medium),
            )
        }

        Spacer(modifier = Modifier.width(AuriyaTokens.padding.normal))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}



private fun saveSettingsChange(
    viewModel: UiViewModel,
    settings: Settings,
    defaultGov: String,
    globalPreset: String,
) {
    val updated =
        settings.copy(
            cpu = settings.cpu.copy(defaultGovernor = defaultGov),
            daemon = settings.daemon.copy(defaultMode = globalPreset),
        )
    viewModel.saveSettings(updated)
}
