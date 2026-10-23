package dev.auriya.app.ui.setup.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.bouncyClickable

/**
 * SetupBottomBar featuring smooth top corners, animated step counter,
 * and morphing 360-degree rotating FAB button.
 */
@Composable
fun SetupBottomBar(
    currentPage: Int,
    pageCount: Int,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit,
    isNextButtonEnabled: Boolean,
    isFinishButtonEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    val targetShapeValues =
        when (currentPage % 3) {
            0 -> listOf(50f, 50f, 50f, 50f)
            1 -> listOf(26f, 26f, 26f, 26f)
            else -> listOf(18f, 50f, 18f, 50f)
        }

    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")

    val animatedRotation by animateFloatAsState(
        targetValue = currentPage * 360f,
        animationSpec = rotationAnimationSpec,
        label = "Rotation",
    )

    var errorTrigger by remember { mutableStateOf(0L) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0L) {
            for (i in 0 until 2) {
                shakeOffset.animateTo(-10f, tween(40, easing = LinearEasing))
                shakeOffset.animateTo(10f, tween(40, easing = LinearEasing))
            }
            shakeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            kotlinx.coroutines.delay(1000)
            errorTrigger = 0L
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> -height } + fadeOut(),
                        )
                    } else {
                        (slideInVertically { height -> -height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> height } + fadeOut(),
                        )
                    }
                },
                label = "StepTextAnimation",
            ) { targetPage ->
                if (targetPage == 0) {
                    Text(
                        text = "Let's go",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "Step $targetPage of ${pageCount - 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val isLastPage = currentPage == pageCount - 1
            val isPrimaryButtonEnabled = if (isLastPage) isFinishButtonEnabled else isNextButtonEnabled
            val isErrorActive = errorTrigger > 0L

            val containerColor by animateColorAsState(
                targetValue =
                    when {
                        isErrorActive -> MaterialTheme.colorScheme.errorContainer
                        !isPrimaryButtonEnabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                animationSpec = tween(200),
                label = "fab-container-color",
            )
            val contentColor by animateColorAsState(
                targetValue =
                    when {
                        isErrorActive -> MaterialTheme.colorScheme.onErrorContainer
                        !isPrimaryButtonEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                animationSpec = tween(200),
                label = "fab-content-color",
            )

            val dynamicShape =
                RoundedCornerShape(
                    topStartPercent = animatedTopStart.toInt(),
                    topEndPercent = animatedTopEnd.toInt(),
                    bottomStartPercent = animatedBottomStart.toInt(),
                    bottomEndPercent = animatedBottomEnd.toInt(),
                )

            Surface(
                shape = dynamicShape,
                color = containerColor,
                contentColor = contentColor,
                modifier =
                    Modifier
                        .offset(x = shakeOffset.value.dp)
                        .size(width = 84.dp, height = 56.dp)
                        .bouncyClickable(onClick = {
                            if (isPrimaryButtonEnabled) {
                                if (isLastPage) onFinishClicked() else onNextClicked()
                            } else {
                                errorTrigger = System.currentTimeMillis()
                            }
                        })
                        .rotate(animatedRotation),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        modifier = Modifier.rotate(-animatedRotation),
                        targetState =
                            when {
                                isErrorActive -> 2
                                currentPage < pageCount - 1 -> 0
                                else -> 1
                            },
                        transitionSpec = {
                            (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                .togetherWith(scaleOut() + fadeOut())
                        },
                        label = "AnimatedFabIcon",
                    ) { iconState ->
                        when (iconState) {
                            2 -> {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Permission Required",
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            0 -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Finish",
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
