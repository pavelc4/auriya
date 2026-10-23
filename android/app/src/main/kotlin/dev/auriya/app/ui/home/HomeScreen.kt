package dev.auriya.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.ui.components.AuriyaPullToRefreshIndicator
import dev.auriya.app.ui.components.ProfilePickerBottomSheet
import dev.auriya.app.ui.home.components.*
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.viewmodel.UiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: UiViewModel,
    onNavigateToGames: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
    val gameList by viewModel.gameList.collectAsStateWithLifecycle()
    val isDaemonRunning = systemInfo.pid != null && systemInfo.pid != "null"
    val uriHandler = LocalUriHandler.current
    var showProfileSheet by remember { mutableStateOf(false) }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    var showInfoSheet by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showProfileSheet) {
        ProfilePickerBottomSheet(
            selectedProfile = systemInfo.profile,
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
            sheetState = infoSheetState,
        )
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
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Auriya",
                style =
                    ExpTitleTypography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            )

            FilledIconButton(
                onClick = onNavigateToSettings,
                shape = CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
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
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refresh { isRefreshing = false }
                },
                state = refreshState,
                indicator = {
                    AuriyaPullToRefreshIndicator(
                        state = refreshState,
                        isRefreshing = isRefreshing,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val pullProgress = refreshState.distanceFraction
                val density = androidx.compose.ui.platform.LocalDensity.current
                val contentTranslationY by androidx.compose.animation.core.animateFloatAsState(
                    targetValue =
                        when {
                            isRefreshing -> with(density) { 72.dp.toPx() }
                            pullProgress > 0f -> with(density) { (80.dp * pullProgress).toPx().coerceIn(0f, 96.dp.toPx()) }
                            else -> 0f
                        },
                    animationSpec =
                        androidx.compose.animation.core
                            .spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "homeContentTranslationY",
                )

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = contentTranslationY }
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                ) {
                    item {
                        HomeHeroCard(
                            isDaemonRunning = isDaemonRunning,
                            systemInfo = systemInfo,
                            onInfoClick = { showInfoSheet = true },
                        )
                    }
                    item {
                        MiniMetricRow(
                            profile = systemInfo.profile,
                            gameCount = gameList.games.size,
                            onGamesClick = onNavigateToGames,
                            onProfileClick = { showProfileSheet = true },
                        )
                    }
                    item { SystemMetricsList(systemInfo = systemInfo) }
                    item {
                        HomeLinkRow(
                            iconVector = Icons.AutoMirrored.Outlined.MenuBook,
                            title = stringResource(R.string.home_documentation),
                            subtitle = stringResource(R.string.home_doc_desc),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                uriHandler.openUri("https://auriya.pages.dev")
                            },
                        )
                    }
                    item {
                        HomeLinkRow(
                            iconPainter = painterResource(R.drawable.ic_github),
                            title = stringResource(R.string.home_github_star),
                            subtitle = stringResource(R.string.home_github_star_desc),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                uriHandler.openUri("https://github.com/Pavelc4/Auriya")
                            },
                        )
                    }
                    item {
                        HomeLinkRow(
                            iconPainter = painterResource(R.drawable.ic_telegram),
                            title = stringResource(R.string.home_telegram_channel),
                            subtitle = stringResource(R.string.home_telegram_channel_desc),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                uriHandler.openUri("https://t.me/pvlcply")
                            },
                        )
                    }
                }
            }
        }
    }
}
