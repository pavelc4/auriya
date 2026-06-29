package dev.auriya.app.ui.oobe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.viewmodel.UiViewModel

@Composable
fun RootCheckContent(
    isDark: Boolean,
    viewModel: UiViewModel,
    hasRoot: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    var isRequesting by remember { mutableStateOf(false) }
    LaunchedEffect(hasRoot) { if (hasRoot) isRequesting = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SUPERUSER",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Auriya needs root authorization to optimize kernel governors, cores, and frequencies in real-time.",
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = descColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (hasRoot) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasRoot) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hasRoot) Icons.Outlined.CheckCircle else Icons.Outlined.Shield,
                        null,
                        tint = if (hasRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        if (hasRoot) "Root verified" else "Waiting for authorization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        if (hasRoot) "Auriya daemon initialized successfully." else "Grant Magisk / KSU / APatch prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = descColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasRoot) {
            OutlinedButton(
                onClick = { isRequesting = true; viewModel.checkRoot() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRequesting,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                if (isRequesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isRequesting) "Checking root..." else "Request permission", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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
                enabled = hasRoot,
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
