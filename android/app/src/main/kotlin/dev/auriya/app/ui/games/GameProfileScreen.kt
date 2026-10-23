package dev.auriya.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.data.stats.AutoRecordPrefs
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.ProfilePickerBottomSheet
import dev.auriya.app.ui.games.detail.*
import dev.auriya.shared.model.GameProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameProfileScreen(
    game: GameProfile,
    governorOptions: List<String>,
    isExistingProfile: Boolean = true,
    thermalAvailable: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (GameProfile) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val autoRecordPrefs = remember { AutoRecordPrefs(context) }
    var autoRecord by remember(game.packageName) {
        mutableStateOf(autoRecordPrefs.isAutoRecordEnabled(game.packageName))
    }
    val appLabel =
        remember(game.packageName) {
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
            }.getOrDefault(game.packageName.substringAfterLast('.'))
        }
    val iconBitmap = rememberAppIcon(game.packageName)

    val initialGov =
        game.cpuGovernor.ifEmpty {
            governorOptions.firstOrNull() ?: "schedutil"
        }
    var selectedMode by remember(game.packageName, game.mode) { mutableStateOf(game.mode ?: "performance") }
    var selectedGov by remember(game.packageName, initialGov) { mutableStateOf(initialGov) }
    var targetFps by remember(game.packageName, game.targetFps) { mutableStateOf(game.targetFps?.toFloat() ?: 60f) }
    var refreshRate by remember(game.packageName, game.refreshRate) { mutableStateOf(game.refreshRate?.toFloat() ?: 0f) }
    var enableDnd by remember(game.packageName, game.enableDnd) { mutableStateOf(game.enableDnd) }
    var selectedCeiling by remember(game.packageName, game.ceiling) { mutableStateOf(game.ceiling ?: "default") }
    var selectedThermal by remember(game.packageName, game.thermalPreset) { mutableStateOf(game.thermalPreset ?: "default") }

    val ceilingOptions = remember { listOf("default", "low", "balance", "high") }
    val thermalOptions = remember { listOf("default", "dynamic", "extreme", "class0", "incalls", "thermal20") }
    val effectiveGovOptions =
        remember(governorOptions, selectedGov) {
            if (selectedGov.isNotBlank() && selectedGov !in governorOptions) {
                listOf(selectedGov) + governorOptions
            } else {
                governorOptions.ifEmpty { listOf("schedutil", "performance", "powersave") }
            }
        }

    var showModeSheet by remember { mutableStateOf(false) }
    var showGovSheet by remember { mutableStateOf(false) }
    var showCeilingSheet by remember { mutableStateOf(false) }
    var showThermalSheet by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }

    val modeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val govSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ceilingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val thermalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingDelete by remember { mutableStateOf(false) }

    fun updateAndSave(
        mode: String = selectedMode,
        gov: String = selectedGov,
        ceiling: String = selectedCeiling,
        thermal: String = selectedThermal,
        fps: Float = targetFps,
        refresh: Float = refreshRate,
        dnd: Boolean = enableDnd,
    ) {
        selectedMode = mode
        selectedGov = gov
        selectedCeiling = ceiling
        selectedThermal = thermal
        targetFps = fps
        refreshRate = refresh
        enableDnd = dnd
        onSave(
            GameProfile(
                packageName = game.packageName,
                cpuGovernor = gov,
                enableDnd = dnd,
                targetFps = fps.toInt(),
                refreshRate = if (refresh.toInt() == 0) null else refresh.toInt(),
                mode = mode,
                ceiling = if (ceiling == "default") null else ceiling,
                thermalPreset = if (thermal == "default") null else thermal,
            ),
        )
    }

    if (showModeSheet) {
        ProfilePickerBottomSheet(
            selectedProfile = selectedMode,
            onSelect = { updateAndSave(mode = it) },
            onDismiss = { showModeSheet = false },
            sheetState = modeSheetState,
        )
    }

    if (showGovSheet) {
        GovernorSelectionBottomSheet(
            selectedGov = selectedGov,
            options = effectiveGovOptions,
            onSelect = { updateAndSave(gov = it) },
            onDismiss = { showGovSheet = false },
            sheetState = govSheetState,
        )
    }

    if (showCeilingSheet) {
        CeilingSelectionBottomSheet(
            selectedCeiling = selectedCeiling,
            options = ceilingOptions,
            onSelect = { updateAndSave(ceiling = it) },
            onDismiss = { showCeilingSheet = false },
            sheetState = ceilingSheetState,
        )
    }

    if (showThermalSheet) {
        ThermalSelectionBottomSheet(
            selectedThermal = selectedThermal,
            options = thermalOptions,
            onSelect = { updateAndSave(thermal = it) },
            onDismiss = { showThermalSheet = false },
            sheetState = thermalSheetState,
        )
    }

    if (showActionsSheet) {
        ProfileActionsBottomSheet(
            appLabel = appLabel,
            isExistingProfile = isExistingProfile,
            onReset = {
                val defaultGov = governorOptions.firstOrNull() ?: game.cpuGovernor
                updateAndSave(
                    gov = defaultGov,
                    ceiling = "default",
                    thermal = "default",
                    fps = 60f,
                    refresh = 0f,
                    dnd = true,
                )
            },
            onRemove =
                if (isExistingProfile && onRemove != null) {
                    { pendingDelete = true }
                } else {
                    null
                },
            onDismiss = { showActionsSheet = false },
            sheetState = actionsSheetState,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        // --- 1. TOP PINNED HEADER (Backdrop layer) ---
        GameProfileTopBar(
            onDismiss = onDismiss,
            onActionsClick = { showActionsSheet = true },
        )

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Hero Header Card
                item {
                    GameProfileHeroHeader(
                        label = appLabel,
                        iconBitmap = iconBitmap,
                        mode = selectedMode,
                        targetFps = targetFps.toInt(),
                        dnd = enableDnd,
                        gov = selectedGov,
                        ceiling = selectedCeiling,
                    )
                }

                // Section 1: Performance
                item {
                    SectionLabel("Performance")
                }

                item {
                    ExpressiveList(count = if (thermalAvailable) 6 else 5) { index ->
                        when (index) {
                            0 -> {
                                ProfileModeRow(
                                    selected = selectedMode,
                                    onClick = { showModeSheet = true },
                                )
                            }

                            1 -> {
                                GovernorRow(
                                    selected = selectedGov,
                                    onClick = { showGovSheet = true },
                                )
                            }

                            2 -> {
                                CeilingRow(
                                    selected = selectedCeiling,
                                    onClick = { showCeilingSheet = true },
                                )
                            }

                            3 -> {
                                SliderRow(
                                    title = stringResource(R.string.games_target_fps),
                                    value = targetFps,
                                    onChange = { targetFps = it },
                                    onValueChangeFinished = { updateAndSave(fps = targetFps) },
                                    range = 30f..120f,
                                    steps = 5,
                                    valueLabel = "${targetFps.toInt()} FPS",
                                )
                            }

                            4 -> {
                                SliderRow(
                                    title = stringResource(R.string.games_refresh_rate),
                                    value = refreshRate,
                                    onChange = { refreshRate = it },
                                    onValueChangeFinished = { updateAndSave(refresh = refreshRate) },
                                    range = 0f..120f,
                                    steps = 3,
                                    valueLabel = if (refreshRate.toInt() == 0) "System default" else "${refreshRate.toInt()} Hz",
                                )
                            }

                            5 -> {
                                if (thermalAvailable) {
                                    ThermalRow(
                                        selected = selectedThermal,
                                        onClick = { showThermalSheet = true },
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: System Triggers
                item {
                    SectionLabel("System triggers")
                }

                item {
                    ExpressiveList(count = 2) { index ->
                        when (index) {
                            0 -> {
                                SwitchRow(
                                    title = stringResource(R.string.games_dnd),
                                    subtitle = stringResource(R.string.games_priority_notif_sub),
                                    checked = enableDnd,
                                    onCheck = { updateAndSave(dnd = it) },
                                )
                            }

                            1 -> {
                                SwitchRow(
                                    title = stringResource(R.string.games_auto_record),
                                    subtitle = stringResource(R.string.games_auto_bench_sub),
                                    checked = autoRecord,
                                    onCheck = {
                                        autoRecord = it
                                        autoRecordPrefs.setAutoRecordEnabled(game.packageName, it)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete && onRemove != null) {
        DeleteProfileConfirmationDialog(
            appLabel = appLabel,
            onConfirm = {
                pendingDelete = false
                onRemove()
            },
            onDismiss = { pendingDelete = false },
        )
    }
}
