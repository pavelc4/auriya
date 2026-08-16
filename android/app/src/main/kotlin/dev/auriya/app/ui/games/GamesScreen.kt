package dev.auriya.app.ui.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.SportsEsports
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.AppIconCache
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.components.MaterialShapes
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfoItem(val packageName: String, val label: String)
data class ActiveAppItem(val packageName: String, val label: String, val profile: GameProfile)

enum class GameTab(val title: String) {
    ALL("All"),
    ACTIVE("Active"),
    INSTALLED("Installed"),
}

enum class SortMode {
    NAME_ASC,
    NAME_DESC,
}

private val rowShapes = arrayOf(
    MaterialShapes.Cookie9,
    MaterialShapes.Scallop12,
    MaterialShapes.Clover6,
    MaterialShapes.Puffy,
)

private fun shapeFor(packageName: String): Shape {
    val hash = packageName.hashCode().let { it xor (it ushr 16) }
    return rowShapes[hash and 0x3]
}

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = AppIconCache.get(packageName), packageName) {
        if (value == null && !AppIconCache.isMiss(packageName)) {
            value = withContext(Dispatchers.IO) {
                AppIconCache.load(context.packageManager, packageName)
            }
        }
    }.value
}

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
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.NAME_ASC) }
    var selectedTab by remember { mutableStateOf(GameTab.ALL) }
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { appInfo ->
                    val label = runCatching {
                        pm.getApplicationLabel(appInfo).toString()
                    }.getOrDefault(appInfo.packageName)
                    AppIconCache.load(pm, appInfo.packageName)
                    AppInfoItem(packageName = appInfo.packageName, label = label)
                }
                .sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoading = false
            }
        }
    }

    if (isLoading) {
        LoadingState()
        return
    }

    val activeProfilesMap = remember(gameList.games) {
        gameList.games.associateBy { it.packageName }
    }
    val (activeApps, inactiveApps) = remember(installedApps, gameList.games, sortMode) {
        val active = mutableListOf<ActiveAppItem>()
        val inactive = mutableListOf<AppInfoItem>()
        installedApps.forEach { app ->
            val profile = activeProfilesMap[app.packageName]
            if (profile != null) active += ActiveAppItem(app.packageName, app.label, profile)
            else inactive += app
        }
        gameList.games.forEach { game ->
            if (active.none { it.packageName == game.packageName }) {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
                }.getOrDefault(game.packageName.substringAfterLast('.'))
                active += ActiveAppItem(game.packageName, label, game)
            }
        }
        if (sortMode == SortMode.NAME_ASC) {
            active.sortBy { it.label.lowercase() }
            inactive.sortBy { it.label.lowercase() }
        } else {
            active.sortByDescending { it.label.lowercase() }
            inactive.sortByDescending { it.label.lowercase() }
        }
        active to inactive
    }

    val filteredActive = remember(searchQuery, activeApps) {
        if (searchQuery.isEmpty()) activeApps
        else activeApps.filter { it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
    }
    val filteredInactive = remember(searchQuery, inactiveApps) {
        if (searchQuery.isEmpty()) inactiveApps
        else inactiveApps.filter { it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val state = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // --- 1. TOP PINNED HEADER (Backdrop layer) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Games",
                style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // --- 2. PINNED FILTER PILLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val count = when (tab) {
                    GameTab.ALL -> filteredActive.size + filteredInactive.size
                    GameTab.ACTIVE -> filteredActive.size
                    GameTab.INSTALLED -> filteredInactive.size
                }
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "tab_bg"
                )
                val pillTextColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab_text"
                )

                Surface(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(24.dp),
                    color = pillBg,
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tab.title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = pillTextColor
                        )
                        if (count > 0) {
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = pillTextColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Row (Actions / Sort / Search)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Action Pill: Add Custom Game
                    FilledTonalButton(
                        onClick = {
                            if (filteredInactive.isNotEmpty()) {
                                onEditGame(
                                    GameProfile(
                                        packageName = filteredInactive.first().packageName,
                                        cpuGovernor = "performance",
                                        enableDnd = true,
                                        targetFps = 60,
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Add Profile",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right Segmented Action Buttons: Search & Sort
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { isSearchVisible = !isSearchVisible },
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isSearchVisible || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSearchVisible || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                sortMode = if (sortMode == SortMode.NAME_ASC) SortMode.NAME_DESC else SortMode.NAME_ASC
                            },
                            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (sortMode == SortMode.NAME_DESC) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (sortMode == SortMode.NAME_DESC) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Expandable Search Bar
                AnimatedVisibility(
                    visible = isSearchVisible || searchQuery.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SearchPill(searchQuery, onChange = { searchQuery = it })
                    }
                }

                // Scrollable List
                @OptIn(ExperimentalMaterial3Api::class)
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
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            val progress = state.distanceFraction.coerceIn(0f, 1f)
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!bannerDismissed && selectedTab != GameTab.INSTALLED) {
                            item {
                                HeroBanner(
                                    onDismiss = {
                                        sharedPrefs.edit().putBoolean("games_banner_dismissed", true).apply()
                                        bannerDismissed = true
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            GameTab.ALL -> {
                                if (filteredActive.isNotEmpty()) {
                                    item {
                                        SectionLabel(
                                            label = "Active profiles",
                                            count = filteredActive.size,
                                            modifier = Modifier.padding(top = AuriyaTokens.padding.small, bottom = AuriyaTokens.padding.smaller),
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
                                            modifier = Modifier.padding(top = AuriyaTokens.padding.normal, bottom = AuriyaTokens.padding.smaller),
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
                                                    )
                                                )
                                            })
                                        }
                                    }
                                }
                            }
                            GameTab.ACTIVE -> {
                                if (filteredActive.isNotEmpty()) {
                                    itemsIndexed(
                                        items = filteredActive,
                                        key = { _, a -> "active-${a.packageName}" },
                                    ) { index, app ->
                                        ContinuousRow(index = index, lastIndex = filteredActive.lastIndex) {
                                            ActiveRowContent(app, onClick = { onEditGame(app.profile) })
                                        }
                                    }
                                }
                            }
                            GameTab.INSTALLED -> {
                                if (filteredInactive.isNotEmpty()) {
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
                                                    )
                                                )
                                            })
                                        }
                                    }
                                }
                            }
                        }

                        val isTabEmpty = when (selectedTab) {
                            GameTab.ALL -> filteredActive.isEmpty() && filteredInactive.isEmpty()
                            GameTab.ACTIVE -> filteredActive.isEmpty()
                            GameTab.INSTALLED -> filteredInactive.isEmpty()
                        }
                        if (isTabEmpty) {
                            item { EmptyState(tab = selectedTab) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
        ) {
            AuriyaLoadingIndicator(size = 56.dp)
            Text("Resolving applications…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HeroBanner(onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.large),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuriyaTokens.padding.smaller),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AuriyaTokens.padding.smaller)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(AuriyaTokens.iconSize.medium)
                )
            }
            Column(
                modifier = Modifier.padding(AuriyaTokens.padding.larger),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
            ) {
                Box(
                    modifier = Modifier
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
                    text = "Games Tuner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Tune CPU governor, target FPS, refresh rate and DnD per game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text(
                "Search apps or package…",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuriyaTokens.padding.small),
        singleLine = true,
        shape = RoundedCornerShape(AuriyaTokens.rounding.full),
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SectionLabel(label: String, count: Int, modifier: Modifier = Modifier) {
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
    val large = AuriyaTokens.rounding.extraLarge
    val small = AuriyaTokens.rounding.extraSmall
    val shape = when {
        lastIndex == 0 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
        index == lastIndex -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
        else -> RoundedCornerShape(small)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun ActiveRowContent(app: ActiveAppItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconBox(packageName = app.packageName, shape = shapeFor(app.packageName))
            Spacer(Modifier.width(AuriyaTokens.padding.normal))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(AuriyaTokens.padding.small))
            val (badgeText, badgeTone) = when (app.profile.cpuGovernor.lowercase()) {
                "performance" -> "Performance" to StatusTone.PRIMARY
                "powersave" -> "Powersave" to StatusTone.WARNING
                else -> app.profile.cpuGovernor to StatusTone.SECONDARY
            }
            StatusBadge(label = badgeText, tone = badgeTone)
        }
    }
}

@Composable
private fun InactiveRowContent(app: AppInfoItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconBox(packageName = app.packageName, shape = shapeFor(app.packageName))
            Spacer(Modifier.width(AuriyaTokens.padding.normal))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppIconBox(packageName: String, shape: Shape) {
    val bitmap = rememberAppIcon(packageName)
    Box(
        modifier = Modifier
            .size(48.dp)
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
                modifier = Modifier.size(AuriyaTokens.iconSize.normal),
            )
        }
    }
}

@Composable
private fun EmptyState(tab: GameTab = GameTab.ALL) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = when (tab) {
                    GameTab.ALL -> "No games found"
                    GameTab.ACTIVE -> "No active game profiles yet"
                    GameTab.INSTALLED -> "No installed apps found"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (tab) {
                    GameTab.ALL -> "Try searching with a different term."
                    GameTab.ACTIVE -> "Select an installed app below to configure its profile."
                    GameTab.INSTALLED -> "All installed apps are currently tuned."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
