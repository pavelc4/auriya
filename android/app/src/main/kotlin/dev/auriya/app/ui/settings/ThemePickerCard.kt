package dev.auriya.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.theme.AuriyaTokens

private val PALETTE = listOf(
    0xFFFFB68E.toInt(), // peach (default)
    0xFFFFB4AB.toInt(), // red
    0xFFFFD188.toInt(), // amber
    0xFFA7E0A2.toInt(), // green
    0xFF9ECAFF.toInt(), // blue
    0xFFD0BCFF.toInt(), // violet
    0xFFF8AFD2.toInt(), // pink
    0xFFA7D8DA.toInt(), // teal
)

@Composable
fun ThemePickerCard(
    seedColor: Int,
    useDynamicColor: Boolean,
    navMode: NavMode,
    onSeedChange: (Int) -> Unit,
    onDynamicToggle: (Boolean) -> Unit,
    onNavModeChange: (NavMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = AuriyaTokens.padding.smaller),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Material You",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Use wallpaper colors (Android 12+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useDynamicColor, onCheckedChange = onDynamicToggle)
            }

            Text(
                text = "Theme color",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = AuriyaTokens.padding.small),
            )
            Text(
                text = "Tap a swatch to apply (turns off Material You)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
                modifier = Modifier.padding(top = AuriyaTokens.padding.smaller),
            ) {
                items(PALETTE) { color ->
                    SwatchDot(
                        color = Color(color),
                        selected = !useDynamicColor && color == seedColor,
                        onClick = { onSeedChange(color) },
                    )
                }
            }

            Spacer(Modifier.height(AuriyaTokens.padding.small))

            Text(
                text = "Navigation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AuriyaTokens.padding.smaller),
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
            ) {
                NavModeChip(
                    label = "Standard",
                    selected = navMode == NavMode.STANDARD,
                    onClick = { onNavModeChange(NavMode.STANDARD) },
                    modifier = Modifier.weight(1f),
                )
                NavModeChip(
                    label = "Floating pill",
                    selected = navMode == NavMode.FLOATING,
                    onClick = { onNavModeChange(NavMode.FLOATING) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SwatchDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .let { if (selected) it.then(Modifier.background(color)) else it },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (selected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.Black.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AuriyaTokens.rounding.full),
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(vertical = AuriyaTokens.padding.small),
            textAlign = TextAlign.Center,
        )
    }
}
