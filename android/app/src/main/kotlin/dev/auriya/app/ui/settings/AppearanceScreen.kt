package dev.auriya.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.components.ClickableSettingItem
import dev.auriya.app.ui.components.ExpressiveSelectableCard
import dev.auriya.app.ui.components.SettingsSubsection
import dev.auriya.app.ui.components.SwitchSettingItem
import dev.auriya.app.ui.components.animatedItemShapeFor
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.components.rememberGroupedItemShape
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.util.LocaleHelper
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun AppearanceScreen(
    themeViewModel: ThemeViewModel,
    onDismiss: () -> Unit,
    onNavigateToLanguage: () -> Unit = {},
) {
    val prefs by themeViewModel.prefs.collectAsStateWithLifecycle()
    val currentPrefs = prefs ?: return
    var showCornerRadiusScreen by remember { mutableStateOf(false) }

    if (showCornerRadiusScreen) {
        BackHandler {
            showCornerRadiusScreen = false
        }
        AdjustCornerRadiusScreen(
            cornerRadius = currentPrefs.cornerRadius,
            onCornerRadiusChange = themeViewModel::setCornerRadius,
            onDismiss = { showCornerRadiusScreen = false },
        )
        return
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDarkActive =
        when (currentPrefs.darkThemeMode) {
            DarkThemeMode.FOLLOW_SYSTEM -> isSystemDark
            DarkThemeMode.LIGHT -> false
            DarkThemeMode.DARK -> true
        }

    val isFloating = currentPrefs.navMode == NavMode.FLOATING

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onDismiss,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(R.string.appearance_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            ) {
                // --- 1. THEME MODE SUBSECTION ---
                item {
                    SettingsSubsection(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.appearance_theme_mode),
                    ) {
                        val themeModeOptions =
                            remember {
                                listOf(
                                    Triple(
                                        DarkThemeMode.FOLLOW_SYSTEM,
                                        dev.auriya.app.R.string.appearance_follow_system,
                                        "Automatically matches Android system settings",
                                    ),
                                    Triple(
                                        DarkThemeMode.LIGHT,
                                        dev.auriya.app.R.string.appearance_light_theme,
                                        "Bright surfaces with warm terracotta accents",
                                    ),
                                    Triple(
                                        DarkThemeMode.DARK,
                                        dev.auriya.app.R.string.appearance_dark_theme,
                                        "Deep dark surfaces gentle on eyes in low light",
                                    ),
                                )
                            }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            themeModeOptions.forEachIndexed { index, (mode, titleRes, subtitle) ->
                                val isSelected = currentPrefs.darkThemeMode == mode
                                val itemShape = animatedItemShapeFor(index, themeModeOptions.size, isSelected = isSelected)
                                val icon =
                                    when (mode) {
                                        DarkThemeMode.FOLLOW_SYSTEM -> Icons.Rounded.BrightnessAuto
                                        DarkThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        DarkThemeMode.DARK -> Icons.Rounded.DarkMode
                                    }

                                ExpressiveSelectableCard(
                                    selected = isSelected,
                                    onClick = { themeViewModel.setDarkThemeMode(mode) },
                                    title =
                                        androidx.compose.ui.res
                                            .stringResource(titleRes),
                                    subtitle = subtitle,
                                    leadingIcon = icon,
                                    shape = itemShape,
                                )
                            }
                        }
                    }
                }

                // --- 2. CONTRAST SUBSECTION ---
                item {
                    SettingsSubsection(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.appearance_global_theme),
                    ) {
                        SwitchSettingItem(
                            title =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.appearance_amoled),
                            subtitle =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.appearance_amoled_desc),
                            checked = currentPrefs.isAmoled,
                            onCheckedChange = themeViewModel::setAmoled,
                            icon = Icons.Rounded.DarkMode,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = itemShapeFor(0, 1),
                        )
                    }
                }

                // --- 2. NAVIGATION BAR SUBSECTION ---
                item {
                    SettingsSubsection(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.appearance_nav_layout),
                    ) {
                        val navItemCount = if (!isFloating) 2 else 1
                        var navIndex = 0

                        // Floating Island Layout Switch
                        SwitchSettingItem(
                            title =
                                androidx.compose.ui.res
                                    .stringResource(dev.auriya.app.R.string.appearance_floating_island),
                            subtitle =
                                if (isFloating) {
                                    "Floating pill capsule with rounded edges"
                                } else {
                                    "Docked full-width standard navigation bar"
                                },
                            checked = isFloating,
                            onCheckedChange = { floating ->
                                themeViewModel.setNavMode(if (floating) NavMode.FLOATING else NavMode.STANDARD)
                            },
                            icon = Icons.Rounded.Dock,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = itemShapeFor(navIndex++, navItemCount),
                        )

                        // NavBar Corner Radius Setting (Only visible when Floating is OFF!)
                        if (!isFloating) {
                            ClickableSettingItem(
                                title =
                                    androidx.compose.ui.res
                                        .stringResource(dev.auriya.app.R.string.appearance_corner_radius),
                                subtitle =
                                    androidx.compose.ui.res
                                        .stringResource(dev.auriya.app.R.string.appearance_navbar_radius_sub),
                                onClick = { showCornerRadiusScreen = true },
                                icon = Icons.Rounded.RoundedCorner,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = itemShapeFor(navIndex++, navItemCount),
                            )
                        }
                    }
                }

                // --- 4. LANGUAGE SUBSECTION ---
                item {
                    val context = LocalContext.current
                    val currentLangCode = LocaleHelper.getCurrentLanguage(context)
                    val currentLangLabel =
                        if (currentLangCode == LocaleHelper.SYSTEM) {
                            androidx.compose.ui.res
                                .stringResource(R.string.language_system)
                        } else {
                            val loc = java.util.Locale.forLanguageTag(currentLangCode)
                            loc.getDisplayName(loc).replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(loc) else it.toString()
                            }
                        }

                    SettingsSubsection(
                        title =
                            androidx.compose.ui.res
                                .stringResource(R.string.settings_app_language),
                    ) {
                        ClickableSettingItem(
                            title =
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_app_language),
                            subtitle = currentLangLabel,
                            onClick = onNavigateToLanguage,
                            icon = Icons.Filled.Translate,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = itemShapeFor(0, 1),
                            showChevron = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustCornerRadiusScreen(
    cornerRadius: Int,
    onCornerRadiusChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding(),
    ) {
        // 1. Top Action Bar: Back button on left, [ ✓ Done ] pill button on right
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onDismiss,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }

            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.height(40.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // 2. Header Text
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Adjust Corner Radius",
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Match the navbar shape's corners with your device's physical corners for a seamless look.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Adjuster Control Card (Positioned at top directly under header)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Corner Radius",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        onClick = { onCornerRadiusChange(24) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RoundedCorner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )

                    Slider(
                        value = cornerRadius.toFloat(),
                        onValueChange = { onCornerRadiusChange(it.toInt()) },
                        valueRange = 0f..50f,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "$cornerRadius dp",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Low-Fidelity Phone Mockup Canvas (Matching wireframe reference)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Inner Screen Wireframe Content
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(top = 22.dp, start = 18.dp, end = 18.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Low-Fi Header: [Title Pill] --------- [Info Dot]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 54.dp, height = 10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                            )
                        }

                        // Low-Fi Card 1 (Auriya Status Card)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(width = 130.dp, height = 8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(width = 75.dp, height = 6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                                    )
                                }
                            }
                        }

                        // Low-Fi Card 2 (System Metrics Card)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(width = 110.dp, height = 8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(width = 60.dp, height = 6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                                    )
                                }
                            }
                        }
                    }

                    // Low-Fi Docked Navbar Preview at bottom
                    Surface(
                        shape =
                            RoundedCornerShape(
                                topStart = cornerRadius.dp,
                                topEnd = cornerRadius.dp,
                                bottomStart = 26.dp,
                                bottomEnd = 26.dp,
                            ),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Active Tab Capsule Pill
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                modifier = Modifier.size(width = 44.dp, height = 24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(width = 16.dp, height = 4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }

                            // Inactive Tab 2 Dot
                            Box(
                                modifier =
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                            )

                            // Inactive Tab 3 Dot
                            Box(
                                modifier =
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
