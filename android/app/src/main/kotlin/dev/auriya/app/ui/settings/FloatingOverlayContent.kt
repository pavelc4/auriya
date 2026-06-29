package dev.auriya.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import dev.auriya.app.ui.components.SectionCard
import dev.auriya.app.ui.components.SettingRow
import dev.auriya.app.ui.components.SettingsSliderCard
import dev.auriya.app.ui.theme.AuriyaTokens

data class ColorPreset(
    val id: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

@Composable
fun FloatingOverlayContent(
    modifier: Modifier = Modifier
) {
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
    
    var overlayPreset by remember { mutableStateOf(prefs.getString("overlay_preset", "green_default") ?: "green_default") }
    var customPrimary by remember { mutableStateOf(prefs.getString("custom_primary", "#AAD2A4") ?: "#AAD2A4") }
    var customSecondary by remember { mutableStateOf(prefs.getString("custom_secondary", "#385E38") ?: "#385E38") }
    var customTertiary by remember { mutableStateOf(prefs.getString("custom_tertiary", "#8A9A5B") ?: "#8A9A5B") }

    var layoutStyle by remember { mutableStateOf(prefs.getString("layout_style", "Horizontal") ?: "Horizontal") }
    var overlayMode by remember { mutableStateOf(prefs.getString("overlay_mode", "Full") ?: "Full") }
    var updateIntervalMs by remember { mutableStateOf(prefs.getLong("update_interval_ms", 1000L)) }
    
    var textSizeSp by remember { mutableStateOf(prefs.getFloat("text_size_sp", 12f)) }
    var bgOpacity by remember { mutableStateOf(prefs.getFloat("bg_opacity", 0.7f)) }
    var paddingDp by remember { mutableStateOf(prefs.getFloat("padding_dp", 12f)) }
    var cornerRadiusDp by remember { mutableStateOf(prefs.getFloat("corner_radius_dp", 16f)) }

    val hasOverlayPermission = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    val colorPresets = remember {
        listOf(
            ColorPreset("green_default", Color(0xFFAAD2A4), Color(0xFF385E38), Color(0xFF8A9A5B)),
            ColorPreset("monochrome", Color(0xFFFFFFFF), Color(0xFF333333), Color(0xFF888888)),
            ColorPreset("sage", Color(0xFFC2D5C6), Color(0xFF4A5D4E), Color(0xFF8FA393)),
            ColorPreset("gaming", Color(0xFF2ECC71), Color(0xFF1B4F72), Color(0xFF00D2FF)),
            ColorPreset("rust", Color(0xFFAAD2A4), Color(0xFF5C3A21), Color(0xFFE07A5F))
        )
    }

    LaunchedEffect(enableOverlay) {
        if (enableOverlay) {
            if (!hasOverlayPermission) {
                val intent = Intent(
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
    ) {
        SectionCard(title = "Overlay Activation") {
            SettingRow(
                icon = Icons.Filled.Layers,
                title = "Show Floating Overlay",
                subtitle = if (hasOverlayPermission) {
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
        
        SectionCard(
            title = "Telemetry Metrics",
            modifier = Modifier.graphicsLayer { alpha = if (enableOverlay) 1f else 0.38f }
        ) {
            SettingRow(
                icon = Icons.Filled.Speed,
                title = "Show FPS Counter",
                subtitle = "Display active frame rate monitoring",
                control = {
                    Switch(
                        checked = showFps,
                        enabled = enableOverlay,
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
                        enabled = enableOverlay,
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
                        enabled = enableOverlay,
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
                icon = Icons.Filled.FlipToFront,
                title = "Show RAM Usage",
                subtitle = "Display active memory usage statistics",
                control = {
                    Switch(
                        checked = showRam,
                        enabled = enableOverlay,
                        onCheckedChange = {
                            showRam = it
                            prefs.edit().putBoolean("show_ram", it).apply()
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
                        enabled = enableOverlay,
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
                        enabled = enableOverlay,
                        onCheckedChange = {
                            showBattery = it
                            prefs.edit().putBoolean("show_battery", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
        }

        SectionCard(
            title = "Visual Customization",
            modifier = Modifier.graphicsLayer { alpha = if (enableOverlay) 1f else 0.38f }
        ) {
            SettingRow(
                icon = Icons.Filled.ColorLens,
                title = "Use Monet Theme Colors",
                subtitle = "Match overlay colors with device style",
                control = {
                    Switch(
                        checked = monetEnabled,
                        enabled = enableOverlay,
                        onCheckedChange = {
                            monetEnabled = it
                            prefs.edit().putBoolean("monet_enabled", it).apply()
                            restartOverlay(context)
                        },
                    )
                },
            )
            
            if (!monetEnabled && enableOverlay) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Color Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorPresets.forEach { preset ->
                            val isPresetSelected = overlayPreset == preset.id
                            ColorPresetCircle(
                                preset = preset,
                                isSelected = isPresetSelected,
                                onClick = {
                                    overlayPreset = preset.id
                                    prefs.edit().putString("overlay_preset", preset.id).apply()
                                    restartOverlay(context)
                                }
                            )
                        }

                        val isCustomSelected = overlayPreset == "custom"
                        val customPreset = remember(customPrimary, customSecondary, customTertiary) {
                            val prim = runCatching { Color(android.graphics.Color.parseColor(customPrimary)) }.getOrDefault(Color(0xFF2ECC71))
                            val sec = runCatching { Color(android.graphics.Color.parseColor(customSecondary)) }.getOrDefault(Color(0xFFF1C40F))
                            val tert = runCatching { Color(android.graphics.Color.parseColor(customTertiary)) }.getOrDefault(Color(0xFFE74C3C))
                            ColorPreset("custom", prim, sec, tert)
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    overlayPreset = "custom"
                                    prefs.edit().putString("overlay_preset", "custom").apply()
                                    restartOverlay(context)
                                }
                                .then(
                                    if (isCustomSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                                )
                                .padding(4.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(color = customPreset.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                                drawArc(color = customPreset.secondary, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                                drawArc(color = customPreset.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                            }
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp).align(Alignment.Center)
                            )
                        }
                    }

                    if (overlayPreset == "custom") {
                        Spacer(modifier = Modifier.height(12.dp))
                        CustomColorPickerRow(
                            label = "Primary Color (FPS)",
                            selectedColor = customPrimary,
                            onColorSelected = { hex ->
                                customPrimary = hex
                                prefs.edit().putString("custom_primary", hex).apply()
                                restartOverlay(context)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CustomColorPickerRow(
                            label = "Secondary Color (CPU/Temps)",
                            selectedColor = customSecondary,
                            onColorSelected = { hex ->
                                customSecondary = hex
                                prefs.edit().putString("custom_secondary", hex).apply()
                                restartOverlay(context)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CustomColorPickerRow(
                            label = "Tertiary Color (GPU/Battery)",
                            selectedColor = customTertiary,
                            onColorSelected = { hex ->
                                customTertiary = hex
                                prefs.edit().putString("custom_tertiary", hex).apply()
                                restartOverlay(context)
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (enableOverlay) 1f else 0.38f },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HUD Format Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val isFullSelected = overlayMode == "Full"
                Card(
                    onClick = {
                        if (enableOverlay) {
                            overlayMode = "Full"
                            prefs.edit().putString("overlay_mode", "Full").apply()
                            restartOverlay(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFullSelected && enableOverlay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isFullSelected && enableOverlay) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Full Info",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isFullSelected && enableOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "With labels (e.g. FPS: 60)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                val isMinimalSelected = overlayMode == "Minimal"
                Card(
                    onClick = {
                        if (enableOverlay) {
                            overlayMode = "Minimal"
                            prefs.edit().putString("overlay_mode", "Minimal").apply()
                            restartOverlay(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMinimalSelected && enableOverlay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isMinimalSelected && enableOverlay) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Minimalist",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isMinimalSelected && enableOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Numbers only (e.g. 60.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (enableOverlay) 1f else 0.38f },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Layout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val isHorizontalSelected = layoutStyle == "Horizontal"
                Card(
                    onClick = {
                        if (enableOverlay) {
                            layoutStyle = "Horizontal"
                            prefs.edit().putString("layout_style", "Horizontal").apply()
                            restartOverlay(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHorizontalSelected && enableOverlay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isHorizontalSelected && enableOverlay) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isHorizontalSelected && enableOverlay) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isHorizontalSelected && enableOverlay) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                        }

                        Text(
                            text = "Horizontal",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isHorizontalSelected && enableOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val isVerticalSelected = layoutStyle == "Vertical"
                Card(
                    onClick = {
                        if (enableOverlay) {
                            layoutStyle = "Vertical"
                            prefs.edit().putString("layout_style", "Vertical").apply()
                            restartOverlay(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVerticalSelected && enableOverlay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isVerticalSelected && enableOverlay) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isVerticalSelected && enableOverlay) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isVerticalSelected && enableOverlay) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                        }

                        Text(
                            text = "Vertical",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isVerticalSelected && enableOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        val currentIntervalSec = updateIntervalMs / 1000f
        SettingsSliderCard(
            title = "Update Interval",
            description = "Frequency of telemetry metrics query",
            icon = Icons.Filled.HourglassEmpty,
            value = currentIntervalSec,
            onValueChange = {
                val valueMs = (it * 1000).toLong()
                updateIntervalMs = valueMs
                prefs.edit().putLong("update_interval_ms", valueMs).apply()
            },
            onValueChangeFinished = { restartOverlay(context) },
            valueRange = 0.5f..5.0f,
            displayValueFormatter = { "%.1f s".format(it) },
            valueLabel = "Polling Delay",
            steps = 8,
            enabled = enableOverlay
        )

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
            steps = 11,
            enabled = enableOverlay
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
            valueLabel = "Opacity Level",
            enabled = enableOverlay
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
            valueLabel = "Internal Margin",
            enabled = enableOverlay
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
            valueLabel = "Edge Rounding",
            enabled = enableOverlay
        )
    }
}

@Composable
private fun ColorPresetCircle(
    preset: ColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
            )
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = preset.primary,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true
            )
            drawArc(
                color = preset.secondary,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = true
            )
            drawArc(
                color = preset.tertiary,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true
            )
        }
    }
}

@Composable
private fun CustomColorPickerRow(
    label: String,
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#E74C3C", // Red
        "#2ECC71", // Green
        "#3498DB", // Blue
        "#00D2FF", // Cyan
        "#E67E22", // Orange
        "#9B59B6", // Purple
        "#FFFFFF", // White
        "#F1C40F"  // Yellow
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { hex ->
                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .clickable { onColorSelected(hex) }
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

private fun restartOverlay(context: Context) {
    val prefs = context.getSharedPreferences("auriya_overlay", Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean("enabled", false)
    val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)

    context.stopService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    if (enabled && hasPermission) {
        context.startService(Intent(context, dev.auriya.app.service.OverlayService::class.java))
    }
}
