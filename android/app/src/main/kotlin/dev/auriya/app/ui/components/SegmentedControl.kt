package dev.auriya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cornerRadius: Int = 12,
    itemCornerRadius: Int = 8,
    verticalPadding: Int = 10
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(containerColor)
            .padding(4.dp)
    ) {
        items.forEachIndexed { index, title ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(itemCornerRadius.dp))
                    .background(if (selectedIndex == index) activeColor else Color.Transparent)
                    .clickable { onItemSelected(index) }
                    .padding(vertical = verticalPadding.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selectedIndex == index) activeTextColor else inactiveTextColor
                )
            }
        }
    }
}
