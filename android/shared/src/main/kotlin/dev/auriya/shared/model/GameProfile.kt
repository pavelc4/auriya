package dev.auriya.shared.model

/**
 * Per-game profile stored in `gamelist.toml`.
 */
data class GameProfile(
    val packageName: String = "",
    val mode: String = "performance",
    val cpuGovernor: String = "performance",
    val refreshRate: Int? = null,
    val enableDnd: Boolean = true,
    val targetFps: Int? = null,
)
