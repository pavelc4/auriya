package dev.auriya.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.SystemInfo
import dev.auriya.app.viewmodel.UiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: UiViewModel,
    onNavigateToGames: () -> Unit
) {
    val systemInfo by viewModel.systemInfo.collectAsState()
    val gameList by viewModel.gameList.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()
    val isDaemonRunning = systemInfo.pid != null && systemInfo.pid != "null"
    val context = LocalContext.current
    var showProfileSheet by remember { mutableStateOf(false) }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    var showInfoSheet by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val currentProfile = systemInfo.profile.lowercase()

    val topBackdropColor = MaterialTheme.colorScheme.surfaceContainer
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLowest

    if (showProfileSheet) {
        ProfileSelectionBottomSheet(
            currentProfile = systemInfo.profile,
            onSelect = { mode ->
                viewModel.updateProfile(mode)
            },
            onDismiss = { showProfileSheet = false },
            sheetState = profileSheetState,
        )
    }

    if (showInfoSheet) {
        AuriyaInfoBottomSheet(
            systemInfo = systemInfo,
            onDismiss = { showInfoSheet = false },
            sheetState = infoSheetState
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auriya",
                style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            FilledIconButton(
                onClick = { showInfoSheet = true },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Version & App Info",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refresh { isRefreshing = false }
                },
                state = refreshState,
                indicator = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        val progress = refreshState.distanceFraction.coerceIn(0f, 1f)
                        if (isRefreshing || progress > 0f) {
                            AuriyaLoadingIndicator(
                                size = 56.dp * if (isRefreshing) 1f else progress,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
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
                            onProfileClick = { showProfileSheet = true }
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
    val cardBg = if (isDaemonRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val onCardBg = if (isDaemonRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDaemonRunning) "Auriya is working" else "Auriya is stopped",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = onCardBg,
                    )
                    val cleanVersion = systemInfo.version.removePrefix("v").removePrefix("V")
                    val cleanArch = when (val arch = systemInfo.deviceArch.uppercase()) {
                        "V8A" -> "ARM64-V8A"
                        "V7A" -> "ARM-V7A"
                        else -> arch
                    }
                    Text(
                        text = "Version $cleanVersion (${systemInfo.commit}) · $cleanArch",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardBg.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
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

@Composable
private fun MiniCardRow(
    profile: String,
    gameCount: Int,
    onGamesClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val profileShort = when (profile.lowercase()) {
        "performance", "1" -> "Perf"
        "balance", "2" -> "Balance"
        "powersave", "3" -> "Saver"
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
        )
        Spacer(Modifier.width(AuriyaTokens.padding.normal))
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelectionBottomSheet(
    currentProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val normProfile = currentProfile.lowercase()
    val displayProfile = when (normProfile) {
        "powersave", "3" -> "Power Save"
        "performance", "1" -> "Performance"
        else -> "Balance"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Performance Profile",
                        style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Active: $displayProfile",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Power Save Card
            item {
                val isSelected = normProfile == "powersave" || normProfile == "3"
                ProfileCard(
                    title = "Power Save",
                    subtitle = "Profile 3 • Battery Preservation",
                    description = "Limits clock frequencies and aggressively throttles background tasks to maximize battery life.",
                    icon = Icons.Outlined.Eco,
                    iconContainerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = isSelected,
                    onClick = {
                        onSelect("3")
                        onDismiss()
                    }
                )
            }

            // Balance Card
            item {
                val isSelected = normProfile == "balance" || normProfile == "2" || normProfile.isEmpty() || normProfile == "unknown"
                ProfileCard(
                    title = "Balance",
                    subtitle = "Profile 2 • Daily Dynamic Tuning",
                    description = "Dynamic optimization and adaptive frequency scaling for smooth daily responsiveness and efficiency.",
                    icon = Icons.Outlined.Tune,
                    iconContainerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = isSelected,
                    onClick = {
                        onSelect("2")
                        onDismiss()
                    }
                )
            }

            // Performance Card
            item {
                val isSelected = normProfile == "performance" || normProfile == "1"
                ProfileCard(
                    title = "Performance",
                    subtitle = "Profile 1 • Maximum Power",
                    description = "Unlocks high clock frequencies and unthrottled rendering for demanding gaming sessions.",
                    icon = Icons.Outlined.Bolt,
                    iconContainerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = isSelected,
                    onClick = {
                        onSelect("1")
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuriyaInfoBottomSheet(
    systemInfo: SystemInfo,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val cleanVersion = systemInfo.version.removePrefix("v").removePrefix("V")
    val cleanArch = when (val arch = systemInfo.deviceArch.uppercase()) {
        "V8A" -> "ARM64-V8A"
        "V7A" -> "ARM-V7A"
        else -> arch
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            item {
                Text(
                    text = "Auriya $cleanVersion",
                    style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Card 1: Build Info
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "α",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auriya Daemon $cleanVersion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Running daemon build (${systemInfo.commit}) optimized for $cleanArch architecture. eBPF kernel tracing engine active.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Card 2: GitHub issue shortcut
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(dev.auriya.app.R.drawable.ic_github),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub issue shortcut",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Search first, then open a focused report for bugs, crashes, requests, or questions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pavelc4/Auriya/issues")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Open existing issues", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pavelc4/Auriya/issues/new")))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Report issue or crash", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Card 3: What to expect
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "What to expect",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Kernel daemon features and optimizations:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.padding(start = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "• Low-overhead eBPF render thread monitoring",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Dynamic governor frequency scaling per frame load",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Per-app thermal throttle & FPS cap optimization",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Pure native Rust daemon with zero battery waste",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
