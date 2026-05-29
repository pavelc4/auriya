package dev.auriya.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    GAMES("Games", Icons.Filled.Star),
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

    when {
        editingGameProfile != null -> GameProfileScreen(
            game = editingGameProfile!!,
            onDismiss = { editingGameProfile = null },
            onSave = { updated ->
                viewModel.addGame(updated)
                editingGameProfile = null
            },
        )
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
                bottomBar = {
                    AuriyaBottomBar(
                        items = navItems,
                        selectedIndex = selectedIndex,
                        onSelect = { activeTab = NavigationTab.entries[it] },
                        mode = navMode,
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                }
            }
        }
    }
}
