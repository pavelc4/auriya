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
    val (docTitleRes, docItems) =
        when (activeSubScreen) {
            RecordSubScreen.NONE -> {
                Pair(
                    dev.auriya.app.R.string.guide_telemetry_title,
                    listOf(
                        DocItem(
                            dev.auriya.app.R.string.guide_telemetry_1_title,
                            dev.auriya.app.R.string.guide_telemetry_1_desc,
                            Icons.Outlined.Analytics,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_telemetry_2_title,
                            dev.auriya.app.R.string.guide_telemetry_2_desc,
                            Icons.Outlined.FiberManualRecord,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_telemetry_3_title,
                            dev.auriya.app.R.string.guide_telemetry_3_desc,
                            Icons.Outlined.Bolt,
                        ),
                    ),
                )
            }

            RecordSubScreen.FPS_DETAILS -> {
                Pair(
                    dev.auriya.app.R.string.guide_fps_title,
                    listOf(
                        DocItem(
                            dev.auriya.app.R.string.guide_fps_avg_title,
                            dev.auriya.app.R.string.guide_fps_avg_desc,
                            Icons.Outlined.Speed,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_fps_low_title,
                            dev.auriya.app.R.string.guide_fps_low_desc,
                            Icons.AutoMirrored.Filled.TrendingDown,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_fps_peak_title,
                            dev.auriya.app.R.string.guide_fps_peak_desc,
                            Icons.AutoMirrored.Filled.TrendingUp,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_fps_jank_title,
                            dev.auriya.app.R.string.guide_fps_jank_desc,
                            Icons.Outlined.WarningAmber,
                        ),
                    ),
                )
            }

            RecordSubScreen.THERMALS -> {
                Pair(
                    dev.auriya.app.R.string.guide_thermals_title,
                    listOf(
                        DocItem(
                            dev.auriya.app.R.string.guide_thermals_cpu_title,
                            dev.auriya.app.R.string.guide_thermals_cpu_desc,
                            Icons.Outlined.Memory,
                        ),
                        DocItem(
                            dev.auriya.app.R.string.guide_thermals_battery_title,
                            dev.auriya.app.R.string.guide_thermals_battery_desc,
                            Icons.Outlined.BatteryChargingFull,
                        ),
                    ),
                )
            }

            RecordSubScreen.BATTERY -> {
                Pair(
                    dev.auriya.app.R.string.guide_battery_title,
                    listOf(
                        DocItem(
                            dev.auriya.app.R.string.guide_battery_title,
                            dev.auriya.app.R.string.record_battery_sub,
                            Icons.Outlined.ElectricBolt,
                        ),
                    ),
                )
            }

            RecordSubScreen.SESSIONS, RecordSubScreen.SESSION_DETAIL -> {
                Pair(
                    dev.auriya.app.R.string.guide_sessions_title,
                    listOf(
                        DocItem(
                            dev.auriya.app.R.string.record_sessions,
                            dev.auriya.app.R.string.record_sessions_sub,
                            Icons.Outlined.History,
                        ),
                    ),
                )
            }
        }

    if (showDocSheet) {
        RecordDocBottomSheet(
            titleRes = docTitleRes,
            subtitleRes = dev.auriya.app.R.string.guide_overview,
            items = docItems,
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

                AnimatedContent(
                    targetState = activeSubScreen,
                    transitionSpec = {
                        (fadeIn(tween(200, easing = EaseOutCubic)) + slideInVertically(initialOffsetY = { -it / 4 }))
                            .togetherWith(fadeOut(tween(150, easing = EaseInCubic)) + slideOutVertically(targetOffsetY = { it / 4 }))
                    },
                    label = "RecordHeaderTransition",
                    modifier = Modifier.weight(1f),
                ) { targetSubScreen ->
                    Column {
                        val titleRes =
                            when (targetSubScreen) {
                                RecordSubScreen.NONE -> dev.auriya.app.R.string.record_title
                                RecordSubScreen.FPS_DETAILS -> dev.auriya.app.R.string.record_fps_details
                                RecordSubScreen.THERMALS -> dev.auriya.app.R.string.record_thermals
                                RecordSubScreen.BATTERY -> dev.auriya.app.R.string.record_battery
                                RecordSubScreen.SESSIONS -> dev.auriya.app.R.string.record_sessions
                                RecordSubScreen.SESSION_DETAIL -> null
                            }
                        val subtitleRes =
                            when (targetSubScreen) {
                                RecordSubScreen.NONE -> dev.auriya.app.R.string.record_subtitle
                                RecordSubScreen.FPS_DETAILS -> dev.auriya.app.R.string.record_fps_details_sub
                                RecordSubScreen.THERMALS -> dev.auriya.app.R.string.record_thermals_sub
                                RecordSubScreen.BATTERY -> dev.auriya.app.R.string.record_battery_sub
                                RecordSubScreen.SESSIONS -> dev.auriya.app.R.string.record_sessions_sub
                                RecordSubScreen.SESSION_DETAIL -> null
                            }

                        Text(
                            text =
                                if (titleRes != null) {
                                    androidx.compose.ui.res
                                        .stringResource(titleRes)
                                } else {
                                    (selectedSession?.appLabel ?: "Benchmark")
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
                                if (subtitleRes != null) {
                                    androidx.compose.ui.res
                                        .stringResource(subtitleRes)
                                } else {
                                    selectedSession?.let {
                                        "${it.profile.uppercase()} • ${formatDuration(it.durationSeconds)} • ${it.samplesCount} samples"
                                    } ?: "Performance metrics"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        val roundFps by viewModel.roundFps.collectAsState()
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
                                                            "LIVE TELEMETRY"
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
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.tertiary),
                                                        )
                                                        Text(
                                                            text =
                                                                androidx.compose.ui.res
                                                                    .stringResource(dev.auriya.app.R.string.common_active),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            fontSize = 10.sp,
                                                        )
                                                    }
                                                }
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
                                                    dev.auriya.app.data.AppPrefs
                                                        .formatFps(fps.avg, roundFps)
                                                } else if (lastSession != null) {
                                                    dev.auriya.app.data.AppPrefs
                                                        .formatFps(lastSession.avgFps, roundFps)
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
                                                    "1% Low: ${dev.auriya.app.data.AppPrefs.formatFps(fps.low_1pct, roundFps)} FPS • Peak: ${dev.auriya.app.data.AppPrefs.formatFps(fps.peak, roundFps)} FPS"
                                                } else if (lastSession != null) {
                                                    "1% Low: ${dev.auriya.app.data.AppPrefs.formatFps(lastSession.minLow1Pct, roundFps)} FPS • Peak: ${dev.auriya.app.data.AppPrefs.formatFps(lastSession.maxFps, roundFps)} FPS • ${formatDuration(
                                                        lastSession.durationSeconds,
                                                    )}"
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
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.record_fps_frametimes),
                                        subtitle =
                                            if (fps != null && liveStats?.session?.active == true) {
                                                "Avg ${dev.auriya.app.data.AppPrefs.formatFps(fps.avg, roundFps)} FPS • 1% Low ${dev.auriya.app.data.AppPrefs.formatFps(fps.low_1pct, roundFps)} FPS • ${fps.jank} Jank"
                                            } else {
                                                "View real-time frame rates and sparkline"
                                            },
                                        onClick = { activeSubScreen = RecordSubScreen.FPS_DETAILS },
                                        shape = itemShapeFor(0, 3),
                                    )

                                    // Item 1: Thermals
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DeviceThermostat,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.record_thermals),
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
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.record_battery),
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
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(dev.auriya.app.R.string.record_recorded_sessions),
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
