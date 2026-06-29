package dev.auriya.app.ui.oobe

import androidx.compose.foundation.layout.*
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
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.ui.components.SegmentedControl
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun NavbarContent(
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Text(
            "NAVIGATION",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )
        Text(
            "Select placement styles for your system bottom navigation.",
            style = MaterialTheme.typography.bodyMedium,
            color = descColor
        )
        Spacer(modifier = Modifier.height(20.dp))

        LiveUiPreviewCard(
            seedColor = currentPrefs.seedColor,
            useDynamicColor = currentPrefs.useDynamicColor,
            navMode = currentPrefs.navMode,
            navType = currentPrefs.navType,
            cornerRadius = currentPrefs.cornerRadius
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text("Navigation Layout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Standard", "Floating"),
                    selectedIndex = if (currentPrefs.navMode == NavMode.STANDARD) 0 else 1,
                    onItemSelected = { themeViewModel.setNavMode(if (it == 0) NavMode.STANDARD else NavMode.FLOATING) },
                    inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                )
            }

            Column {
                Text("Navigation Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Legacy (Dots)", "Modern (Squircles)"),
                    selectedIndex = if (currentPrefs.navType == NavType.LEGACY) 0 else 1,
                    onItemSelected = { themeViewModel.setNavType(if (it == 0) NavType.LEGACY else NavType.MODERN) },
                    inactiveTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
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
