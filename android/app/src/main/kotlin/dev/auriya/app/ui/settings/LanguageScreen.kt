package dev.auriya.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.R
import dev.auriya.app.ui.components.ExpressiveSelectableCard
import dev.auriya.app.ui.components.animatedItemShapeFor
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.util.LocaleHelper
import kotlinx.coroutines.delay

@Composable
fun LanguageScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage by LocaleHelper.currentLanguage.collectAsStateWithLifecycle()
    val languageOptions = remember(context, currentLanguage) { LocaleHelper.getSupportedLanguages(context) }
    var showAppliedBanner by remember { mutableStateOf(false) }

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

                @Suppress("UnusedContentLambdaTargetStateParameter")
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                ) {
                    itemsIndexed(languageOptions, key = { _, lang -> lang.tag }) { index, lang ->
                        val isSelected = currentLanguage == lang.tag
                        val shape = animatedItemShapeFor(index, languageOptions.size, isSelected = isSelected)

                        ExpressiveSelectableCard(
                            selected = isSelected,
                            onClick = {
                                if (currentLanguage != lang.tag) {
                                    LocaleHelper.setLocale(context, lang.tag)
                                    showAppliedBanner = true
                                }
                            },
                            title = lang.nativeName,
                            subtitle = lang.localizedName,
                            shape = shape,
                        )
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
