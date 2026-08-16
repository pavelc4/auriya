package dev.auriya.app.ui.oobe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.components.MaterialShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SineWaveLine drawing an animated sine wave line.
 */
@Composable
fun SineWaveLine(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 1f,
    strokeWidth: Dp = 2.dp,
    amplitude: Dp = 8.dp,
    waves: Float = 2f,
    phase: Float = 0f,
    animate: Boolean? = false,
    animationDurationMillis: Int = 2000,
    samples: Int = 400,
    cap: StrokeCap = StrokeCap.Round
) {
    val density = LocalDensity.current

    val currentPhase = if (animate == true) {
        val infiniteTransition = rememberInfiniteTransition(label = "SineWaveAnimation")
        val animatedPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = animationDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phaseAnimation"
        )
        animatedPhase
    } else {
        phase
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val strokePx = with(density) { strokeWidth.toPx() }
        val ampPx = with(density) { amplitude.toPx() }

        if (w <= 0f || samples < 2) return@Canvas

        val path = Path().apply {
            val step = w / (samples - 1)
            moveTo(0f, centerY + (ampPx * sin(currentPhase)))
            for (i in 1 until samples) {
                val x = i * step
                val theta = (x / w) * (2f * PI.toFloat() * waves) + currentPhase
                val y = centerY + ampPx * sin(theta)
                lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = cap,
                join = StrokeJoin.Round
            ),
            alpha = alpha
        )
    }
}


// Custom Dynamic Palette Items (Screenshot 2)
data class PaletteItem(
    val seed: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color
)

val PALETTE_ITEMS = listOf(
    // Greenish theme (Monet template 1)
    PaletteItem(
        seed = 0xFFA7E0A2.toInt(),
        primary = Color(0xFF388E3C),
        secondary = Color(0xFF81C784),
        tertiary = Color(0xFFC8E6C9),
        neutral = Color(0xFFE8F5E9)
    ),
    // Blueish/gray theme (Monet template 2)
    PaletteItem(
        seed = 0xFF9ECAFF.toInt(),
        primary = Color(0xFF1976D2),
        secondary = Color(0xFF64B5F6),
        tertiary = Color(0xFFBBDEFB),
        neutral = Color(0xFFE3F2FD)
    ),
    // Purple theme (Monet template 3)
    PaletteItem(
        seed = 0xFFD0BCFF.toInt(),
        primary = Color(0xFF7B1FA2),
        secondary = Color(0xFFBA68C8),
        tertiary = Color(0xFFE1BEE7),
        neutral = Color(0xFFF3E5F5)
    ),
    // Pinkish/orange theme (Monet template 4)
    PaletteItem(
        seed = 0xFFFFB68E.toInt(),
        primary = Color(0xFFE64A19),
        secondary = Color(0xFFFF8A65),
        tertiary = Color(0xFFFFCCBC),
        neutral = Color(0xFFFBE9E7)
    ),
    // Yellow/sand theme (Monet template 5)
    PaletteItem(
        seed = 0xFFFFD188.toInt(),
        primary = Color(0xFFFBC02D),
        secondary = Color(0xFFFFF176),
        tertiary = Color(0xFFFFF9C4),
        neutral = Color(0xFFFFFDE7)
    )
)

// Helper to determine if we should render light or dark text based on theme preferences
@Composable
fun isThemeDark(prefs: ThemePrefs?): Boolean {
    if (prefs == null) return true // default to dark OOBE theme
    return when (prefs.darkThemeMode) {
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
}

// Sine easing transition curve for smooth bouncing/floating
val SineBow = Easing { fraction ->
    val t = fraction * 2f * Math.PI.toFloat()
    (1f - cos(t)) / 2f
}

@Stable
data class OobeIconPlacement(
    val size: Dp,
    val color: Color,
    val align: Alignment,
    val rot: Float,
    val shape: Shape,
    val offsetX: Dp,
    val offsetY: Dp
)

@Composable
fun AuriyaIconCollage(
    icons: List<ImageVector>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val minDim = minOf(180.dp, maxHeight)
        val primaryCol = MaterialTheme.colorScheme.primary
        val secCol = MaterialTheme.colorScheme.secondary
        val tertCol = MaterialTheme.colorScheme.tertiary
        val onSurf = MaterialTheme.colorScheme.onSurfaceVariant

        val configs = listOf(
            OobeIconPlacement(size = minDim * 0.72f, color = secCol, align = Alignment.Center, rot = -12f, shape = RoundedCornerShape(24.dp), offsetX = 0.dp, offsetY = 0.dp),
            OobeIconPlacement(size = minDim * 0.42f, color = onSurf, align = Alignment.TopStart, rot = 16f, shape = CircleShape, offsetX = 18.dp, offsetY = 6.dp),
            OobeIconPlacement(size = minDim * 0.44f, color = primaryCol, align = Alignment.BottomEnd, rot = 8f, shape = CircleShape, offsetX = (-18).dp, offsetY = (-6).dp),
            OobeIconPlacement(size = minDim * 0.48f, color = tertCol, align = Alignment.TopEnd, rot = -18f, shape = RoundedCornerShape(20.dp), offsetX = (-22).dp, offsetY = 8.dp),
            OobeIconPlacement(size = minDim * 0.38f, color = secCol, align = Alignment.BottomStart, rot = 12f, shape = MaterialShapes.Clover6, offsetX = 24.dp, offsetY = (-8).dp)
        )

        icons.take(5).forEachIndexed { index, icon ->
            val cfg = configs.getOrElse(index) { configs[0] }
            Surface(
                modifier = Modifier
                    .size(cfg.size)
                    .align(cfg.align)
                    .offset(cfg.offsetX, cfg.offsetY)
                    .graphicsLayer { rotationZ = cfg.rot },
                shape = cfg.shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = cfg.color,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Detailed, compact checklist row item for Done Screen
@Composable
fun SummaryItem(label: String, value: String, isDark: Boolean) {
    val labelColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// Quadrant-divided color swatch dots matching Screenshot 2
@Composable
fun SwatchDot(
    item: PaletteItem,
    selected: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(54.dp), // handle pulsating ring size bounds
        contentAlignment = Alignment.Center
    ) {
        if (selected && pulseAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(pulseScale)
                    .border(BorderStroke(2.dp, item.primary.copy(alpha = pulseAlpha)), CircleShape)
            )
        }
        Canvas(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            // Draw 4 arcs representing Monet palette tone quadrants
            drawArc(color = item.primary, startAngle = 180f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.secondary, startAngle = 270f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.neutral, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(BorderStroke(1.dp, item.primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = item.primary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// LiveUiPreviewCard: Dynamic multi-widget previewing sharp vs rounded styling (Step 5) - 220dp Height
@Composable
fun LiveUiPreviewCard(
    seedColor: Int, useDynamicColor: Boolean,
    navMode: NavMode, navType: NavType, cornerRadius: Int
) {
    val shapeRadius = cornerRadius.dp
    val subRadius = (cornerRadius / 2f).dp
    val miniRadius = (cornerRadius / 3f).dp

    Surface(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Top Row: Avatar icon, description lines, and mock Apply button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(shapeRadius))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("*", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Box(modifier = Modifier.size(width = 80.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 50.dp, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f)))
                        }
                    }

                    // Apply button
                    Surface(
                        shape = RoundedCornerShape(shapeRadius),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(width = 68.dp, height = 28.dp)
                            .clickable {}
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Apply",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Middle Row: 4 horizontal quick settings tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(subRadius))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                }

                // 3. Lower Middle Row: Full width settings container card (list row)
                Surface(
                    shape = RoundedCornerShape(shapeRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(miniRadius))
                                    .background(MaterialTheme.colorScheme.primary)
                              )
                              Spacer(modifier = Modifier.width(8.dp))
                              Box(
                                  modifier = Modifier
                                      .size(width = 72.dp, height = 7.dp)
                                      .clip(RoundedCornerShape(3.dp))
                                      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                              )
                          }
                          // Mock switch shape
                          Box(
                              modifier = Modifier
                                  .size(width = 28.dp, height = 16.dp)
                                  .clip(RoundedCornerShape(8.dp))
                                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                          )
                      }
                  }
              }

              // 4. System Bottom Navigation Bar
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .align(Alignment.BottomCenter)
                      .padding(bottom = if (navMode == NavMode.FLOATING) 6.dp else 0.dp),
                  contentAlignment = Alignment.Center
              ) {
                  val capsule = RoundedCornerShape(cornerRadius.dp / 2f)
                  Box(
                      modifier = Modifier
                          .fillMaxWidth(if (navMode == NavMode.FLOATING) 0.65f else 1f)
                          .height(24.dp)
                          .clip(capsule)
                          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                      contentAlignment = Alignment.Center
                  ) {
                      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                          repeat(3) { index ->
                              val sel = index == 0
                              Box(
                                  modifier = Modifier
                                      .size(if (sel) 10.dp else 6.dp)
                                      .clip(if (navType == NavType.MODERN) RoundedCornerShape(2.dp) else CircleShape)
                                      .background(
                                          if (sel) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                      )
                              )
                          }
                      }
                  }
              }
          }
      }
  }

@Composable
fun ThemeColorWidgetCompositionCard(
    pulseScale: Float,
    pulseAlpha: Float
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // 600ms smooth transition color states
    val animPrimary by animateColorAsState(targetValue = primaryColor, animationSpec = tween(600), label = "animPrimary")
    val animOnPrimary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onPrimary, animationSpec = tween(600), label = "animOnPrimary")
    val animSecondary by animateColorAsState(targetValue = secondaryColor, animationSpec = tween(600), label = "animSecondary")
    val animOnSecondary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onSecondary, animationSpec = tween(600), label = "animOnSecondary")
    val animSecondaryContainer by animateColorAsState(targetValue = MaterialTheme.colorScheme.secondaryContainer, animationSpec = tween(600), label = "animSecondaryContainer")
    val animTertiary by animateColorAsState(targetValue = tertiaryColor, animationSpec = tween(600), label = "animTertiary")
    val animOnTertiary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onTertiary, animationSpec = tween(600), label = "animOnTertiary")
    val animSurfaceContainer by animateColorAsState(targetValue = MaterialTheme.colorScheme.surfaceContainer, animationSpec = tween(600), label = "animSurfaceContainer")
    val animOutlineVariant by animateColorAsState(targetValue = MaterialTheme.colorScheme.outlineVariant, animationSpec = tween(600), label = "animOutlineVariant")

    // Micro-animations infinite transition
    val infiniteTransition = rememberInfiniteTransition(label = "microAnims")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = SineBow),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )
    val progressVal by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressVal"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        shape = RoundedCornerShape(24.dp),
        color = animSurfaceContainer,
        border = BorderStroke(1.dp, animOutlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Custom polar M3 shapes (Flower & Heart) drawn mathematically on Canvas + CPU line graph
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = floatOffset.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. M3 Flower Shape (8-leaf clover) on the left side
                    val flowerCenterX = w * 0.28f
                    val flowerCenterY = h * 0.35f
                    val flowerBaseRad = w * 0.15f
                    val flowerAmp = w * 0.035f
                    val flowerPath = Path()
                    val numPetals = 8
                    for (i in 0..360) {
                        val theta = (i * Math.PI.toFloat() / 180f)
                        val r = flowerBaseRad + flowerAmp * sin(numPetals * theta)
                        val x = flowerCenterX + r * cos(theta)
                        val y = flowerCenterY + r * sin(theta)
                        if (i == 0) flowerPath.moveTo(x, y) else flowerPath.lineTo(x, y)
                    }
                    flowerPath.close()

                    // Pulse ring around the flower
                    if (pulseAlpha > 0f) {
                        scale(scale = pulseScale, pivot = Offset(flowerCenterX, flowerCenterY)) {
                            drawPath(
                                path = flowerPath,
                                color = animPrimary.copy(alpha = pulseAlpha),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    // Fill and outline
                    drawPath(path = flowerPath, color = animPrimary.copy(alpha = 0.22f))
                    drawPath(path = flowerPath, color = animPrimary, style = Stroke(width = 2.dp.toPx()))


                    // 2. M3 Heart Shape overlapping on the right side
                    val heartCenterX = w * 0.62f
                    val heartCenterY = h * 0.38f
                    val heartSize = w * 0.26f
                    val heartPath = Path()
                    val heartScale = heartSize / 32f
                    for (i in 0..360) {
                        val t = (i * Math.PI.toFloat() / 180f)
                        val sinT = sin(t)
                        val x = heartCenterX + (16f * sinT * sinT * sinT) * heartScale
                        val y = heartCenterY - (13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)) * heartScale
                        if (i == 0) heartPath.moveTo(x, y) else heartPath.lineTo(x, y)
                    }
                    heartPath.close()

                    // Pulse ring around the heart
                    if (pulseAlpha > 0f) {
                        scale(scale = pulseScale, pivot = Offset(heartCenterX, heartCenterY)) {
                            drawPath(
                                path = heartPath,
                                color = animTertiary.copy(alpha = pulseAlpha),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    // Fill and outline
                    drawPath(path = heartPath, color = animTertiary.copy(alpha = 0.18f))
                    drawPath(path = heartPath, color = animTertiary, style = Stroke(width = 2.dp.toPx()))

                    // 3. Draw a mock CPU governor performance line graph at the bottom center of the Canvas
                    val graphPath = Path()
                    val graphStart = Offset(0f, h * 0.72f)
                    graphPath.moveTo(graphStart.x, graphStart.y)
                    graphPath.quadraticTo(w * 0.25f, h * 0.60f, w * 0.5f, h * 0.75f)
                    graphPath.quadraticTo(w * 0.75f, h * 0.88f, w * 0.95f, h * 0.65f)
                    drawPath(
                        path = graphPath,
                        color = animSecondary.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Tiny dynamic progress bar underneath shapes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Optimizing", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = animPrimary)
                        Text("${(progressVal * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = animPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = animPrimary,
                        trackColor = animOutlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Right Column: A comprehensive collection of Material 3 widgets + mock chat bubble
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Mock Primary Filled Button with dynamic pulse outer ring
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pulseAlpha > 0f) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .scale(scaleX = pulseScale, scaleY = pulseScale),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(2.dp, animPrimary.copy(alpha = pulseAlpha))
                        ) {}
                    }
                    Button(
                        onClick = {},
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = animPrimary,
                            contentColor = animOnPrimary
                        )
                    ) {
                        Text("Primary Filled", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Mock Tonal Button
                Button(
                    onClick = {},
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animSecondaryContainer,
                        contentColor = animSecondary
                    )
                ) {
                    Text("Tonal Style", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Mock Chat Bubble colored in tertiary Monet (richer element)
                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp),
                    color = animTertiary,
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Auriya core active", color = animOnTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Mock Chip & Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = animSecondary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, animSecondary.copy(alpha = 0.4f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Chip", color = animSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = animOnPrimary,
                            checkedTrackColor = animPrimary
                        )
                    )
                }

                // Mock Slider
                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Slider(
                        value = 0.65f,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = animPrimary,
                            activeTrackColor = animPrimary,
                            inactiveTrackColor = animOutlineVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * SetupBottomBar featuring smooth top corners, animated step counter,
 * and morphing 360-degree rotating FAB button.
 */
@Composable
fun SetupBottomBar(
    currentPage: Int,
    pageCount: Int,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit,
    isNextButtonEnabled: Boolean,
    isFinishButtonEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    val targetShapeValues = when (currentPage % 3) {
        0 -> listOf(50f, 50f, 50f, 50f) // Circle
        1 -> listOf(26f, 26f, 26f, 26f) // Rounded Square
        else -> listOf(18f, 50f, 18f, 50f) // Leaf shape
    }

    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")

    val animatedRotation by animateFloatAsState(
        targetValue = currentPage * 360f,
        animationSpec = rotationAnimationSpec,
        label = "Rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                    } else {
                        (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                    }
                },
                label = "StepTextAnimation"
            ) { targetPage ->
                if (targetPage == 0) {
                    Text(
                        text = "Let's go",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "Step $targetPage of ${pageCount - 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val isLastPage = currentPage == pageCount - 1
            val isPrimaryButtonEnabled = if (isLastPage) isFinishButtonEnabled else isNextButtonEnabled
            val containerColor = if (!isPrimaryButtonEnabled) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = if (!isPrimaryButtonEnabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            val dynamicShape = RoundedCornerShape(
                topStartPercent = animatedTopStart.toInt(),
                topEndPercent = animatedTopEnd.toInt(),
                bottomStartPercent = animatedBottomStart.toInt(),
                bottomEndPercent = animatedBottomEnd.toInt()
            )

            Surface(
                onClick = if (isLastPage) onFinishClicked else onNextClicked,
                enabled = isPrimaryButtonEnabled,
                shape = dynamicShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .size(width = 84.dp, height = 56.dp)
                    .rotate(animatedRotation)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        modifier = Modifier.rotate(-animatedRotation),
                        targetState = currentPage < pageCount - 1,
                        label = "AnimatedFabIcon"
                    ) { isNextPage ->
                        if (isNextPage) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Finish",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ThemeOptionItem(
    val mode: DarkThemeMode,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val recommended: Boolean = false
)

@Composable
fun ThemeModeOptionCard(
    option: ThemeOptionItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (option.recommended) {
                        Surface(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionPageLayout(
    title: String,
    granted: Boolean = false,
    description: String,
    buttonText: String,
    icons: List<ImageVector>,
    buttonEnabled: Boolean = true,
    onGrantClicked: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AuriyaIconCollage(
                modifier = Modifier.height(210.dp),
                icons = icons
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClicked,
                enabled = buttonEnabled,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AnimatedContent(targetState = granted, label = "ButtonAnim") { isGranted ->
                    if (isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun NavBarPreview(isFloating: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 1) 0.65f else 1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                AnimatedContent(
                    targetState = isFloating,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + slideInVertically { it })
                            .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically { it })
                    },
                    label = "NavbarPreviewAnim"
                ) { floating ->
                    if (floating) {
                        Surface(
                            modifier = Modifier
                                .padding(16.dp)
                                .width(180.dp)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(24.dp, 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(28.dp, 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

