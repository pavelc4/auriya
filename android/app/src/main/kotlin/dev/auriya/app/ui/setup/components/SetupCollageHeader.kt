package dev.auriya.app.ui.setup.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.MaterialShapes

@Stable
data class SetupIconPlacement(
    val size: Dp,
    val color: Color,
    val align: Alignment,
    val rot: Float,
    val shape: Shape,
    val offsetX: Dp,
    val offsetY: Dp,
)

typealias OobeIconPlacement = SetupIconPlacement

@Composable
fun AuriyaIconCollage(
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
    ) {
        val minDim = minOf(180.dp, maxHeight)
        val primaryCol = MaterialTheme.colorScheme.primary
        val secCol = MaterialTheme.colorScheme.secondary
        val tertCol = MaterialTheme.colorScheme.tertiary
        val onSurf = MaterialTheme.colorScheme.onSurfaceVariant

        val configs =
            listOf(
                SetupIconPlacement(
                    size = minDim * 0.72f,
                    color = secCol,
                    align = Alignment.Center,
                    rot = -12f,
                    shape = RoundedCornerShape(24.dp),
                    offsetX = 0.dp,
                    offsetY = 0.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.42f,
                    color = onSurf,
                    align = Alignment.TopStart,
                    rot = 16f,
                    shape = CircleShape,
                    offsetX = 18.dp,
                    offsetY = 6.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.44f,
                    color = primaryCol,
                    align = Alignment.BottomEnd,
                    rot = 8f,
                    shape = CircleShape,
                    offsetX = (-18).dp,
                    offsetY = (-6).dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.48f,
                    color = tertCol,
                    align = Alignment.TopEnd,
                    rot = -18f,
                    shape = RoundedCornerShape(20.dp),
                    offsetX = (-22).dp,
                    offsetY = 8.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.38f,
                    color = secCol,
                    align = Alignment.BottomStart,
                    rot = 12f,
                    shape = MaterialShapes.Clover6,
                    offsetX = 24.dp,
                    offsetY = (-8).dp,
                ),
            )

        icons.take(5).forEachIndexed { index, icon ->
            val cfg = configs.getOrElse(index) { configs[0] }
            if (index == 0) {
                Box(
                    modifier =
                        Modifier
                            .size(cfg.size)
                            .align(cfg.align)
                            .offset(cfg.offsetX, cfg.offsetY)
                            .graphicsLayer { rotationZ = cfg.rot }
                            .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = cfg.color,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Surface(
                    modifier =
                        Modifier
                            .size(cfg.size)
                            .align(cfg.align)
                            .offset(cfg.offsetX, cfg.offsetY)
                            .graphicsLayer { rotationZ = cfg.rot },
                    shape = cfg.shape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = cfg.color,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuriyaDrawableCollage(
    drawables: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height),
    ) {
        val minDim = minOf(180.dp, maxHeight)
        val primaryCol = MaterialTheme.colorScheme.primary
        val secCol = MaterialTheme.colorScheme.secondary
        val tertCol = MaterialTheme.colorScheme.tertiary

        val configs =
            listOf(
                SetupIconPlacement(
                    size = minDim * 0.68f,
                    color = primaryCol,
                    align = Alignment.Center,
                    rot = -8f,
                    shape = RoundedCornerShape(32.dp),
                    offsetX = 0.dp,
                    offsetY = 0.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.46f,
                    color = secCol,
                    align = Alignment.TopStart,
                    rot = 12f,
                    shape = RoundedCornerShape(topStart = 26.dp, bottomEnd = 26.dp, topEnd = 8.dp, bottomStart = 8.dp),
                    offsetX = 16.dp,
                    offsetY = 4.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.46f,
                    color = tertCol,
                    align = Alignment.BottomEnd,
                    rot = 8f,
                    shape = CircleShape,
                    offsetX = (-18).dp,
                    offsetY = (-6).dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.48f,
                    color = secCol,
                    align = Alignment.TopEnd,
                    rot = -14f,
                    shape = MaterialShapes.Scallop12,
                    offsetX = (-20).dp,
                    offsetY = 6.dp,
                ),
                SetupIconPlacement(
                    size = minDim * 0.48f,
                    color = primaryCol,
                    align = Alignment.BottomStart,
                    rot = 10f,
                    shape = MaterialShapes.PixelCircle,
                    offsetX = 18.dp,
                    offsetY = (-6).dp,
                ),
            )

        drawables.take(5).forEachIndexed { index, resId ->
            val cfg = configs.getOrElse(index) { configs[0] }
            Surface(
                modifier =
                    Modifier
                        .size(cfg.size)
                        .align(cfg.align)
                        .offset(cfg.offsetX, cfg.offsetY)
                        .graphicsLayer { rotationZ = cfg.rot },
                shape = cfg.shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (index == 0) 6.dp else 4.dp,
                shadowElevation = if (index == 0) 4.dp else 2.dp,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(if (index == 0) 14.dp else 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        tint = cfg.color,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
