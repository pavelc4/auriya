package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.components.*
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsSubScreen {
    NONE,
    APP,
    FLOATING_OVERLAY,
    DEVELOPER_OPTIONS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: UiViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetOobe: () -> Unit,
) {
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.NONE) }
    var showThermalDefaultSheet by remember { mutableStateOf(false) }

    val thermalAvailable by viewModel.thermalAvailable.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    if (showThermalDefaultSheet && thermalAvailable) {
        ThermalDefaultSheet(
            settings = settings,
            onSelect = { value ->
                viewModel.saveSettings(
                    settings.copy(thermal = settings.thermal.copy(default = value)),
                )
            },
            onDismiss = { showThermalDefaultSheet = false },
        )
    }

    androidx.activity.compose.BackHandler(enabled = activeSubScreen != SettingsSubScreen.NONE) {
        activeSubScreen =
            when (activeSubScreen) {
                SettingsSubScreen.DEVELOPER_OPTIONS -> SettingsSubScreen.APP
                else -> SettingsSubScreen.NONE
            }
    }

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
                onClick = {
                    when (activeSubScreen) {
                        SettingsSubScreen.DEVELOPER_OPTIONS -> activeSubScreen = SettingsSubScreen.APP
                        SettingsSubScreen.APP, SettingsSubScreen.FLOATING_OVERLAY -> activeSubScreen = SettingsSubScreen.NONE
                        SettingsSubScreen.NONE -> onNavigateBack()
                    }
                },
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

            AnimatedContent(
                targetState = activeSubScreen,
                transitionSpec = {
                    (fadeIn(tween(200, easing = EaseOutCubic)) + slideInVertically(initialOffsetY = { -it / 4 }))
                        .togetherWith(fadeOut(tween(150, easing = EaseInCubic)) + slideOutVertically(targetOffsetY = { it / 4 }))
                },
                label = "SettingsHeaderTransition",
            ) { targetScreen ->
                Column {
                    val title =
                        when (targetScreen) {
                            SettingsSubScreen.APP -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_app_title)
                            }

                            SettingsSubScreen.FLOATING_OVERLAY -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_floating_overlay)
                            }

                            SettingsSubScreen.DEVELOPER_OPTIONS -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_dev_options)
                            }

                            SettingsSubScreen.NONE -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_title)
                            }
                        }
                    val subtitle =
                        when (targetScreen) {
                            SettingsSubScreen.NONE -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_subtitle)
                            }

                            SettingsSubScreen.APP -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_app_sub)
                            }

                            SettingsSubScreen.FLOATING_OVERLAY -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_floating_overlay_desc)
                            }

                            SettingsSubScreen.DEVELOPER_OPTIONS -> {
                                androidx.compose.ui.res
                                    .stringResource(R.string.settings_dev_options_desc)
                            }
                        }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            AnimatedContent(
                targetState = activeSubScreen,
                transitionSpec = {
                    if (targetState == SettingsSubScreen.NONE) {
                        (
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            ) + fadeIn(tween(200))
                        ).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            ) + fadeOut(tween(160)),
                        )
                    } else {
                        (
                            slideInHorizontally(
                                initialOffsetX = { it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            ) + fadeIn(tween(200))
                        ).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                            ) + fadeOut(tween(160)),
                        )
                    }
                },
                label = "SettingsSubScreenTransition",
            ) { targetSubScreen ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                ) {
                    when (targetSubScreen) {
                        SettingsSubScreen.NONE -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    val totalItems = 4

                                    SettingsMenuItem(
                                        icon = Icons.Filled.Build,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_app_title),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_app_sub),
                                        onClick = { activeSubScreen = SettingsSubScreen.APP },
                                        shape = itemShapeFor(0, totalItems),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Filled.Palette,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_appearance),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_appearance_desc),
                                        onClick = onNavigateToAppearance,
                                        shape = itemShapeFor(1, totalItems),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Filled.Layers,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_floating_overlay),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_floating_overlay_desc),
                                        onClick = { activeSubScreen = SettingsSubScreen.FLOATING_OVERLAY },
                                        shape = itemShapeFor(2, totalItems),
                                    )

                                    SettingsMenuItem(
                                        icon = Icons.Filled.Info,
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_about),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_about_desc),
                                        onClick = onNavigateToAbout,
                                        shape = itemShapeFor(3, totalItems),
                                    )
                                }
                            }
                        }

                        SettingsSubScreen.APP -> {
                            item {
                                val roundFps by viewModel.roundFps.collectAsStateWithLifecycle()
                                SettingsSubsection(
                                    title =
                                        androidx.compose.ui.res
                                            .stringResource(R.string.settings_display_metrics),
                                ) {
                                    SwitchSettingItem(
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_round_fps),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_round_fps_desc),
                                        checked = roundFps,
                                        onCheckedChange = { viewModel.setRoundFps(it) },
                                        icon = Icons.Outlined.Numbers,
                                        shape = itemShapeFor(0, 1),
                                    )
                                }
                            }

                            item {
                                if (thermalAvailable) {
                                    SettingsSubsection(
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_sec_thermal),
                                    ) {
                                        SwitchSettingItem(
                                            title =
                                                androidx.compose.ui.res
                                                    .stringResource(R.string.settings_perapp_thermal),
                                            subtitle =
                                                androidx.compose.ui.res
                                                    .stringResource(R.string.settings_perapp_thermal_desc),
                                            checked = settings.thermal.enabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.saveSettings(
                                                    settings.copy(thermal = settings.thermal.copy(enabled = enabled)),
                                                )
                                            },
                                            icon = Icons.Outlined.Thermostat,
                                            shape = itemShapeFor(0, 2),
                                        )
                                        ClickableSettingItem(
                                            title =
                                                androidx.compose.ui.res
                                                    .stringResource(R.string.settings_thermal_default),
                                            subtitle =
                                                androidx.compose.ui.res
                                                    .stringResource(R.string.settings_thermal_default_desc) +
                                                    " (sconfig ${settings.thermal.default ?: "?"})",
                                            onClick = { showThermalDefaultSheet = true },
                                            icon = Icons.Outlined.DeviceThermostat,
                                            shape = itemShapeFor(1, 2),
                                        )
                                    }
                                }
                            }

                            item {
                                SettingsSubsection(
                                    title =
                                        androidx.compose.ui.res
                                            .stringResource(R.string.settings_general_tools),
                                ) {
                                    ClickableSettingItem(
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_dev_options),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_dev_options_desc),
                                        onClick = { activeSubScreen = SettingsSubScreen.DEVELOPER_OPTIONS },
                                        icon = Icons.Filled.Code,
                                        shape = itemShapeFor(0, 1),
                                        showChevron = true,
                                    )
                                }
                            }
                        }

                        SettingsSubScreen.FLOATING_OVERLAY -> {
                            item {
                                FloatingOverlayContent()
                            }
                        }

                        SettingsSubScreen.DEVELOPER_OPTIONS -> {
                            item {
                                DeveloperOptionsContent(
                                    viewModel = viewModel,
                                    onResetOobe = onResetOobe,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val thermalDefaultOptions = listOf("auto", "dynamic", "extreme", "class0", "incalls", "thermal20")

private fun thermalDefaultName(value: Int?): String =
    when (value) {
        null -> "auto"
        10 -> "dynamic"
        2 -> "extreme"
        11 -> "class0"
        8 -> "incalls"
        20 -> "thermal20"
        else -> value.toString()
    }

private fun thermalDefaultValue(name: String): Int? =
    when (name.lowercase()) {
        "auto" -> null
        "dynamic" -> 10
        "extreme" -> 2
        "class0" -> 11
        "incalls" -> 8
        "thermal20" -> 20
        else -> name.toIntOrNull()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalDefaultSheet(
    settings: Settings,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        val currentName = thermalDefaultName(settings.thermal.default)
        val options =
            if (currentName in thermalDefaultOptions) {
                thermalDefaultOptions
            } else {
                thermalDefaultOptions + currentName
            }
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            androidx.compose.ui.res
                                .stringResource(R.string.settings_thermal_default),
                        style =
                            ExpTitleTypography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            androidx.compose.ui.res
                                .stringResource(R.string.settings_thermal_default_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(options) { opt ->
                val isSelected = opt == currentName
                val (icon, subtitle) = getThermalInfo(opt)
                val desc =
                    if (opt == "auto") {
                        androidx.compose.ui.res
                            .stringResource(R.string.settings_thermal_default_auto)
                    } else {
                        subtitle
                    }

                Surface(
                    onClick = {
                        onSelect(thermalDefaultValue(opt))
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
