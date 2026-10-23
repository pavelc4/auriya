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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalCafe
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.auriya.app.R
import dev.auriya.app.data.repository.Contributor
import dev.auriya.app.data.repository.OwnerInfo
import dev.auriya.app.data.repository.RepoInfo
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
import dev.auriya.app.ui.components.itemShapeFor
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.viewmodel.AboutViewModel

private const val OWNER_LOGIN = "Pavelc4"
private const val REPO_NAME = "Auriya"
private const val APP_TAGLINE = "Technical documentation for Auriya and personal optimization experiments."

@Composable
fun AboutScreen(
    onDismiss: () -> Unit,
    viewModel: AboutViewModel =
        androidx.lifecycle.viewmodel.compose
            .viewModel(),
) {
    val context = LocalContext.current
    val repoInfo by viewModel.repoInfo.collectAsStateWithLifecycle()
    val ownerInfo by viewModel.ownerInfo.collectAsStateWithLifecycle()
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val isLoadingInfo by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingOwner = isLoadingInfo
    val isLoadingContributors = isLoadingInfo

    // Filter out repo owner and automated bot accounts
    val filteredContributors =
        remember(contributors) {
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        // --- 1. TOP PINNED HEADER AREA ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onDismiss,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.about_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        androidx.compose.ui.res
                            .stringResource(dev.auriya.app.R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            ) {
                // 1. Clean App Hero Card
                item(key = "hero_card") {
                    AuriyaHeroCard(
                        repoInfo = repoInfo,
                        isLoading = isLoadingInfo,
                    )
                }

                // 2. Licenses Section (Moved below Auriya Card, above Maintainer)
                item(key = "license_header") {
                    SectionHeader(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_sec_license),
                        subtitle =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_license_sub),
                    )
                }

                item(key = "license_card") {
                    LicenseAndSpecsCard(
                        onLicenseClick = {
                            openUrl(context, "https://github.com/$OWNER_LOGIN/$REPO_NAME/blob/main/LICENSE")
                        },
                    )
                }

                // 3. Documentation Section
                item(key = "docs_header") {
                    SectionHeader(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_documentation),
                        subtitle =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_doc_sub),
                    )
                }

                item(key = "docs_card") {
                    DocumentationCard(
                        onDocsClick = {
                            openUrl(context, "https://auriya.pages.dev/")
                        },
                    )
                }

                // 4. Maintainer Section
                item(key = "maintainer_header") {
                    SectionHeader(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_maintainer),
                        subtitle =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_maintainer_sub),
                    )
                }

                item(key = "maintainer_card") {
                    MaintainerCard(
                        owner = ownerInfo,
                        isLoading = isLoadingOwner,
                        onCardClick = { openUrl(context, ownerInfo.htmlUrl) },
                    )
                }

                // 5. Support Section (Directly under Maintainer)
                item(key = "support_header") {
                    SectionHeader(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_support),
                        subtitle =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_support_sub),
                    )
                }

                item(key = "support_card") {
                    SupportCard(
                        onCoffeeClick = {
                            openUrl(context, "https://github.com/sponsors/$OWNER_LOGIN")
                        },
                    )
                }

                // 6. Community Contributors Section
                item(key = "spotlight_header") {
                    SectionHeader(
                        title =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_sec_contributors),
                        subtitle =
                            androidx.compose.ui.res
                                .stringResource(dev.auriya.app.R.string.about_contributors_sub),
                    )
                }

                if (isLoadingContributors) {
                    item(key = "contributors_loading") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AuriyaLoadingIndicator(size = 20.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Fetching contributors...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (filteredContributors.isEmpty()) {
                    item(key = "contributors_empty") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "No other contributors found yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    item(key = "contributors_list") {
                        ContributorsBlock(
                            contributors = filteredContributors,
                            onContributorClick = { url -> openUrl(context, url) },
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
    modifier: Modifier = Modifier,
) {
    val cleanVersion =
        remember(repoInfo) {
            val raw = repoInfo?.version ?: "2.0.0"
            raw.trimStart('v')
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top Row: App Icon + App Name & Tagline
            val context = LocalContext.current
            val appIconBitmap =
                remember(context) {
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(54.dp),
                ) {
                    Image(
                        painter = painterResource(dev.auriya.app.R.drawable.hiyuki),
                        contentDescription = "Auriya",
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Auriya",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = APP_TAGLINE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Version Capsule Pill (Prominent left-aligned, compact)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = if (isLoading || repoInfo == null) "Version v2.0.0" else "Version v$cleanVersion",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Compact Technology Capsule Pills (Sleek FlowRow)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.height(28.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MaintainerCard(
    owner: OwnerInfo?,
    isLoading: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AvatarImage(
                url = owner?.avatarUrl,
                fallbackInitial = (owner?.login ?: OWNER_LOGIN).take(1).uppercase(),
                modifier = Modifier.size(48.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (isLoading) "Loading..." else (owner?.name ?: OWNER_LOGIN),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val bioText =
                    owner?.bio?.takeIf { it.isNotBlank() }
                        ?: "Rust daemon, eBPF telemetry, and Android Kotlin UI"

                Text(
                    text = bioText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "Maintainer",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SupportCard(
    onCoffeeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onCoffeeClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = dev.auriya.app.ui.components.MaterialShapes.PixelCircle,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocalCafe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Buy a Coffee ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Support development and show your appreciation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContributorsBlock(
    contributors: List<Contributor>,
    onContributorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val visibleContributors = if (isExpanded) contributors else contributors.take(3)
    val showToggle = contributors.size > 3
    val totalCount = visibleContributors.size + (if (showToggle) 1 else 0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visibleContributors.forEachIndexed { index, contributor ->
            ContributorCard(
                contributor = contributor,
                shape = itemShapeFor(index, totalCount),
                onCardClick = { onContributorClick(contributor.htmlUrl) },
            )
        }

        if (showToggle) {
            Surface(
                onClick = { isExpanded = !isExpanded },
                shape = itemShapeFor(visibleContributors.size, totalCount),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "Show Less" else "View All (${contributors.size}) Contributors",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onCardClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AvatarImage(
                url = contributor.avatarUrl,
                fallbackInitial = contributor.login.take(1).uppercase(),
                modifier = Modifier.size(44.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "@${contributor.login}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${contributor.contributions} contrib.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LicenseAndSpecsCard(
    onLicenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onLicenseClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPL-3.0 License",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Free & Open Source Software • © 2026 Pavelc4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentationCard(
    onDocsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onDocsClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auriya Wiki",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Technical documentation for Auriya and personal optimization experiments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    fallbackInitial: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
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

private fun openUrl(
    context: Context,
    url: String,
) {
    runCatching {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
