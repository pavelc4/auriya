package dev.auriya.shared.config

/**
 * Filesystem paths shared between the Rust daemon and the Android
 * companion service.
 *
 * These constants are the single source of truth on the Kotlin side —
 * the Rust daemon mirrors them in `src/core/system_status/mod.rs` and
 * `src/core/config/path.rs`. Update both sides when changing.
 */
object ConfigPaths {
    const val CONFIG_DIR: String = "/data/adb/.config/auriya"

    const val SETTINGS_FILE: String = "$CONFIG_DIR/settings.toml"
    const val GAMELIST_FILE: String = "$CONFIG_DIR/gamelist.toml"

    /** Companion → daemon: parsed system snapshot (foreground, screen, …). */
    const val STATUS_FILE: String = "$CONFIG_DIR/system_status"

    /** Daemon → companion: commands such as DnD toggle or refresh-rate set. */
    const val CMD_FILE: String = "$CONFIG_DIR/auriya_cmd"

    /** Daemon writes the currently-applied profile mode here. */
    const val CURRENT_PROFILE_FILE: String = "$CONFIG_DIR/current_profile"

    /** Companion holds an exclusive flock on this file while running. */
    const val COMPANION_LOCK_FILE: String = "$CONFIG_DIR/companion.lock"
}
