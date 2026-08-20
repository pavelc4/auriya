package dev.auriya.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.RootShell
import dev.auriya.app.ui.components.*
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ColorPreset(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
)

@Composable
fun FloatingOverlayContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auriya_overlay", Context.MODE_PRIVATE) }
    var enableOverlay by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var showFps by remember { mutableStateOf(prefs.getBoolean("show_fps", true)) }
    var showCpu by remember { mutableStateOf(prefs.getBoolean("show_cpu", true)) }
    var showGpu by remember { mutableStateOf(prefs.getBoolean("show_gpu", true)) }
    var showRam by remember { mutableStateOf(prefs.getBoolean("show_ram", true)) }
    var showTemp by remember { mutableStateOf(prefs.getBoolean("show_temp", true)) }
    var showBattery by remember { mutableStateOf(prefs.getBoolean("show_battery", true)) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }

    var overlayPreset by remember { mutableStateOf(prefs.getString("overlay_preset", "gaming") ?: "gaming") }

    var layoutStyle by remember { mutableStateOf(prefs.getString("layout_style", "Horizontal") ?: "Horizontal") }
    var overlayMode by remember { mutableStateOf(prefs.getString("overlay_mode", "Full") ?: "Full") }
    var cpuStyle by remember { mutableStateOf(prefs.getString("cpu_style", "tags") ?: "tags") }
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

    val colorPresets =
        remember {
            listOf(
                ColorPreset("gaming", "Cyber Neon", Color(0xFF00FF66), Color(0xFF00E5FF), Color(0xFFFFE600), Color(0xFF00FFA3)),
                ColorPreset("green_default", "Auriya Green", Color(0xFFAAD2A4), Color(0xFF385E38), Color(0xFF8A9A5B), Color(0xFFC2D5C6)),
                ColorPreset("ocean", "Ocean Blue", Color(0xFF0099FF), Color(0xFF00E5FF), Color(0xFF38B6FF), Color(0xFFB0E0E6)),
                ColorPreset("violet", "Hyper Violet", Color(0xFFB026FF), Color(0xFFD946EF), Color(0xFFFF007F), Color(0xFFE9D5FF)),
                ColorPreset("solar", "Solar Flare", Color(0xFFFF6600), Color(0xFFFF1A53), Color(0xFFFFB703), Color(0xFFFFD8A8)),
                ColorPreset("volt", "Volt Amber", Color(0xFFFFE600), Color(0xFFFFB703), Color(0xFFFF6600), Color(0xFFFFF3BF)),
                ColorPreset("monochrome", "Monochrome", Color(0xFFFFFFFF), Color(0xFFCCCCCC), Color(0xFF888888), Color(0xFF555555)),
                ColorPreset("sage", "Sage Pastel", Color(0xFFC2D5C6), Color(0xFF4A5D4E), Color(0xFF8FA393), Color(0xFFDDE7DF)),
                ColorPreset("rust", "Rust Flame", Color(0xFFFF6600), Color(0xFFFF1A53), Color(0xFF5C3A21), Color(0xFFE07A5F)),
            )
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

    var isGpuAvailable by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val out = RootShell.run("printf 'STATUS\nQUIT\n' | timeout 2 nc -U /dev/socket/auriya.sock 2>/dev/null")
            val gpuLine = out.lines().find { it.contains("GPU_FREQ=") }
            val hasGpuIpc =
                if (gpuLine != null) {
                    val vendor = gpuLine.split(" ").find { it.startsWith("GPU_VENDOR=") }?.removePrefix("GPU_VENDOR=")
                    val freq =
                        gpuLine
                            .split(" ")
                            .find { it.startsWith("GPU_FREQ=") }
                            ?.removePrefix("GPU_FREQ=")
                            ?.toIntOrNull()
                    (vendor != null && vendor != "None" && !vendor.contains("None")) || (freq != null && freq > 0)
                } else {
                    false
                }
            val sysfsGpu =
                java.io.File("/sys/class/kgsl/kgsl-3d0/gpuclk").exists() ||
                    java.io.File("/sys/class/kgsl/kgsl-3d0/clock_mhz").exists() ||
                    java.io.File("/sys/kernel/gpu/gpu_clock").exists() ||
                    java.io.File("/sys/kernel/ged/gpu/gpu_cur_freq").exists()
            isGpuAvailable = hasGpuIpc || sysfsGpu
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. OVERLAY ACTIVATION SUBSECTION ---
        SettingsSubsection(title = "OVERLAY ACTIVATION") {
            SwitchSettingItem(
                title = "Show Floating Overlay",
                subtitle =
                    if (hasOverlayPermission) {
                        "Display real-time telemetry HUD over running apps"
                    } else {
                        "Tap to grant system overlay permission"
                    },
                checked = enableOverlay,
                onCheckedChange = { enableOverlay = it },
                icon = Icons.Rounded.Layers,
                shape = itemShapeFor(0, 1),
            )
        }

        // --- 2. TELEMETRY METRICS SUBSECTION ---
        SettingsSubsection(title = "TELEMETRY METRICS") {
            val metricsList =
                buildList {
                    add("fps")
                    add("cpu")
                    if (isGpuAvailable) add("gpu")
                    add("ram")
                    add("temp")
                    add("battery")
                }
            val totalMetrics = metricsList.size

            SwitchSettingItem(
                title = "FPS Counter",
                subtitle = "Display active frame rate monitoring",
                checked = showFps,
                enabled = enableOverlay,
                onCheckedChange = {
                    showFps = it
                    prefs.edit().putBoolean("show_fps", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Speed,
                shape = itemShapeFor(metricsList.indexOf("fps"), totalMetrics),
            )

            SwitchSettingItem(
                title = "CPU Clusters",
                subtitle = "Monitor Little, Mid, Big core frequencies",
                checked = showCpu,
                enabled = enableOverlay,
                onCheckedChange = {
                    showCpu = it
                    prefs.edit().putBoolean("show_cpu", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Memory,
                shape = itemShapeFor(metricsList.indexOf("cpu"), totalMetrics),
            )

            if (isGpuAvailable) {
                SwitchSettingItem(
                    title = "GPU Metrics",
                    subtitle = "Display GPU frequency & load percentage",
                    checked = showGpu,
                    enabled = enableOverlay,
                    onCheckedChange = {
                        showGpu = it
                        prefs.edit().putBoolean("show_gpu", it).apply()
                        restartOverlay(context)
                    },
                    icon = Icons.Rounded.DeveloperBoard,
                    shape = itemShapeFor(metricsList.indexOf("gpu"), totalMetrics),
                )
            }

            SwitchSettingItem(
                title = "RAM Usage",
                subtitle = "Display active memory usage statistics",
                checked = showRam,
                enabled = enableOverlay,
                onCheckedChange = {
                    showRam = it
                    prefs.edit().putBoolean("show_ram", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.PieChart,
                shape = itemShapeFor(metricsList.indexOf("ram"), totalMetrics),
            )

            SwitchSettingItem(
                title = "CPU Temperature",
                subtitle = "Monitor core thermal metrics in real-time",
                checked = showTemp,
                enabled = enableOverlay,
                onCheckedChange = {
                    showTemp = it
                    prefs.edit().putBoolean("show_temp", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Thermostat,
                shape = itemShapeFor(metricsList.indexOf("temp"), totalMetrics),
            )

            SwitchSettingItem(
                title = "Battery Temperature",
                subtitle = "Display current battery thermal metrics",
                checked = showBattery,
                enabled = enableOverlay,
                onCheckedChange = {
                    showBattery = it
                    prefs.edit().putBoolean("show_battery", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.BatteryChargingFull,
                shape = itemShapeFor(metricsList.indexOf("battery"), totalMetrics),
            )
        }

        // --- 3. DISPLAY & LAYOUT FORMAT SUBSECTION ---
        SettingsSubsection(title = "DISPLAY & LAYOUT") {
            val totalLayoutItems = 3

            SegmentedSettingItem(
                title = "HUD Format Mode",
                subtitle = if (overlayMode == "Full") "With labels (e.g. 120 FPS)" else "Numbers only (e.g. 120.0)",
                icon = Icons.Rounded.Dashboard,
                items = listOf("Full Info", "Minimalist"),
                selectedIndex = if (overlayMode == "Full") 0 else 1,
                onItemSelected = {
                    overlayMode = if (it == 0) "Full" else "Minimal"
                    prefs.edit().putString("overlay_mode", overlayMode).apply()
                    restartOverlay(context)
                },
                shape = itemShapeFor(0, totalLayoutItems),
                enabled = enableOverlay,
            )

            SegmentedSettingItem(
                title = "CPU Metric Style",
                subtitle =
                    when (cpuStyle) {
                        "tags" -> "Cluster tags (e.g. L1.5 B1.8 P0.8)"
                        "load_peak" -> "Load % & Peak clock (e.g. 40% @ 1.8G)"
                        "pipe" -> "Pipe separator (e.g. 1.5 | 1.8 | 0.8G)"
                        else -> "Classic slash (e.g. 1.5/1.8/0.8 GHz)"
                    },
                icon = Icons.Rounded.Memory,
                items = listOf("Tags", "Overview", "Pipe", "Slash"),
                selectedIndex =
                    when (cpuStyle) {
                        "tags" -> 0
                        "load_peak" -> 1
                        "pipe" -> 2
                        else -> 3
                    },
                onItemSelected = {
                    cpuStyle =
                        when (it) {
                            0 -> "tags"
                            1 -> "load_peak"
                            2 -> "pipe"
                            else -> "slash"
                        }
                    prefs.edit().putString("cpu_style", cpuStyle).apply()
                    restartOverlay(context)
                },
                shape = itemShapeFor(1, totalLayoutItems),
                enabled = enableOverlay,
            )

            SegmentedSettingItem(
                title = "Layout Orientation",
                subtitle = if (layoutStyle == "Horizontal") "Horizontal wide floating bar" else "Vertical compact stack",
                icon = Icons.Rounded.ViewAgenda,
                items = listOf("Horizontal", "Vertical"),
                selectedIndex = if (layoutStyle == "Horizontal") 0 else 1,
                onItemSelected = {
                    layoutStyle = if (it == 0) "Horizontal" else "Vertical"
                    prefs.edit().putString("layout_style", layoutStyle).apply()
                    restartOverlay(context)
                },
                shape = itemShapeFor(2, totalLayoutItems),
                enabled = enableOverlay,
            )
        }

        // --- 4. THEME & COLORS SUBSECTION ---
        val themeItemCount = if (monetEnabled) 1 else 2
        SettingsSubsection(title = "THEME & COLORS") {
            SwitchSettingItem(
                title = "Use Monet Theme Colors",
                subtitle = "Match overlay colors with system wallpaper theme",
                checked = monetEnabled,
                enabled = enableOverlay,
                onCheckedChange = {
                    monetEnabled = it
                    prefs.edit().putBoolean("monet_enabled", it).apply()
                    restartOverlay(context)
                },
                icon = Icons.Rounded.ColorLens,
                shape = itemShapeFor(0, themeItemCount),
            )

            if (!monetEnabled) {
                val activePreset = colorPresets.find { it.id == overlayPreset } ?: colorPresets.first()

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = itemShapeFor(1, themeItemCount),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (enableOverlay) 1f else 0.38f },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Color Presets",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Selected: ${activePreset.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Presets Swatches matching Image 1
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(colorPresets) { preset ->
                                ColorPresetSwatch(
                                    preset = preset,
                                    isSelected = overlayPreset == preset.id,
                                    onClick = {
                                        if (enableOverlay) {
                                            overlayPreset = preset.id
                                            prefs.edit().putString("overlay_preset", preset.id).apply()
                                            restartOverlay(context)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. OVERLAY TUNING SUBSECTION ---
        SettingsSubsection(title = "OVERLAY TUNING") {
            val totalTuningItems = 5
            val currentIntervalSec = updateIntervalMs / 1000f

            SliderSettingItem(
                title = "Update Interval",
                description = "Frequency of telemetry metrics query",
                icon = Icons.Rounded.HourglassEmpty,
                value = currentIntervalSec.coerceIn(0.2f, 5.0f),
                onValueChange = {
                    val valueMs = (it * 1000).toLong().coerceIn(200L, 5000L)
                    updateIntervalMs = valueMs
                    prefs.edit().putLong("update_interval_ms", valueMs).apply()
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 0.2f..5.0f,
                displayValueFormatter = { "%.1f s".format(it) },
                shape = itemShapeFor(0, totalTuningItems),
                steps = 23,
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title = "Text Size",
                description = "Scale of the floating overlay text",
                icon = Icons.Rounded.TextFields,
                value = textSizeSp,
                onValueChange = {
                    textSizeSp = it
                    prefs.edit().putFloat("text_size_sp", it).apply()
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 8f..20f,
                displayValueFormatter = { "${it.toInt()} sp" },
                shape = itemShapeFor(1, totalTuningItems),
                steps = 11,
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title = "Background Opacity",
                description = "Opacity level of the backing block",
                icon = Icons.Rounded.Opacity,
                value = bgOpacity,
                onValueChange = {
                    bgOpacity = it
                    prefs.edit().putFloat("bg_opacity", it).apply()
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 0f..1f,
                displayValueFormatter = { "${(it * 100).toInt()}%" },
                shape = itemShapeFor(2, totalTuningItems),
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title = "Container Padding",
                description = "Thickness of internal margins",
                icon = Icons.Rounded.AspectRatio,
                value = paddingDp,
                onValueChange = {
                    paddingDp = it
                    prefs.edit().putFloat("padding_dp", it).apply()
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 4f..24f,
                displayValueFormatter = { "${it.toInt()} dp" },
                shape = itemShapeFor(3, totalTuningItems),
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title = "Corner Radius",
                description = "Rounding index of the overlay capsule",
                icon = Icons.Rounded.RoundedCorner,
                value = cornerRadiusDp,
                onValueChange = {
                    cornerRadiusDp = it
                    prefs.edit().putFloat("corner_radius_dp", it).apply()
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 0f..32f,
                displayValueFormatter = { "${it.toInt()} dp" },
                shape = itemShapeFor(4, totalTuningItems),
                enabled = enableOverlay,
            )
        }
    }
}

@Composable
private fun ColorPresetSwatch(
    preset: ColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(50.dp)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
            )
        }
        Canvas(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape),
        ) {
            drawArc(color = preset.primary, startAngle = 180f, sweepAngle = 90f, useCenter = true)
            drawArc(color = preset.secondary, startAngle = 270f, sweepAngle = 90f, useCenter = true)
            drawArc(color = preset.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = preset.neutral, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        }
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, preset.primary), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = preset.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private fun restartOverlay(context: Context) {
    val prefs = context.getSharedPreferences("auriya_overlay", Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean("enabled", false)
    val hasPermission =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)

    context.stopService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    if (enabled && hasPermission) {
        context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    }
}
