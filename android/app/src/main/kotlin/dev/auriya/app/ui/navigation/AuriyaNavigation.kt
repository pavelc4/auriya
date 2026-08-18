package dev.auriya.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.ui.components.AuriyaBottomBar
import dev.auriya.app.ui.components.AuriyaNavItem
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.config.ConfigScreen
import dev.auriya.app.ui.games.GameProfileScreen
import dev.auriya.app.ui.games.GamesPane
import dev.auriya.app.ui.games.GamesScreen
import dev.auriya.app.ui.home.HomeScreen
import dev.auriya.app.ui.oobe.OobeScreen
import dev.auriya.app.ui.settings.AboutScreen
import dev.auriya.app.ui.settings.AppearanceScreen
import dev.auriya.app.ui.settings.LanguageScreen
import dev.auriya.app.ui.settings.SettingsScreen
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.app.ui.record.RecordScreen
import dev.auriya.shared.model.GameProfile
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Tune

enum class NavigationTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    GAMES("Games", Icons.Filled.SportsEsports),
    RECORD("Record", Icons.Filled.Analytics),
    CONFIG("Config", Icons.Filled.Tune),
}

private enum class SubScreen { None, Language, About, Appearance, Settings }

private sealed interface AppRoute {
    data object Main : AppRoute
    data class GameDetail(val profile: GameProfile) : AppRoute
    data object Appearance : AppRoute
    data object Language : AppRoute
    data object About : AppRoute
    data object Settings : AppRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuriyaNavigation(
    viewModel: UiViewModel,
    themeViewModel: ThemeViewModel,
) {
    var activeTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    var editingGameProfile by remember { mutableStateOf<GameProfile?>(null) }
    var selectedGameProfile by remember { mutableStateOf<GameProfile?>(null) }
    var subScreen by remember { mutableStateOf(SubScreen.None) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val themePrefs by themeViewModel.prefs.collectAsState()
    val governors by viewModel.availableGovernors.collectAsState()
    val gameList by viewModel.gameList.collectAsState()

    if (editingGameProfile != null) {
        BackHandler {
            editingGameProfile = null
        }
    }
    if (selectedGameProfile != null) {
        BackHandler {
            selectedGameProfile = null
        }
    }
    if (subScreen != SubScreen.None) {
        BackHandler {
            subScreen = SubScreen.None
        }
    }
    if (activeTab != NavigationTab.HOME && subScreen == SubScreen.None) {
        BackHandler {
            activeTab = NavigationTab.HOME
        }
    }

    val prefs = themePrefs
    val screenState = when {
        prefs == null -> 0
        !prefs.isOobeCompleted -> 1
        else -> 2
    }

    Crossfade(
        targetState = screenState,
        animationSpec = tween(500, easing = EaseInOutCubic),
        label = "AppScreenTransition"
    ) { state ->
        when (state) {
            0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    AuriyaLoadingIndicator(size = 96.dp)
                }
            }
            1 -> {
                OobeScreen(
                    viewModel = viewModel,
                    themeViewModel = themeViewModel,
                    onFinished = {
                        themeViewModel.setOobeCompleted(true)
                    }
                )
            }
            2 -> {
                val currentRoute = when {
                    editingGameProfile != null -> AppRoute.GameDetail(editingGameProfile!!)
                    subScreen == SubScreen.Appearance -> AppRoute.Appearance
                    subScreen == SubScreen.Language -> AppRoute.Language
                    subScreen == SubScreen.About -> AppRoute.About
                    subScreen == SubScreen.Settings -> AppRoute.Settings
                    else -> AppRoute.Main
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = {
                            if (targetState is AppRoute.Main) {
                                (scaleIn(initialScale = 0.94f, animationSpec = tween(220, easing = EaseOutCubic)) + fadeIn(animationSpec = tween(200)))
                                .togetherWith(
                                    (slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(180, easing = EaseInCubic)))
                                )
                            } else {
                                (slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(animationSpec = tween(220)))
                                .togetherWith(
                                    (scaleOut(targetScale = 0.94f, animationSpec = tween(180, easing = EaseInCubic)) + fadeOut(animationSpec = tween(180)))
                                )
                            }
                        },
                        label = "SubScreenForwardDepthTransition"
                    ) { route ->
                        when (route) {
                            is AppRoute.GameDetail -> {
                                val current = route.profile
                                val isExisting = gameList.games.any { it.packageName == current.packageName }
                                GameProfileScreen(
                                    game = current,
                                    governorOptions = governors,
                                    isExistingProfile = isExisting,
                                    onDismiss = { editingGameProfile = null },
                                    onSave = { updated ->
                                        viewModel.addGame(updated)
                                    },
                                    onRemove = if (isExisting) {
                                        {
                                            editingGameProfile = null
                                            viewModel.removeGame(current.packageName)
                                        }
                                    } else null,
                                )
                            }
                            AppRoute.Appearance -> AppearanceScreen(
                                themeViewModel = themeViewModel,
                                onDismiss = { subScreen = SubScreen.None },
                            )
                            AppRoute.Language -> LanguageScreen(
                                onDismiss = { subScreen = SubScreen.None },
                            )
                            AppRoute.About -> AboutScreen(
                                onDismiss = { subScreen = SubScreen.None },
                            )
                            AppRoute.Settings -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { subScreen = SubScreen.None },
                                onNavigateToAppearance = { subScreen = SubScreen.Appearance },
                                onNavigateToLanguage = { subScreen = SubScreen.Language },
                                onNavigateToAbout = { subScreen = SubScreen.About },
                                onResetOobe = { themeViewModel.setOobeCompleted(false) },
                            )
                            AppRoute.Main -> {
                                val navItems = NavigationTab.entries.map { AuriyaNavItem(it.title, it.icon) }
                                val selectedIndex = NavigationTab.entries.indexOf(activeTab)
                                val navMode = themePrefs?.navMode ?: NavMode.STANDARD
                                val navType = themePrefs?.navType ?: NavType.LEGACY
                                val cornerRadius = themePrefs?.cornerRadius ?: 24
                                val showBottomBar = editingGameProfile == null && selectedGameProfile == null && subScreen == SubScreen.None

                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                AnimatedContent(
                                    targetState = activeTab,
                                    transitionSpec = {
                                        val targetIndex = NavigationTab.entries.indexOf(targetState)
                                        val initialIndex = NavigationTab.entries.indexOf(initialState)
                                        val isForward = targetIndex > initialIndex
                                        if (isForward) {
                                            (slideInHorizontally(
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                initialOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                                            ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                    targetOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                                                ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic))
                                            )
                                        } else {
                                            (slideInHorizontally(
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                initialOffsetX = { fullWidth -> -(fullWidth * 0.2f).toInt() }
                                            ) + fadeIn(animationSpec = tween(200, easing = EaseOutCubic)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                                                    targetOffsetX = { fullWidth -> (fullWidth * 0.2f).toInt() }
                                                ) + fadeOut(animationSpec = tween(160, easing = EaseInCubic))
                                            )
                                        }
                                    },
                                    label = "TabSharedAxisTransition"
                                ) { currentTab ->
                                    saveableStateHolder.SaveableStateProvider(currentTab) {
                                        when (currentTab) {
                                            NavigationTab.HOME -> HomeScreen(
                                                viewModel = viewModel,
                                                onNavigateToGames = { activeTab = NavigationTab.GAMES },
                                                onNavigateToSettings = { subScreen = SubScreen.Settings }
                                            )
                                            NavigationTab.GAMES -> {
                                                val current = selectedGameProfile
                                                if (current != null) {
                                                    val isExisting = gameList.games.any { it.packageName == current.packageName }
                                                    GameProfileScreen(
                                                        game = current,
                                                        governorOptions = governors,
                                                        isExistingProfile = isExisting,
                                                        onDismiss = { selectedGameProfile = null },
                                                        onSave = { updated ->
                                                            viewModel.addGame(updated)
                                                        },
                                                        onRemove = if (isExisting) {
                                                            {
                                                                selectedGameProfile = null
                                                                viewModel.removeGame(current.packageName)
                                                            }
                                                        } else null,
                                                    )
                                                } else {
                                                    GamesPane(
                                                        viewModel = viewModel,
                                                        onEditGame = { selectedGameProfile = it }
                                                    )
                                                }
                                            }
                                            NavigationTab.RECORD -> RecordScreen(
                                                viewModel = viewModel
                                            )
                                            NavigationTab.CONFIG -> ConfigScreen(
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                }

                                if (showBottomBar) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        AuriyaBottomBar(
                                            items = navItems,
                                            selectedIndex = selectedIndex,
                                            onSelect = { activeTab = NavigationTab.entries[it] },
                                            mode = navMode,
                                            type = navType,
                                            cornerRadius = cornerRadius,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
