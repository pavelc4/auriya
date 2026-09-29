package dev.auriya.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.theme.AuriyaTokens

@Composable
fun ExpressiveList(
    count: Int,
    modifier: Modifier = Modifier,
    cornerLarge: Dp = AuriyaTokens.rounding.xl + 2.dp,
    cornerSmall: Dp = AuriyaTokens.rounding.extraSmall,
    gap: Dp = 2.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    item: @Composable (index: Int) -> Unit,
) {
    if (count == 0) return

    val singleShape = androidx.compose.runtime.remember(cornerLarge) { RoundedCornerShape(cornerLarge) }
    val topShape =
        androidx.compose.runtime.remember(cornerLarge, cornerSmall) {
            RoundedCornerShape(
                topStart = cornerLarge,
                topEnd = cornerLarge,
                bottomStart = cornerSmall,
                bottomEnd = cornerSmall,
            )
        }
    val bottomShape =
        androidx.compose.runtime.remember(cornerLarge, cornerSmall) {
            RoundedCornerShape(
                topStart = cornerSmall,
                topEnd = cornerSmall,
                bottomStart = cornerLarge,
                bottomEnd = cornerLarge,
            )
        }
    val middleShape = androidx.compose.runtime.remember(cornerSmall) { RoundedCornerShape(cornerSmall) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0 until count) {
            val shape =
                when {
                    count == 1 -> singleShape
                    i == 0 -> topShape
                    i == count - 1 -> bottomShape
                    else -> middleShape
                }
            Surface(
                shape = shape,
                color = containerColor,
            ) {
                item(i)
            }
        }
    }
}
