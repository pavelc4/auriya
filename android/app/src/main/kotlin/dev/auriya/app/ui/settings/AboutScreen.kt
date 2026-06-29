package dev.auriya.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.components.AuriyaLoadingIndicator
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

    // Filter out pavelc4 from the dynamic contributors list, per your request
    val filteredContributors = remember(contributors) {
        contributors.filter { it.login.lowercase() != OWNER_LOGIN.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            contentPadding = PaddingValues(top = AuriyaTokens.padding.normal, bottom = 80.dp)
        ) {
            // 1. Support Card
            item {
                SupportCard(
                    onCoffeeClick = {
                        val url = "https://github.com/sponsors/$OWNER_LOGIN"
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

            // 2. Contributors Card (Creator pavelc4 + Dynamic Expandable Contributor list)
            item {
                ContributorsCard(
                    owner = ownerInfo,
                    isLoadingOwner = isLoadingOwner,
                    contributors = filteredContributors,
                    isLoadingContributors = isLoadingContributors,
                    onCreatorClick = {
                        val url = ownerInfo?.htmlUrl ?: "https://github.com/$OWNER_LOGIN"
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    },
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

            // 3. Community & License Double Cards
            item {
                CommunityAndLicenseRow(
                    onTelegramClick = {
                        val url = "https://t.me/auriya_chat"
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    },
                    onLicenseClick = {
                        val url = "https://github.com/$OWNER_LOGIN/$REPO_NAME/blob/main/LICENSE"
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
private fun SupportCard(
    onCoffeeClick: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
        ) {
            Text(
                text = "Support",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "If Auriya saves your time when updating or tuning, consider buying me a coffee to keep the development going.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )

            Button(
                onClick = onCoffeeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(AuriyaTokens.rounding.large),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8C39E), // warm light peach/sand tone
                    contentColor = Color(0xFF2E1C0C) // dark wood/brown tone
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalCafe,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Buy a Coffee",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorsCard(
    owner: OwnerInfo?,
    isLoadingOwner: Boolean,
    contributors: List<Contributor>,
    isLoadingContributors: Boolean,
    onCreatorClick: () -> Unit,
    onContributorClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(AuriyaTokens.padding.larger),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.People,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Contributors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Creator Sub-Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreatorClick),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuriyaTokens.padding.normal, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                        if (isLoadingOwner) {
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
                                text = "Creator & Lead Developer",
                                style = MaterialTheme.typography.bodySmall,
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

            // Expandable link to view all other contributors
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.AutoMirrored.Filled.ArrowForward,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "Hide Contributors" else "View All Contributors",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AuriyaTokens.padding.small),
                        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller)
                    ) {
                        if (isLoadingContributors) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AuriyaLoadingIndicator(
                                    size = 20.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Fetching contributors...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (contributors.isEmpty()) {
                            Text(
                                text = "No other contributors found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
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
    }
}

@Composable
private fun ContributorRow(contributor: Contributor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AuriyaTokens.rounding.full),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = 8.dp),
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
private fun CommunityAndLicenseRow(
    onTelegramClick: () -> Unit,
    onLicenseClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
    ) {
        // Community Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(AuriyaTokens.padding.normal),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFECE4D8)), // warm soft accent background
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        tint = Color(0xFF6B4E2B),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Community",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Join the discussion about Auriya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2
                )

                Button(
                    onClick = onTelegramClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(AuriyaTokens.rounding.large),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5A442E), // premium dark warm tone
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Telegram Group",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // License Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(AuriyaTokens.padding.normal),
                verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "License",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Open Source Software.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2
                )

                Button(
                    onClick = onLicenseClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(AuriyaTokens.rounding.large),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "2026 pavelc4",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarImage(
    url: String?,
    fallbackInitial: String,
    fallbackContent: Color,
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
