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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.components.SettingsMenuItem
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.record.components.formatDuration
import dev.auriya.app.ui.record.panes.FpsDetailPane
import dev.auriya.app.ui.record.panes.HardwareDetailPane
import dev.auriya.app.ui.record.panes.SessionsHistoryPane
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.viewmodel.UiViewModel
import kotlin.math.roundToInt

private enum class RecordSubScreen {
    NONE,
    FPS_DETAILS,
    HARDWARE,
    SESSIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: UiViewModel,
    modifier: Modifier = Modifier
) {
    val liveStats by viewModel.liveStats.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationSec by viewModel.currentRecordingDurationSec.collectAsState()
    val recordedSamplesCount by viewModel.currentSamplesCount.collectAsState()
    val benchmarkSessions by viewModel.benchmarkSessions.collectAsState()

    var activeSubScreen by remember { mutableStateOf(RecordSubScreen.NONE) }

    BackHandler(enabled = activeSubScreen != RecordSubScreen.NONE) {
        activeSubScreen = RecordSubScreen.NONE
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // --- 1. TOP PINNED HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (activeSubScreen != RecordSubScreen.NONE) {
                    FilledIconButton(
                        onClick = { activeSubScreen = RecordSubScreen.NONE },
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
                }

                Column {
                    Text(
                        text = when (activeSubScreen) {
                            RecordSubScreen.NONE -> "Record & Stats"
                            RecordSubScreen.FPS_DETAILS -> "FPS & Frametimes"
                            RecordSubScreen.HARDWARE -> "Hardware Telemetry"
                            RecordSubScreen.SESSIONS -> "Benchmark History"
                        },
                        style = ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = when (activeSubScreen) {
                            RecordSubScreen.NONE -> "Live performance & session benchmark"
                            RecordSubScreen.FPS_DETAILS -> "Real-time frame rates and stability"
                            RecordSubScreen.HARDWARE -> "CPU, GPU, Thermal, and Power sensors"
                            RecordSubScreen.SESSIONS -> "${benchmarkSessions.size} saved benchmark sessions"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Top Status Pill
            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = formatDuration(recordingDurationSec),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // --- 2. SUB-SCREEN ANIMATED CONTENT ---
        AnimatedContent(
            targetState = activeSubScreen,
            transitionSpec = {
                if (targetState != RecordSubScreen.NONE) {
                    (slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                        initialOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                    ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            targetOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                        ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic))
                    )
                } else {
                    (slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                    ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            targetOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                        ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic))
                    )
                }
            },
            label = "RecordSubScreenTransition",
            modifier = Modifier.weight(1f)
        ) { currentSubScreen ->
            when (currentSubScreen) {
                RecordSubScreen.FPS_DETAILS -> {
                    FpsDetailPane(
                        fps = liveStats?.fps,
                        session = liveStats?.session ?: dev.auriya.app.data.stats.Session(),
                        fpsHistory = fpsHistory
                    )
                }

                RecordSubScreen.HARDWARE -> {
                    HardwareDetailPane(
                        cpu = liveStats?.cpu,
                        gpu = liveStats?.gpu,
                        thermal = liveStats?.thermal ?: dev.auriya.app.data.stats.Thermal(),
                        battery = liveStats?.battery ?: dev.auriya.app.data.stats.Battery()
                    )
                }

                RecordSubScreen.SESSIONS -> {
                    SessionsHistoryPane(
                        sessions = benchmarkSessions,
                        onDeleteSession = { viewModel.deleteBenchmarkSession(it) }
                    )
                }

                RecordSubScreen.NONE -> {
                    // --- CLEAN CONFIG-STYLE MENU HUB ---
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Sleek Live FPS Hero Card
                            item {
                                val session = liveStats?.session
                                val fps = liveStats?.fps
                                val isSessionActive = session?.active == true

                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSessionActive) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                                ) {
                                                    Text(
                                                        text = if (isSessionActive) (session.pkg?.substringAfterLast('.') ?: "ACTIVE") else "STANDBY",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSessionActive) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                if (isSessionActive && session != null) {
                                                    Text(
                                                        text = session.profile.uppercase(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))

                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = if (fps != null && isSessionActive) "%.1f".format(fps.avg) else "--",
                                                    style = ExpTitleTypography.titleLarge.copy(
                                                        fontSize = 38.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (fps != null && isSessionActive) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                                Text(
                                                    text = "FPS",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                            }

                                            Text(
                                                text = if (fps != null && isSessionActive) {
                                                    "1% Low: %.1f FPS • Peak: %.1f FPS".format(fps.low_1pct, fps.peak)
                                                } else "Start a game to begin live telemetry",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Start / Stop REC Button
                                        if (isRecording) {
                                            FilledTonalButton(
                                                onClick = { viewModel.stopRecording() },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Stop REC")
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val currentPkg = liveStats?.session?.pkg
                                                    val currentProfile = liveStats?.session?.profile ?: "balance"
                                                    viewModel.startRecording(currentPkg, currentProfile)
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Record")
                                            }
                                        }
                                    }
                                }
                            }

                            // --- Section 1: Live Telemetry ---
                            item {
                                SettingsSubsection("LIVE TELEMETRY") {
                                    val fps = liveStats?.fps
                                    val cpu = liveStats?.cpu
                                    val gpu = liveStats?.gpu
                                    val thermal = liveStats?.thermal
                                    val battery = liveStats?.battery

                                    // Item 0: FPS & Frametimes
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Speed,
                                        title = "FPS & Frametimes",
                                        subtitle = if (fps != null && liveStats?.session?.active == true) {
                                            "Avg %.1f FPS • 1%% Low %.1f FPS • %d Jank".format(fps.avg, fps.low_1pct, fps.jank)
                                        } else "View real-time frame rates and sparkline",
                                        onClick = { activeSubScreen = RecordSubScreen.FPS_DETAILS },
                                        shape = itemShapeFor(0, 4)
                                    )

                                    // Item 1: CPU Processor
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Memory,
                                        title = "CPU Processor",
                                        subtitle = if (cpu != null) {
                                            "Load ${cpu.load_pct.roundToInt()}% • ${cpu.cores.size} Active Cores"
                                        } else "Core frequencies & cluster governors",
                                        onClick = { activeSubScreen = RecordSubScreen.HARDWARE },
                                        shape = itemShapeFor(1, 4)
                                    )

                                    // Item 2: GPU Graphics
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DeveloperBoard,
                                        title = "GPU Graphics",
                                        subtitle = if (gpu?.mhz != null) {
                                            "${gpu.mhz} MHz • ${gpu.vendor ?: "GPU"}"
                                        } else "Graphics clock and rendering engine",
                                        onClick = { activeSubScreen = RecordSubScreen.HARDWARE },
                                        shape = itemShapeFor(2, 4)
                                    )

                                    // Item 3: Thermals & Battery
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.DeviceThermostat,
                                        title = "Thermals & Power",
                                        subtitle = if (thermal?.cpu_c != null || battery?.pct != null) {
                                            val tStr = if (thermal?.cpu_c != null) "CPU %.1f°C".format(thermal.cpu_c) else ""
                                            val bStr = if (battery?.pct != null) "Battery ${battery.pct}% (${battery.current_ma ?: 0} mA)" else ""
                                            listOf(tStr, bStr).filter { it.isNotEmpty() }.joinToString(" • ")
                                        } else "Temperature sensors and discharge rates",
                                        onClick = { activeSubScreen = RecordSubScreen.HARDWARE },
                                        shape = itemShapeFor(3, 4)
                                    )
                                }
                            }

                            // --- Section 2: Benchmark History ---
                            item {
                                SettingsSubsection("BENCHMARK SESSIONS") {
                                    SettingsMenuItem(
                                        icon = Icons.Outlined.Analytics,
                                        title = "Recorded Sessions",
                                        subtitle = if (benchmarkSessions.isNotEmpty()) {
                                            "${benchmarkSessions.size} sessions recorded • Tap to inspect & export reports"
                                        } else "No sessions recorded yet",
                                        onClick = { activeSubScreen = RecordSubScreen.SESSIONS },
                                        shape = itemShapeFor(0, 1)
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
