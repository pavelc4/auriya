package dev.auriya.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.components.AuriyaBottomBar
import dev.auriya.app.ui.components.AuriyaNavItem
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
    var mockSelectedIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            mockSelectedIndex = (mockSelectedIndex + 1) % 3
        }
    }

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
                text = "Bottom Navigation Style",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AuriyaTokens.padding.smaller),
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
            ) {
                NavigationStyleCard(
                    label = "Standard",
                    selected = navMode == NavMode.STANDARD,
                    onClick = { onNavModeChange(NavMode.STANDARD) },
                    modifier = Modifier.weight(1f),
                ) {
                    StandardMockup(mockSelectedIndex)
                }
                
                NavigationStyleCard(
                    label = "Floating",
                    selected = navMode == NavMode.FLOATING,
                    onClick = { onNavModeChange(NavMode.FLOATING) },
                    modifier = Modifier.weight(1f),
                ) {
                    FloatingMockup(mockSelectedIndex)
                }
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
private fun NavigationStyleCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderStroke = if (selected) BorderStroke(1.5.dp, borderColor) else null
    
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(AuriyaTokens.rounding.large),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) 
                             else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = borderStroke,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161616)),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardMockup(selectedIndex: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index == selectedIndex
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isActive) 16.dp else 5.dp,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                        label = "std-dot-width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF555555),
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                        label = "std-dot-color"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 5.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingMockup(selectedIndex: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 18.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index == selectedIndex
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isActive) 12.dp else 4.dp,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                        label = "flt-dot-width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF555555),
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                        label = "flt-dot-color"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 4.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}
