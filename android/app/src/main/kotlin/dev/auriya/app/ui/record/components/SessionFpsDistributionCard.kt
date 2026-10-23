package dev.auriya.app.ui.record.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.ui.theme.GoogleSansRounded

private data class FpsTier(
    val label: String,
    val subtitle: String,
    val count: Int,
    val percentage: Float,
    val color: Color,
)

/**
 * Material 3 Horizontal Bar Chart representing Frame Rate Consistency Distribution.
 * Directly modeled after the Google Material 3 Data Visualization reference.
 */
@Composable
fun SessionFpsDistributionCard(
    session: BenchmarkSession,
    modifier: Modifier = Modifier,
) {
    val samples = session.samples
    if (samples.isEmpty()) return

    val targetFps =
        remember(session.maxFps) {
            when {
                session.maxFps >= 135 -> 144
                session.maxFps >= 110 -> 120
                session.maxFps >= 80 -> 90
                else -> 60
            }
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error

    val tiers =
        remember(samples, targetFps) {
            val total = samples.size.toFloat()
            var smoothCount = 0
            var normalCount = 0
            var dropCount = 0
            var severeCount = 0

            val smoothThreshold = targetFps * 0.95
            val normalThreshold = targetFps * 0.80
            val dropThreshold = targetFps * 0.60

            for (s in samples) {
                when {
                    s.fps >= smoothThreshold -> smoothCount++
                    s.fps >= normalThreshold -> normalCount++
                    s.fps >= dropThreshold -> dropCount++
                    else -> severeCount++
                }
            }

            listOf(
                FpsTier(
                    label = "Smooth",
                    subtitle = "≥ ${smoothThreshold.toInt()} FPS",
                    count = smoothCount,
                    percentage = (smoothCount / total) * 100f,
                    color = primaryColor,
                ),
                FpsTier(
                    label = "Stable",
                    subtitle = "${normalThreshold.toInt()}–${smoothThreshold.toInt()} FPS",
                    count = normalCount,
                    percentage = (normalCount / total) * 100f,
                    color = tertiaryColor,
                ),
                FpsTier(
                    label = "Drops",
                    subtitle = "${dropThreshold.toInt()}–${normalThreshold.toInt()} FPS",
                    count = dropCount,
                    percentage = (dropCount / total) * 100f,
                    color = secondaryColor,
                ),
                FpsTier(
                    label = "Stutter",
                    subtitle = "< ${dropThreshold.toInt()} FPS",
                    count = severeCount,
                    percentage = (severeCount / total) * 100f,
                    color = errorColor,
                ),
            )
        }

    var animateBars by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateBars = true
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Stability Distribution",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Target frame consistency percentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.height(30.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Target: $targetFps FPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // 2. Horizontal Bars List (Modeled after Material 3 "Sales by region" reference)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tiers.forEach { tier ->
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (animateBars) (tier.percentage / 100f).coerceIn(0f, 1f) else 0f,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                        label = "barFill_${tier.label}",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Label Column
                        Column(
                            modifier = Modifier.width(72.dp),
                        ) {
                            Text(
                                text = tier.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tier.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Horizontal Bar Container
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            if (animatedFraction > 0.005f) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedFraction)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(tier.color),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Percentage & Count Text
                        Text(
                            text = "%.1f%%".format(tier.percentage),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }

            // 3. Bottom Summary Info Chips
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Total Samples: ${samples.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = "Jank Events: ${session.totalJank}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (session.totalJank > 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (session.totalJank > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
