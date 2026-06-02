package dev.auriya.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.data.NavMode
import dev.auriya.app.ui.theme.AuriyaTokens

data class AuriyaNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AuriyaBottomBar(
    items: List<AuriyaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    mode: NavMode,
) {
    when (mode) {
        NavMode.STANDARD -> StandardBar(items, selectedIndex, onSelect)
        NavMode.FLOATING -> FloatingPillBar(items, selectedIndex, onSelect)
    }
}

@Composable
private fun StandardBar(
    items: List<AuriyaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = AuriyaTokens.rounding.xl,
            topEnd = AuriyaTokens.rounding.xl,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    label = { Text(item.label) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun FloatingPillBar(
    items: List<AuriyaNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 36.dp, top = AuriyaTokens.padding.smaller),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = AuriyaTokens.padding.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
            ) {
                items.forEachIndexed { index, item ->
                    PillNavItem(
                        item = item,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillNavItem(
    item: AuriyaNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        label = "nav-bg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        label = "nav-fg",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = if (selected) 16.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(durationMillis = 200)
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = fg,
                modifier = Modifier.size(AuriyaTokens.iconSize.normal),
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        color = fg,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
