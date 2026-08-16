package dev.auriya.app.ui.oobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.ui.theme.AuriyaFontFamily
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun ColoringContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    val themeOptions = listOf(
        ThemeOptionItem(
            mode = DarkThemeMode.DARK,
            title = "Dark Theme",
            description = "High contrast, battery-efficient dark interface",
            icon = Icons.Rounded.DarkMode,
            recommended = true
        ),
        ThemeOptionItem(
            mode = DarkThemeMode.LIGHT,
            title = "Light Theme",
            description = "Clean, crisp daytime brightness",
            icon = Icons.Rounded.LightMode
        ),
        ThemeOptionItem(
            mode = DarkThemeMode.FOLLOW_SYSTEM,
            title = "Follow System",
            description = "Dynamically synchronizes with Android device settings",
            icon = Icons.Rounded.PhoneAndroid
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = "Choose Your Theme",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Select your preferred visual style and personalization mode.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            themeOptions.forEach { option ->
                ThemeModeOptionCard(
                    option = option,
                    selected = currentPrefs.darkThemeMode == option.mode,
                    onClick = { themeViewModel.setDarkThemeMode(option.mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
