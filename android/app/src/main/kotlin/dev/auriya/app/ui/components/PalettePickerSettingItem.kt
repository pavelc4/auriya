package dev.auriya.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.theme.PALETTE_PRESETS
import dev.auriya.app.data.theme.PaletteItem

/**
 * Quadrant-divided color swatch dots for palette selection.
 */
@Composable
fun SwatchDot(
    item: PaletteItem,
    selected: Boolean,
    pulseScale: Float = 1f,
    pulseAlpha: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && pulseAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .scale(pulseScale)
                        .border(BorderStroke(2.dp, item.primary.copy(alpha = pulseAlpha)), CircleShape),
            )
        }
        Canvas(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
        ) {
            drawArc(color = item.primary, startAngle = 180f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.secondary, startAngle = 270f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.neutral, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(1.dp, item.primary), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = item.primary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * Reusable Setting Row for accent palette picker.
 */
@Composable
fun PalettePickerSettingItem(
    seedColor: Int,
    onSeedChange: (Int) -> Unit,
    pulseScale: Float = 1f,
    pulseAlpha: Float = 0f,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    presets: List<PaletteItem> = PALETTE_PRESETS,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Accent Palette",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Select a vibrant seed accent color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(presets) { item ->
                    SwatchDot(
                        item = item,
                        selected = item.seed == seedColor,
                        pulseScale = pulseScale,
                        pulseAlpha = pulseAlpha,
                        onClick = { onSeedChange(item.seed) },
                    )
                }
            }
        }
    }
}
