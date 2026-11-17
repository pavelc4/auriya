package dev.auriya.app.service

import android.graphics.drawable.Icon
import androidx.annotation.StringRes
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
        Toast.makeText(
                this,
                getString(R.string.tile_toast_profile_set, getString(profileTitleRes(nextMode))),
                Toast.LENGTH_SHORT,
            ).show()

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val current =
            StatsParser.fetchStats()?.session?.profile?.lowercase()
                ?: RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim()?.lowercase()
                ?: "balance"
        val profileName = getString(profileTitleRes(current))
        val iconRes =
            when (current) {
                "1", "performance" -> R.drawable.ic_gamepad
                "3", "powersave" -> R.drawable.ic_battery
                "4", "fast", "fas" -> R.drawable.ic_gamepad
                else -> R.drawable.ic_settings
            }

        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = profileName
        } else {
            tile.label = getString(R.string.tile_label_with_profile, profileName)
        }

        tile.icon = Icon.createWithResource(this, iconRes)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    /** Maps a raw profile key to its localized display-name string resource. */
    @StringRes
    private fun profileTitleRes(profile: String): Int =
        when (profile) {
            "1", "performance" -> R.string.profile_preset_performance_title
            "2", "balance" -> R.string.profile_preset_balance_title
            "3", "powersave" -> R.string.profile_preset_powersave_title
            "4", "fast", "fas" -> R.string.profile_preset_fas_title
            else -> R.string.profile_preset_balance_title
        }
}
