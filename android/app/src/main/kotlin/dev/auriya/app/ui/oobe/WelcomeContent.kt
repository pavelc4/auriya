package dev.auriya.app.ui.oobe

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.auriya.app.R
import dev.auriya.app.ui.theme.GoogleSansRounded
import dev.auriya.app.viewmodel.ThemeViewModel

@Composable
fun WelcomeContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_welcome_title),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 1.15.em,
                    ),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Center Art Collage
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            AuriyaIconCollage(
                icons =
                    listOf(
                        Icons.Rounded.Speed,
                        Icons.Rounded.Bolt,
                        Icons.Rounded.Memory,
                        Icons.Rounded.Security,
                        Icons.Rounded.Tune,
                    ),
                height = 220.dp,
            )
        }

        // Bottom Spacer / Accent
        Spacer(modifier = Modifier.height(12.dp))
    }
}
