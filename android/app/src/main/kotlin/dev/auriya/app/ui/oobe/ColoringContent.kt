package dev.auriya.app.ui.oobe

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.ui.components.SegmentedControl
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun ColoringContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    // Swatch focus pulse loop
    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Text(
            "COLORING",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )
        Text(
            "Configure Monet dynamic styling and color accents.",
            style = MaterialTheme.typography.bodyMedium,
            color = descColor
        )
        Spacer(modifier = Modifier.height(20.dp))

        ThemeColorWidgetCompositionCard(
            pulseScale = pulseScale,
            pulseAlpha = pulseAlpha
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Material You", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("Match system theme colors", style = MaterialTheme.typography.bodySmall, color = descColor)
                }
                Switch(
                    checked = currentPrefs.useDynamicColor,
                    onCheckedChange = { themeViewModel.setUseDynamicColor(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!currentPrefs.useDynamicColor) {
                Column {
                    Text("Custom Palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(PALETTE_ITEMS) { item ->
                            SwatchDot(
                                item = item,
                                selected = item.seed == currentPrefs.seedColor,
                                pulseScale = pulseScale,
                                pulseAlpha = pulseAlpha
                            ) {
                                themeViewModel.setSeedColor(item.seed)
                            }
                        }
                    }
                }
            }

            // Dark Mode Segmented Control (System, Light, Dark)
            Column {
                Text("Theme Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = when (currentPrefs.darkThemeMode) {
                        DarkThemeMode.FOLLOW_SYSTEM -> 0
                        DarkThemeMode.LIGHT -> 1
                        DarkThemeMode.DARK -> 2
                    },
                    onItemSelected = {
                        themeViewModel.setDarkThemeMode(
                            when (it) {
                                0 -> DarkThemeMode.FOLLOW_SYSTEM
                                1 -> DarkThemeMode.LIGHT
                                else -> DarkThemeMode.DARK
                            }
                        )
                    },
                    inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                )
            }

            // AMOLED Toggle switch
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pure Black (AMOLED)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("Turn background pitch black in dark mode", style = MaterialTheme.typography.bodySmall, color = descColor)
                }
                Switch(
                    checked = currentPrefs.isAmoled,
                    onCheckedChange = { themeViewModel.setAmoled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
