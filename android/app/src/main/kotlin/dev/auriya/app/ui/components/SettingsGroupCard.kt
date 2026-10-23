package dev.auriya.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.theme.GoogleSansRounded

@Composable
fun AuriyaDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(vertical = 12.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
    )
}

@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
fun SettingsGroupItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.8.dp,
                )
            }
        }
    }
}

fun itemShapeFor(
    index: Int,
    total: Int,
    outerCorner: Dp = 28.dp,
    innerCorner: Dp = 4.dp,
): RoundedCornerShape =
    when {
        total <= 1 -> {
            RoundedCornerShape(outerCorner)
        }

        index == 0 -> {
            RoundedCornerShape(topStart = outerCorner, topEnd = outerCorner, bottomStart = innerCorner, bottomEnd = innerCorner)
        }

        index == total - 1 -> {
            RoundedCornerShape(
                topStart = innerCorner,
                topEnd = innerCorner,
                bottomStart = outerCorner,
                bottomEnd = outerCorner,
            )
        }

        else -> {
            RoundedCornerShape(innerCorner)
        }
    }

/**
 * Calculates adaptive Material 3 Expressive corner shapes for grouped vertical items.
 *
 * Modeled after ShinkaiWalls design:
 * - Middle items: when selected, smoothly animate into fully rounded corners [outerCorner] (26.dp).
 *   When unselected, stay with normal grouped inner corners [innerCorner] (6.dp).
 * - First and last items: retain their distinct grouped boundary shapes (first has top rounded,
 *   last has bottom rounded).
 */
@Composable
fun animatedItemShapeFor(
    index: Int,
    total: Int,
    isSelected: Boolean = false,
    outerCorner: Dp = 26.dp,
    innerCorner: Dp = 6.dp,
): RoundedCornerShape {
    val animatedMiddleCorner by
        animateDpAsState(
            targetValue = if (isSelected) outerCorner else innerCorner,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "MiddleCornerAnimation_$index",
        )

    return when {
        total <= 1 -> {
            RoundedCornerShape(outerCorner)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = outerCorner,
                topEnd = outerCorner,
                bottomStart = innerCorner,
                bottomEnd = innerCorner,
            )
        }

        index == total - 1 -> {
            RoundedCornerShape(
                topStart = innerCorner,
                topEnd = innerCorner,
                bottomStart = outerCorner,
                bottomEnd = outerCorner,
            )
        }

        else -> {
            RoundedCornerShape(animatedMiddleCorner)
        }
    }
}

@Composable
fun rememberGroupedItemShape(
    index: Int,
    total: Int,
    isSelected: Boolean = false,
    outerCorner: Dp = 26.dp,
    innerCorner: Dp = 6.dp,
): Shape = animatedItemShapeFor(index, total, isSelected, outerCorner, innerCorner)

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
