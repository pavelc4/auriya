package dev.auriya.app.ui.config

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.components.SettingsMenuItem
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.config.panes.*
import dev.auriya.app.ui.config.popups.*
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.config.TomlParser
import dev.auriya.shared.model.FasMode
import dev.auriya.shared.model.Settings
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class ConfigSubScreen {
    NONE,
    DAEMON,
    CPU,
    FAS,
    DYNAMIC_GOVERNOR,
    DND,
    PROFILES,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: UiViewModel) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var activeSubScreen by remember { mutableStateOf(ConfigSubScreen.NONE) }

    BackHandler(enabled = activeSubScreen != ConfigSubScreen.NONE) {
        activeSubScreen = ConfigSubScreen.NONE
    }

    fun persistChanges(updatedSettings: Settings) {
        viewModel.saveSettings(updatedSettings)
    }

    val importConfigLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val content = stream.bufferedReader().readText()
                        val parsed = TomlParser.parseSettings(content)
                        persistChanges(parsed)
                        Toast.makeText(context, "Settings imported & applied successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to parse settings: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

    // Local mutable states synced with ViewModel settings
    var logLevel by remember(settings) { mutableStateOf(settings.daemon.logLevel) }
    var checkIntervalMs by remember(settings) { mutableFloatStateOf(settings.daemon.checkIntervalMs.toFloat()) }
    var defaultMode by remember(settings) { mutableStateOf(settings.daemon.defaultMode) }

    var defaultGov by remember(settings) { mutableStateOf(settings.cpu.defaultGovernor) }

    var dndEnabled by remember(settings) { mutableStateOf(settings.dnd.defaultEnable) }

    var fasEnabled by remember(settings) { mutableStateOf(settings.fas.enabled) }
    var fasTargetFps by remember(settings) { mutableFloatStateOf(settings.fas.targetFps.toFloat()) }
    var fasPollIntervalMs by remember(settings) { mutableFloatStateOf(settings.fas.pollIntervalMs.toFloat()) }
    var fasThermalThreshold by remember(settings) { mutableFloatStateOf(settings.fas.thermalThreshold.toFloat()) }

    var dgEnabled by remember(settings) { mutableStateOf(settings.dynamicGovernor.enabled) }
    var dgCvThreshold by remember(settings) { mutableFloatStateOf(settings.dynamicGovernor.cvThreshold.toFloat()) }
    var dgDebounceFrames by remember(settings) { mutableFloatStateOf(settings.dynamicGovernor.debounceFrames.toFloat()) }

    val defaultModes =
        remember {
            mapOf(
                "powersave" to FasMode(margin = 5.0, thermalThreshold = 80.0),
                "balance" to FasMode(margin = 2.0, thermalThreshold = 90.0),
                "performance" to FasMode(margin = 1.0, thermalThreshold = 95.0),
                "fast" to FasMode(margin = 0.0, thermalThreshold = 95.0),
            )
        }
    val effectiveModes =
        remember(settings.modes) {
            if (settings.modes.isNotEmpty()) defaultModes + settings.modes else defaultModes
        }

    var selectedModeKey by rememberSaveable(settings.daemon.defaultMode) {
        mutableStateOf(settings.daemon.defaultMode.ifEmpty { "balance" })
    }

    val currentMode = effectiveModes[selectedModeKey] ?: defaultModes[selectedModeKey] ?: FasMode(margin = 2.0, thermalThreshold = 90.0)
    var modeMargin by remember(selectedModeKey, currentMode.margin) { mutableFloatStateOf(currentMode.margin.toFloat()) }
    var modeThermal by remember(
        selectedModeKey,
        currentMode.thermalThreshold,
    ) { mutableFloatStateOf(currentMode.thermalThreshold.toFloat()) }

    val governorsFromVm by viewModel.availableGovernors.collectAsState()
    val effectiveGovernors =
        remember(governorsFromVm, defaultGov) {
            if (defaultGov.isNotBlank() && defaultGov !in governorsFromVm) {
                listOf(defaultGov) + governorsFromVm
            } else {
                governorsFromVm.ifEmpty { listOf("schedutil", "performance", "powersave") }
            }
        }

    var showGovPopup by remember { mutableStateOf(false) }
    var showPresetPopup by remember { mutableStateOf(false) }
    var showLogLevelPopup by remember { mutableStateOf(false) }
    var showTuneProfilePopup by remember { mutableStateOf(false) }
    var showConfigActionsPopup by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (activeSubScreen != ConfigSubScreen.NONE) {
                FilledIconButton(
                    onClick = { activeSubScreen = ConfigSubScreen.NONE },
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val titleRes =
                    when (activeSubScreen) {
                        ConfigSubScreen.NONE -> dev.auriya.app.R.string.config_title
                        ConfigSubScreen.DAEMON -> dev.auriya.app.R.string.config_daemon
                        ConfigSubScreen.CPU -> dev.auriya.app.R.string.config_cpu
                        ConfigSubScreen.FAS -> dev.auriya.app.R.string.config_fas
                        ConfigSubScreen.DYNAMIC_GOVERNOR -> dev.auriya.app.R.string.config_dynamic_gov
                        ConfigSubScreen.DND -> dev.auriya.app.R.string.config_dnd
                        ConfigSubScreen.PROFILES -> dev.auriya.app.R.string.config_profiles
                    }
                val subtitleRes =
                    when (activeSubScreen) {
                        ConfigSubScreen.NONE -> dev.auriya.app.R.string.config_subtitle
                        ConfigSubScreen.DAEMON -> dev.auriya.app.R.string.config_daemon_sub
                        ConfigSubScreen.CPU -> dev.auriya.app.R.string.config_cpu_sub
                        ConfigSubScreen.FAS -> dev.auriya.app.R.string.config_fas_sub
                        ConfigSubScreen.DYNAMIC_GOVERNOR -> dev.auriya.app.R.string.config_dynamic_gov_sub
                        ConfigSubScreen.DND -> dev.auriya.app.R.string.config_dnd_sub
                        ConfigSubScreen.PROFILES -> dev.auriya.app.R.string.config_profiles_sub
                    }
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(titleRes),
                    style =
                        if (activeSubScreen == ConfigSubScreen.NONE) {
                            ExpTitleTypography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 36.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                )
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (activeSubScreen == ConfigSubScreen.NONE) {
                FilledIconButton(
                    onClick = { showConfigActionsPopup = true },
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Config Actions",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            AnimatedContent(
                targetState = activeSubScreen,
                transitionSpec = {
                    if (targetState == ConfigSubScreen.NONE) {
                        (slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()).togetherWith(
                            slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        )
                    } else {
                        (slideInHorizontally(initialOffsetX = { it }) + fadeIn()).togetherWith(
                            slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(),
                        )
                    }
                },
                label = "ConfigScreenNavigation",
            ) { subScreen ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                ) {
                    when (subScreen) {
                        ConfigSubScreen.NONE -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    val total = 6

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Dns,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_daemon),
                                        subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.home_active_profile)}: ${defaultMode.replaceFirstChar {
                                            it.uppercase()
                                        }} · ${checkIntervalMs.roundToInt()}ms",
                                        onClick = { activeSubScreen = ConfigSubScreen.DAEMON },
                                        shape = itemShapeFor(0, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Speed,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_cpu),
                                        subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.games_cpu_governor)}: $defaultGov",
                                        onClick = { activeSubScreen = ConfigSubScreen.CPU },
                                        shape = itemShapeFor(1, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.AutoGraph,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_fas),
                                        subtitle =
                                            if (fasEnabled) {
                                                "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.common_active)} · Target ${fasTargetFps.roundToInt()} FPS · ${fasThermalThreshold.roundToInt()}°C"
                                            } else {
                                                androidx.compose.ui.res
                                                    .stringResource(dev.auriya.app.R.string.common_disabled)
                                            },
                                        onClick = { activeSubScreen = ConfigSubScreen.FAS },
                                        shape = itemShapeFor(2, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DynamicForm,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_dynamic_gov),
                                        subtitle =
                                            if (dgEnabled) {
                                                "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.common_active)} · Jitter CV: ${String.format(java.util.Locale.US, "%.2f", dgCvThreshold)} · ${dgDebounceFrames.roundToInt()}f"
                                            } else {
                                                androidx.compose.ui.res
                                                    .stringResource(dev.auriya.app.R.string.common_disabled)
                                            },
                                        onClick = { activeSubScreen = ConfigSubScreen.DYNAMIC_GOVERNOR },
                                        shape = itemShapeFor(3, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DoNotDisturbOn,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_dnd),
                                        subtitle =
                                            if (dndEnabled) {
                                                androidx.compose.ui.res
                                                    .stringResource(dev.auriya.app.R.string.config_dnd_auto_game_desc)
                                            } else {
                                                androidx.compose.ui.res
                                                    .stringResource(dev.auriya.app.R.string.common_disabled)
                                            },
                                        onClick = { activeSubScreen = ConfigSubScreen.DND },
                                        shape = itemShapeFor(4, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Tune,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_profiles),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_profiles_sub),
                                        onClick = { activeSubScreen = ConfigSubScreen.PROFILES },
                                        shape = itemShapeFor(5, total),
                                    )
                                }
                            }
                        }

                        ConfigSubScreen.DAEMON -> {
                            daemonConfigPane(
                                defaultMode = defaultMode,
                                logLevel = logLevel,
                                checkIntervalMs = checkIntervalMs,
                                onOpenPresetPicker = { showPresetPopup = true },
                                onOpenLogLevelPicker = { showLogLevelPopup = true },
                                onCheckIntervalChange = { checkIntervalMs = it },
                                onCheckIntervalFinished = {
                                    val updated =
                                        settings.copy(
                                            daemon = settings.daemon.copy(checkIntervalMs = checkIntervalMs.roundToLong()),
                                        )
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.CPU -> {
                            cpuConfigPane(
                                defaultGov = defaultGov,
                                onOpenGovPicker = { showGovPopup = true },
                            )
                        }

                        ConfigSubScreen.FAS -> {
                            fasConfigPane(
                                fasEnabled = fasEnabled,
                                fasTargetFps = fasTargetFps,
                                fasPollIntervalMs = fasPollIntervalMs,
                                fasThermalThreshold = fasThermalThreshold,
                                onFasEnabledChange = {
                                    fasEnabled = it
                                    val updated = settings.copy(fas = settings.fas.copy(enabled = it))
                                    persistChanges(updated)
                                },
                                onTargetFpsChange = { fasTargetFps = it },
                                onTargetFpsFinished = {
                                    val updated =
                                        settings.copy(
                                            fas = settings.fas.copy(targetFps = fasTargetFps.roundToInt()),
                                        )
                                    persistChanges(updated)
                                },
                                onPollIntervalChange = { fasPollIntervalMs = it },
                                onPollIntervalFinished = {
                                    val updated =
                                        settings.copy(
                                            fas = settings.fas.copy(pollIntervalMs = fasPollIntervalMs.roundToLong()),
                                        )
                                    persistChanges(updated)
                                },
                                onThermalThresholdChange = { fasThermalThreshold = it },
                                onThermalThresholdFinished = {
                                    val updated =
                                        settings.copy(
                                            fas = settings.fas.copy(thermalThreshold = fasThermalThreshold.toDouble()),
                                        )
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.DYNAMIC_GOVERNOR -> {
                            dynamicGovernorConfigPane(
                                dgEnabled = dgEnabled,
                                dgCvThreshold = dgCvThreshold,
                                dgDebounceFrames = dgDebounceFrames,
                                onDgEnabledChange = {
                                    dgEnabled = it
                                    val updated =
                                        settings.copy(
                                            dynamicGovernor = settings.dynamicGovernor.copy(enabled = it),
                                        )
                                    persistChanges(updated)
                                },
                                onCvThresholdChange = { dgCvThreshold = it },
                                onCvThresholdFinished = {
                                    val updated =
                                        settings.copy(
                                            dynamicGovernor = settings.dynamicGovernor.copy(cvThreshold = dgCvThreshold.toDouble()),
                                        )
                                    persistChanges(updated)
                                },
                                onDebounceFramesChange = { dgDebounceFrames = it },
                                onDebounceFramesFinished = {
                                    val updated =
                                        settings.copy(
                                            dynamicGovernor = settings.dynamicGovernor.copy(debounceFrames = dgDebounceFrames.roundToInt()),
                                        )
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.DND -> {
                            dndConfigPane(
                                dndEnabled = dndEnabled,
                                onDndEnabledChange = {
                                    dndEnabled = it
                                    val updated = settings.copy(dnd = settings.dnd.copy(defaultEnable = it))
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.PROFILES -> {
                            profileTuningConfigPane(
                                selectedModeKey = selectedModeKey,
                                modeMargin = modeMargin,
                                modeThermal = modeThermal,
                                onOpenTuneProfilePicker = { showTuneProfilePopup = true },
                                onMarginChange = { modeMargin = it },
                                onMarginFinished = {
                                    val updatedModes =
                                        effectiveModes.toMutableMap().apply {
                                            this[selectedModeKey] =
                                                FasMode(margin = modeMargin.toDouble(), thermalThreshold = modeThermal.toDouble())
                                        }
                                    val updated = settings.copy(modes = updatedModes)
                                    persistChanges(updated)
                                },
                                onThermalChange = { modeThermal = it },
                                onThermalFinished = {
                                    val updatedModes =
                                        effectiveModes.toMutableMap().apply {
                                            this[selectedModeKey] =
                                                FasMode(margin = modeMargin.toDouble(), thermalThreshold = modeThermal.toDouble())
                                        }
                                    val updated = settings.copy(modes = updatedModes)
                                    persistChanges(updated)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // --- POPUPS & BOTTOM SHEETS ---
    if (showGovPopup) {
        CpuGovernorPopup(
            defaultGov = defaultGov,
            availableGovernors = effectiveGovernors,
            onSelect = { gov ->
                defaultGov = gov
                val currentSettings = viewModel.settings.value
                val updated = currentSettings.copy(cpu = currentSettings.cpu.copy(defaultGovernor = gov))
                persistChanges(updated)
                Toast.makeText(context, "Governor set to $gov", Toast.LENGTH_SHORT).show()
                showGovPopup = false
            },
            onDismiss = { showGovPopup = false },
        )
    }

    if (showPresetPopup) {
        ProfilePickerPopup(
            defaultMode = defaultMode,
            onSelect = { key ->
                defaultMode = key
                val currentSettings = viewModel.settings.value
                val updated =
                    currentSettings.copy(
                        daemon = currentSettings.daemon.copy(defaultMode = key),
                        fas = currentSettings.fas.copy(defaultMode = key),
                    )
                persistChanges(updated)
                viewModel.updateProfile(key)
                Toast.makeText(context, "Default profile set to ${key.replaceFirstChar { c -> c.uppercase() }}", Toast.LENGTH_SHORT).show()
                showPresetPopup = false
            },
            onDismiss = { showPresetPopup = false },
        )
    }

    if (showLogLevelPopup) {
        LogLevelPickerPopup(
            logLevel = logLevel,
            onSelect = { key ->
                logLevel = key
                val updated = settings.copy(daemon = settings.daemon.copy(logLevel = key))
                persistChanges(updated)
                dev.auriya.app.data.RootShell
                    .exec("echo 'SETLOG ${key.uppercase()}' | nc -U /dev/socket/auriya.sock")
                Toast.makeText(context, "Log level set to ${key.uppercase()}", Toast.LENGTH_SHORT).show()
                showLogLevelPopup = false
            },
            onDismiss = { showLogLevelPopup = false },
        )
    }

    if (showTuneProfilePopup) {
        TuneProfilePickerPopup(
            selectedMode = selectedModeKey,
            onSelect = { key ->
                selectedModeKey = key
                val targetMode = effectiveModes[key] ?: defaultModes[key] ?: FasMode(margin = 2.0, thermalThreshold = 90.0)
                modeMargin = targetMode.margin.toFloat()
                modeThermal = targetMode.thermalThreshold.toFloat()
                showTuneProfilePopup = false
            },
            onDismiss = { showTuneProfilePopup = false },
        )
    }

    if (showConfigActionsPopup) {
        ConfigActionsPopup(
            settings = settings,
            onImportRequest = { importConfigLauncher.launch("*/*") },
            onResetRequest = {
                val reset =
                    Settings(
                        modes =
                            mapOf(
                                "powersave" to FasMode(margin = 5.0, thermalThreshold = 80.0),
                                "balance" to FasMode(margin = 2.0, thermalThreshold = 90.0),
                                "performance" to FasMode(margin = 1.0, thermalThreshold = 95.0),
                                "fast" to FasMode(margin = 0.0, thermalThreshold = 95.0),
                            ),
                    )
                persistChanges(reset)
                Toast.makeText(context, "Settings reset to default", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showConfigActionsPopup = false },
        )
    }
}
