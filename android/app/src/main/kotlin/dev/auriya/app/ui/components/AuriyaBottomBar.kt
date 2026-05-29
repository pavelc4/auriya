package dev.auriya.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    NavigationBar {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = item.label) },
            )
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
            .padding(bottom = AuriyaTokens.padding.larger, top = AuriyaTokens.padding.smaller),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 16.dp,
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
private fun RowScope.PillNavItem(
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
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .let { if (selected) it.then(Modifier.padding()) else it },
        ) {}
        androidx.compose.material3.Surface(
            modifier = Modifier.size(if (selected) 48.dp else 0.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = bg,
            content = {},
        )
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = fg,
            modifier = Modifier.size(AuriyaTokens.iconSize.normal),
        )
    }
}
