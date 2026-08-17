package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.style.TextAlign
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.ui.theme.GoogleSansRounded

private enum class SettingsSubScreen {
    NONE,
    APP,
    FLOATING_OVERLAY,
    DEVELOPER_OPTIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: UiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetOobe: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var defaultGov by remember(settings) { mutableStateOf(settings.cpu.defaultGovernor) }
    var globalPreset by remember(settings) { mutableStateOf(settings.daemon.defaultMode) }

    val govSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGovernorSheet by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf(false) }

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
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    if (activeSubScreen != SettingsSubScreen.NONE) activeSubScreen = SettingsSubScreen.NONE
                    else onNavigateBack()
                },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                val title = when (activeSubScreen) {
                    SettingsSubScreen.APP -> "App Settings"
                    SettingsSubScreen.FLOATING_OVERLAY -> "Floating Overlay"
                    SettingsSubScreen.DEVELOPER_OPTIONS -> "Developer Options"
                    SettingsSubScreen.NONE -> "Settings"
                }
                val subtitle = when (activeSubScreen) {
                    SettingsSubScreen.NONE -> "Manage daemon, appearance, and monitoring"
                    SettingsSubScreen.APP -> "Performance and governor presets"
                    SettingsSubScreen.FLOATING_OVERLAY -> "Real-time floating monitor preferences"
                    SettingsSubScreen.DEVELOPER_OPTIONS -> "Diagnostics, logs, and resets"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            ) {
                when (activeSubScreen) {
                    SettingsSubScreen.NONE -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val totalItems = 5

                                SettingsMenuItem(
                                    icon = Icons.Filled.Build,
                                    title = "App",
                                    subtitle = "General application and performance settings",
                                    onClick = { activeSubScreen = SettingsSubScreen.APP },
                                    shape = itemShapeFor(0, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Palette,
                                    title = "Appearance",
                                    subtitle = "Theme, seed colors, and navigation style",
                                    onClick = onNavigateToAppearance,
                                    shape = itemShapeFor(1, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Layers,
                                    title = "Floating Overlay",
                                    subtitle = "Global system monitor floating overlay settings",
                                    onClick = { activeSubScreen = SettingsSubScreen.FLOATING_OVERLAY },
                                    shape = itemShapeFor(2, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Info,
                                    title = "About",
                                    subtitle = "Developer information and project specs",
                                    onClick = onNavigateToAbout,
                                    shape = itemShapeFor(3, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Code,
                                    title = "Developer Options",
                                    subtitle = "App reset, diagnostics, and debugging tools",
                                    onClick = { activeSubScreen = SettingsSubScreen.DEVELOPER_OPTIONS },
                                    shape = itemShapeFor(4, totalItems),
                                )
                            }
                        }
                    }

                    SettingsSubScreen.APP -> {
                        item {
                            SectionCard(title = "Tuning") {
                                SettingRow(
                                    icon = Icons.Filled.Settings,
                                    title = "CPU Governor",
                                    subtitle = "Scaling governor: $defaultGov",
                                    onClick = { showGovernorSheet = true },
                                    showChevron = false,
                                    control = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = defaultGov,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    thickness = 1.dp,
                                )
                                SettingRow(
                                    icon = Icons.Filled.Star,
                                    title = "Global Preset",
                                    subtitle = "Default idle profile: ${globalPreset.replaceFirstChar { it.uppercase() }}",
                                    onClick = { showPresetSheet = true },
                                    showChevron = false,
                                    control = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = globalPreset.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
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
                                    showChevron = false,
                                )
                            }
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

    if (showGovernorSheet) {
        GovernorSelectionBottomSheet(
            selectedGov = defaultGov,
            options = availableGovernors,
            onSelect = {
                defaultGov = it
                saveSettingsChange(viewModel, settings, defaultGov, globalPreset)
                Toast.makeText(context, "Governor set to $it", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showGovernorSheet = false },
            sheetState = govSheetState,
        )
    }

    if (showPresetSheet) {
        PresetSelectionBottomSheet(
            selectedPreset = globalPreset,
            options = availablePresets,
            onSelect = {
                globalPreset = it
                saveSettingsChange(viewModel, settings, defaultGov, globalPreset)
                Toast.makeText(context, "Preset set to $it", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPresetSheet = false },
            sheetState = presetSheetState,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorSelectionBottomSheet(
    selectedGov: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CPU Governor",
                        style = ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select global CPU frequency scaling policy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(options.size) { index ->
                val opt = options[index]
                val isSelected = opt.equals(selectedGov, ignoreCase = true)
                val (icon, subtitle) = getGovernorInfo(opt)

                Surface(
                    onClick = {
                        onSelect(opt)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = opt.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSelectionBottomSheet(
    selectedPreset: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Global Preset",
                        style = ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select default daemon performance profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(options.size) { index ->
                val opt = options[index]
                val isSelected = opt.equals(selectedPreset, ignoreCase = true)
                val (icon, title, subtitle) = getPresetInfo(opt)

                Surface(
                    onClick = {
                        onSelect(opt)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getGovernorInfo(gov: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    val name = gov.lowercase().trim()
    return when (name) {
        "performance" -> Icons.Outlined.Speed to "Locks CPU clusters to maximum operating frequencies for maximum throughput."
        "powersave" -> Icons.Outlined.BatterySaver to "Locks CPU to lowest frequencies to conserve battery and minimize thermals."
        "schedutil" -> Icons.Outlined.Tune to "Energy-Aware Scheduling governor scaling frequencies dynamically via task load."
        "walt" -> Icons.Outlined.Analytics to "Qualcomm Window-Assisted Load Tracking predicting workload demand history."
        "conservative" -> Icons.AutoMirrored.Filled.TrendingDown to "Gradual step-by-step frequency scaling prioritizing battery longevity."
        "ondemand" -> Icons.Outlined.Bolt to "Rapidly jumps to maximum frequency on CPU load spikes, then steps down."
        "interactive" -> Icons.Outlined.TouchApp to "Responsive scaling tailored for low latency and smooth UI touch response."
        "userspace" -> Icons.Outlined.Tune to "Allows manual frequency control by userspace daemons and external tools."
        "blu_schedutil", "blu_active" -> Icons.Outlined.Tune to "Tuned EAS governor balancing smooth frametimes and efficiency."
        "helix_schedutil" -> Icons.Outlined.Tune to "Energy-Aware Scheduling governor tuned for responsive UI and reduced power spikes."
        "electroutil" -> Icons.Outlined.EnergySavingsLeaf to "Schedutil tuning designed for battery efficiency and low frametime jitter."
        "pwrutilx", "pwrutil" -> Icons.Outlined.BatterySaver to "Power-focused schedutil variant designed for extended battery endurance."
        "elementalx" -> Icons.Outlined.Speed to "ElementalX custom governor balancing touch response with battery preservation."
        "alucard" -> Icons.Outlined.Speed to "Custom governor with aggressive frequency ramp-up on high loads."
        "darkness", "nightmare" -> Icons.Outlined.Speed to "Aggressive scaling governor prioritizing rapid task completion."
        "impulse" -> Icons.Outlined.Bolt to "Tuned interactive governor delivering instant frequency bursts."
        "ironactive" -> Icons.Outlined.EnergySavingsLeaf to "Interactive variant modified for aggressive power conservation."
        "zzmoove" -> Icons.Outlined.Tune to "Dynamic multi-profile governor adapting frequency scaling."
        "smartmax", "smartmax_eps" -> Icons.Outlined.EnergySavingsLeaf to "Custom governor tuned for UI smoothness with strict battery caps."
        "wheatley" -> Icons.Outlined.EnergySavingsLeaf to "Governor designed to maximize CPU C-state sleep duration."
        "pegasusq" -> Icons.Outlined.Tune to "Multi-core aware governor managing core hotplugging and scaling."
        "cultivation", "cultivation_schedutil" -> Icons.Outlined.SportsEsports to "Gaming-focused governor optimized for stable 3D framerates."
        "bioshock" -> Icons.Outlined.Speed to "Snappy responsiveness under heavy loads."
        "yankactive", "yankbattery" -> Icons.Outlined.BatterySaver to "Battery-centric governor tuned for endurance."
        "smartass", "smartassv2" -> Icons.Outlined.TouchApp to "Smart interactive governor with dedicated idle states."
        else -> when {
            "sched" in name || "util" in name -> Icons.Outlined.Tune to "Scheduler-based dynamic frequency scaling policy."
            "save" in name || "eco" in name || "pwr" in name || "batt" in name -> Icons.Outlined.BatterySaver to "Power-saving frequency policy to minimize battery drain."
            "perf" in name || "boost" in name || "turbo" in name || "max" in name -> Icons.Outlined.Speed to "High-performance frequency scaling policy."
            "active" in name || "interact" in name || "touch" in name -> Icons.Outlined.TouchApp to "Interactive scaling policy responsive to UI and input events."
            else -> Icons.Outlined.Memory to "Custom CPU scaling governor."
        }
    }
}

private fun getPresetInfo(preset: String): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String> {
    return when (preset.lowercase().trim()) {
        "powersave" -> Triple(
            Icons.Outlined.BatterySaver,
            "Powersave",
            "Locks background daemon to conservative clocks and limits background boost frequencies to minimize battery drain."
        )

        "balance" -> Triple(
            Icons.Outlined.Balance,
            "Balance",
            "Optimal balance between battery consumption and dynamic responsiveness for daily multitasking."
        )

        "performance" -> Triple(
            Icons.Outlined.Speed,
            "Performance",
            "Biases governor schedutil/frequencies to maximum performance and snappy touch response."
        )

        else -> Triple(
            Icons.Outlined.Tune,
            preset.replaceFirstChar { it.uppercase() },
            "Custom performance profile tuned for specific workloads."
        )
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
