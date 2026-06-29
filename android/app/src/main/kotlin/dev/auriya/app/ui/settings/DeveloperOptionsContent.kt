package dev.auriya.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.SectionCard
import dev.auriya.app.ui.components.SettingRow
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.viewmodel.UiViewModel

@Composable
fun DeveloperOptionsContent(
    viewModel: UiViewModel,
    onResetOobe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasRoot by viewModel.hasRoot.collectAsState()
    val systemInfo by viewModel.systemInfo.collectAsState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal)
    ) {
        SectionCard(title = "Application State") {
            SettingRow(
                icon = Icons.Filled.Refresh,
                title = "Reset Setup Wizard",
                subtitle = "Re-enable OOBE setup flow next launch",
                control = {
                    Button(
                        onClick = {
                            onResetOobe()
                            Toast.makeText(context, "OOBE State reset. Showing setup...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(AuriyaTokens.rounding.medium)
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        SectionCard(title = "Privileges & Diagnostics") {
            SettingRow(
                icon = Icons.Filled.Shield,
                title = "Root Verified",
                subtitle = if (hasRoot) "Privileged su access active" else "Non-root restricted execution",
                control = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AuriyaTokens.rounding.small))
                            .background(
                                if (hasRoot) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasRoot) "ACTIVE" else "DENIED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRoot) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )

            SettingRow(
                icon = Icons.Filled.Info,
                title = "Daemon Status",
                subtitle = "Current active daemon state",
                control = {
                    Text(
                        text = systemInfo.daemonStatus.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (systemInfo.daemonStatus == "working") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}
