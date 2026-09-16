package dev.auriya.app.ui.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.AppIconCache
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.components.bouncyClickable
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.AppInfoItem
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActiveAppItem(
    val packageName: String,
    val label: String,
    val profile: GameProfile,
)

enum class SortMode(
    val title: String,
) {
    NAME_ASC("Name (A → Z)"),
    NAME_DESC("Name (Z → A)"),
    PACKAGE_ASC("Package (A → Z)"),
}

private val appIconShape = RoundedCornerShape(14.dp)

private fun shapeFor(packageName: String): Shape = appIconShape

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = AppIconCache.get(packageName), packageName) {
        if (value == null && !AppIconCache.isMiss(packageName)) {
            value =
                withContext(Dispatchers.IO) {
                    AppIconCache.load(context.packageManager, packageName)
                }
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: UiViewModel,
    onEditGame: (GameProfile) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val sharedPrefs = remember { context.getSharedPreferences("auriya_ui", Context.MODE_PRIVATE) }
    var bannerDismissed by remember { mutableStateOf(sharedPrefs.getBoolean("games_banner_dismissed", false)) }

    val gameList by viewModel.gameList.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isAppsLoading by viewModel.isAppsLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME_ASC) }

    var showGamesInfoSheet by remember { mutableStateOf(false) }
    val gamesInfoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showGamesInfoSheet) {
        GamesInfoBottomSheet(
            onDismiss = { showGamesInfoSheet = false },
            sheetState = gamesInfoSheetState,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(pm)
    }

    val activeProfilesMap =
        remember(gameList.games) {
            gameList.games.associateBy { it.packageName }
        }
    val (activeApps, inactiveApps) =
        remember(installedApps, gameList.games, sortMode) {
            val active = mutableListOf<ActiveAppItem>()
            val inactive = mutableListOf<AppInfoItem>()
            installedApps.forEach { app ->
                val profile = activeProfilesMap[app.packageName]
                if (profile != null) {
                    active += ActiveAppItem(app.packageName, app.label, profile)
                } else {
                    inactive += app
                }
            }
            gameList.games.forEach { game ->
                if (active.none { it.packageName == game.packageName }) {
                    val label =
                        runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
                        }.getOrDefault(game.packageName.substringAfterLast('.'))
                    active += ActiveAppItem(game.packageName, label, game)
                }
            }
            when (sortMode) {
                SortMode.NAME_ASC -> {
                    active.sortBy { it.label.lowercase() }
                    inactive.sortBy { it.label.lowercase() }
                }

                SortMode.NAME_DESC -> {
                    active.sortByDescending { it.label.lowercase() }
                    inactive.sortByDescending { it.label.lowercase() }
                }

                SortMode.PACKAGE_ASC -> {
                    active.sortBy { it.packageName.lowercase() }
                    inactive.sortBy { it.packageName.lowercase() }
                }
            }
            active to inactive
        }

    val filteredActive =
        remember(searchQuery, activeApps) {
            if (searchQuery.isEmpty()) {
                activeApps
            } else {
                activeApps.filter { it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
            }
        }
    val filteredInactive =
        remember(searchQuery, inactiveApps) {
            if (searchQuery.isEmpty()) {
                inactiveApps
            } else {
                inactiveApps.filter { it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
            }
        }

    var isRefreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // --- 1. TOP PINNED HEADER (Backdrop layer with question icon) ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_title),
                    style =
                        dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 36.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledIconButton(
                onClick = { showGamesInfoSheet = true },
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Games Info & Help",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search & Sort Row (Full width elongated search bar + Sort icon button)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search apps or package…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                    )

                    val sortIcon =
                        when (sortMode) {
                            SortMode.NAME_ASC -> Icons.AutoMirrored.Filled.Sort
                            SortMode.NAME_DESC -> Icons.Outlined.SwapVert
                            SortMode.PACKAGE_ASC -> Icons.Outlined.Apps
                        }

                    FilledTonalIconButton(
                        onClick = {
                            sortMode =
                                when (sortMode) {
                                    SortMode.NAME_ASC -> SortMode.NAME_DESC
                                    SortMode.NAME_DESC -> SortMode.PACKAGE_ASC
                                    SortMode.PACKAGE_ASC -> SortMode.NAME_ASC
                                }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor =
                                    if (sortMode != SortMode.NAME_ASC) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                contentColor =
                                    if (sortMode != SortMode.NAME_ASC) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            ),
                        modifier = Modifier.size(52.dp),
                    ) {
                        AnimatedContent(
                            targetState = sortIcon,
                            transitionSpec = {
                                (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                    .togetherWith(scaleOut() + fadeOut())
                            },
                            label = "sort-icon-anim",
                        ) { targetIcon ->
                            Icon(
                                imageVector = targetIcon,
                                contentDescription = "Sort: ${sortMode.title}",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Scrollable List
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.refresh {
                            isRefreshing = false
                        }
                    },
                    state = state,
                    indicator = {
                        val rawProgress = state.distanceFraction
                        val isVisible = isRefreshing || rawProgress >= 0.4f
                        if (isVisible) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp),
                            ) {
                                val scale = if (isRefreshing) 1f else ((rawProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                                AuriyaLoadingIndicator(
                                    size = (52.dp * scale).coerceAtLeast(28.dp),
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!bannerDismissed) {
                            item {
                                HeroBanner(
                                    onDismiss = {
                                        sharedPrefs.edit().putBoolean("games_banner_dismissed", true).apply()
                                        bannerDismissed = true
                                    },
                                )
                            }
                        }

                        if (isAppsLoading && installedApps.isEmpty()) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
                                    ) {
                                        AuriyaLoadingIndicator(size = 48.dp)
                                        Text(
                                            "Resolving applications…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredActive.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    label = "Active profiles",
                                    count = filteredActive.size,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                                )
                            }
                            itemsIndexed(
                                items = filteredActive,
                                key = { _, a -> "active-${a.packageName}" },
                            ) { index, app ->
                                ContinuousRow(index = index, lastIndex = filteredActive.lastIndex) {
                                    ActiveRowContent(app, onClick = { onEditGame(app.profile) })
                                }
                            }
                        }

                        if (filteredInactive.isNotEmpty()) {
                            item {
                                SectionLabel(
                                    label = "Installed applications",
                                    count = filteredInactive.size,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                                )
                            }
                            itemsIndexed(
                                items = filteredInactive,
                                key = { _, a -> "inactive-${a.packageName}" },
                            ) { index, app ->
                                ContinuousRow(index = index, lastIndex = filteredInactive.lastIndex) {
                                    InactiveRowContent(app, onClick = {
                                        onEditGame(
                                            GameProfile(
                                                packageName = app.packageName,
                                                cpuGovernor = "performance",
                                                enableDnd = true,
                                                targetFps = 60,
                                            ),
                                        )
                                    })
                                }
                            }
                        }

                        if (filteredActive.isEmpty() && filteredInactive.isEmpty()) {
                            item { EmptyState() }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamesInfoBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            dev.auriya.app.ui.components
                .AuriyaDragHandle()
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.games_tuner_title),
                        style =
                            dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 30.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.games_tuner_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Card 1: Per-App Governor
            item {
                InfoFeatureCard(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_cpu_governor),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_cpu_sub),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_cpu_governor_desc),
                    icon = Icons.Outlined.Tune,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // Card 2: Target FPS & Refresh Rate
            item {
                InfoFeatureCard(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_target_fps),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_refresh_rate),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.guide_fps_jank_desc),
                    icon = Icons.Outlined.Speed,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // Card 3: Do Not Disturb
            item {
                InfoFeatureCard(
                    title =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_dnd),
                    subtitle =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_dnd_sub),
                    description =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.config_dnd_sub),
                    icon = Icons.Outlined.NotificationsOff,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun InfoFeatureCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroBanner(onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.large),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AuriyaTokens.padding.smaller),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(AuriyaTokens.padding.smaller),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(AuriyaTokens.iconSize.medium),
                )
            }
            Column(
                modifier = Modifier.padding(AuriyaTokens.padding.larger),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(AuriyaTokens.rounding.medium))
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(AuriyaTokens.iconSize.medium),
                    )
                }
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_tuner_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.games_tuner_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = AuriyaTokens.padding.smaller, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ContinuousRow(
    index: Int,
    lastIndex: Int,
    content: @Composable () -> Unit,
) {
    val large = 20.dp
    val small = 4.dp
    val shape =
        when {
            lastIndex == 0 -> RoundedCornerShape(large)
            index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
            index == lastIndex -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
            else -> RoundedCornerShape(small)
        }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun ActiveRowContent(
    app: ActiveAppItem,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .bouncyClickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconBox(packageName = app.packageName, shape = shapeFor(app.packageName))
            Text(
                text = app.label,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val displayMode =
                when (app.profile.mode?.lowercase()) {
                    "fas", "fast" -> "FAS"
                    "balance" -> "BAL"
                    "powersave" -> "PWR"
                    else -> "PERF"
                }
            Surface(
                shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = displayMode,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun InactiveRowContent(
    app: AppInfoItem,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .bouncyClickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconBox(packageName = app.packageName, shape = shapeFor(app.packageName))
            Text(
                text = app.label,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppIconBox(
    packageName: String,
    shape: Shape,
) {
    val bitmap = rememberAppIcon(packageName)
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.games_not_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    androidx.compose.ui.res
                        .stringResource(dev.auriya.app.R.string.games_not_found_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
