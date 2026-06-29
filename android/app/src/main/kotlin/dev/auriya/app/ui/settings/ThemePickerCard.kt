package dev.auriya.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.ui.components.AuriyaBottomBar
import dev.auriya.app.ui.components.AuriyaNavItem
import dev.auriya.app.ui.components.SegmentedControl
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
    navType: NavType,
    cornerRadius: Int,
    darkThemeMode: DarkThemeMode,
    isAmoled: Boolean,
    onSeedChange: (Int) -> Unit,
    onDynamicToggle: (Boolean) -> Unit,
    onNavModeChange: (NavMode) -> Unit,
    onNavTypeChange: (NavType) -> Unit,
    onCornerRadiusChange: (Int) -> Unit,
    onDarkModeChange: (DarkThemeMode) -> Unit,
    onAmoledToggle: (Boolean) -> Unit,
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
                        seedColor = Color(color),
                        selected = !useDynamicColor && color == seedColor,
                        onClick = { onSeedChange(color) },
                    )
                }
            }

            Spacer(Modifier.height(AuriyaTokens.padding.small))

            Text(
                text = "Dark Theme Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AuriyaTokens.padding.small),
            )
            
            SegmentedControl(
                items = listOf("System", "Light", "Dark"),
                selectedIndex = when (darkThemeMode) {
                    DarkThemeMode.FOLLOW_SYSTEM -> 0
                    DarkThemeMode.LIGHT -> 1
                    DarkThemeMode.DARK -> 2
                },
                onItemSelected = { index ->
                    val mode = when (index) {
                        0 -> DarkThemeMode.FOLLOW_SYSTEM
                        1 -> DarkThemeMode.LIGHT
                        2 -> DarkThemeMode.DARK
                        else -> DarkThemeMode.FOLLOW_SYSTEM
                    }
                    onDarkModeChange(mode)
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                activeColor = MaterialTheme.colorScheme.surface,
                activeTextColor = MaterialTheme.colorScheme.onSurface,
                inactiveTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cornerRadius = 16,
                itemCornerRadius = 12,
                verticalPadding = 12
            )

            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkActive = when (darkThemeMode) {
                DarkThemeMode.FOLLOW_SYSTEM -> isSystemDark
                DarkThemeMode.LIGHT -> false
                DarkThemeMode.DARK -> true
            }

            AnimatedVisibility(visible = isDarkActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AuriyaTokens.padding.smaller),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AMOLED Black",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Use pure black for dark backgrounds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isAmoled, onCheckedChange = onAmoledToggle)
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

            Text(
                text = "Bottom Navigation Type",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AuriyaTokens.padding.small),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AuriyaTokens.padding.smaller),
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
            ) {
                NavigationStyleCard(
                    label = "Legacy",
                    selected = navType == NavType.LEGACY,
                    onClick = { onNavTypeChange(NavType.LEGACY) },
                    modifier = Modifier.weight(1f),
                ) {
                    LegacyMockup(mockSelectedIndex)
                }

                NavigationStyleCard(
                    label = "Modern",
                    selected = navType == NavType.MODERN,
                    onClick = { onNavTypeChange(NavType.MODERN) },
                    modifier = Modifier.weight(1f),
                ) {
                    ModernMockup(mockSelectedIndex)
                }
            }

            Text(
                text = "Corner Radius",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AuriyaTokens.padding.small),
            )
            Text(
                text = "Customize navigation bar rounding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Slider(
                    value = cornerRadius.toFloat(),
                    onValueChange = { onCornerRadiusChange(it.toInt()) },
                    valueRange = 0f..32f,
                    steps = 32,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${cornerRadius}dp",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun SwatchDot(seedColor: Color, selected: Boolean, onClick: () -> Unit) {
    val primary = seedColor
    val secondary = Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
        android.graphics.Color.colorToHSV(primary.toArgb(), this)
        this[0] = (this[0] + 30) % 360f
        this[1] = (this[1] * 0.8f).coerceIn(0f, 1f)
    }))
    val tertiary = Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
        android.graphics.Color.colorToHSV(primary.toArgb(), this)
        this[0] = (this[0] + 120) % 360f
        this[1] = (this[1] * 0.9f).coerceIn(0f, 1f)
    }))
    val neutral = Color(android.graphics.Color.HSVToColor(FloatArray(3).apply {
        android.graphics.Color.colorToHSV(primary.toArgb(), this)
        this[1] = (this[1] * 0.2f).coerceIn(0f, 1f)
        this[2] = 0.5f
    }))

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(color = primary, startAngle = 180f, sweepAngle = 90f, useCenter = true)
            drawArc(color = secondary, startAngle = 270f, sweepAngle = 90f, useCenter = true)
            drawArc(color = tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = neutral, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
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

@Composable
private fun LegacyMockup(selectedIndex: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isActive = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF555555))
                )
            }
        }
    }
}

@Composable
private fun ModernMockup(selectedIndex: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isActive = index == selectedIndex
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1.2f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "mockup-scale"
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF555555))
                )
            }
        }
    }
}
