package dev.auriya.app.ui.oobe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.ui.theme.AuriyaFontFamily
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun DoneContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier
) {
    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    val finishIcons = remember {
        listOf(
            Icons.Rounded.CheckCircle,
            Icons.Rounded.RocketLaunch,
            Icons.Rounded.Speed,
            Icons.Rounded.Bolt,
            Icons.Rounded.Favorite
        )
    }

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
                text = "You're All Set!",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = dev.auriya.app.ui.theme.GoogleSansRounded,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Your appearance preferences are active and Auriya kernel daemon optimizations are configured.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Celebration Icon Collage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AuriyaIconCollage(
                icons = finishIcons,
                height = 240.dp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}



