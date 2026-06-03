package dev.auriya.shared.status

import dev.auriya.shared.model.Cmd
import dev.auriya.shared.model.DndFilter

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
                else -> Unit
            }
        }

        val s = seq ?: return null
        return Cmd(seq = s, dnd = dnd, refreshRate = refreshRate)
    }
}
