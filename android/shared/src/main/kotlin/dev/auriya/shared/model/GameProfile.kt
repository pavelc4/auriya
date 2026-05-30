package dev.auriya.shared.model

data class GameProfile(
    val packageName: String,
    val cpuGovernor: String,
    val enableDnd: Boolean,
    val targetFps: Int? = null,
    val refreshRate: Int? = null,
    val mode: String? = null,
    val lockRotation: Boolean = false,
    /**
     * Total silence while the game is foreground — overrides the more
     * permissive `enableDnd` (priority-only) when both are set. Mapped
     * on the daemon side to `DndFilter::None` and restored to `All` on
     * exit.
     */
    val blockNotifications: Boolean = false,
)

data class GameList(
    val games: List<GameProfile> = emptyList()
)
