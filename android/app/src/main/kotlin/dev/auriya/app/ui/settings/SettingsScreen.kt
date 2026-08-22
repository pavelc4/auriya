package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.auriya.app.R
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.RootType
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
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onResetOobe: () -> Unit,
) {
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.NONE) }

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
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
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
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                                val rootEnv by viewModel.rootEnvironment.collectAsState()
                                val context = LocalContext.current

                                SettingsSubsection(
                                    title =
                                        androidx.compose.ui.res
                                            .stringResource(R.string.settings_sec_root_env),
                                ) {
                                    val hasKernel = rootEnv.hasRoot && rootEnv.kernelVersion.isNotBlank()
                                    val hasPkg = rootEnv.hasRoot && (rootEnv.managerPackage != null || rootEnv.managerSignatureHash != null)
                                    val totalRootItems = 1 + (if (hasKernel) 1 else 0) + (if (hasPkg) 1 else 0)
                                    var rootIdx = 0

                                    val fallbackDrawable =
                                        when {
                                            rootEnv.rootName.contains("Wild", ignoreCase = true) || rootEnv.rootName.contains("WKSU", ignoreCase = true) -> R.drawable.ic_wksu
                                            rootEnv.rootName.contains("ReSuki", ignoreCase = true) || rootEnv.rootType == RootType.SUKISU -> R.drawable.ic_resukisu
                                            rootEnv.rootType == RootType.KERNELSU_NEXT -> R.drawable.ic_kernelsu_next
                                            rootEnv.rootType == RootType.KOWSU -> R.drawable.ic_kowsu
                                            rootEnv.rootType == RootType.MAGISK || rootEnv.rootType == RootType.MAGISK_ALPHA || rootEnv.rootType == RootType.KITSUNE_MASK -> R.drawable.ic_magisk
                                            rootEnv.rootType == RootType.APATCH -> R.drawable.ic_apatch
                                            else -> R.drawable.ic_kernelsu
                                        }

                                    val launchIntent =
                                        remember(rootEnv.managerPackage) {
                                            if (rootEnv.managerPackage != null) {
                                                context.packageManager.getLaunchIntentForPackage(rootEnv.managerPackage!!)
                                            } else {
                                                null
                                            }
                                        }

                                    // 1. Main Manager Row (Sliced Top Card)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = itemShapeFor(rootIdx++, totalRootItems),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (launchIntent != null) {
                                                            Modifier.clickable { context.startActivity(launchIntent) }
                                                        } else {
                                                            Modifier
                                                        },
                                                    ).padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(44.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (rootEnv.managerIcon != null) {
                                                        androidx.compose.foundation.Image(
                                                            bitmap = rootEnv.managerIcon!!,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                                                        )
                                                    } else {
                                                        androidx.compose.foundation.Image(
                                                            painter = painterResource(id = fallbackDrawable),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(28.dp),
                                                        )
                                                    }
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = if (rootEnv.hasRoot) rootEnv.rootName else "Root Access",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontFamily = GoogleSansRounded,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                    if (rootEnv.hasRoot) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                        ) {
                                                            Text(
                                                                text = if (rootEnv.isLkm) "LKM" else "ACTIVE",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                                fontSize = 9.sp,
                                                            )
                                                        }
                                                        if (rootEnv.isSpoofed) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                                                            ) {
                                                                Text(
                                                                    text = "SPOOFED",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.tertiary,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                                    fontSize = 9.sp,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                val subtitleText =
                                                    if (rootEnv.hasRoot) {
                                                        val v = if (rootEnv.coreVersion.isNotBlank()) rootEnv.coreVersion else "Active"
                                                        val m = if (rootEnv.managerLabel != null) " • ${rootEnv.managerLabel}" else ""
                                                        "$v$m"
                                                    } else {
                                                        "Root access not granted or not detected"
                                                    }

                                                Text(
                                                    text = subtitleText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            if (launchIntent != null) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = "Open Manager",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }

                                    // 2. Kernel & Driver Info Row (Sliced Middle Card)
                                    if (hasKernel) {
                                        ClickableSettingItem(
                                            title =
                                                androidx.compose.ui.res
                                                    .stringResource(R.string.settings_kernel_interface),
                                            subtitle = "Kernel ${rootEnv.kernelVersion} • ${if (rootEnv.isLkm) "LKM driver" else "Built-in GKI"}",
                                            onClick = {},
                                            icon = Icons.Outlined.Memory,
                                            shape = itemShapeFor(rootIdx++, totalRootItems),
                                            showChevron = false,
                                        )
                                    }

                                    // 3. Manager Package Identifier Row (Sliced Bottom Card)
                                    if (hasPkg) {
                                        ClickableSettingItem(
                                            title = if (rootEnv.isSpoofed) "Spoofed Package" else "Manager Identity",
                                            subtitle = rootEnv.managerPackage ?: "Verified via SHA-256 Checksum",
                                            onClick = {},
                                            icon = Icons.Outlined.Fingerprint,
                                            shape = itemShapeFor(rootIdx++, totalRootItems),
                                            showChevron = false,
                                        )
                                    }
                                }
                            }

                            item {
                                val roundFps by viewModel.roundFps.collectAsState()
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
                                val currentLangCode =
                                    dev.auriya.app.util.LocaleHelper
                                        .getCurrentLanguage(LocalContext.current)
                                val currentLangLabel =
                                    if (currentLangCode == dev.auriya.app.util.LocaleHelper.SYSTEM) {
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
                                            .stringResource(R.string.settings_general_tools),
                                ) {
                                    ClickableSettingItem(
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_app_language),
                                        subtitle = currentLangLabel,
                                        onClick = onNavigateToLanguage,
                                        icon = Icons.Filled.Translate,
                                        shape = itemShapeFor(0, 2),
                                        showChevron = false,
                                    )

                                    ClickableSettingItem(
                                        title =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_dev_options),
                                        subtitle =
                                            androidx.compose.ui.res
                                                .stringResource(R.string.settings_dev_options_desc),
                                        onClick = { activeSubScreen = SettingsSubScreen.DEVELOPER_OPTIONS },
                                        icon = Icons.Filled.Code,
                                        shape = itemShapeFor(1, 2),
                                        showChevron = false,
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
