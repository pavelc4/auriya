package dev.auriya.app.ui.oobe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun OverlayContent(
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasOverlayPermission =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.canDrawOverlays(context)
                        } else {
                            true
                        }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = "Overlay Permission",
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontFamily = GoogleSansRounded,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Grant permission to display real-time telemetry HUD for FPS, CPU, GPU, RAM, and thermals on top of running games.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Center Animated Telemetry Preview Mockup
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            TelemetryOverlayPreview()
        }

        // Bottom Permission Status & Action Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Status Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color =
                    if (hasOverlayPermission) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasOverlayPermission) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (hasOverlayPermission) Icons.Rounded.CheckCircle else Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasOverlayPermission) "Overlay Permission Granted" else "Authorization Required",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        Text(
                            text = if (hasOverlayPermission) "Overlay is ready to display over games" else "Enable 'Display over other apps' in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = {
                    if (!hasOverlayPermission) {
                        val intent =
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                },
                enabled = !hasOverlayPermission,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (hasOverlayPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (hasOverlayPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AnimatedContent(targetState = hasOverlayPermission, label = "OverlayButtonAnim") { isGranted ->
                    if (isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permission Granted",
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                    } else {
                        Text(
                            text = "Grant Overlay Permission",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private enum class OverlayLayoutPreviewMode(
    val isVertical: Boolean,
    val isDetailed: Boolean,
) {
    HORIZONTAL_DETAILED(isVertical = false, isDetailed = true),
    VERTICAL_LEFT(isVertical = true, isDetailed = false),
    HORIZONTAL_MINIMAL(isVertical = false, isDetailed = false),
    VERTICAL_RIGHT(isVertical = true, isDetailed = true),
}

@Composable
private fun TelemetryOverlayPreview() {
    var modeIndex by remember { mutableIntStateOf(0) }
    val modes = remember { OverlayLayoutPreviewMode.entries }
    val currentMode = modes[modeIndex % modes.size]

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2800)
            modeIndex++
        }
    }

    val animHorizontalBias by animateFloatAsState(
        targetValue =
            when (currentMode) {
                OverlayLayoutPreviewMode.HORIZONTAL_DETAILED -> 0f
                OverlayLayoutPreviewMode.VERTICAL_LEFT -> -0.85f
                OverlayLayoutPreviewMode.HORIZONTAL_MINIMAL -> 0f
                OverlayLayoutPreviewMode.VERTICAL_RIGHT -> 0.85f
            },
        animationSpec =
            spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "alignXBias",
    )

    val animVerticalBias by animateFloatAsState(
        targetValue =
            when (currentMode) {
                OverlayLayoutPreviewMode.HORIZONTAL_DETAILED -> -0.75f
                OverlayLayoutPreviewMode.VERTICAL_LEFT -> 0f
                OverlayLayoutPreviewMode.HORIZONTAL_MINIMAL -> 0.75f
                OverlayLayoutPreviewMode.VERTICAL_RIGHT -> 0f
            },
        animationSpec =
            spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "alignYBias",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            // Mock Screen Content (3 tall cards spanning top to bottom)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Mock App Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp, 8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    )
                }

                // Mock Content Card 1
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.45f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }

                // Mock Content Card 2
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.35f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }

                // Mock Content Card 3
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }
            }

            // Dynamic Floating Overlay with spring-gliding smooth position & shape morphing
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    modifier =
                        Modifier
                            .align(androidx.compose.ui.BiasAlignment(animHorizontalBias, animVerticalBias))
                            .animateContentSize(
                                animationSpec =
                                    spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ).clip(RoundedCornerShape(18.dp)),
                ) {
                    AnimatedContent(
                        targetState = currentMode,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(280, easing = LinearOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(280))
                            ).togetherWith(fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)))
                        },
                        label = "OverlayContentMorph",
                    ) { mode ->
                        if (!mode.isVertical) {
                            // Horizontal Layout
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "120 FPS",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                if (mode.isDetailed) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(1.dp, 14.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    )
                                    Text(
                                        text = "GPU 580M",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(1.dp, 14.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    )
                                    Text(
                                        text = "RAM 6.0G",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .size(1.dp, 14.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                )

                                Text(
                                    text = "51°C",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            // Vertical Layout
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "120 FPS",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                )

                                Box(
                                    modifier =
                                        Modifier
                                            .width(36.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                )

                                if (mode.isDetailed) {
                                    Text(
                                        text = "GPU 580M",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "RAM 6.0G",
                                        style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                Text(
                                    text = "51°C",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
