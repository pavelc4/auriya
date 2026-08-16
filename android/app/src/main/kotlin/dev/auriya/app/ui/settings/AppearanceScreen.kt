package dev.auriya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.components.SegmentedControl
import dev.auriya.app.ui.oobe.PALETTE_ITEMS
import dev.auriya.app.ui.oobe.SwatchDot
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun AppearanceScreen(
    themeViewModel: ThemeViewModel,
    onDismiss: () -> Unit
) {
    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    val infiniteTransition = rememberInfiniteTransition(label = "themePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isSystemDark = isSystemInDarkTheme()
    val isDarkActive = when (currentPrefs.darkThemeMode) {
        DarkThemeMode.FOLLOW_SYSTEM -> isSystemDark
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.DARK -> true
    }

    val isFloating = currentPrefs.navMode == NavMode.FLOATING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onDismiss,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Appearance",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Theme mode, colors, and navigation layout",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                // --- 1. GLOBAL THEME SUBSECTION ---
                item {
                    SettingsSubsection(title = "GLOBAL THEME") {
                        val themeItemCount = 2 + (if (isDarkActive) 1 else 0) + (if (!currentPrefs.useDynamicColor) 1 else 0)
                        var currentIndex = 0

                        // 1a. Theme Mode
                        ThemeSelectorSettingItem(
                            selectedMode = currentPrefs.darkThemeMode,
                            onModeSelected = themeViewModel::setDarkThemeMode,
                            shape = shapeFor(currentIndex++, themeItemCount)
                        )

                        // 1b. Material You Dynamic Color
                        SwitchSettingItem(
                            title = "Material You Dynamic Color",
                            subtitle = "Match system wallpaper colors dynamically",
                            checked = currentPrefs.useDynamicColor,
                            onCheckedChange = themeViewModel::setUseDynamicColor,
                            icon = Icons.Rounded.Palette,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = shapeFor(currentIndex++, themeItemCount)
                        )

                        // 1c. Pure Black (AMOLED)
                        if (isDarkActive) {
                            SwitchSettingItem(
                                title = "Pure Black (AMOLED)",
                                subtitle = "Turn off OLED pixels for pure dark contrast",
                                checked = currentPrefs.isAmoled,
                                onCheckedChange = themeViewModel::setAmoled,
                                icon = Icons.Rounded.DarkMode,
                                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = shapeFor(currentIndex++, themeItemCount)
                            )
                        }

                        // 1d. Custom Palette Swatches
                        if (!currentPrefs.useDynamicColor) {
                            PalettePickerSettingItem(
                                seedColor = currentPrefs.seedColor,
                                onSeedChange = themeViewModel::setSeedColor,
                                pulseScale = pulseScale,
                                pulseAlpha = pulseAlpha,
                                shape = shapeFor(currentIndex++, themeItemCount)
                            )
                        }
                    }
                }

                // --- 2. NAVIGATION BAR SUBSECTION ---
                item {
                    SettingsSubsection(title = "NAVIGATION BAR") {
                        // Floating Island Layout Switch
                        SwitchSettingItem(
                            title = "Floating Island Layout",
                            subtitle = if (isFloating) "Floating pill capsule with rounded edges"
                            else "Docked full-width standard navigation bar",
                            checked = isFloating,
                            onCheckedChange = { floating ->
                                themeViewModel.setNavMode(if (floating) NavMode.FLOATING else NavMode.STANDARD)
                            },
                            icon = Icons.Rounded.Dock,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubsection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Transparent),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

private fun shapeFor(index: Int, total: Int): RoundedCornerShape {
    return when {
        total <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(4.dp)
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    shape: RoundedCornerShape,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ThemeSelectorSettingItem(
    selectedMode: DarkThemeMode,
    onModeSelected: (DarkThemeMode) -> Unit,
    shape: RoundedCornerShape,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose light, dark, or follow device settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SegmentedControl(
                items = listOf("System", "Light", "Dark"),
                selectedIndex = when (selectedMode) {
                    DarkThemeMode.FOLLOW_SYSTEM -> 0
                    DarkThemeMode.LIGHT -> 1
                    DarkThemeMode.DARK -> 2
                },
                onItemSelected = { index ->
                    val mode = when (index) {
                        0 -> DarkThemeMode.FOLLOW_SYSTEM
                        1 -> DarkThemeMode.LIGHT
                        else -> DarkThemeMode.DARK
                    }
                    onModeSelected(mode)
                }
            )
        }
    }
}

@Composable
private fun PalettePickerSettingItem(
    seedColor: Int,
    onSeedChange: (Int) -> Unit,
    pulseScale: Float,
    pulseAlpha: Float,
    shape: RoundedCornerShape,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Accent Palette",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select a vibrant seed accent color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PALETTE_ITEMS) { item ->
                    SwatchDot(
                        item = item,
                        selected = item.seed == seedColor,
                        pulseScale = pulseScale,
                        pulseAlpha = pulseAlpha,
                        onClick = { onSeedChange(item.seed) }
                    )
                }
            }
        }
    }
}
