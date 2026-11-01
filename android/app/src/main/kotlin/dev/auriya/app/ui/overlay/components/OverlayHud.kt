package dev.auriya.app.ui.overlay.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.theme.GoogleSansRounded

@Immutable
data class CpuClusterInfo(
    val name: String,
    val mhz: Long,
    val loadPct: Float,
)

@Immutable
data class CpuCoreInfo(
    val id: Int,
    val mhz: Long,
    val loadPct: Float,
)

@Immutable
data class OverlayTelemetry(
    val fps: String = "--",
    val rawFps: Float = 0f,
    val cpuText: String = "",
    val cpuClusters: List<String> = emptyList(),
    val cpuClusterLabels: List<String> = emptyList(),
    val cpuClusterList: List<CpuClusterInfo> = emptyList(),
    val cpuCoreList: List<CpuCoreInfo> = emptyList(),
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
    showFps: Boolean = false,
    showCpu: Boolean = false,
    showGpu: Boolean = false,
    showRam: Boolean = false,
    showTemp: Boolean = false,
    showBattery: Boolean = false,
    textSizeSp: Float = 12f,
    bgOpacity: Float = 1f,
    paddingDp: Float = 12f,
    cornerRadiusDp: Float = 16f,
    overlayMode: String = "Full",
    modifier: Modifier = Modifier,
) {
    val isMinimal = overlayMode == "Minimal"
    val textSize = textSizeSp.sp
    val subTextSize = (textSizeSp - 1f).coerceAtLeast(8f).sp
    val padding = paddingDp.dp
    val cornerRadius = cornerRadiusDp.dp

    val hasCpu = showCpu && (telemetry.cpuText.isNotEmpty() || telemetry.cpuClusters.isNotEmpty() || telemetry.cpuClusterList.isNotEmpty() || telemetry.cpuCoreList.isNotEmpty() || telemetry.cpuLoadPct > 0f)
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

    val isMultiRowCpu = hasCpu && (telemetry.cpuStyle == "clusters" || telemetry.cpuStyle == "cores")
    val isVertical = isMultiRowCpu || activeCount > 3

    // Outer backing container — directly respects cornerRadius and bgOpacity (cached to eliminate per-frame GC allocations)
    val pillShape = remember(cornerRadiusDp) { RoundedCornerShape(cornerRadiusDp.dp) }
    val innerPillShape =
        remember(cornerRadiusDp) {
            if (cornerRadiusDp >= 24f) {
                RoundedCornerShape(percent = 50)
            } else {
                val radius = (cornerRadiusDp * 0.7f).coerceIn(0f, 16f)
                RoundedCornerShape(radius.dp)
            }
        }

    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceColor =
        remember(bgOpacity, containerHigh) {
            if (bgOpacity >= 0.98f) {
                containerHigh
            } else {
                containerHigh.copy(alpha = bgOpacity.coerceIn(0f, 1f))
            }
        }

    val hPadding = remember(paddingDp) { paddingDp.dp }
    val vPadding = remember(paddingDp) { (paddingDp * 0.55f).coerceAtLeast(2f).dp }
    val itemSpacing = remember(paddingDp) { (paddingDp * 0.35f).coerceIn(2f, 8f).dp }
    val rowSpacing = remember(paddingDp) { (paddingDp * 0.45f).coerceIn(3f, 10f).dp }
    val pillHPadding = remember(paddingDp) { (paddingDp * 0.55f).coerceIn(5f, 12f).dp }
    val pillVPadding = remember(paddingDp) { (paddingDp * 0.22f).coerceIn(1.5f, 6f).dp }

    Surface(
        shape = pillShape,
        color = surfaceColor,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier.padding(
                    horizontal = hPadding,
                    vertical = vPadding,
                ),
        ) {
            if (isVertical) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
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
                        isVertical = true,
                        textSize = textSizeSp,
                        subTextSize = subTextSize.value,
                        pillShape = innerPillShape,
                        pillHPadding = pillHPadding,
                        pillVPadding = pillVPadding,
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    modifier = Modifier.wrapContentSize(),
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
                        isVertical = false,
                        textSize = textSizeSp,
                        subTextSize = subTextSize.value,
                        pillShape = innerPillShape,
                        pillHPadding = pillHPadding,
                        pillVPadding = pillVPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(percent = 50),
    hPadding: androidx.compose.ui.unit.Dp = 8.dp,
    vPadding: androidx.compose.ui.unit.Dp = 2.5.dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.5.dp),
            modifier = Modifier.padding(horizontal = hPadding, vertical = vPadding),
            content = content,
        )
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
    isVertical: Boolean,
    textSize: Float,
    subTextSize: Float,
    pillShape: androidx.compose.ui.graphics.Shape,
    pillHPadding: androidx.compose.ui.unit.Dp,
    pillVPadding: androidx.compose.ui.unit.Dp,
) {
    if (showFps) {
        MetricPill(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = pillShape,
            hPadding = pillHPadding,
            vPadding = pillVPadding,
        ) {
            Text(
                text = telemetry.fps,
                fontSize = textSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false,
            )
            if (!isMinimal) {
                Text(
                    text = "FPS",
                    fontSize = (subTextSize * 0.82f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    if (hasCpu) {
        when {
            telemetry.cpuStyle == "clusters" && telemetry.cpuClusterList.isNotEmpty() -> {
                telemetry.cpuClusterList.forEach { cluster ->
                    MetricPill(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = pillShape,
                        hPadding = pillHPadding,
                        vPadding = pillVPadding,
                    ) {
                        Text(
                            text = "${cluster.name}:",
                            fontSize = (subTextSize * 0.85f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val loadPct = cluster.loadPct.coerceIn(0f, 100f).toInt()
                        Text(
                            text = "${cluster.mhz} MHz · $loadPct%",
                            fontSize = (subTextSize * 0.9f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            telemetry.cpuStyle == "cores" && telemetry.cpuCoreList.isNotEmpty() -> {
                telemetry.cpuCoreList.forEach { core ->
                    MetricPill(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = pillShape,
                        hPadding = pillHPadding,
                        vPadding = pillVPadding,
                    ) {
                        Text(
                            text = "cpu${core.id}:",
                            fontSize = (subTextSize * 0.85f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val loadPct = core.loadPct.coerceIn(0f, 100f).toInt()
                        Text(
                            text = "${core.mhz} MHz · $loadPct%",
                            fontSize = (subTextSize * 0.9f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            telemetry.cpuStyle == "overview" -> {
                MetricPill(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = pillShape,
                    hPadding = pillHPadding,
                    vPadding = pillVPadding,
                ) {
                    if (!isMinimal) {
                        Text(
                            text = "CPU",
                            fontSize = (subTextSize * 0.85f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = telemetry.cpuText.ifEmpty { "%.0f%%".format(telemetry.cpuLoadPct) },
                        fontSize = subTextSize.sp,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            else -> {
                // Default "tags" style (compact inline cluster badges)
                MetricPill(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = pillShape,
                    hPadding = pillHPadding,
                    vPadding = pillVPadding,
                ) {
                    if (!isMinimal) {
                        Text(
                            text = "CPU",
                            fontSize = (subTextSize * 0.85f).sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (telemetry.cpuClusterLabels.isNotEmpty() && telemetry.cpuClusters.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                        ) {
                            telemetry.cpuClusterLabels.zip(telemetry.cpuClusters).forEachIndexed { idx, (lbl, freq) ->
                                if (idx > 0) {
                                    Text(
                                        text = "·",
                                        fontSize = subTextSize.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                Text(
                                    text = lbl,
                                    fontSize = (subTextSize * 0.85f).sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    text = freq,
                                    fontSize = subTextSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (telemetry.cpuText.isNotEmpty()) telemetry.cpuText else telemetry.cpuClusters.joinToString(" "),
                            fontSize = subTextSize.sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }

    if (hasGpu) {
        MetricPill(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = pillShape,
            hPadding = pillHPadding,
            vPadding = pillVPadding,
        ) {
            if (!isMinimal) {
                Text(
                    text = "GPU",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = telemetry.gpuText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasRam) {
        MetricPill(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = pillShape,
            hPadding = pillHPadding,
            vPadding = pillVPadding,
        ) {
            if (!isMinimal) {
                Text(
                    text = "RAM",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = telemetry.ramText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasTemp) {
        val (tempContainer, tempTextCol) =
            if (telemetry.rawCpuTemp >= 48f) {
                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
            }

        MetricPill(
            containerColor = tempContainer,
            shape = pillShape,
            hPadding = pillHPadding,
            vPadding = pillVPadding,
        ) {
            if (!isMinimal) {
                Text(
                    text = "TEMP",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = tempTextCol.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = telemetry.tempText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = tempTextCol,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    if (hasBat) {
        val (batContainer, batTextCol) =
            if (telemetry.rawBatTemp >= 43f) {
                MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
            }

        MetricPill(
            containerColor = batContainer,
            shape = pillShape,
            hPadding = pillHPadding,
            vPadding = pillVPadding,
        ) {
            if (!isMinimal) {
                Text(
                    text = "BAT",
                    fontSize = (subTextSize * 0.85f).sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Black,
                    color = batTextCol.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = telemetry.batText,
                fontSize = subTextSize.sp,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = batTextCol,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
