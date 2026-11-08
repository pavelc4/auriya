package dev.auriya.app.ui.record.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.record.components.SessionCpuLoadChartCard
import dev.auriya.app.ui.record.components.SessionFpsChartCard
import dev.auriya.app.ui.record.components.SessionFpsDistributionCard
import dev.auriya.app.ui.record.components.SessionThermalChartCard
import dev.auriya.app.ui.record.components.SessionThermalHardwareRow
import dev.auriya.app.ui.record.components.formatDuration
import dev.auriya.app.ui.theme.ExpTitleTypography

@Composable
fun FpsDetailPane(
    lastSession: BenchmarkSession? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val roundFps by dev.auriya.app.data.AppPrefs
        .getInstance(context)
        .roundFps
        .collectAsStateWithLifecycle()

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
            // Hero Status Banner
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val activeGameText =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_active_game)
                            val systemIdleText =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_system_idle)
                            val standbyText =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_standby)
                            val noSessionRecordedText =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.record_no_session_recorded)
                            Text(
                                text = lastSession?.appLabel ?: noSessionRecordedText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color =
                                    if (lastSession != null) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                            ) {
                                Text(
                                    text = lastSession?.profile?.uppercase() ?: standbyText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (lastSession != null) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val meanFpsText =
                                if (lastSession != null) {
                                    dev.auriya.app.data.AppPrefs
                                        .formatFps(lastSession.avgFps, roundFps)
                                } else {
                                    "--"
                                }

                            Text(
                                text = meanFpsText,
                                style =
                                    ExpTitleTypography.titleLarge.copy(
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        color =
                                            if (lastSession != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                            )
                            Text(
                                text =
                                    androidx.compose.ui.res
                                        .stringResource(dev.auriya.app.R.string.record_fps_mean),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }

                        Text(
                            text =
                                if (lastSession != null) {
                                    androidx.compose.ui.res.stringResource(
                                        dev.auriya.app.R.string.record_fps_last_match_summary,
                                        formatDuration(lastSession.durationSeconds),
                                        lastSession.samplesCount,
                                    )
                                } else {
                                    androidx.compose.ui.res
                                        .stringResource(dev.auriya.app.R.string.record_no_benchmark_yet)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 4 Grid Metric Cards (populated with latest session data)
            item {
                val low1Pct =
                    if (lastSession != null) {
                        dev.auriya.app.data.AppPrefs
                            .formatFps(lastSession.minLow1Pct, roundFps)
                    } else {
                        "--"
                    }

                val peakFps =
                    if (lastSession != null) {
                        dev.auriya.app.data.AppPrefs
                            .formatFps(lastSession.maxFps, roundFps)
                    } else {
                        "--"
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailCard(
                        title = stringResource(R.string.record_fps_low),
                        value = if (low1Pct != "--") "$low1Pct FPS" else "--",
                        desc = stringResource(R.string.record_fps_low_desc),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    DetailCard(
                        title = stringResource(R.string.record_fps_peak),
                        value = if (peakFps != "--") "$peakFps FPS" else "--",
                        desc = stringResource(R.string.record_fps_peak_desc),
                        icon = Icons.Outlined.Speed,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            item {
                val low01Pct =
                    if (lastSession != null && lastSession.low01Pct > 0.0) {
                        dev.auriya.app.data.AppPrefs
                            .formatFps(lastSession.low01Pct, roundFps)
                    } else {
                        "--"
                    }

                val stdDev =
                    if (lastSession != null && lastSession.stdDevMs > 0.0) {
                        "%.2f ms".format(lastSession.stdDevMs)
                    } else {
                        "--"
                    }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailCard(
                        title = stringResource(R.string.record_fps_low_01),
                        value = if (low01Pct != "--") "$low01Pct FPS" else "--",
                        desc = stringResource(R.string.record_fps_low_01_desc),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    DetailCard(
                        title = stringResource(R.string.record_frame_pacing),
                        value = stdDev,
                        desc = stringResource(R.string.record_frame_pacing_desc),
                        icon = Icons.Outlined.Timeline,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            item {
                val jank = if (lastSession != null) "${lastSession.totalJank}" else "--"
                val samples = if (lastSession != null) "${lastSession.samplesCount}" else "--"

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailCard(
                        title = stringResource(R.string.record_jank_stutter),
                        value = if (jank != "--") "$jank frames" else "--",
                        desc = stringResource(R.string.record_jank_desc),
                        icon = Icons.Outlined.WarningAmber,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    DetailCard(
                        title = stringResource(R.string.record_sample_window),
                        value = samples,
                        desc = stringResource(R.string.record_sample_window_desc),
                        icon = Icons.Outlined.Analytics,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            // All Session Charts (Imported when reviewing last match / session)
            if (lastSession != null && lastSession.samples.size >= 2) {
                // 1. Stability Distribution
                item {
                    SessionFpsDistributionCard(session = lastSession)
                }

                // 2. Performance Timeline (FPS & 1% Low)
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
                        session = lastSession,
                        roundFps = roundFps,
                    )
                }

                // 3. Hardware & Thermal Timelines (CPU Load & Thermals)
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
                    SessionCpuLoadChartCard(session = lastSession)
                }

                item {
                    SessionThermalChartCard(session = lastSession)
                }

                // 4. Thermal & Hardware Peaks
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
                    SessionThermalHardwareRow(session = lastSession)
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text =
                                        androidx.compose.ui.res
                                            .stringResource(dev.auriya.app.R.string.record_no_benchmark_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text =
                                        androidx.compose.ui.res
                                            .stringResource(dev.auriya.app.R.string.record_no_benchmark_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    value: String,
    desc: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
