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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.data.stats.Fps
import dev.auriya.app.data.stats.Session
import dev.auriya.app.ui.record.components.FpsSparkline
import dev.auriya.app.ui.record.components.formatDuration
import dev.auriya.app.ui.theme.ExpTitleTypography

@Composable
fun FpsDetailPane(
    fps: Fps?,
    session: Session,
    fpsHistory: List<Float>,
    lastSession: BenchmarkSession? = null,
    modifier: Modifier = Modifier,
) {
    val isLive = session.active && fps != null
    val displayHistory =
        if (isLive && fpsHistory.isNotEmpty()) {
            fpsHistory
        } else if (lastSession != null && lastSession.samples.isNotEmpty()) {
            lastSession.samples.map { it.fps.toFloat() }
        } else {
            emptyList()
        }

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
                            Text(
                                text =
                                    if (isLive) {
                                        (session.pkg?.substringAfterLast('.') ?: "Active Game")
                                    } else if (lastSession != null) {
                                        lastSession.appLabel
                                    } else {
                                        "System Idle"
                                    },
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
                                    if (isLive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else if (lastSession != null) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                            ) {
                                Text(
                                    text =
                                        if (isLive) {
                                            session.profile.uppercase()
                                        } else if (lastSession != null) {
                                            lastSession.profile.uppercase()
                                        } else {
                                            "STANDBY"
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (isLive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else if (lastSession != null) {
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
                                if (isLive) {
                                    "%.1f".format(fps.avg)
                                } else if (lastSession != null) {
                                    "%.1f".format(lastSession.avgFps)
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
                                            if (isLive || lastSession != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                            )
                            Text(
                                text = "FPS (Mean)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }

                        Text(
                            text =
                                if (isLive) {
                                    "Computed from ${fps.frames} rolling frames (~5s buffer)"
                                } else if (lastSession !=
                                    null
                                ) {
                                    "Last match • ${formatDuration(lastSession.durationSeconds)} (${lastSession.samplesCount} samples)"
                                } else {
                                    "Launch a configured game from Gamelist to track live rendering."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Real-time or Latest Session Sparkline Graph
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    if (isLive) {
                                        "Real-time FPS Stream"
                                    } else if (lastSession !=
                                        null
                                    ) {
                                        "Last Match Frametimes"
                                    } else {
                                        "FPS History"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (lastSession != null && !isLive) {
                                Text(
                                    text = formatDuration(lastSession.durationSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (displayHistory.isNotEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                            ) {
                                FpsSparkline(
                                    values = displayHistory,
                                    lineColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SportsEsports,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(26.dp),
                                    )
                                    Text(
                                        text = "FPS sparkline will stream when a game is running",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4 Grid Metric Cards (populated with live or latest session data)
            item {
                val low1Pct =
                    if (isLive) {
                        "%.1f".format(fps.low_1pct)
                    } else if (lastSession != null) {
                        "%.1f".format(lastSession.minLow1Pct)
                    } else {
                        "--"
                    }

                val peakFps =
                    if (isLive) {
                        "%.1f".format(fps.peak)
                    } else if (lastSession != null) {
                        "%.1f".format(lastSession.maxFps)
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
                        title = "1% Low FPS",
                        value = low1Pct,
                        desc = "Mean of slowest 1% frames (stutter)",
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    DetailCard(
                        title = "Peak FPS",
                        value = peakFps,
                        desc = "Fastest single frame render",
                        icon = Icons.Outlined.Speed,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }

            item {
                val jank =
                    if (isLive) {
                        "${fps.jank}"
                    } else if (lastSession != null) {
                        "${lastSession.totalJank}"
                    } else {
                        "--"
                    }

                val samples =
                    if (isLive) {
                        "${fps.frames}"
                    } else if (lastSession != null) {
                        "${lastSession.samplesCount}"
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
                        title = "Jank Stutter",
                        value = jank,
                        desc = "Frames slower than target × 1.5",
                        icon = Icons.Outlined.WarningAmber,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                    DetailCard(
                        title = "Sample Window",
                        value = samples,
                        desc = "Frame sample data points",
                        icon = Icons.Outlined.Analytics,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
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
