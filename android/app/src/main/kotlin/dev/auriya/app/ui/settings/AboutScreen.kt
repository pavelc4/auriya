package dev.auriya.app.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.auriya.app.R
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OWNER_LOGIN = "Pavelc4"
private const val REPO_NAME = "Auriya"
private const val APP_TAGLINE = "An experimental learning project — Magisk/KernelSU performance tuning for Android."

data class RepoInfo(
    val name: String,
    val description: String,
    val version: String,
)

data class OwnerInfo(
    val login: String,
    val name: String?,
    val bio: String?,
    val htmlUrl: String,
    val avatarUrl: String?,
)

data class Contributor(
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val contributions: Int,
    val role: String = "Community Contributor",
)

@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val fallbackContributors = remember { emptyList<Contributor>() }

    var repoInfo by remember { mutableStateOf<RepoInfo?>(RepoInfo(REPO_NAME, APP_TAGLINE, "2.0.0")) }
    var ownerInfo by remember { mutableStateOf<OwnerInfo?>(AboutCache.getCachedOwner(context)) }
    var contributors by remember {
        val cached = AboutCache.getCachedContributors(context)
        mutableStateOf(cached)
    }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var isLoadingOwner by remember { mutableStateOf(false) }
    var isLoadingContributors by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (AboutCache.shouldRefresh(context)) {
            coroutineScope.launch(Dispatchers.IO) {
                val info = fetchRepoInfo()
                withContext(Dispatchers.Main) {
                    repoInfo = info
                }
            }
            coroutineScope.launch(Dispatchers.IO) {
                val owner = fetchOwnerInfo()
                AboutCache.saveOwner(context, owner)
                withContext(Dispatchers.Main) {
                    ownerInfo = owner
                }
            }
            coroutineScope.launch(Dispatchers.IO) {
                val list = fetchContributors()
                if (list.isNotEmpty()) {
                    AboutCache.saveContributors(context, list)
                    withContext(Dispatchers.Main) {
                        contributors = list
                    }
                }
            }
        }
    }

    // Filter out repo owner and automated bot accounts
    val filteredContributors = remember(contributors) {
        contributors.filter { contributor ->
            val login = contributor.login.lowercase()
            login != OWNER_LOGIN.lowercase() &&
                    !login.endsWith("[bot]") &&
                    !login.contains("[bot]") &&
                    login != "github-actions" &&
                    login != "dependabot" &&
                    !login.contains("actions-user")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onDismiss,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "About",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Developer information and project specs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                // 1. Clean App Hero Card
                item(key = "hero_card") {
                    AuriyaHeroCard(
                        repoInfo = repoInfo,
                        isLoading = isLoadingInfo
                    )
                }

                // 2. Licenses Section (Moved below Auriya Card, above Maintainer)
                item(key = "license_header") {
                    SectionHeader(
                        title = "License",
                        subtitle = "Open source license and terms."
                    )
                }

                item(key = "license_card") {
                    LicenseAndSpecsCard(
                        onLicenseClick = {
                            openUrl(context, "https://github.com/$OWNER_LOGIN/$REPO_NAME/blob/main/LICENSE")
                        }
                    )
                }

                // 3. Documentation Section
                item(key = "docs_header") {
                    SectionHeader(
                        title = "Documentation",
                        subtitle = "Explore More Abour Auriya."
                    )
                }

                item(key = "docs_card") {
                    DocumentationCard(
                        onDocsClick = {
                            openUrl(context, "https://auriya.pages.dev/")
                        }
                    )
                }

                // 4. Maintainer Section
                item(key = "maintainer_header") {
                    SectionHeader(
                        title = "Maintainer",
                        subtitle = "The person behind Auriya."
                    )
                }

                item(key = "maintainer_card") {
                    MaintainerCard(
                        owner = ownerInfo,
                        isLoading = isLoadingOwner,
                        onCardClick = { openUrl(context, ownerInfo?.htmlUrl ?: "https://github.com/$OWNER_LOGIN") }
                    )
                }

                // 5. Support Section (Directly under Maintainer)
                item(key = "support_header") {
                    SectionHeader(
                        title = "Support",
                        subtitle = "Help fuel ongoing development and updates."
                    )
                }

                item(key = "support_card") {
                    SupportCard(
                        onCoffeeClick = {
                            openUrl(context, "https://github.com/sponsors/$OWNER_LOGIN")
                        }
                    )
                }

                // 6. Community Contributors Section
                item(key = "spotlight_header") {
                    SectionHeader(
                        title = "Contributors",
                        subtitle = "Collaborators and contributors to the project."
                    )
                }

                if (isLoadingContributors) {
                    item(key = "contributors_loading") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AuriyaLoadingIndicator(size = 20.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Fetching contributors...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (filteredContributors.isEmpty()) {
                    item(key = "contributors_empty") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No other contributors found yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    item(key = "contributors_list") {
                        ContributorsBlock(
                            contributors = filteredContributors,
                            onContributorClick = { url -> openUrl(context, url) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuriyaHeroCard(
    repoInfo: RepoInfo?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val cleanVersion = remember(repoInfo) {
        val raw = repoInfo?.version ?: "2.0.0"
        raw.trimStart('v')
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: App Icon + App Name & Tagline
            val context = LocalContext.current
            val appIconBitmap = remember(context) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(context.packageName)
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = dev.auriya.app.ui.components.MaterialShapes.PixelCircle,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "Auriya",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(dev.auriya.app.ui.components.MaterialShapes.PixelCircle)
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Auriya",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = APP_TAGLINE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Version Capsule Pill (Prominent left-aligned, compact)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = if (isLoading || repoInfo == null) "Version v2.0.0-expressive" else "Version v$cleanVersion-expressive",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Compact Technology Capsule Pills (Sleek FlowRow)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeroSignalPill(label = "Kotlin")
                HeroSignalPill(label = "Rust")
                HeroSignalPill(label = "MDE3")
                HeroSignalPill(label = "GPL-3.0")
                HeroSignalPill(label = "Open source")
            }
        }
    }
}

@Composable
private fun HeroSignalPill(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaintainerCard(
    owner: OwnerInfo?,
    isLoading: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AvatarImage(
                url = owner?.avatarUrl,
                fallbackInitial = (owner?.login ?: OWNER_LOGIN).take(1).uppercase(),
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isLoading) "Loading..." else (owner?.name ?: OWNER_LOGIN),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val bioText = owner?.bio?.takeIf { it.isNotBlank() }
                    ?: "Rust daemon, eBPF telemetry, and Android Kotlin UI"

                Text(
                    text = bioText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Maintainer",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SupportCard(
    onCoffeeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onCoffeeClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = dev.auriya.app.ui.components.MaterialShapes.PixelCircle,
                color = Color(0xFFE8C39E),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocalCafe,
                        contentDescription = null,
                        tint = Color(0xFF3E2713),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Buy a Coffee ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Support development and show your appreciation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContributorsBlock(
    contributors: List<Contributor>,
    onContributorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleContributors = if (isExpanded) contributors else contributors.take(3)
    val showToggle = contributors.size > 3
    val totalCount = visibleContributors.size + (if (showToggle) 1 else 0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        visibleContributors.forEachIndexed { index, contributor ->
            ContributorCard(
                contributor = contributor,
                shape = itemShapeFor(index, totalCount),
                onCardClick = { onContributorClick(contributor.htmlUrl) }
            )
        }

        if (showToggle) {
            Surface(
                onClick = { isExpanded = !isExpanded },
                shape = itemShapeFor(visibleContributors.size, totalCount),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "Show Less" else "View All (${contributors.size}) Contributors",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(
    contributor: Contributor,
    shape: RoundedCornerShape,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onCardClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AvatarImage(
                url = contributor.avatarUrl,
                fallbackInitial = contributor.login.take(1).uppercase(),
                modifier = Modifier.size(44.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "@${contributor.login}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "${contributor.contributions} contrib.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LicenseAndSpecsCard(
    onLicenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onLicenseClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPL-3.0 License",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Free & Open Source Software • © 2026 Pavelc4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentationCard(
    onDocsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onDocsClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Documentation & Guides",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Architecture specs, FAS governor guide & settings manual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    fallbackInitial: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackInitial,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private suspend fun fetchJson(url: String): String? = withContext(Dispatchers.IO) {
    try {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Auriya-App")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 5000
            readTimeout = 5000
        }
        if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private object AboutCache {
    private const val PREFS_NAME = "auriya_about_cache"
    private const val KEY_OWNER_NAME = "owner_name"
    private const val KEY_OWNER_BIO = "owner_bio"
    private const val KEY_OWNER_AVATAR = "owner_avatar"
    private const val KEY_CONTRIBUTORS_JSON = "contributors_json"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun getCachedOwner(context: Context): OwnerInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_OWNER_NAME, "Pavel") ?: "Pavel"
        val bio = prefs.getString(KEY_OWNER_BIO, "Rust daemon, eBPF telemetry, and Android Kotlin UI")
            ?: "Rust daemon, eBPF telemetry, and Android Kotlin UI"
        val avatar = prefs.getString(KEY_OWNER_AVATAR, "https://github.com/$OWNER_LOGIN.png")
            ?: "https://github.com/$OWNER_LOGIN.png"
        return OwnerInfo(
            login = OWNER_LOGIN,
            name = name,
            bio = bio,
            htmlUrl = "https://github.com/$OWNER_LOGIN",
            avatarUrl = avatar
        )
    }

    fun saveOwner(context: Context, owner: OwnerInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_OWNER_NAME, owner.name)
            .putString(KEY_OWNER_BIO, owner.bio)
            .putString(KEY_OWNER_AVATAR, owner.avatarUrl)
            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
            .apply()
    }

    fun getCachedContributors(context: Context): List<Contributor> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONTRIBUTORS_JSON, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Contributor(
                    login = obj.getString("login"),
                    avatarUrl = obj.getString("avatar_url"),
                    htmlUrl = obj.getString("html_url"),
                    contributions = obj.getInt("contributions"),
                    role = "Community Contributor"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContributors(context: Context, list: List<Contributor>) {
        if (list.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray()
            for (c in list) {
                val obj = org.json.JSONObject().apply {
                    put("login", c.login)
                    put("avatar_url", c.avatarUrl)
                    put("html_url", c.htmlUrl)
                    put("contributions", c.contributions)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CONTRIBUTORS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shouldRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        return (System.currentTimeMillis() - lastFetch) > CACHE_TTL_MS
    }
}

suspend fun fetchRepoInfo(): RepoInfo {
    val body = fetchJson("https://api.github.com/repos/$OWNER_LOGIN/$REPO_NAME")
        ?: return RepoInfo(REPO_NAME, APP_TAGLINE, "2.0.0")
    return try {
        val obj = org.json.JSONObject(body)
        RepoInfo(
            name = obj.getString("name"),
            description = APP_TAGLINE,
            version = "2.0.0",
        )
    } catch (e: Exception) {
        e.printStackTrace()
        RepoInfo(REPO_NAME, APP_TAGLINE, "2.0.0")
    }
}

suspend fun fetchOwnerInfo(): OwnerInfo {
    val fallback = OwnerInfo(
        login = OWNER_LOGIN,
        name = "Pavel",
        bio = "Rust daemon, eBPF telemetry, and Android Kotlin UI",
        htmlUrl = "https://github.com/$OWNER_LOGIN",
        avatarUrl = "https://github.com/$OWNER_LOGIN.png"
    )
    val body = fetchJson("https://api.github.com/users/$OWNER_LOGIN") ?: return fallback
    return try {
        val obj = org.json.JSONObject(body)
        OwnerInfo(
            login = obj.getString("login"),
            name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Pavel",
            bio = obj.optString("bio").takeIf { it.isNotBlank() } ?: fallback.bio,
            htmlUrl = obj.optString("html_url", "https://github.com/$OWNER_LOGIN"),
            avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() } ?: fallback.avatarUrl,
        )
    } catch (e: Exception) {
        e.printStackTrace()
        fallback
    }
}

suspend fun fetchContributors(): List<Contributor> {
    val body = fetchJson("https://api.github.com/repos/$OWNER_LOGIN/$REPO_NAME/contributors")
        ?: return emptyList()
    return try {
        val jsonArray = org.json.JSONArray(body)
        List(jsonArray.length()) { i ->
            val obj = jsonArray.getJSONObject(i)
            Contributor(
                login = obj.getString("login"),
                avatarUrl = obj.getString("avatar_url"),
                htmlUrl = obj.getString("html_url"),
                contributions = obj.getInt("contributions"),
                role = "Community Contributor"
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
