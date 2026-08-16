package dev.auriya.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
const val AOSP_TRANSITION_DURATION = 350

fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(
            durationMillis = AOSP_TRANSITION_DURATION,
            easing = { f -> f * f * f }
        )
    ) + scaleOut(
        targetScale = 0.85f,
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
const val TRANSITION_DURATION = 400

fun enterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.4f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.4f).toInt() }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)
