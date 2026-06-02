package dev.auriya.app.ui.games

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
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
import dev.auriya.app.data.AppIconCache
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
    val (activeApps, inactiveApps) = remember(installedApps, gameList.games) {
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
        active.sortBy { it.label.lowercase() }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuriyaTokens.padding.normal),
        contentPadding = PaddingValues(top = AuriyaTokens.padding.normal, bottom = AuriyaTokens.padding.largest * 3),
    ) {
        if (!bannerDismissed) {
            item {
                HeroBanner(
                    onDismiss = {
                        sharedPrefs.edit().putBoolean("games_banner_dismissed", true).apply()
                        bannerDismissed = true
                    }
                )
            }
        }
        item { SearchPill(searchQuery, onChange = { searchQuery = it }) }

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
                    label = "All applications",
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

        if (filteredActive.isEmpty() && filteredInactive.isEmpty()) {
            item { EmptyState() }
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
            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = AuriyaTokens.padding.small),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(AuriyaTokens.rounding.full))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    fontWeight = FontWeight.SemiBold,
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
                Spacer(Modifier.height(AuriyaTokens.padding.smallest))
                Row(horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest)) {
                    app.profile.targetFps?.let {
                        StatusBadge(label = "$it FPS", tone = StatusTone.SUCCESS)
                    }
                    if (app.profile.enableDnd) {
                        StatusBadge(label = "DnD", tone = StatusTone.WARNING)
                    }
                    StatusBadge(label = app.profile.cpuGovernor, tone = StatusTone.OUTLINE)
                }
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            AppIconBox(packageName = app.packageName, shape = RoundedCornerShape(AuriyaTokens.rounding.large))
            Spacer(Modifier.width(AuriyaTokens.padding.normal))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
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
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuriyaTokens.padding.largest * 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No apps match your search.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
