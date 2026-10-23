package dev.auriya.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auriya.app.R
import dev.auriya.app.ui.components.AnimatedTickerText
import dev.auriya.app.ui.components.bouncyClickable
import dev.auriya.app.ui.theme.AuriyaTokens

@Composable
fun MiniMetricRow(
    profile: String,
    gameCount: Int,
    onGamesClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileShort =
        when (profile.lowercase()) {
            "performance", "1" -> "Perf"
            "balance", "2" -> "Balance"
            "powersave", "3" -> "Saver"
            "fast" -> "Fast"
            else -> profile
        }
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
    ) {
        MiniCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            icon = Icons.Outlined.SportsEsports,
            value = gameCount.toString(),
            label = stringResource(R.string.nav_games),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onGamesClick,
        )
        MiniCard(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            icon = Icons.Outlined.Tune,
            value = profileShort,
            label = stringResource(R.string.home_active_profile),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onProfileClick,
        )
    }
}

@Composable
fun MiniCard(
    icon: ImageVector,
    value: String,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .bouncyClickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedTickerText(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
