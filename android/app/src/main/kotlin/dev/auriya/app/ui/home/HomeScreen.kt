package dev.auriya.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.LinearWavyProgress
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.components.rememberCookie9
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.SystemInfo
import dev.auriya.app.viewmodel.UiViewModel

@Composable
fun HomeScreen(viewModel: UiViewModel) {
    val systemInfo by viewModel.systemInfo.collectAsState()
    val gameList by viewModel.gameList.collectAsState()
    val foregroundApp by viewModel.foregroundApp.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val isDaemonRunning = systemInfo.pid != null && systemInfo.pid != "null"
    val context = LocalContext.current

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
        item { MiniCardRow(profile = systemInfo.profile, gameCount = gameList.games.size) }
        item { SystemMetricsList(systemInfo = systemInfo, foregroundApp = foregroundApp) }
        item {
            LinkRow(
                icon = Icons.Outlined.Info,
                title = "Learn more about Auriya",
                subtitle = "github.com/Pavelc4/Auriya",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pavelc4/Auriya")))
                },
            )
        }
        item {
            LinkRow(
                icon = Icons.Outlined.Share,
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
private fun HeroCard(isDaemonRunning: Boolean, systemInfo: SystemInfo) {    val workingBg = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    )
    val stoppedBg = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        )
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
                            .size(56.dp)
                            .clip(rememberCookie9())
                            .background(
                                if (isDaemonRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isDaemonRunning) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (isDaemonRunning) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(AuriyaTokens.iconSize.large),
                        )
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
                        StatusBadge(label = "eBPF", tone = StatusTone.PRIMARY)
                        StatusBadge(label = "PID ${systemInfo.pid}", tone = StatusTone.SUCCESS)
                        StatusBadge(label = "FAS", tone = StatusTone.OUTLINE)
                    } else {
                        StatusBadge(label = "Stopped", tone = StatusTone.ERROR)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCardRow(profile: String, gameCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)) {
        MiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.SportsEsports,
            value = gameCount.toString(),
            label = "Games optimized",
            valueColor = MaterialTheme.colorScheme.primary,
        )
        MiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Tune,
            value = profile,
            label = "Active profile",
            valueColor = MaterialTheme.colorScheme.tertiary,
            isText = true,
        )
    }
}

@Composable
private fun MiniCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    isText: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.normal),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AuriyaTokens.iconSize.medium),
            )
            Text(
                text = value,
                style = if (isText) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemMetricsList(systemInfo: SystemInfo, foregroundApp: String?) {
    val rows = remember(systemInfo, foregroundApp) {
        buildList {
            foregroundApp?.let { add(MetricRow(Icons.Outlined.Bolt, "Foreground", it, null)) }
            add(MetricRow(Icons.Outlined.Memory, "Memory", systemInfo.ram, null))
            add(MetricRow(Icons.Outlined.Thermostat, "Thermal", systemInfo.temp, null))
            add(MetricRow(Icons.Outlined.BatteryFull, "Battery", systemInfo.battery, null))
            add(MetricRow(Icons.Outlined.Settings, "Kernel", systemInfo.kernel, null))
            add(MetricRow(Icons.Outlined.Speed, "Chipset", systemInfo.chipset, null))
        }
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
            val r = rows[i]
            MetricRowItem(r)
        }
        Spacer(Modifier.height(AuriyaTokens.padding.smaller))
        ProfileLoadBar()
    }
}

private data class MetricRow(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val progress: Float?,
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
private fun ProfileLoadBar() {
    // Placeholder live metric — replace with real CPU usage when daemon exposes it.
    var fakeLoad by remember { mutableStateOf(0.6f) }
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AuriyaTokens.iconSize.medium),
            )
            Spacer(Modifier.width(AuriyaTokens.padding.small))
            Text(
                text = "Profile load",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            LinearWavyProgress(
                progress = fakeLoad,
                modifier = Modifier
                    .weight(2f)
                    .padding(horizontal = AuriyaTokens.padding.smaller),
            )
            Text(
                text = "${(fakeLoad * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
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
                imageVector = icon,
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
