package dev.auriya.app.ui.oobe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.theme.AuriyaFontFamily
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun WelcomeContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 1.15.em
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Auriya",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 46.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 1.15.em
                ),
                textAlign = TextAlign.Center
            )
        }

        // Center Art Collage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            AuriyaIconCollage(
                icons = listOf(
                    androidx.compose.material.icons.Icons.Rounded.Speed,
                    androidx.compose.material.icons.Icons.Rounded.Bolt,
                    androidx.compose.material.icons.Icons.Rounded.Memory,
                    androidx.compose.material.icons.Icons.Rounded.Security,
                    androidx.compose.material.icons.Icons.Rounded.Tune
                ),
                height = 220.dp
            )
        }

        // Bottom Description Text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Next-generation Android daemon & kernel frame scheduling engine written in Rust with Material 3 Expressive personalization.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

