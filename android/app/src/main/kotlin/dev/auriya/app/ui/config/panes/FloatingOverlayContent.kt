package dev.auriya.app.ui.config.panes

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.OverlayPrefs
import dev.auriya.app.ui.components.*
import dev.auriya.app.ui.overlay.components.OverlayHud
import dev.auriya.app.ui.overlay.components.OverlayTelemetry
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FloatingOverlayContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { OverlayPrefs.getInstance(context) }
    var enableOverlay by remember { mutableStateOf(prefs.enabled) }
    var showFps by remember { mutableStateOf(prefs.showFps) }
    var showCpu by remember { mutableStateOf(prefs.showCpu) }
    var showGpu by remember { mutableStateOf(prefs.showGpu) }
    var showRam by remember { mutableStateOf(prefs.showRam) }
    var showTemp by remember { mutableStateOf(prefs.showTemp) }
    var showBattery by remember { mutableStateOf(prefs.showBattery) }

    var layoutStyle by remember { mutableStateOf(prefs.layoutStyle) }
    var overlayMode by remember { mutableStateOf(prefs.overlayMode) }
    var cpuStyle by remember { mutableStateOf(prefs.cpuStyle) }
    var updateIntervalMs by remember { mutableStateOf(prefs.updateIntervalMs) }

    var textSizeSp by remember { mutableStateOf(prefs.textSizeSp) }
    var bgOpacity by remember { mutableStateOf(prefs.bgOpacity) }
    var paddingDp by remember { mutableStateOf(prefs.paddingDp) }
    var cornerRadiusDp by remember { mutableStateOf(prefs.cornerRadiusDp) }

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
                prefs.enabled = true
                context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
            }
        } else {
            prefs.enabled = false
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
            val stats = dev.auriya.app.data.stats.StatsParser.fetchStats()
            val hasGpuIpc = stats?.gpu != null && ((stats.gpu.vendor != null && stats.gpu.vendor != "None") || (stats.gpu.mhz != null && stats.gpu.mhz > 0))
            val sysfsGpu =
                java.io.File("/sys/class/kgsl/kgsl-3d0/gpuclk").exists() ||
                    java.io.File("/sys/class/kgsl/kgsl-3d0/clock_mhz").exists() ||
                    java.io.File("/sys/kernel/gpu/gpu_clock").exists() ||
                    java.io.File("/sys/kernel/ged/gpu/gpu_cur_freq").exists()
            isGpuAvailable = hasGpuIpc || sysfsGpu
        }
    }

    val previewTelemetry =
        remember(cpuStyle) {
            OverlayTelemetry(
                fps = "120",
                rawFps = 119.8f,
                cpuText = "L1.1 M1.7 P0.8",
                cpuClusters = listOf("1.1", "1.7", "0.8"),
                cpuClusterLabels = listOf("L", "M", "P"),
                cpuStyle = cpuStyle,
                cpuLoadPct = 34f,
                maxCpuFreqGHz = 2.4f,
                gpuText = "591M (24%)",
                ramText = "5.4G",
                tempText = "42°C",
                batText = "37°C",
                rawCpuTemp = 42f,
                rawBatTemp = 37f,
            )
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. OVERLAY ACTIVATION SUBSECTION ---
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.overlay_sec_activation),
        ) {
            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_show),
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

        // --- 2. LIVE HUD PREVIEW ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Live HUD Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Material 3 Expressive · Monet dynamic color",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OverlayHud(
                        telemetry = previewTelemetry,
                        showFps = showFps,
                        showCpu = showCpu,
                        showGpu = showGpu,
                        showRam = showRam,
                        showTemp = showTemp,
                        showBattery = showBattery,
                        textSizeSp = textSizeSp,
                        bgOpacity = bgOpacity,
                        paddingDp = paddingDp,
                        cornerRadiusDp = cornerRadiusDp,
                        layoutStyle = layoutStyle,
                        overlayMode = overlayMode,
                    )
                }
            }
        }

        // --- 3. TELEMETRY METRICS SUBSECTION ---
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.overlay_sec_telemetry),
        ) {
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
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_fps_counter),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_fps_counter_sub),
                checked = showFps,
                enabled = enableOverlay,
                onCheckedChange = {
                    showFps = it
                    prefs.showFps = it
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Speed,
                shape = itemShapeFor(metricsList.indexOf("fps"), totalMetrics),
            )

            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_cpu_clusters),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_cpu_clusters_sub),
                checked = showCpu,
                enabled = enableOverlay,
                onCheckedChange = {
                    showCpu = it
                    prefs.showCpu = it
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Memory,
                shape = itemShapeFor(metricsList.indexOf("cpu"), totalMetrics),
            )

            if (isGpuAvailable) {
                SwitchSettingItem(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.overlay_gpu_metrics),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.overlay_gpu_metrics_sub),
                    checked = showGpu,
                    enabled = enableOverlay,
                    onCheckedChange = {
                        showGpu = it
                        prefs.showGpu = it
                        restartOverlay(context)
                    },
                    icon = Icons.Rounded.DeveloperBoard,
                    shape = itemShapeFor(metricsList.indexOf("gpu"), totalMetrics),
                )
            }

            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_ram_usage),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_ram_usage_sub),
                checked = showRam,
                enabled = enableOverlay,
                onCheckedChange = {
                    showRam = it
                    prefs.showRam = it
                    restartOverlay(context)
                },
                icon = Icons.Rounded.PieChart,
                shape = itemShapeFor(metricsList.indexOf("ram"), totalMetrics),
            )

            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_cpu_temp),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_cpu_temp_sub),
                checked = showTemp,
                enabled = enableOverlay,
                onCheckedChange = {
                    showTemp = it
                    prefs.showTemp = it
                    restartOverlay(context)
                },
                icon = Icons.Rounded.Thermostat,
                shape = itemShapeFor(metricsList.indexOf("temp"), totalMetrics),
            )

            SwitchSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_battery_temp),
                subtitle =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_battery_temp_sub),
                checked = showBattery,
                enabled = enableOverlay,
                onCheckedChange = {
                    showBattery = it
                    prefs.showBattery = it
                    restartOverlay(context)
                },
                icon = Icons.Rounded.BatteryChargingFull,
                shape = itemShapeFor(metricsList.indexOf("battery"), totalMetrics),
            )
        }

        // --- 4. DISPLAY & LAYOUT FORMAT SUBSECTION ---
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.overlay_sec_display_layout),
        ) {
            SegmentedSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_hud_mode),
                subtitle = if (overlayMode == "Full") "With labels (e.g. 120 FPS)" else "Numbers only (e.g. 120.0)",
                icon = Icons.Rounded.Dashboard,
                items = listOf("Full Info", "Minimalist"),
                selectedIndex = if (overlayMode == "Full") 0 else 1,
                onItemSelected = {
                    overlayMode = if (it == 0) "Full" else "Minimal"
                    prefs.overlayMode = overlayMode
                    restartOverlay(context)
                },
                shape = itemShapeFor(0, 1),
                enabled = enableOverlay,
            )
        }

        // --- 5. OVERLAY TUNING SUBSECTION ---
        SettingsSubsection(
            title =
                androidx.compose.ui.res
                    .stringResource(dev.auriya.app.R.string.overlay_sec_tuning),
        ) {
            val totalTuningItems = 5
            val currentIntervalSec = updateIntervalMs / 1000f

            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_update_interval),
                description =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_update_interval_desc),
                icon = Icons.Rounded.HourglassEmpty,
                value = currentIntervalSec.coerceIn(0.2f, 5.0f),
                onValueChange = {
                    val valueMs = (it * 1000).toLong().coerceIn(200L, 5000L)
                    updateIntervalMs = valueMs
                    prefs.updateIntervalMs = valueMs
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 0.2f..5.0f,
                displayValueFormatter = { "%.1f s".format(it) },
                shape = itemShapeFor(0, totalTuningItems),
                steps = 23,
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_text_size),
                description = "Scale of the floating overlay text",
                icon = Icons.Rounded.TextFields,
                value = textSizeSp,
                onValueChange = {
                    textSizeSp = it
                    prefs.textSizeSp = it
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 8f..20f,
                displayValueFormatter = { "${it.toInt()} sp" },
                shape = itemShapeFor(1, totalTuningItems),
                steps = 11,
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_opacity),
                description = "Opacity level of the backing block",
                icon = Icons.Rounded.Opacity,
                value = bgOpacity,
                onValueChange = {
                    bgOpacity = it
                    prefs.bgOpacity = it
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 0f..1f,
                displayValueFormatter = { "${(it * 100).toInt()}%" },
                shape = itemShapeFor(2, totalTuningItems),
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_padding),
                description = "Thickness of internal margins",
                icon = Icons.Rounded.AspectRatio,
                value = paddingDp,
                onValueChange = {
                    paddingDp = it
                    prefs.paddingDp = it
                },
                onValueChangeFinished = { restartOverlay(context) },
                valueRange = 4f..24f,
                displayValueFormatter = { "${it.toInt()} dp" },
                shape = itemShapeFor(3, totalTuningItems),
                enabled = enableOverlay,
            )

            SliderSettingItem(
                title =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.overlay_corner_radius),
                description = "Rounding index of the overlay capsule",
                icon = Icons.Rounded.RoundedCorner,
                value = cornerRadiusDp,
                onValueChange = {
                    cornerRadiusDp = it
                    prefs.cornerRadiusDp = it
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

private fun restartOverlay(context: Context) {
    val prefs = OverlayPrefs.getInstance(context)
    val enabled = prefs.enabled
    val hasPermission =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)

    context.stopService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    if (enabled && hasPermission) {
        context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    }
}
