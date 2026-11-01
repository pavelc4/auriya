package dev.auriya.app.ui.overlay.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.theme.GoogleSansRounded

data class OverlayTelemetry(
    val fps: String = "--",
    val rawFps: Float = 0f,
    val cpuText: String = "",
    val cpuClusters: List<String> = emptyList(),
    val cpuClusterLabels: List<String> = emptyList(),
    val cpuStyle: String = "tags",
    val cpuLoadPct: Float = 0f,
    val maxCpuFreqGHz: Float = 0f,
    val gpuText: String = "",
    val ramText: String = "",
    val tempText: String = "",
    val batText: String = "",
    val rawCpuTemp: Float = 0f,
    val rawBatTemp: Float = 0f,
)

@Composable
fun OverlayHud(
    telemetry: OverlayTelemetry,
    showFps: Boolean,
    showCpu: Boolean,
    showGpu: Boolean,
    showRam: Boolean,
    showTemp: Boolean,
    showBattery: Boolean,
    textSizeSp: Float,
    bgOpacity: Float,
    paddingDp: Float,
    cornerRadiusDp: Float,
    layoutStyle: String,
    overlayMode: String,
    modifier: Modifier = Modifier,
) {
    val isMinimal = overlayMode == "Minimal"
    val textSize = textSizeSp.sp
    val subTextSize = (textSizeSp - 1f).coerceAtLeast(8f).sp
    val padding = paddingDp.dp
    val cornerRadius = cornerRadiusDp.dp

    val hasCpu = showCpu && (telemetry.cpuText.isNotEmpty() || telemetry.cpuClusters.isNotEmpty() || telemetry.cpuLoadPct > 0f)
    val hasGpu = showGpu && telemetry.gpuText.isNotEmpty()
    val hasRam = showRam && telemetry.ramText.isNotEmpty()
    val hasTemp = showTemp && telemetry.tempText.isNotEmpty()
    val hasBat = showBattery && telemetry.batText.isNotEmpty()

    val activeCount =
        listOf(
            showFps,
            hasCpu,
            hasGpu,
            hasRam,
            hasTemp,
            hasBat,
        ).count { it }

    // Auto-switch to vertical layout when items are crowded (>= 4 items or >= 3 with multi-cluster CPU)
    val forceVertical =
        layoutStyle == "Vertical" ||
            activeCount >= 4 ||
            (activeCount >= 3 && hasCpu && telemetry.cpuClusters.size >= 2)
    val isVertical = forceVertical

    // Single unified solid pill / card container — NO double pills, NO borders, NO status dots!
    val pillShape =
        if (isVertical) {
            RoundedCornerShape(cornerRadius.coerceAtLeast(20.dp))
        } else {
            RoundedCornerShape(percent = 50)
        }

    val surfaceColor =
        if (bgOpacity >= 0.98f) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = bgOpacity)
        }

    Surface(
        shape = pillShape,
        color = surfaceColor,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        val hPadding = if (isVertical) (padding * 0.85f).coerceIn(10.dp, 16.dp) else (padding * 1.1f).coerceIn(12.dp, 18.dp)
        val vPadding = if (isVertical) (padding * 0.65f).coerceIn(6.dp, 12.dp) else (padding * 0.45f).coerceIn(5.dp, 8.dp)

        Box(
            modifier =
                Modifier.padding(
                    horizontal = hPadding,
                    vertical = vPadding,
                ),
        ) {
            if (isVertical) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    HudItems(
                        telemetry = telemetry,
                        showFps = showFps,
                        hasCpu = hasCpu,
                        hasGpu = hasGpu,
                        hasRam = hasRam,
                        hasTemp = hasTemp,
                        hasBat = hasBat,
                        isMinimal = isMinimal,
                        textSize = textSizeSp,
                        subTextSize = subTextSize.value,
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudItems(
                        telemetry = telemetry,
                        showFps = showFps,
                        hasCpu = hasCpu,
                        hasGpu = hasGpu,
                        hasRam = hasRam,
                        hasTemp = hasTemp,
                        hasBat = hasBat,
                        isMinimal = isMinimal,
                        textSize = textSizeSp,
                        subTextSize = subTextSize.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun HudItems(
    telemetry: OverlayTelemetry,
    showFps: Boolean,
    hasCpu: Boolean,
    hasGpu: Boolean,
    hasRam: Boolean,
    hasTemp: Boolean,
    hasBat: Boolean,
    isMinimal: Boolean,
    textSize: Float,
    subTextSize: Float,
) {
    if (showFps) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = telemetry.fps,
                fontSize = textSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
            if (!isMinimal) {
                Text(
                    text = "FPS",
                    fontSize = (subTextSize * 0.82f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    if (hasCpu) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMinimal) {
                Text(
                    text = "CPU",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (telemetry.cpuClusterLabels.isNotEmpty() && telemetry.cpuClusters.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.5.dp),
                ) {
                    telemetry.cpuClusterLabels.zip(telemetry.cpuClusters).forEach { (lbl, freq) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = lbl,
                                fontSize = (subTextSize * 0.85f).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                            )
                            Text(
                                text = freq,
                                fontSize = subTextSize.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (telemetry.cpuText.isNotEmpty()) telemetry.cpuText else telemetry.cpuClusters.joinToString(" "),
                    fontSize = subTextSize.sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    if (hasGpu) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMinimal) {
                Text(
                    text = "GPU",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = telemetry.gpuText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasRam) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMinimal) {
                Text(
                    text = "RAM",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                )
            }
            Text(
                text = telemetry.ramText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasTemp) {
        val (tempLabelColor, tempValColor) =
            when {
                telemetry.rawCpuTemp >= 48f -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onErrorContainer
                telemetry.rawCpuTemp >= 40f -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondaryContainer
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMinimal) {
                Text(
                    text = "TEMP",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = tempLabelColor,
                )
            }
            Text(
                text = telemetry.tempText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = tempValColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasBat) {
        val (batLabelColor, batValColor) =
            when {
                telemetry.rawBatTemp >= 43f -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onErrorContainer
                telemetry.rawBatTemp >= 38f -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimaryContainer
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMinimal) {
                Text(
                    text = "BAT",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = batLabelColor,
                )
            }
            Text(
                text = telemetry.batText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = batValColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
