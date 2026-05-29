package dev.auriya.app.ui.games

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.components.rememberClover
import dev.auriya.app.ui.components.rememberCookie9
import dev.auriya.app.ui.components.rememberPuffy
import dev.auriya.app.ui.components.rememberScallop
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.UiViewModel
import dev.auriya.shared.model.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfoItem(val packageName: String, val label: String)
data class ActiveAppItem(val packageName: String, val label: String, val profile: GameProfile)

@Composable
fun rememberAppIconPainter(packageName: String): Painter? {
    val context = LocalContext.current
    val pm = context.packageManager
    return remember(packageName) {
        try {
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            BitmapPainter(bitmap.asImageBitmap())
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun GamesScreen(
    viewModel: UiViewModel,
    onEditGame: (GameProfile) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

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
        item { HeroBanner() }
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
private fun HeroBanner() {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuriyaTokens.padding.smaller),
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(rememberCookie9())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(AuriyaTokens.iconSize.normal),
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
            )
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
            AppIconBox(packageName = app.packageName, shape = pickShape(app.packageName))
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
            Text(
                text = "＋",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AppIconBox(packageName: String, shape: Shape) {
    val painter = rememberAppIconPainter(packageName)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize())
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
private fun pickShape(packageName: String): Shape {
    val hash = packageName.hashCode().let { it xor (it ushr 16) }
    return when ((hash and 0x3)) {
        0 -> rememberCookie9()
        1 -> rememberScallop()
        2 -> rememberClover()
        else -> rememberPuffy()
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
