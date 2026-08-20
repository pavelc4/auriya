package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            Column {
                val title =
                    when (activeSubScreen) {
                        SettingsSubScreen.APP -> "App Settings"
                        SettingsSubScreen.FLOATING_OVERLAY -> "Floating Overlay"
                        SettingsSubScreen.DEVELOPER_OPTIONS -> "Developer Options"
                        SettingsSubScreen.NONE -> "Settings"
                    }
                val subtitle =
                    when (activeSubScreen) {
                        SettingsSubScreen.NONE -> "Manage app preferences, appearance, and monitoring"
                        SettingsSubScreen.APP -> "Language preferences and developer tools"
                        SettingsSubScreen.FLOATING_OVERLAY -> "Real-time floating monitor preferences"
                        SettingsSubScreen.DEVELOPER_OPTIONS -> "Diagnostics, logs, and resets"
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

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            ) {
                when (activeSubScreen) {
                    SettingsSubScreen.NONE -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                val totalItems = 4

                                SettingsMenuItem(
                                    icon = Icons.Filled.Build,
                                    title = "App",
                                    subtitle = "General application settings, language, and developer tools",
                                    onClick = { activeSubScreen = SettingsSubScreen.APP },
                                    shape = itemShapeFor(0, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Palette,
                                    title = "Appearance",
                                    subtitle = "Theme, seed colors, and navigation style",
                                    onClick = onNavigateToAppearance,
                                    shape = itemShapeFor(1, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Layers,
                                    title = "Floating Overlay",
                                    subtitle = "Global system monitor floating overlay settings",
                                    onClick = { activeSubScreen = SettingsSubScreen.FLOATING_OVERLAY },
                                    shape = itemShapeFor(2, totalItems),
                                )

                                SettingsMenuItem(
                                    icon = Icons.Filled.Info,
                                    title = "About",
                                    subtitle = "Developer information and project specs",
                                    onClick = onNavigateToAbout,
                                    shape = itemShapeFor(3, totalItems),
                                )
                            }
                        }
                    }

                    SettingsSubScreen.APP -> {
                        item {
                            SectionCard(title = "General") {
                                SettingRow(
                                    icon = Icons.Filled.Translate,
                                    title = "App Language",
                                    subtitle = "English (System Default)",
                                    onClick = onNavigateToLanguage,
                                    showChevron = false,
                                )
                            }
                        }

                        item {
                            SectionCard(title = "Advanced Tools") {
                                SettingRow(
                                    icon = Icons.Filled.Code,
                                    title = "Developer Options",
                                    subtitle = "App reset, daemon diagnostics, and debugging logs",
                                    onClick = { activeSubScreen = SettingsSubScreen.DEVELOPER_OPTIONS },
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
