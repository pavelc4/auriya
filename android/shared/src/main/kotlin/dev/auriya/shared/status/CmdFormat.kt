package dev.auriya.shared.status

import dev.auriya.shared.model.Cmd
import dev.auriya.shared.model.DndFilter

/**
 * Line-based key/value wire format for the `auriya_cmd` file.
 *
 * Layout (subset, order-independent, unknown keys ignored):
 *
 *   seq 42
 *   dnd 1              # 0=off, 1=priority
 *   refresh_rate 90    # Hz; 0 means "restore the previous rate"
 *
 * `seq` is mandatory — the companion uses it for deduplication.
 */
object CmdFormat {
    fun encode(cmd: Cmd): String = buildString {
        append("seq ").append(cmd.seq).append('\n')
        cmd.dnd?.let { append("dnd ").append(it.wire).append('\n') }
        cmd.refreshRate?.let { append("refresh_rate ").append(it).append('\n') }
    }

    fun decode(text: String): Cmd? {
        var seq: Long? = null
        var dnd: DndFilter? = null
        var refreshRate: Int? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val sep = line.indexOf(' ')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep)
            val value = line.substring(sep + 1).trim()

            when (key) {
                "seq" -> seq = value.toLongOrNull()
                "dnd" -> dnd = value.toIntOrNull()?.let(DndFilter::fromWire)
                "refresh_rate" -> refreshRate = value.toIntOrNull()
                else -> Unit // forward-compatible
            }
        }

        val s = seq ?: return null
        return Cmd(seq = s, dnd = dnd, refreshRate = refreshRate)
    }
}
