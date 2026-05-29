package dev.auriya.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.auriya.app.ui.theme.AuriyaTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OWNER_LOGIN = "Pavelc4"
private const val REPO_NAME = "Auriya"

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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var repoInfo by remember { mutableStateOf<RepoInfo?>(null) }
    var ownerInfo by remember { mutableStateOf<OwnerInfo?>(null) }
    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    var isLoadingInfo by remember { mutableStateOf(true) }
    var isLoadingOwner by remember { mutableStateOf(true) }
    var isLoadingContributors by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val info = fetchRepoInfo()
            withContext(Dispatchers.Main) {
                repoInfo = info
                isLoadingInfo = false
            }
        }
        coroutineScope.launch(Dispatchers.IO) {
            val owner = fetchOwnerInfo()
            withContext(Dispatchers.Main) {
                ownerInfo = owner
                isLoadingOwner = false
            }
        }
        coroutineScope.launch(Dispatchers.IO) {
            val list = fetchContributors()
            withContext(Dispatchers.Main) {
                contributors = list
                isLoadingContributors = false
            }
        }
    }

    val badgeTexts = listOf("Free", "Open Source", "Rust", "Kotlin", "eBPF", "M3 Expressive")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AuriyaTokens.padding.normal),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
            contentPadding = PaddingValues(top = AuriyaTokens.padding.normal, bottom = AuriyaTokens.padding.largest)
        ) {
            item { HeroCard(repoInfo, isLoadingInfo, badgeTexts) }

            item {
                OwnerCard(
                    owner = ownerInfo,
                    isLoading = isLoadingOwner,
                    onClick = {
                        val url = ownerInfo?.htmlUrl ?: "https://github.com/$OWNER_LOGIN"
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    }
                )
            }

            item {
                ContributorsCard(
                    contributors = contributors,
                    isLoading = isLoadingContributors,
                    onContributorClick = { url ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    repoInfo: RepoInfo?,
    isLoading: Boolean,
    badgeTexts: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLargeIncreased),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(AuriyaTokens.rounding.large))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuriyaTokens.iconSize.small),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = repoInfo?.name ?: "Auriya",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = repoInfo?.description ?: "eBPF-based game optimizer for Android",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AuriyaTokens.rounding.large))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = AuriyaTokens.padding.small, vertical = AuriyaTokens.padding.smallest)
            ) {
                Text(
                    text = repoInfo?.version ?: "v2.0.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                badgeTexts.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuriyaTokens.rounding.large))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .padding(horizontal = AuriyaTokens.padding.small, vertical = 6.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerCard(
    owner: OwnerInfo?,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)
        ) {
            Text(
                text = "PROJECT OWNER",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarImage(
                            url = owner?.avatarUrl,
                            fallbackInitial = (owner?.login ?: OWNER_LOGIN).take(1).uppercase(),
                            fallbackContent = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (isLoading) {
                            Text(
                                text = "Loading owner...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = owner?.name ?: OWNER_LOGIN,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = owner?.bio?.takeIf { it.isNotBlank() }
                                    ?: "@${owner?.login ?: OWNER_LOGIN} on GitHub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorsCard(
    contributors: List<Contributor>,
    isLoading: Boolean,
    onContributorClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)
        ) {
            Text(
                text = "CONTRIBUTORS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            when {
                isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
                    modifier = Modifier.padding(vertical = AuriyaTokens.padding.smaller)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AuriyaTokens.iconSize.small),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fetching contributors...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                contributors.isEmpty() -> Text(
                    text = "No contributors found or rate limited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                else -> Column(
                    verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller)
                ) {
                    contributors.forEach { contributor ->
                        ContributorRow(
                            contributor = contributor,
                            onClick = { onContributorClick(contributor.htmlUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(contributor: Contributor, onClick: () -> Unit) {    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AuriyaTokens.rounding.full),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AvatarImage(
                    url = contributor.avatarUrl,
                    fallbackInitial = contributor.login.take(1).uppercase(),
                    fallbackContent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contributor.login,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${contributor.contributions} contributions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
                modifier = Modifier.size(AuriyaTokens.iconSize.small)
            )
        }
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    fallbackInitial: String,
    fallbackContent: androidx.compose.ui.graphics.Color,
) {
    if (url.isNullOrBlank()) {
        Text(
            text = fallbackInitial,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = fallbackContent,
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

private suspend fun fetchJson(url: String): String? = withContext(Dispatchers.IO) {    try {
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

suspend fun fetchRepoInfo(): RepoInfo {
    val body = fetchJson("https://api.github.com/repos/$OWNER_LOGIN/$REPO_NAME")
        ?: return RepoInfo(REPO_NAME, "eBPF-based game optimizer for Android", "v2.0.0")
    return try {
        val obj = org.json.JSONObject(body)
        RepoInfo(
            name = obj.getString("name"),
            description = obj.optString("description", "eBPF-based game optimizer for Android"),
            version = "v2.0.0",
        )
    } catch (e: Exception) {
        e.printStackTrace()
        RepoInfo(REPO_NAME, "eBPF-based game optimizer for Android", "v2.0.0")
    }
}

suspend fun fetchOwnerInfo(): OwnerInfo {
    val fallback = OwnerInfo(OWNER_LOGIN, null, null, "https://github.com/$OWNER_LOGIN", null)
    val body = fetchJson("https://api.github.com/users/$OWNER_LOGIN") ?: return fallback
    return try {
        val obj = org.json.JSONObject(body)
        OwnerInfo(
            login = obj.getString("login"),
            name = obj.optString("name").takeIf { it.isNotBlank() },
            bio = obj.optString("bio").takeIf { it.isNotBlank() },
            htmlUrl = obj.optString("html_url", "https://github.com/$OWNER_LOGIN"),
            avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() },
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
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
