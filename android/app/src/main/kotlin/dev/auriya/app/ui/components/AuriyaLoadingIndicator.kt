package dev.auriya.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AuriyaLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    elevation: Dp = 6.dp,
    isDynamicRotation: Boolean = true,
    manualRotation: Float? = null,
) {
    val indicatorSize = size * 0.6f

    // Rotation cycle animation (calm 3.0 seconds loop)
    val infiniteTransition = rememberInfiniteTransition(label = "loadingRotation")
    val dynamicRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    // Pulsing expand/shrink animation (calm 1.5 seconds loop with standard M3 curve)
    val dynamicScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "scale",
    )

    val finalRotation = if (isDynamicRotation) dynamicRotation else (manualRotation ?: 0f)
    val finalScale = if (isDynamicRotation) dynamicScale else 1f

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        shadowElevation = elevation,
        tonalElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // We use graphicsLayer to offload rotation, scale, and shape clipping to the GPU.
            // This avoids layout invalidation and recomposition on every frame, delivering 60fps/120fps.
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .graphicsLayer {
                            rotationZ = finalRotation
                            scaleX = finalScale
                            scaleY = finalScale
                            clip = true
                            shape = MaterialShapes.Puffy
                        }.background(indicatorColor),
            )
        }
    }
}

/**
 * Material 3 expressive pull-to-refresh indicator that responds directly to pull distance,
 * gives subtle haptic feedback when reaching the trigger threshold, and seamlessly transitions
 * to the dynamic spinning Puffy indicator during active refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.AuriyaPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
) {
    val haptic = LocalHapticFeedback.current
    var hasHapticFired by remember { mutableStateOf(false) }

    val rawProgress = state.distanceFraction

    // Trigger haptic tick exactly when crossing the 1.0f threshold to signal refresh readiness
    LaunchedEffect(rawProgress >= 1f) {
        if (rawProgress >= 1f && !hasHapticFired && !isRefreshing) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasHapticFired = true
        } else if (rawProgress < 1f) {
            hasHapticFired = false
        }
    }

    val isVisible = isRefreshing || rawProgress > 0.05f
    if (!isVisible) return

    val animatedOffsetY by animateDpAsState(
        targetValue = if (isRefreshing) 16.dp else (12.dp + (16.dp * rawProgress)).coerceIn(12.dp, 28.dp),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pullOffset",
    )

    val scale = if (isRefreshing) 1f else (rawProgress / 0.85f).coerceIn(0.2f, 1f)
    val alpha = if (isRefreshing) 1f else (rawProgress / 0.35f).coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .padding(top = animatedOffsetY)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
    ) {
        AuriyaLoadingIndicator(
            size = 44.dp,
            containerColor = containerColor,
            indicatorColor = indicatorColor,
            elevation = 6.dp,
            isDynamicRotation = isRefreshing,
            manualRotation = if (!isRefreshing) rawProgress * 270f else null,
        )
    }
}
