package dev.auriya.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AuriyaLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
) {
    val indicatorSize = size * 0.6f

    // Rotation cycle animation (calm 3.0 seconds loop)
    val infiniteTransition = rememberInfiniteTransition(label = "loadingRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing expand/shrink animation (calm 1.5 seconds loop with standard M3 curve)
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        // We use graphicsLayer to offload rotation, scale, and shape clipping to the GPU.
        // This avoids layout invalidation and recomposition on every frame, delivering 60fps/120fps.
        Box(
            modifier = Modifier
                .size(indicatorSize)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                    clip = true
                    shape = MaterialShapes.Puffy
                }
                .background(indicatorColor)
        )
    }
}
