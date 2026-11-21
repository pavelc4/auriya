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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            "performance", "1" -> stringResource(R.string.home_profile_performance)
            "balance", "2" -> stringResource(R.string.home_profile_balance)
            "powersave", "3" -> stringResource(R.string.home_profile_powersave)
            "fast", "fas", "4" -> stringResource(R.string.home_profile_fast)
            else -> profile
        }

    val profileStyle =
        when {
            profileShort.length > 9 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 34.sp)
            profileShort.length > 7 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 34.sp)
            else -> MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp)
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
    ) {
        MiniCard(
            modifier = Modifier.weight(1f).height(120.dp),
            icon = Icons.Outlined.SportsEsports,
            value = gameCount.toString(),
            label = stringResource(R.string.nav_games),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onGamesClick,
            valueStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp),
        )
        MiniCard(
            modifier = Modifier.weight(1f).height(120.dp),
            icon = Icons.Outlined.Tune,
            value = profileShort,
            label = stringResource(R.string.home_active_profile),
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onProfileClick,
            valueStyle = profileStyle,
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
    valueStyle: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .bouncyClickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedTickerText(
                text = value,
                style = valueStyle,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
