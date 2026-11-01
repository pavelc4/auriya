package dev.auriya.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dev.auriya.app.R
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.stats.StatsParser
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.config.TomlParser

class AuriyaTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val current =
            StatsParser.fetchStats()?.session?.profile?.lowercase()
                ?: RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim()?.lowercase()
                ?: "balance"
        val nextMode =
            when (current) {
                "1", "performance" -> "balance"
                "2", "balance" -> "powersave"
                "3", "powersave" -> "fast"
                "4", "fast", "fas" -> "performance"
                else -> "balance"
            }
        val nextCode =
            when (nextMode) {
                "performance" -> "1"
                "balance" -> "2"
                "powersave" -> "3"
                "fast", "fas" -> "4"
                else -> "2"
            }

        // 1. Notify daemon via CLI
        RootShell.exec("${ConfigPaths.AURIYACTL_BIN} set-profile $nextMode")

        // 2. Persist current profile code for backwards compatibility
        RootShell.writeText(ConfigPaths.CURRENT_PROFILE_FILE, nextCode)

        // 3. Persist profile choice directly to settings.toml
        val settingsText = RootShell.readText(ConfigPaths.SETTINGS_FILE)
        if (settingsText != null) {
            try {
                val settings = TomlParser.parseSettings(settingsText)
                val updated =
                    settings.copy(
                        daemon = settings.daemon.copy(defaultMode = nextMode),
                        fas = settings.fas.copy(defaultMode = nextMode),
                    )
                val serialized = TomlParser.serializeSettings(updated)
                RootShell.writeText(ConfigPaths.SETTINGS_FILE, serialized)
            } catch (_: Throwable) {
            }
        }

        // Show Toast Notification
        val profileName = nextMode.replaceFirstChar { it.uppercase() }
        Toast.makeText(this, "Auriya: Profile set to $profileName", Toast.LENGTH_SHORT).show()

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val current =
            StatsParser.fetchStats()?.session?.profile?.lowercase()
                ?: RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim()?.lowercase()
                ?: "balance"
        val (profileName, iconRes) =
            when (current) {
                "1", "performance" -> Pair("Performance", R.drawable.ic_gamepad)
                "2", "balance" -> Pair("Balance", R.drawable.ic_settings)
                "3", "powersave" -> Pair("Powersave", R.drawable.ic_battery)
                "4", "fast", "fas" -> Pair("FAS", R.drawable.ic_gamepad)
                else -> Pair("Balance", R.drawable.ic_settings)
            }

        tile.label = "Auriya Profile"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = profileName
        } else {
            tile.label = "Auriya: $profileName"
        }

        tile.icon = Icon.createWithResource(this, iconRes)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
