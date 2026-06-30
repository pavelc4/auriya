package dev.auriya.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dev.auriya.app.R
import dev.auriya.app.data.RootShell
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.config.TomlParser

class AuriyaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val current = RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim() ?: "2"
        val next = when (current) {
            "1" -> "2" // Performance -> Balance
            "2" -> "3" // Balance -> Powersave
            "3" -> "1" // Powersave -> Performance
            else -> "2"
        }
        val profileString = when (next) {
            "1" -> "PERFORMANCE"
            "2" -> "BALANCE"
            "3" -> "POWERSAVE"
            else -> "BALANCE"
        }
        val modeString = profileString.lowercase()

        // 1. Notify daemon via UDS
        RootShell.exec("echo 'SET_PROFILE $profileString' | nc -U /dev/socket/auriya.sock")
        
        // 2. Persist current profile code
        RootShell.writeText(ConfigPaths.CURRENT_PROFILE_FILE, next)

        // 3. Persist profile choice directly to settings.toml
        val settingsText = RootShell.readText(ConfigPaths.SETTINGS_FILE)
        if (settingsText != null) {
            try {
                val settings = TomlParser.parseSettings(settingsText)
                val updated = settings.copy(
                    daemon = settings.daemon.copy(defaultMode = modeString),
                    fas = settings.fas.copy(defaultMode = modeString)
                )
                val serialized = TomlParser.serializeSettings(updated)
                RootShell.writeText(ConfigPaths.SETTINGS_FILE, serialized)
            } catch (_: Throwable) {}
        }

        // Show Toast Notification
        val profileName = when (next) {
            "1" -> "Performance"
            "2" -> "Balance"
            "3" -> "Powersave"
            else -> "Balance"
        }
        Toast.makeText(this, "Auriya: Profile set to $profileName", Toast.LENGTH_SHORT).show()

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val current = RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim() ?: "2"
        val (profileName, iconRes) = when (current) {
            "1" -> Pair("Performance", R.drawable.ic_gamepad)
            "2" -> Pair("Balance", R.drawable.ic_settings)
            "3" -> Pair("Powersave", R.drawable.ic_battery)
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
