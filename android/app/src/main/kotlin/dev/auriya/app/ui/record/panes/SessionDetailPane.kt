package dev.auriya.app.ui.record.panes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.data.AppPrefs
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.record.components.SessionCpuLoadChartCard
import dev.auriya.app.ui.record.components.SessionFpsChartCard
import dev.auriya.app.ui.record.components.SessionFpsDistributionCard
import dev.auriya.app.ui.record.components.SessionHeaderCard
import dev.auriya.app.ui.record.components.SessionPerformanceMetricsCard
import dev.auriya.app.ui.record.components.SessionThermalChartCard
import dev.auriya.app.ui.record.components.SessionThermalHardwareRow

@Composable
fun SessionDetailPane(
    session: BenchmarkSession,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val roundFps by remember(context) { AppPrefs.getInstance(context) }.roundFps.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
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
            // Header Info Card
            item {
                SessionHeaderCard(session = session)
            }

            // Thermal Peak Summary Section (Directly below game card)
            item {
                Text(
                    text = stringResource(R.string.record_sec_thermal_peaks),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                SessionThermalHardwareRow(session = session)
            }

            // Hero Performance Metric Card
            item {
                SessionPerformanceMetricsCard(
                    session = session,
                    roundFps = roundFps,
                )
            }

            // Stability Distribution (Directly below performance metrics)
            if (session.samples.isNotEmpty()) {
                item {
                    SessionFpsDistributionCard(
                        session = session,
                    )
                }
            }

            // Timeline Cards
            if (session.samples.size > 1) {
                item {
                    Text(
                        text = stringResource(R.string.record_timeline_perf),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }

                item {
                    SessionFpsChartCard(
                        session = session,
                        roundFps = roundFps,
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.record_timeline_hardware),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }

                item {
                    SessionCpuLoadChartCard(
                        session = session,
                    )
                }

                item {
                    SessionThermalChartCard(
                        session = session,
                    )
                }
            }
        }
    }
}
