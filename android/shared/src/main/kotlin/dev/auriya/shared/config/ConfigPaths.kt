package dev.auriya.shared.config

/**
 * Filesystem paths shared between the Rust daemon and the Android app.
 *
 * These constants are the single source of truth on the Kotlin side —
 * the Rust daemon mirrors them in `src/core/config/mod.rs`.
 */
object ConfigPaths {
    const val CONFIG_DIR: String = "/data/adb/.config/auriya"

    const val SETTINGS_FILE: String = "$CONFIG_DIR/settings.toml"
    const val GAMELIST_FILE: String = "$CONFIG_DIR/gamelist.toml"

    /** Daemon writes the currently-applied profile mode here. */
    const val CURRENT_PROFILE_FILE: String = "$CONFIG_DIR/current_profile"

    const val AURIYACTL_BIN: String = "/data/adb/modules/auriya/system/bin/auriyactl"
    const val SOCKET_PATH: String = "/dev/socket/auriya.sock"
}
