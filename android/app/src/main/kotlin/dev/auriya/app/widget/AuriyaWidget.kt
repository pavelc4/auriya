package dev.auriya.app.widget

import android.content.Context
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import dev.auriya.app.R
import androidx.glance.layout.Box
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.action.clickable
import androidx.glance.text.TextStyle
import dev.auriya.app.data.RootShell
import dev.auriya.shared.config.TomlParser
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.compose.ui.graphics.Color

data class StatsItem(
    val iconRes: Int,
    val label: String,
    val value: String
)

class AuriyaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rawProfile = RootShell.readText("/data/adb/.config/auriya/current_profile")?.trim() ?: "2"
        val currentProfile = when (rawProfile) {
            "3" -> "powersave"
            "2" -> "balance"
            "1" -> "performance"
            else -> rawProfile.lowercase()
        }

        // Gather daemon status & RAM usage
        val pid = RootShell.run("pidof auriya").trim()
        val daemonActive = pid.isNotEmpty() && pid != "null"
        val ramUsage = if (daemonActive) {
            val rss = RootShell.run("grep VmRSS /proc/$pid/status 2>/dev/null | awk '{print \$2}'").trim().toDoubleOrNull()
            if (rss != null) {
                "${String.format("%.1f", rss / 1024.0)} MB"
            } else {
                "-"
            }
        } else {
            "-"
        }

        // Gather battery percentage
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Gather whitelisted apps count
        val gameListFile = "/data/adb/.config/auriya/gamelist.toml"
        val whitelistedCount = if (RootShell.exists(gameListFile)) {
            val content = RootShell.readText(gameListFile)
            if (content != null) {
                runCatching {
                    TomlParser.parseGameList(content).games.size
                }.getOrDefault(0)
            } else {
                0
            }
        } else {
            0
        }

        // Gather current CPU governor
        val cpuGov = RootShell.run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null").trim().ifEmpty { "unknown" }

        provideContent {
            AuriyaWidgetContent(currentProfile, daemonActive, ramUsage, batteryPct, whitelistedCount, cpuGov)
        }
    }
}

@Composable
fun AuriyaWidgetContent(
    currentProfile: String,
    daemonActive: Boolean,
    ramUsage: String,
    batteryPct: Int,
    whitelistedCount: Int,
    cpuGov: String
) {
    val size = LocalSize.current
    val isWide = size.width >= 250.dp

    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auriya FAS",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "Active Profile Optimizer",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
                
                Spacer(modifier = GlanceModifier.defaultWeight())
                
                val displayProfile = when (currentProfile.lowercase()) {
                    "powersave" -> "Power Save"
                    "balance" -> "Balance"
                    "performance" -> "Performance"
                    else -> currentProfile.replaceFirstChar { it.uppercase() }
                }
                
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayProfile,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Dynamic Stats Row based on width
            val statsList = if (isWide) {
                listOf(
                    StatsItem(R.drawable.ic_battery, "Battery", "$batteryPct%"),
                    StatsItem(R.drawable.ic_memory, "RAM", ramUsage),
                    StatsItem(R.drawable.ic_gamepad, "Apps", "$whitelistedCount"),
                    StatsItem(R.drawable.ic_settings, "Gov", cpuGov.replaceFirstChar { it.uppercase() })
                )
            } else {
                listOf(
                    StatsItem(R.drawable.ic_battery, "Battery", "$batteryPct%"),
                    StatsItem(R.drawable.ic_memory, "RAM", ramUsage)
                )
            }

            Row(
                modifier = GlanceModifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                statsList.forEachIndexed { index, item ->
                    StatsCard(
                        item = item,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (index < statsList.lastIndex) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Daemon status footer
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = if (daemonActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                val statusText = if (daemonActive) "Daemon Active" else "Daemon Stopped"
                
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .background(dotColor)
                        .cornerRadius(4.dp)
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = statusText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                ProfileButton(
                    label = "Save",
                    profile = "powersave",
                    isActive = currentProfile.lowercase() == "powersave"
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                ProfileButton(
                    label = "Bal",
                    profile = "balance",
                    isActive = currentProfile.lowercase() == "balance" || currentProfile.isEmpty() || currentProfile == "unknown"
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                ProfileButton(
                    label = "Perf",
                    profile = "performance",
                    isActive = currentProfile.lowercase() == "performance"
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun StatsCard(item: StatsItem, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(12.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(item.iconRes),
                    contentDescription = item.label,
                    modifier = GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
                Spacer(modifier = GlanceModifier.width(3.dp))
                Text(
                    text = item.label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = item.value,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}


@Composable
private fun ProfileButton(
    label: String,
    profile: String,
    isActive: Boolean
) {
    val bg = if (isActive) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
    val textColor = if (isActive) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurfaceVariant

    Box(
        modifier = GlanceModifier
            .width(64.dp)
            .height(40.dp)
            .background(bg)
            .cornerRadius(20.dp)
            .clickable(
                actionRunCallback<ProfileChangeCallback>(
                    actionParametersOf(ProfileChangeCallback.profileKey to profile)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

class ProfileChangeCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val newProfile = parameters[profileKey] ?: return
        
        val profileString = when (newProfile) {
            "powersave" -> "POWERSAVE"
            "balance" -> "BALANCE"
            "performance" -> "PERFORMANCE"
            else -> "BALANCE"
        }
        val rawValue = when (newProfile) {
            "powersave" -> "3"
            "balance" -> "2"
            "performance" -> "1"
            else -> "2"
        }
        RootShell.exec("echo 'SET_PROFILE $profileString' | nc -U /dev/socket/auriya.sock")
        RootShell.writeText("/data/adb/.config/auriya/current_profile", rawValue)

        AuriyaWidget().update(context, glanceId)
    }

    companion object {
        val profileKey = ActionParameters.Key<String>("profile_key")
    }
}
