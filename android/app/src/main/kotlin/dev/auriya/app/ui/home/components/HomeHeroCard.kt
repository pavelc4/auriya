package dev.auriya.app.ui.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.R
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.theme.ExpTitleTypography
import dev.auriya.app.viewmodel.SystemInfo

@Composable
fun HomeHeroCard(
    isDaemonRunning: Boolean,
    systemInfo: SystemInfo,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBg by animateColorAsState(
        targetValue = if (isDaemonRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HeroCardBg",
    )
    val onCardBg by animateColorAsState(
        targetValue = if (isDaemonRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HeroCardOnBg",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    AnimatedContent(
                        targetState = isDaemonRunning,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it / 3 }))
                                .togetherWith(fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 3 }))
                        },
                        label = "HeroStatusText",
                    ) { running ->
                        Text(
                            text =
                                if (running) {
                                    stringResource(R.string.home_status_active)
                                } else {
                                    stringResource(R.string.home_status_inactive)
                                },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = onCardBg,
                        )
                    }

                    val cleanVersion = systemInfo.version.removePrefix("v").removePrefix("V")
                    Text(
                        text = "v$cleanVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCardBg.copy(alpha = 0.8f),
                    )
                }

                FilledIconButton(
                    onClick = onInfoClick,
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = onCardBg.copy(alpha = 0.14f),
                            contentColor = onCardBg,
                        ),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Version & App Info",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isDaemonRunning) {
                    StatusBadge(
                        label = "PID ${systemInfo.pid}",
                        containerColor = onCardBg.copy(alpha = 0.16f),
                        contentColor = onCardBg,
                    )
                } else {
                    StatusBadge(
                        label = stringResource(R.string.home_stopped),
                        containerColor = onCardBg.copy(alpha = 0.16f),
                        contentColor = onCardBg,
                    )
                }

                if (systemInfo.codename.isNotEmpty() && systemInfo.codename != "...") {
                    StatusBadge(
                        label = systemInfo.codename,
                        containerColor = onCardBg.copy(alpha = 0.16f),
                        contentColor = onCardBg,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuriyaInfoBottomSheet(
    systemInfo: SystemInfo,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    val uriHandler = LocalUriHandler.current
    val cleanVersion = systemInfo.version.removePrefix("v").removePrefix("V")
    val cleanArch =
        when (val arch = systemInfo.deviceArch.uppercase()) {
            "V8A" -> "ARM64-V8A"
            "V7A" -> "ARM-V7A"
            else -> arch
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        ) {
            item {
                Text(
                    text = "Auriya $cleanVersion",
                    style =
                        ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )
            }

            // Card 1: Build Info
            item {
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
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "α",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auriya Daemon $cleanVersion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Running daemon build (${systemInfo.commit}) optimized for $cleanArch architecture. eBPF kernel tracing engine active.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Card 2: Documentation Card
            item {
                Surface(
                    onClick = {
                        uriHandler.openUri("https://auriya.pages.dev")
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
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
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_documentation),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_doc_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Card 3: GitHub issue shortcut
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_github_star),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_github_star_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/Pavelc4/Auriya/issues")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            Text(
                                stringResource(R.string.home_open_issues),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Button(
                            onClick = {
                                uriHandler.openUri("https://github.com/Pavelc4/Auriya/issues/new")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            Text(
                                stringResource(R.string.home_report_issue),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
