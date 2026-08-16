package dev.auriya.app.ui.oobe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    var step by remember { mutableStateOf(0) }
    val hasRoot by viewModel.hasRoot.collectAsState()

    val prefs by themeViewModel.prefs.collectAsState()
    val isDark = isThemeDark(prefs)

    // Root polling only on step 1 (RootCheck)
    LaunchedEffect(step, hasRoot) {
        if (step == 1 && !hasRoot) {
            while (!hasRoot) {
                viewModel.checkRoot()
                delay(1500)
            }
        }
    }

    BackHandler(enabled = step > 0) {
        step -= 1
    }

    val pageCount = 5
    val isNextButtonEnabled = when (step) {
        1 -> hasRoot
        else -> true
    }

    Scaffold(
        bottomBar = {
            SetupBottomBar(
                currentPage = step,
                pageCount = pageCount,
                onNextClicked = {
                    if (step < pageCount - 1) {
                        step += 1
                    }
                },
                onFinishClicked = onFinished,
                isNextButtonEnabled = isNextButtonEnabled,
                isFinishButtonEnabled = true
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    0 -> WelcomeContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel
                    )
                    1 -> RootCheckContent(
                        isDark = isDark,
                        viewModel = viewModel,
                        hasRoot = hasRoot
                    )
                    2 -> ColoringContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel
                    )
                    3 -> NavbarContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel
                    )
                    4 -> DoneContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel
                    )
                }
            }
        }
    }
}

