package dev.auriya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import dev.auriya.app.ui.theme.AuriyaFontFamily

@Composable
fun ExpressiveTopBarContent(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedTitleStartPadding: Dp = 56.dp,
    expandedTitleStartPadding: Dp = 20.dp,
    collapsedTitleEndPadding: Dp = 24.dp,
    expandedTitleEndPadding: Dp = 24.dp,
    containerHeightRange: Pair<Dp, Dp> = 88.dp to 56.dp,
    collapsedTitleVerticalBias: Float = -1f,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    titleScaleRange: Pair<Float, Float> = 1.15f to 0.85f,
    titleFontSizeRange: Pair<TextUnit, TextUnit>? = null,
    maxLines: Int = 1,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fadeSubtitleOnCollapse: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val clampedFraction = collapseFraction.coerceIn(0f, 1f)
    val titleScale = lerp(titleScaleRange.first, titleScaleRange.second, clampedFraction)
    val titlePaddingStart = lerp(expandedTitleStartPadding, collapsedTitleStartPadding, clampedFraction)
    val titlePaddingEnd = lerp(expandedTitleEndPadding, collapsedTitleEndPadding, clampedFraction)
    val titleVerticalBias = lerp(1f, collapsedTitleVerticalBias, clampedFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val subtitleAlpha = if (fadeSubtitleOnCollapse) 1f - clampedFraction else 1f
    val titleFontSize = (titleFontSizeRange?.let { lerp(it.first, it.second, clampedFraction) } ?: titleStyle.fontSize) * titleScale

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = titlePaddingStart, end = titlePaddingEnd),
            contentAlignment = animatedTitleAlignment
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = titleStyle.copy(
                        fontFamily = AuriyaFontFamily,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    ),
                    maxLines = maxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank() && subtitleAlpha > 0.05f) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = subtitleColor.copy(alpha = subtitleAlpha)
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        supportingContent?.invoke()
    }
}

@Composable
fun CollapsibleCommonTopBar(
    title: String,
    collapseFraction: Float,
    headerHeight: Dp,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedTitleStartPadding: Dp = 68.dp,
    expandedTitleStartPadding: Dp = 20.dp,
    collapsedTitleEndPadding: Dp = 24.dp,
    expandedTitleEndPadding: Dp = 24.dp,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val backgroundColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(backgroundColor)
            .zIndex(5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ExpressiveTopBarContent(
                title = title,
                collapseFraction = collapseFraction,
                modifier = Modifier.fillMaxSize(),
                subtitle = subtitle,
                collapsedTitleStartPadding = collapsedTitleStartPadding,
                expandedTitleStartPadding = expandedTitleStartPadding,
                collapsedTitleEndPadding = collapsedTitleEndPadding,
                expandedTitleEndPadding = expandedTitleEndPadding,
                titleStyle = titleStyle,
                contentColor = contentColor,
                subtitleColor = subtitleColor
            )

            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp)
                    .zIndex(1f),
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 12.dp)
                    .zIndex(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
