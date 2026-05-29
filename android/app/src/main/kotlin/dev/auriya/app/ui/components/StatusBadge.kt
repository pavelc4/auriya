package dev.auriya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.theme.AuriyaTheme
import dev.auriya.app.ui.theme.AuriyaTokens

enum class StatusTone { PRIMARY, SECONDARY, SUCCESS, WARNING, ERROR, OUTLINE }

@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone = StatusTone.SECONDARY,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (tone) {
        StatusTone.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        StatusTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.SUCCESS -> AuriyaTheme.semantic.successContainer to AuriyaTheme.semantic.onSuccessContainer
        StatusTone.WARNING -> AuriyaTheme.semantic.warningContainer to AuriyaTheme.semantic.onWarningContainer
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.OUTLINE -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(AuriyaTokens.rounding.full))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
