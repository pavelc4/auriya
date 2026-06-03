package dev.auriya.shared.model

/**
 * Commands the Rust daemon writes to `auriya_cmd` for the companion
 * service to execute. The command file is a single snapshot — newer
 * writes overwrite older ones — and every line is keyed so the
 * companion can ignore commands it doesn't understand.
 *
 * The `seq` value lets the companion deduplicate when an inotify event
 * fires twice for the same write (or when the writer rewrites the same
 * payload to bump consumers). It is monotonically increasing per
 * daemon instance but does not need to survive daemon restarts.
 */
data class Cmd(
    val seq: Long,
    val dnd: DndFilter? = null,
    val refreshRate: Int? = null,
)

/**
 * Maps directly to NotificationManager's INTERRUPTION_FILTER_* values
 * so the actuator can pass it through without translation:
 *
 *   ALL      → no DnD, all notifications pass
 *   PRIORITY → priority-only (the most common gaming setting)
 */
enum class DndFilter(val wire: Int) {
    ALL(0),
    PRIORITY(1);

    companion object {
        fun fromWire(value: Int): DndFilter? = entries.firstOrNull { it.wire == value }
    }
}
