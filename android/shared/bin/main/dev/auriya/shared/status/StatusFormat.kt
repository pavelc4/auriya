package dev.auriya.shared.status

import dev.auriya.shared.model.SystemStatus

/**
 * Line-based key/value wire format for the system_status file.
 *
 * Layout (any subset, any order, unknown keys ignored):
 *
 *   focused_app <package> <pid> <uid>
 *   screen_awake <0|1>
 *   battery_saver <0|1>
 *   zen_mode <0|1|2|3>
 *
 * The Rust daemon parses the same format in
 * `src/core/system_status/mod.rs`.
 */
object StatusFormat {
    /**
     * Render a snapshot to its wire representation. Fields left as
     * `null` are omitted entirely, which the reader treats as
     * "no change since the last write" for that key.
     */
    fun encode(status: SystemStatus): String = buildString {
        status.focusedApp?.let { pkg ->
            append("focused_app ").append(pkg)
            status.focusedPid?.let { append(' ').append(it) }
            status.focusedUid?.let { append(' ').append(it) }
            append('\n')
        }
        status.screenAwake?.let { append("screen_awake ").append(if (it) 1 else 0).append('\n') }
        status.batterySaver?.let { append("battery_saver ").append(if (it) 1 else 0).append('\n') }
        status.zenMode?.let { append("zen_mode ").append(it).append('\n') }
    }

    /**
     * Parse the wire representation back into a [SystemStatus]. Used
     * mostly by tests and by the daemon-side reader (the Rust parser
     * implements the same grammar independently).
     */
    fun decode(text: String): SystemStatus {
        var focusedApp: String? = null
        var focusedPid: Int? = null
        var focusedUid: Int? = null
        var screenAwake: Boolean? = null
        var batterySaver: Boolean? = null
        var zenMode: Int? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val sep = line.indexOf(' ')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep)
            val value = line.substring(sep + 1).trim()

            when (key) {
                "focused_app" -> {
                    val parts = value.split(' ', '\t').filter { it.isNotEmpty() }
                    if (parts.isEmpty()) return@forEach
                    focusedApp = parts[0]
                    focusedPid = parts.getOrNull(1)?.toIntOrNull()
                    focusedUid = parts.getOrNull(2)?.toIntOrNull()
                }
                "screen_awake" -> screenAwake = parseBool(value)
                "battery_saver" -> batterySaver = parseBool(value)
                "zen_mode" -> zenMode = value.toIntOrNull()
                else -> Unit // forward-compatible
            }
        }

        return SystemStatus(
            focusedApp = focusedApp,
            focusedPid = focusedPid,
            focusedUid = focusedUid,
            screenAwake = screenAwake,
            batterySaver = batterySaver,
            zenMode = zenMode,
        )
    }

    private fun parseBool(value: String): Boolean? = when (value.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}
