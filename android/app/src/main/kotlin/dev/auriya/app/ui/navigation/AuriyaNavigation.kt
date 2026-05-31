package dev.auriya.app.ui.navigation

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.components.AuriyaBottomBar
import dev.auriya.app.ui.components.AuriyaNavItem
import dev.auriya.app.ui.games.GameProfileScreen
import dev.auriya.app.ui.games.GamesPane
import dev.auriya.app.ui.games.GamesScreen
import dev.auriya.app.ui.home.HomeScreen
import dev.auriya.app.ui.settings.AboutScreen
import dev.auriya.app.ui.settings.LanguageScreen
import dev.auriya.app.ui.settings.SettingsScreen
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile

enum class NavigationTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    GAMES("Games", Icons.Filled.SportsEsports),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private enum class SubScreen { None, Language, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuriyaNavigation(
    viewModel: UiViewModel,
    themeViewModel: ThemeViewModel,
) {
    var activeTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    var editingGameProfile by remember { mutableStateOf<GameProfile?>(null) }
    var subScreen by remember { mutableStateOf(SubScreen.None) }
    val themePrefs by themeViewModel.prefs.collectAsState()
    val governors by viewModel.availableGovernors.collectAsState()
    val gameList by viewModel.gameList.collectAsState()

    when {
        editingGameProfile != null -> {
            val current = editingGameProfile!!
            val isExisting = gameList.games.any { it.packageName == current.packageName }
            GameProfileScreen(
                game = current,
                governorOptions = governors,
                isExistingProfile = isExisting,
                onDismiss = { editingGameProfile = null },
                onSave = { updated ->
                    viewModel.addGame(updated)
                    editingGameProfile = null
                },
                onRemove = if (isExisting) {
                    {
                        // Pop the detail screen FIRST so the user sees
                        // instant feedback; removeGame is dispatched on
                        // IO and would otherwise hold the screen until
                        // gameList.games recomposes.
                        editingGameProfile = null
                        viewModel.removeGame(current.packageName)
                    }
                } else null,
            )
        }
        subScreen == SubScreen.Language -> LanguageScreen(
            onDismiss = { subScreen = SubScreen.None },
        )
        subScreen == SubScreen.About -> AboutScreen(
            onDismiss = { subScreen = SubScreen.None },
        )
        else -> {
            val navItems = NavigationTab.entries.map { AuriyaNavItem(it.title, it.icon) }
            val selectedIndex = NavigationTab.entries.indexOf(activeTab)
            val navMode = themePrefs?.navMode ?: NavMode.STANDARD

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Auriya",
                                fontWeight = FontWeight.ExtraBold,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    if (navMode == NavMode.STANDARD) {
                        AuriyaBottomBar(
                            items = navItems,
                            selectedIndex = selectedIndex,
                            onSelect = { activeTab = NavigationTab.entries[it] },
                            mode = navMode,
                        )
                    }
                },
            ) { innerPadding ->
                val bottomPadding = if (navMode == NavMode.STANDARD) innerPadding.calculateBottomPadding() else 0.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = bottomPadding,
                            start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                            end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                        ),
                ) {
                    when (activeTab) {
                        NavigationTab.HOME -> HomeScreen(viewModel = viewModel)
                        NavigationTab.GAMES -> GamesPane(viewModel = viewModel)
                        NavigationTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            themePrefs = themePrefs,
                            onSeedChange = themeViewModel::setSeedColor,
                            onDynamicToggle = themeViewModel::setUseDynamicColor,
                            onNavModeChange = themeViewModel::setNavMode,
                            onNavigateToLanguage = { subScreen = SubScreen.Language },
                            onNavigateToAbout = { subScreen = SubScreen.About },
                        )
                    }

                    if (navMode == NavMode.FLOATING) {
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
                            )
                        }
                    }
                }
            }
        }
    }
}
