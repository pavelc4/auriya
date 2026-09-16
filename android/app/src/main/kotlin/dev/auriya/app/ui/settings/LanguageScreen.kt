package dev.auriya.app.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.R
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.util.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LanguageScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage by LocaleHelper.currentLanguage.collectAsState()
    val languageOptions = remember(context, currentLanguage) { LocaleHelper.getSupportedLanguages(context) }
    val coroutineScope = rememberCoroutineScope()
    var showAppliedBanner by remember { mutableStateOf(false) }

    // Floating / Breathing Animation for Header Icon
    val infiniteTransition = rememberInfiniteTransition(label = "HeaderGlobePulse")
    val globeOffset by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "GlobeOffsetY",
    )
    val globeScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "GlobeScale",
    )

    // Hide banner after 2.4s
    LaunchedEffect(showAppliedBanner) {
        if (showAppliedBanner) {
            delay(2400)
            showAppliedBanner = false
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // --- 1. TOP PINNED HEADER AREA ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
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
                        contentDescription = stringResource(R.string.setup_btn_back),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                AnimatedContent(
                    targetState = currentLanguage,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = EaseOutCubic)) + slideInVertically(initialOffsetY = { -it / 3 }))
                            .togetherWith(fadeOut(tween(180, easing = EaseInCubic)) + slideOutVertically(targetOffsetY = { it / 3 }))
                    },
                    label = "LanguageHeaderTransition",
                    modifier = Modifier.weight(1f),
                ) { _ ->
                    Column {
                        Text(
                            text = stringResource(R.string.language_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.language_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Decorative animated translation emblem
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier =
                        Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                translationY = globeOffset
                                scaleX = globeScale
                                scaleY = globeScale
                            },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
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
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 80.dp),
                ) {
                    itemsIndexed(languageOptions) { index, lang ->
                        var isItemVisible by remember { mutableStateOf(false) }

                        // Staggered cascade entrance
                        LaunchedEffect(Unit) {
                            delay(index * 70L)
                            isItemVisible = true
                        }

                        AnimatedVisibility(
                            visible = isItemVisible,
                            enter =
                                slideInVertically(
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    initialOffsetY = { it / 2 },
                                ) + fadeIn(animationSpec = tween(320, easing = EaseOutCubic)),
                        ) {
                            val isSelected = currentLanguage == lang.tag
                            val itemScale = remember { Animatable(1f) }

                            val cardBg by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "CardBg_$index",
                            )

                            val cardBorderColor by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    } else {
                                        Color.Transparent
                                    },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "CardBorder_$index",
                            )

                            val cardRadius by animateDpAsState(
                                targetValue = if (isSelected) 26.dp else 20.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "CardRadius_$index",
                            )

                            val iconContainerColor by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "IconContainer_$index",
                            )

                            val iconTint by animateColorAsState(
                                targetValue =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "IconTint_$index",
                            )

                            val leadIcon =
                                when (lang.tag.lowercase()) {
                                    "system" -> Icons.Outlined.Devices
                                    "en" -> Icons.Outlined.Translate
                                    "id", "in" -> Icons.Outlined.Language
                                    else -> Icons.Outlined.Public
                                }

                            Surface(
                                onClick = {
                                    if (currentLanguage != lang.tag) {
                                        coroutineScope.launch {
                                            itemScale.animateTo(0.93f, tween(60))
                                            itemScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                        LocaleHelper.setLocale(context, lang.tag)
                                        showAppliedBanner = true
                                    }
                                },
                                shape = RoundedCornerShape(cardRadius),
                                color = cardBg,
                                border = BorderStroke(1.5.dp, cardBorderColor),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .scale(itemScale.value),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // Leading Emblem Avatar
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = iconContainerColor,
                                        modifier = Modifier.size(46.dp),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            Icon(
                                                imageVector = leadIcon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = lang.nativeName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                color =
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                            )

                                            // Animated Active Pill Badge
                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                                exit = scaleOut() + fadeOut(),
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.language_active_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }

                                        if (lang.localizedName.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = lang.localizedName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }

                                    // Animated Checkmark Badge
                                    AnimatedVisibility(
                                        visible = isSelected,
                                        enter =
                                            scaleIn(
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                            ) + fadeIn(),
                                        exit = scaleOut() + fadeOut(),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. FLOATING APPLIED TOAST / NOTIFICATION BANNER ---
        AnimatedVisibility(
            visible = showAppliedBanner,
            enter =
                slideInVertically(
                    initialOffsetY = { it * 2 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                ) + fadeIn(tween(220)),
            exit =
                slideOutVertically(
                    targetOffsetY = { it * 2 },
                    animationSpec = tween(220, easing = EaseInCubic),
                ) + fadeOut(tween(160)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.language_applied),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}
