package dev.auriya.app.ui.games

import androidx.compose.runtime.Composable
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile

@Composable
fun GamesPane(
    viewModel: UiViewModel,
    onEditGame: (GameProfile) -> Unit,
) {
    GamesScreen(
        viewModel = viewModel,
        onEditGame = onEditGame,
    )
}
