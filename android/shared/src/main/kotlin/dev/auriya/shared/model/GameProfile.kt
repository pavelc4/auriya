package dev.auriya.shared.model

data class GameProfile(
    val packageName: String,
    val cpuGovernor: String,
    val enableDnd: Boolean,
    val targetFps: Int? = null,
    val refreshRate: Int? = null,
    val mode: String? = null,
    val ceiling: String? = null,
    val thermalPreset: String? = null,
    val wifiLock: Boolean = true,
    val statusbarLock: Boolean = true,
    val touchBoost: Boolean = true,
    val powerHal: Boolean = true,
    val silenceVibrator: Boolean = false,
    val freezeRotation: Boolean = false,
)

data class GameList(
    val games: List<GameProfile> = emptyList(),
)
