package dev.auriya.shared.model

data class GameProfile(
    val packageName: String,
    val cpuGovernor: String,
    val enableDnd: Boolean,
    val targetFps: Int? = null,
    val refreshRate: Int? = null,
    val mode: String? = null,
    val lockRotation: Boolean = false,
)

data class GameList(
    val games: List<GameProfile> = emptyList()
)
