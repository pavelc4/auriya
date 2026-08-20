package dev.auriya.app.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.components.SettingsMenuItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.record.components.DocItem
import dev.auriya.app.ui.record.components.RecordDocBottomSheet
import dev.auriya.app.ui.record.components.formatDuration
import dev.auriya.app.ui.record.panes.BatteryDetailPane
import dev.auriya.app.ui.record.panes.FpsDetailPane
import dev.auriya.app.ui.record.panes.SessionDetailPane
import dev.auriya.app.ui.record.panes.SessionsHistoryPane
import dev.auriya.app.ui.record.panes.ThermalDetailPane
import dev.auriya.app.ui.record.popups.SessionActionsPopup
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.viewmodel.UiViewModel

private enum class RecordSubScreen {
    NONE,
    FPS_DETAILS,
    THERMALS,
    BATTERY,
    SESSIONS,
    SESSION_DETAIL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: UiViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val liveStats by viewModel.liveStats.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationSec by viewModel.currentRecordingDurationSec.collectAsState()
    val recordedSamplesCount by viewModel.currentSamplesCount.collectAsState()
    val benchmarkSessions by viewModel.benchmarkSessions.collectAsState()

    var activeSubScreen by remember { mutableStateOf(RecordSubScreen.NONE) }
    var selectedSession by remember { mutableStateOf<BenchmarkSession?>(null) }

    var showDocSheet by remember { mutableStateOf(false) }
    val docSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSessionActionsPopup by remember { mutableStateOf(false) }
    val sessionActionsPopupState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = activeSubScreen != RecordSubScreen.NONE) {
        if (activeSubScreen == RecordSubScreen.SESSION_DETAIL) {
            activeSubScreen = RecordSubScreen.SESSIONS
            selectedSession = null
        } else {
            activeSubScreen = RecordSubScreen.NONE
        }
    }

    // Keep an in-memory rolling window of recent FPS points for live graphs
    val fpsHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(liveStats?.fps?.avg) {
        val currentAvg = liveStats?.fps?.avg?.toFloat()
        if (currentAvg != null && currentAvg > 0f) {
            fpsHistory.add(currentAvg)
            if (fpsHistory.size > 40) {
                fpsHistory.removeAt(0)
            }
        }
    }

    // Documentation items per sub-screen
    val docData =
        when (activeSubScreen) {
            RecordSubScreen.NONE -> {
                Pair(
                    "Telemetry & Record",
                    listOf(
                        DocItem(
                            "Live Hardware Telemetry",
                            "Auriya polls hardware metrics from the root daemon at 1 Hz with zero background overhead.",
                            Icons.Outlined.Analytics,
                        ),
                        DocItem(
                            "Session Benchmarks",
                            "Record frame rates, 1% lows, and thermal spikes during gameplay matches to compare profile performance.",
                            Icons.Outlined.FiberManualRecord,
                        ),
                        DocItem(
                            "Auto Record Trigger",
                            "Enable Auto Record in any Game Profile to automatically start recording when that game launches.",
                            Icons.Outlined.Bolt,
                        ),
                    ),
                )
            }

            RecordSubScreen.FPS_DETAILS -> {
                Pair(
                    "FPS & Frametimes Guide",
                    listOf(
                        DocItem(
                            "Average FPS",
                            "Mean frame rate calculated over the active measurement window, representing overall target fluidity.",
                            Icons.Outlined.Speed,
                        ),
                        DocItem(
                            "1% Low FPS",
                            "The average of the slowest 1% of frames rendered. A high 1% Low means zero stutter and consistent pace.",
                            Icons.AutoMirrored.Filled.TrendingDown,
                        ),
                        DocItem(
                            "Peak Frame Rate",
                            "The highest instantaneous rendering speed achieved during the current active rendering session.",
                            Icons.AutoMirrored.Filled.TrendingUp,
                        ),
                        DocItem(
                            "Jank & Missed VSYNC",
                            "Frames that took longer than 1.5x of the display refresh period, causing noticeable visual hitching.",
                            Icons.Outlined.WarningAmber,
                        ),
                        DocItem(
                            "Render Sparkline",
                            "Real-time rolling waveform showing rendering stability and frame variance over time.",
                            Icons.AutoMirrored.Filled.ShowChart,
                        ),
                    ),
                )
            }

            RecordSubScreen.THERMALS -> {
                Pair(
                    "Thermals Guide",
                    listOf(
                        DocItem(
                            "Thermal Insight",
                            "Global silicon thermal state analysis and kernel thermal mitigation detection.",
                            Icons.Outlined.DeviceThermostat,
                        ),
                        DocItem(
                            "CPU Junction",
                            "Primary ARM core cluster temperatures polled directly from kernel thermal zone sysfs interfaces.",
                            Icons.Outlined.Memory,
                        ),
                        DocItem(
                            "Battery Cell",
                            "Lithium polymer pack temperature. Maintained within safe thresholds to prevent chemical degradation.",
                            Icons.Outlined.BatteryChargingFull,
                        ),
                        DocItem(
                            "Headroom Margin",
                            "The temperature buffer available before kernel thermal mitigation throttles CPU frequencies.",
                            Icons.Outlined.Shield,
                        ),
                        DocItem(
                            "Cooling Profile",
                            "Indicates whether governors are operating at peak boost or under active thermal mitigation.",
                            Icons.Outlined.Air,
                        ),
                    ),
                )
            }

            RecordSubScreen.BATTERY -> {
                Pair(
                    "Battery Power Guide",
                    listOf(
                        DocItem(
                            "Current Flow (mA)",
                            "Net electrical current entering (+) or exiting (-) the battery cells in real-time.",
                            Icons.Outlined.ElectricBolt,
                        ),
                        DocItem(
                            "Active Wattage (W)",
                            "Real-time power consumption calculated using instantaneous voltage and current (P = V × I).",
                            Icons.Outlined.Bolt,
                        ),
                        DocItem(
                            "Terminal Voltage (V)",
                            "Operating potential of the battery cell pack. Decreases linearly as charge is consumed.",
                            Icons.Outlined.ElectricalServices,
                        ),
                        DocItem(
                            "Health Condition",
                            "Internal state of health reported by the fuel-gauge IC, reflecting chemical cycle integrity.",
                            Icons.Outlined.HealthAndSafety,
                        ),
                    ),
                )
            }

            RecordSubScreen.SESSIONS -> {
                Pair(
                    "Benchmark History Guide",
                    listOf(
                        DocItem(
                            "Recorded Sessions",
                            "Historical records of gameplay sessions with duration, average FPS, 1% low, and sample count.",
                            Icons.Outlined.History,
                        ),
                        DocItem(
                            "Spreadsheet Export (CSV)",
                            "Export full benchmark datasets to Microsoft Excel or Google Sheets for in-depth data analysis.",
                            Icons.Outlined.TableChart,
                        ),
                        DocItem(
                            "Sandbox Storage",
                            "Saved benchmark files are securely stored in the app sandbox without requiring storage permissions.",
                            Icons.Outlined.Storage,
                        ),
                    ),
                )
            }

            RecordSubScreen.SESSION_DETAIL -> {
                Pair(
                    "Session Report Guide",
                    listOf(
                        DocItem(
                            "Excel & Google Sheets",
                            "Export complete second-by-second FPS, thermal, and load telemetry to CSV format.",
                            Icons.Outlined.TableChart,
                        ),
                        DocItem(
                            "1% Low Metric",
                            "Represents the bottom 1% frame rate threshold, proving whether gameplay maintained smooth pacing.",
                            Icons.AutoMirrored.Filled.TrendingDown,
                        ),
                        DocItem(
                            "Thermal Headroom",
                            "Monitors peak CPU and Battery heat reached during heavy multiplayer match combat.",
                            Icons.Outlined.DeviceThermostat,
                        ),
                    ),
                )
            }
        }

    if (showDocSheet) {
        RecordDocBottomSheet(
            title = docData.first,
            subtitle = "Overview of metrics and behavior for this screen",
            items = docData.second,
            onDismiss = { showDocSheet = false },
            sheetState = docSheetState,
        )
    }

    if (showSessionActionsPopup) {
        SessionActionsPopup(
            show = showSessionActionsPopup,
            session = selectedSession,
            sheetState = sessionActionsPopupState,
            onDeleteRequest = {
                selectedSession?.let { session ->
                    viewModel.deleteBenchmarkSession(session.id)
                    activeSubScreen = RecordSubScreen.SESSIONS
                    selectedSession = null
                }
            },
            onDismiss = { showSessionActionsPopup = false },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // --- 1. TOP PINNED HEADER ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (activeSubScreen != RecordSubScreen.NONE) {
                    FilledIconButton(
                        onClick = {
                            if (activeSubScreen == RecordSubScreen.SESSION_DETAIL) {
                                activeSubScreen = RecordSubScreen.SESSIONS
                                selectedSession = null
                            } else {
                                activeSubScreen = RecordSubScreen.NONE
                            }
                        },
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
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            when (activeSubScreen) {
                                RecordSubScreen.NONE -> "Record & Stats"
                                RecordSubScreen.FPS_DETAILS -> "FPS & Frametimes"
                                RecordSubScreen.THERMALS -> "Thermals"
                                RecordSubScreen.BATTERY -> "Battery"
                                RecordSubScreen.SESSIONS -> "Benchmark History"
                                RecordSubScreen.SESSION_DETAIL -> selectedSession?.appLabel ?: "Benchmark"
                            },
                        style =
                            ExpTitleTypography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            when (activeSubScreen) {
                                RecordSubScreen.NONE -> {
                                    "Live performance & session benchmark"
                                }

                                RecordSubScreen.FPS_DETAILS -> {
                                    "Real-time frame rates and stability"
                                }

                                RecordSubScreen.THERMALS -> {
                                    "CPU and Battery temperature sensors"
                                }

                                RecordSubScreen.BATTERY -> {
                                    "Power level, discharge rate, and health"
                                }

                                RecordSubScreen.SESSIONS -> {
                                    "${benchmarkSessions.size} saved benchmark sessions"
                                }

                                RecordSubScreen.SESSION_DETAIL -> {
                                    selectedSession?.let {
                                        "${it.profile.uppercase()} • ${formatDuration(it.durationSeconds)} • ${it.samplesCount} samples"
                                    } ?: "Performance metrics"
                                }
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Right Action: Status Pill + Actions/Info Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRecording) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                            )
                            Text(
                                text = formatDuration(recordingDurationSec),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (activeSubScreen == RecordSubScreen.SESSION_DETAIL) {
                            showSessionActionsPopup = true
                        } else {
                            showDocSheet = true
                        }
                    },
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector =
                            if (activeSubScreen == RecordSubScreen.SESSION_DETAIL) {
                                Icons.Outlined.MoreVert
                            } else {
                                Icons.AutoMirrored.Filled.HelpOutline
                            },
                        contentDescription =
                            if (activeSubScreen == RecordSubScreen.SESSION_DETAIL) {
                                "Session Actions"
                            } else {
                                "Help & Documentation"
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // --- 2. SUB-SCREEN ANIMATED CONTENT ---
        AnimatedContent(
            targetState = activeSubScreen,
            transitionSpec = {
                if (targetState != RecordSubScreen.NONE) {
                    (
                        slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            initialOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() },
                        ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() },
                        ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic)),
                    )
                } else {
                    (
                        slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            initialOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() },
                        ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            targetOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() },
                        ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic)),
                    )
                }
            },
            label = "RecordSubScreenTransition",
            modifier = Modifier.weight(1f),
        ) { currentSubScreen ->
            when (currentSubScreen) {
                RecordSubScreen.FPS_DETAILS -> {
                    FpsDetailPane(
                        fps = liveStats?.fps,
                        session =
                            liveStats?.session ?: dev.auriya.app.data.stats
                                .Session(),
                        fpsHistory = fpsHistory,
                        lastSession = benchmarkSessions.firstOrNull(),
                    )
                }

                RecordSubScreen.THERMALS -> {
                    ThermalDetailPane(
                        thermal =
                            liveStats?.thermal ?: dev.auriya.app.data.stats
                                .Thermal(),
                    )
                }

                RecordSubScreen.BATTERY -> {
                    BatteryDetailPane(
                        battery =
                            liveStats?.battery ?: dev.auriya.app.data.stats
                                .Battery(),
                    )
                }

                RecordSubScreen.SESSIONS -> {
                    SessionsHistoryPane(
                        sessions = benchmarkSessions,
                        onSelectSession = { session ->
                            selectedSession = session
                            activeSubScreen = RecordSubScreen.SESSION_DETAIL
                        },
                        onDeleteSession = { viewModel.deleteBenchmarkSession(it) },
                        onDeleteSessions = { viewModel.deleteBenchmarkSessions(it) },
                    )
                }

                RecordSubScreen.SESSION_DETAIL -> {
                    val session = selectedSession
                    if (session != null) {
                        SessionDetailPane(
                            session = session,
                        )
                    }
                }

                RecordSubScreen.NONE -> {
                    // --- CLEAN CONFIG-STYLE MENU HUB ---
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Sleek Live / Latest FPS Hero Card
                            item {
                                val session = liveStats?.session
                                val fps = liveStats?.fps
                                val isSessionActive = session?.active == true
                                val lastSession = benchmarkSessions.firstOrNull()

                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color =
                                                    if (isSessionActive) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else if (lastSession != null) {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    },
                                            ) {
                                                Text(
                                                    text =
                                                        if (isSessionActive) {
                                                            (session.pkg?.substringAfterLast('.') ?: "ACTIVE")
                                                        } else if (lastSession != null) {
                                                            lastSession.profile.uppercase()
                                                        } else {
                                                            "STANDBY"
                                                        },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color =
                                                        if (isSessionActive) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else if (lastSession != null) {
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    fontSize = 10.sp,
                                                )
                                            }

                                            if (isSessionActive) {
                                                Text(
                                                    text = session.profile.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.sp,
                                                )
                                            } else if (lastSession != null) {
                                                Text(
                                                    text = lastSession.appLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 11.sp,
                                                )
                                            } else {
                                                Text(
                                                    text = "System Monitor",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            val displayFpsText =
                                                if (isSessionActive && fps != null) {
                                                    "%.1f".format(fps.avg)
                                                } else if (lastSession != null) {
                                                    "%.1f".format(lastSession.avgFps)
                                                } else {
                                                    "--"
                                                }

                                            Text(
                                                text = displayFpsText,
                                                style =
                                                    ExpTitleTypography.titleLarge.copy(
                                                        fontSize = 42.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color =
                                                            if (isSessionActive || lastSession != null) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                    ),
                                            )
                                            Text(
                                                text = "FPS",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 4.dp),
                                            )
                                        }

                                        Text(
                                            text =
                                                if (isSessionActive && fps != null) {
                                                    "1%% Low: %.1f FPS • Peak: %.1f FPS".format(fps.low_1pct, fps.peak)
                                                } else if (lastSession != null) {
                                                    "1%% Low: %.1f FPS • Peak: %.1f FPS • ${formatDuration(
                                                        lastSession.durationSeconds,
                                                    )}".format(lastSession.minLow1Pct, lastSession.maxFps)
                                                } else {
                                                    "Launch a configured game from Gamelist to track live rendering"
                                                },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // --- Section 1: Live Telemetry ---
                            item {
                                SettingsSubsection("LIVE TELEMETRY") {
                                    val fps = liveStats?.fps
                                    val thermal = liveStats?.thermal
                                    val battery = liveStats?.battery

                                    // Item 0: FPS & Frametimes
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Speed,
                                        title = "FPS & Frametimes",
                                        subtitle =
                                            if (fps != null && liveStats?.session?.active == true) {
                                                "Avg %.1f FPS • 1%% Low %.1f FPS • %d Jank".format(fps.avg, fps.low_1pct, fps.jank)
                                            } else {
                                                "View real-time frame rates and sparkline"
                                            },
                                        onClick = { activeSubScreen = RecordSubScreen.FPS_DETAILS },
                                        shape = itemShapeFor(0, 3),
                                    )

                                    // Item 1: Thermals
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DeviceThermostat,
                                        title = "Thermals",
                                        subtitle =
                                            if (thermal != null && (thermal.cpu_c != null || thermal.battery_c != null)) {
                                                val tStr = if (thermal.cpu_c != null) "CPU %.1f°C".format(thermal.cpu_c) else ""
                                                val bStr = if (thermal.battery_c != null) "Battery %.1f°C".format(thermal.battery_c) else ""
                                                listOf(tStr, bStr).filter { it.isNotEmpty() }.joinToString(" • ")
                                            } else {
                                                "Hardware temperature monitoring"
                                            },
                                        onClick = { activeSubScreen = RecordSubScreen.THERMALS },
                                        shape = itemShapeFor(1, 3),
                                    )

                                    // Item 2: Battery
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.BatteryChargingFull,
                                        title = "Battery",
                                        subtitle =
                                            if (battery?.pct != null) {
                                                "${battery.pct}% • ${battery.current_ma ?: 0} mA • ${battery.status ?: "Discharging"}"
                                            } else {
                                                "Power state, discharge rate, and health"
                                            },
                                        onClick = { activeSubScreen = RecordSubScreen.BATTERY },
                                        shape = itemShapeFor(2, 3),
                                    )
                                }
                            }

                            // --- Section 2: Benchmark History ---
                            item {
                                SettingsSubsection("BENCHMARK SESSIONS") {
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Analytics,
                                        title = "Recorded Sessions",
                                        subtitle =
                                            if (benchmarkSessions.isNotEmpty()) {
                                                "${benchmarkSessions.size} sessions recorded • Tap to inspect & export reports"
                                            } else {
                                                "No sessions recorded yet"
                                            },
                                        onClick = { activeSubScreen = RecordSubScreen.SESSIONS },
                                        shape = itemShapeFor(0, 1),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
