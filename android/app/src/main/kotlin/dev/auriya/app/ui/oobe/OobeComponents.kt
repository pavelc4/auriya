package dev.auriya.app.ui.oobe

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.ThemePrefs
import kotlin.math.cos
import kotlin.math.sin

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
