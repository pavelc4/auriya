package dev.auriya.app.ui.record.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.Fps
import dev.auriya.app.data.stats.Session
import dev.auriya.app.ui.theme.ExpTitleTypography
import kotlin.math.roundToInt

@Composable
fun LiveFpsHeroCard(
    fps: Fps?,
    session: Session,
    isRecording: Boolean,
    recordingDurationSec: Long,
    recordedSamplesCount: Int,
    fpsHistory: List<Float>,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val roundFps by dev.auriya.app.data.AppPrefs
        .getInstance(context)
        .roundFps
        .collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row: Session State + Recording Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (session.active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (session.active) Icons.Outlined.SportsEsports else Icons.Outlined.Speed,
                                contentDescription = null,
                                tint =
                                    if (session.active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Column {
                        Text(
                            text =
                                if (session.active && !session.pkg.isNullOrEmpty()) {
                                    session.pkg.substringAfterLast('.')
                                } else {
                                    "System Idle"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (session.active) "Profile: ${session.profile.uppercase()}" else "No active game running",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isRecording) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
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
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha)),
                            )
                            Text(
                                text = "REC ${formatDuration(recordingDurationSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (session.active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                    ) {
                        Text(
                            text = if (session.active) "MONITORING" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (session.active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Central Big FPS Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text =
                                if (fps != null && session.active) {
                                    dev.auriya.app.data.AppPrefs
                                        .formatFps(fps.avg, roundFps)
                                } else {
                                    "--"
                                },
                            style =
                                ExpTitleTypography.titleLarge.copy(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Black,
                                    color =
                                        if (fps != null && session.active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                ),
                        )
                        Text(
                            text = "FPS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Text(
                        text =
                            if (fps != null && session.active) {
                                "Based on ${fps.frames} live frame samples"
                            } else {
                                "Start a whitelisted game to view live FPS stats"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Sparkline Real-time Graph
                if (fpsHistory.isNotEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .width(130.dp)
                                .height(52.dp)
                                .padding(bottom = 6.dp),
                    ) {
                        FpsSparkline(
                            values = fpsHistory,
                            lineColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // 4 Core Gamer Metrics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricPill(
                    label = "Avg FPS",
                    value =
                        if (fps != null && session.active) {
                            dev.auriya.app.data.AppPrefs
                                .formatFps(fps.avg, roundFps)
                        } else {
                            "--"
                        },
                    icon = Icons.Outlined.Timeline,
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = "Peak FPS",
                    value =
                        if (fps != null && session.active) {
                            dev.auriya.app.data.AppPrefs
                                .formatFps(fps.peak, roundFps)
                        } else {
                            "--"
                        },
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = "1% Low",
                    value =
                        if (fps != null && session.active) {
                            dev.auriya.app.data.AppPrefs
                                .formatFps(fps.low_1pct, roundFps)
                        } else {
                            "--"
                        },
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = "Jank",
                    value = if (fps != null && session.active) "${fps.jank}" else "--",
                    icon = Icons.Outlined.WarningAmber,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun FpsSparkline(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val maxVal = (values.maxOrNull() ?: 60f).coerceAtLeast(30f)
        val minVal = (values.minOrNull() ?: 0f).coerceAtLeast(0f)
        val range = (maxVal - minVal).coerceAtLeast(10f)

        val width = size.width
        val height = size.height
        val stepX = width / (values.size - 1)

        val path = Path()
        val fillPath = Path()

        values.forEachIndexed { index, v ->
            val x = index * stepX
            val normalized = (v - minVal) / range
            val y = height - (normalized * height * 0.85f) - (height * 0.05f)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevNormalized = (values[index - 1] - minVal) / range
                val prevY = height - (prevNormalized * height * 0.85f) - (height * 0.05f)
                val cX = (prevX + x) / 2f
                path.cubicTo(cX, prevY, cX, y, x, y)
                fillPath.cubicTo(cX, prevY, cX, y, x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush =
                Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = height,
                ),
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
