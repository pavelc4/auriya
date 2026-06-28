package dev.auriya.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.components.rememberCookie9
import dev.auriya.app.ui.components.rememberPixelCircle
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.SystemInfo
import dev.auriya.app.viewmodel.UiViewModel

@Composable
fun HomeScreen(viewModel: UiViewModel, onNavigateToGames: () -> Unit) {
    val systemInfo by viewModel.systemInfo.collectAsState()
    val gameList by viewModel.gameList.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val isDaemonRunning = systemInfo.pid != null && systemInfo.pid != "null"
    val context = LocalContext.current
    var showProfileDialog by remember { mutableStateOf(false) }

    if (showProfileDialog) {
        ProfileSelectionDialog(
            currentProfile = systemInfo.profile,
            onSelect = { mode ->
                viewModel.updateProfile(mode)
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuriyaTokens.padding.normal),
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
        contentPadding = PaddingValues(top = AuriyaTokens.padding.normal, bottom = AuriyaTokens.padding.largest * 3),
    ) {
        if (!hasRoot) {
            item { RootDeniedBanner() }
        }
        item { HeroCard(isDaemonRunning = isDaemonRunning, systemInfo = systemInfo) }
        item {
            MiniCardRow(
                profile = systemInfo.profile,
                gameCount = gameList.games.size,
                onGamesClick = onNavigateToGames,
                onProfileClick = { showProfileDialog = true }
            )
        }
        item { SystemMetricsList(systemInfo = systemInfo) }
        item {
            LinkRow(
                iconPainter = androidx.compose.ui.res.painterResource(dev.auriya.app.R.drawable.ic_github),
                title = "Learn more about Auriya",
                subtitle = "github.com/Pavelc4/Auriya",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pavelc4/Auriya")))
                },
            )
        }
        item {
            LinkRow(
                iconPainter = androidx.compose.ui.res.painterResource(dev.auriya.app.R.drawable.ic_telegram),
                title = "Join Telegram updates channel",
                subtitle = "Latest tuner updates",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/pvlcply")))
                },
            )
        }
    }
}

@Composable
private fun RootDeniedBanner() {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.xl),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AuriyaTokens.padding.larger),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(AuriyaTokens.iconSize.normal),
            )
            Spacer(Modifier.width(AuriyaTokens.padding.normal))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Root access required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Grant superuser permission in KernelSU/Magisk/APatch manager so Auriya can read daemon state and config files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(isDaemonRunning: Boolean, systemInfo: SystemInfo) {
    val workingBg = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
    val stoppedBg = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDaemonRunning) workingBg else stoppedBg)
                .padding(AuriyaTokens.padding.larger),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(rememberPixelCircle())
                            .background(
                                if (isDaemonRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDaemonRunning) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(AuriyaTokens.padding.normal))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDaemonRunning) "Auriya is working" else "Auriya is stopped",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "v${systemInfo.version} (${systemInfo.commit}) · ${systemInfo.deviceArch.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(AuriyaTokens.padding.normal))
                Row(horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest)) {
                    if (isDaemonRunning) {
                        StatusBadge(label = "PID ${systemInfo.pid}", tone = StatusTone.SUCCESS)
                    } else {
                        StatusBadge(label = "Stopped", tone = StatusTone.ERROR)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCardRow(
    profile: String,
    gameCount: Int,
    onGamesClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    // Profile names like "Performance" / "Powersave" overflow the
    // narrow MiniCard. Show a compact label instead — full value lives
    // in the Settings screen anyway.
    val profileShort = when (profile.lowercase()) {
        "performance" -> "Perf"
        "balance" -> "Balance"
        "powersave" -> "Saver"
        "fast" -> "Fast"
        else -> profile
    }
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
    ) {
        MiniCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Outlined.SportsEsports,
            value = gameCount.toString(),
            label = "Games",
            accentColor = MaterialTheme.colorScheme.primary,
            onClick = onGamesClick,
        )
        MiniCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            icon = Icons.Outlined.Tune,
            value = profileShort,
            label = "Profile",
            accentColor = MaterialTheme.colorScheme.tertiary,
            onClick = onProfileClick,
        )
    }
}

@Composable
private fun MiniCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Icon Box (small rounded square) + Label
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Value text below (Large)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SystemMetricsList(systemInfo: SystemInfo) {
    val rows = remember(systemInfo) {
        listOf(
            MetricRow(Icons.Outlined.Memory, "Memory", systemInfo.ram),
            MetricRow(Icons.Outlined.Settings, "Kernel", systemInfo.kernel),
            MetricRow(Icons.Outlined.Speed, "Chipset", systemInfo.chipset),
        )
    }
    Column {
        Text(
            text = "SYSTEM METRICS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = AuriyaTokens.padding.smaller, bottom = AuriyaTokens.padding.smaller),
        )
        ExpressiveList(count = rows.size) { i ->
            MetricRowItem(rows[i])
        }
    }
}

private data class MetricRow(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun MetricRowItem(row: MetricRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AuriyaTokens.rounding.medium))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuriyaTokens.iconSize.medium),
            )
        }
        Spacer(Modifier.width(AuriyaTokens.padding.normal))
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LinkRow(
    iconPainter: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(AuriyaTokens.rounding.xl),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AuriyaTokens.padding.larger),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuriyaTokens.iconSize.normal),
            )
            Spacer(Modifier.width(AuriyaTokens.padding.larger))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(AuriyaTokens.padding.smallest))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ProfileSelectionDialog(
    currentProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Performance Preset",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileDialogItem(
                    title = "Power Save",
                    description = "Limits frequencies to maximize battery life.",
                    icon = Icons.Outlined.Eco,
                    color = MaterialTheme.colorScheme.primary,
                    selected = currentProfile.lowercase() == "powersave" || currentProfile == "3",
                    onClick = {
                        onSelect("3")
                        onDismiss()
                    }
                )
                ProfileDialogItem(
                    title = "Balance",
                    description = "Dynamic optimization for everyday use.",
                    icon = Icons.Outlined.Tune,
                    color = MaterialTheme.colorScheme.secondary,
                    selected = currentProfile.lowercase() == "balance" || currentProfile == "2" || currentProfile.isEmpty() || currentProfile == "unknown",
                    onClick = {
                        onSelect("2")
                        onDismiss()
                    }
                )
                ProfileDialogItem(
                    title = "Performance",
                    description = "Enables full potential for heavy gaming.",
                    icon = Icons.Outlined.Bolt,
                    color = MaterialTheme.colorScheme.error,
                    selected = currentProfile.lowercase() == "performance" || currentProfile == "1",
                    onClick = {
                        onSelect("1")
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProfileDialogItem(
    title: String,
    description: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.5.dp, color) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) color else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
