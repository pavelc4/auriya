package dev.auriya.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.record.components.*
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.viewmodel.UiViewModel

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

    // Keep an in-memory rolling window of recent FPS points for the live sparkline
    val fpsHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(liveStats?.fps?.avg) {
        val currentAvg = liveStats?.fps?.avg?.toFloat()
        if (currentAvg != null && currentAvg > 0f) {
            fpsHistory.add(currentAvg)
            if (fpsHistory.size > 30) {
                fpsHistory.removeAt(0)
            }
        }
    }

    var selectedSessionForDetail by remember { mutableStateOf<BenchmarkSession?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (selectedSessionForDetail != null) {
        BenchmarkDetailBottomSheet(
            session = selectedSessionForDetail!!,
            onDismiss = { selectedSessionForDetail = null },
            onDelete = {
                viewModel.deleteBenchmarkSession(selectedSessionForDetail!!.id)
                selectedSessionForDetail = null
            },
            sheetState = detailSheetState
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear all benchmarks?") },
            text = { Text("All saved benchmark sessions and history will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllBenchmarkSessions()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
    ) {
        // --- 1. TOP PINNED HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Telemetry & Record",
                    style = ExpTitleTypography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "Live FPS performance and session benchmark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (benchmarkSessions.isNotEmpty()) {
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- 2. MAIN CONTENT STACKED SHEET ---
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
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Live FPS Hero Card
                item {
                    val stats = liveStats
                    LiveFpsHeroCard(
                        fps = stats?.fps,
                        session = stats?.session ?: dev.auriya.app.data.stats.Session(),
                        isRecording = isRecording,
                        recordingDurationSec = recordingDurationSec,
                        recordedSamplesCount = recordedSamplesCount,
                        fpsHistory = fpsHistory
                    )
                }

                // Section 2: Manual Recording Action Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRecording) "Recording Session Active" else "Session Benchmark",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRecording) {
                                        "${recordedSamplesCount} samples captured • ${formatDuration(recordingDurationSec)}"
                                    } else "Capture live FPS and frametime stability report",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            if (isRecording) {
                                Button(
                                    onClick = { viewModel.stopRecording() },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = "Stop",
                                        modifier = Modifier.size(18.dp)
                                    )
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
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FiberManualRecord,
                                        contentDescription = "Record",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Record")
                                }
                            }
                        }
                    }
                }

                // Section 3: Hardware Telemetry Cards
                item {
                    Text(
                        text = "HARDWARE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                item {
                    CpuTelemetryCard(cpu = liveStats?.cpu)
                }

                item {
                    GpuTelemetryCard(gpu = liveStats?.gpu)
                }

                item {
                    ThermalTelemetryCard(thermal = liveStats?.thermal ?: dev.auriya.app.data.stats.Thermal())
                }

                item {
                    BatteryTelemetryCard(battery = liveStats?.battery ?: dev.auriya.app.data.stats.Battery())
                }

                // Section 4: Recorded Benchmark History
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "BENCHMARK SESSIONS (${benchmarkSessions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (benchmarkSessions.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No recorded sessions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Enable Auto Record in Game Settings or tap Record to capture a benchmark session.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(benchmarkSessions, key = { it.id }) { session ->
                        BenchmarkSessionCard(
                            session = session,
                            onClick = { selectedSessionForDetail = session },
                            onDelete = { viewModel.deleteBenchmarkSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}
