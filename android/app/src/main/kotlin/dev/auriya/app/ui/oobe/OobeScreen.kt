package dev.auriya.app.ui.oobe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel
import kotlinx.coroutines.delay

@Composable
fun OobeScreen(
    viewModel: UiViewModel,
    themeViewModel: ThemeViewModel,
    onFinished: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    val hasRoot by viewModel.hasRoot.collectAsState()

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs
    val isDark = isThemeDark(currentPrefs)

    // Root polling only on step 2
    LaunchedEffect(step, hasRoot) {
        if (step == 2 && !hasRoot) {
            while (!hasRoot) {
                viewModel.checkRoot()
                delay(1500)
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(400)))
                            .togetherWith(slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(400)))
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(400)))
                            .togetherWith(slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(400)))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "OobeStepTransition"
            ) { s ->
                when (s) {
                    1 -> WelcomeContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onNext = { step = 2 }
                    )
                    2 -> RootCheckContent(
                        isDark = isDark,
                        viewModel = viewModel,
                        hasRoot = hasRoot,
                        onBack = { step = 1 },
                        onNext = { step = 3 }
                    )
                    3 -> ColoringContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 2 },
                        onNext = { step = 4 }
                    )
                    4 -> NavbarContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 3 },
                        onNext = { step = 5 }
                    )
                    5 -> CorneringContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 4 },
                        onNext = { step = 6 }
                    )
                    6 -> DoneContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 5 },
                        onFinished = onFinished
                    )
                }
            }
        }
    }
}
