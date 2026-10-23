package dev.auriya.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.theme.GoogleSansRounded

@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconContainerColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

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

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        disabledCheckedBorderColor = Color.Transparent,
                    ),
            )
        }
    }
}
