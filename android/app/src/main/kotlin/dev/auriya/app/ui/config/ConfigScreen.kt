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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    PERFORMANCE_ENGINE,
    SYSTEM_SERVICES,
    FLOATING_OVERLAY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: UiViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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

    val governorsFromVm by viewModel.availableGovernors.collectAsStateWithLifecycle()
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
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        ConfigSubScreen.PERFORMANCE_ENGINE -> dev.auriya.app.R.string.config_performance_engine
                        ConfigSubScreen.SYSTEM_SERVICES -> dev.auriya.app.R.string.config_system_services
                        ConfigSubScreen.FLOATING_OVERLAY -> dev.auriya.app.R.string.config_floating_overlay
                    }
                val subtitleRes =
                    when (activeSubScreen) {
                        ConfigSubScreen.NONE -> dev.auriya.app.R.string.config_subtitle
                        ConfigSubScreen.PERFORMANCE_ENGINE -> dev.auriya.app.R.string.config_performance_engine_sub
                        ConfigSubScreen.SYSTEM_SERVICES -> dev.auriya.app.R.string.config_system_services_sub
                        ConfigSubScreen.FLOATING_OVERLAY -> dev.auriya.app.R.string.config_floating_overlay_sub
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
            color = MaterialTheme.colorScheme.surfaceContainer,
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
                                    val total = 3

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.AutoGraph,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_performance_engine),
                                        subtitle =
                                            if (fasEnabled) {
                                                "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.common_active)} · Target ${fasTargetFps.roundToInt()} FPS · ${if (dgEnabled) "Adaptive Gov" else "Static"}"
                                            } else {
                                                androidx.compose.ui.res
                                                    .stringResource(dev.auriya.app.R.string.common_disabled)
                                            },
                                        onClick = { activeSubScreen = ConfigSubScreen.PERFORMANCE_ENGINE },
                                        shape = itemShapeFor(0, total),
                                    )

                                    val displayDefaultMode =
                                        if (defaultMode.equals("fast", ignoreCase = true) || defaultMode.equals("fas", ignoreCase = true)) {
                                            "FAS"
                                        } else {
                                            defaultMode.replaceFirstChar { it.uppercase() }
                                        }
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Dns,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_system_services),
                                        subtitle = "${androidx.compose.ui.res.stringResource(dev.auriya.app.R.string.home_active_profile)}: $displayDefaultMode · Gov: $defaultGov · ${checkIntervalMs.roundToInt()}ms",
                                        onClick = { activeSubScreen = ConfigSubScreen.SYSTEM_SERVICES },
                                        shape = itemShapeFor(1, total),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Layers,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_floating_overlay),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.config_floating_overlay_sub),
                                        onClick = { activeSubScreen = ConfigSubScreen.FLOATING_OVERLAY },
                                        shape = itemShapeFor(2, total),
                                    )
                                }
                            }
                        }

                        ConfigSubScreen.PERFORMANCE_ENGINE -> {
                            fasEngineConfigPane(
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
                                    val rounded = fasThermalThreshold.roundToInt().toDouble()
                                    fasThermalThreshold = rounded.toFloat()
                                    val updated =
                                        settings.copy(
                                            fas = settings.fas.copy(thermalThreshold = rounded),
                                        )
                                    persistChanges(updated)
                                },
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
                                    val rounded = (dgCvThreshold * 100).roundToInt() / 100.0
                                    dgCvThreshold = rounded.toFloat()
                                    val updated =
                                        settings.copy(
                                            dynamicGovernor = settings.dynamicGovernor.copy(cvThreshold = rounded),
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
                                selectedModeKey = selectedModeKey,
                                modeMargin = modeMargin,
                                modeThermal = modeThermal,
                                onOpenTuneProfilePicker = { showTuneProfilePopup = true },
                                onMarginChange = { modeMargin = it },
                                onMarginFinished = {
                                    val roundedMargin = (modeMargin * 10).roundToInt() / 10.0
                                    val roundedThermal = modeThermal.roundToInt().toDouble()
                                    modeMargin = roundedMargin.toFloat()
                                    val updatedModes =
                                        effectiveModes.toMutableMap().apply {
                                            this[selectedModeKey] =
                                                FasMode(margin = roundedMargin, thermalThreshold = roundedThermal)
                                        }
                                    val updated = settings.copy(modes = updatedModes)
                                    persistChanges(updated)
                                },
                                onThermalChange = { modeThermal = it },
                                onThermalFinished = {
                                    val roundedMargin = (modeMargin * 10).roundToInt() / 10.0
                                    val roundedThermal = modeThermal.roundToInt().toDouble()
                                    modeThermal = roundedThermal.toFloat()
                                    val updatedModes =
                                        effectiveModes.toMutableMap().apply {
                                            this[selectedModeKey] =
                                                FasMode(margin = roundedMargin, thermalThreshold = roundedThermal)
                                        }
                                    val updated = settings.copy(modes = updatedModes)
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.SYSTEM_SERVICES -> {
                            systemServicesConfigPane(
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
                                defaultGov = defaultGov,
                                onOpenGovPicker = { showGovPopup = true },
                                dndEnabled = dndEnabled,
                                onDndEnabledChange = {
                                    dndEnabled = it
                                    val updated = settings.copy(dnd = settings.dnd.copy(defaultEnable = it))
                                    persistChanges(updated)
                                },
                            )
                        }

                        ConfigSubScreen.FLOATING_OVERLAY -> {
                            item {
                                FloatingOverlayContent()
                            }
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
                val normalizedKey = if (key.equals("fas", ignoreCase = true) || key.equals("fast", ignoreCase = true)) "fast" else key
                defaultMode = normalizedKey
                val currentSettings = viewModel.settings.value
                val updated =
                    currentSettings.copy(
                        daemon = currentSettings.daemon.copy(defaultMode = normalizedKey),
                        fas = currentSettings.fas.copy(defaultMode = normalizedKey),
                    )
                persistChanges(updated)
                viewModel.updateProfile(normalizedKey)
                val displayLabel = if (normalizedKey == "fast") "FAS" else normalizedKey.replaceFirstChar { c -> c.uppercase() }
                Toast.makeText(context, "Default profile set to $displayLabel", Toast.LENGTH_SHORT).show()
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
                    .exec("${dev.auriya.shared.config.ConfigPaths.AURIYACTL_BIN} set-log ${key.lowercase()}")
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
