package dev.auriya.app.ui.games

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GamesPane(viewModel: UiViewModel) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val scope = rememberCoroutineScope()
    var selectedGame by remember { mutableStateOf<GameProfile?>(null) }
    val governors by viewModel.availableGovernors.collectAsState()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                GamesScreen(
                    viewModel = viewModel,
                    onEditGame = { profile ->
                        selectedGame = profile
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val game = selectedGame
                if (game != null) {
                    GameProfileScreen(
                        game = game,
                        governorOptions = governors,
                        onDismiss = {
                            scope.launch { navigator.navigateBack() }
                        },
                        onSave = { updated ->
                            viewModel.addGame(updated)
                            scope.launch { navigator.navigateBack() }
                        },
                    )
                }
            }
        },
    )
}
