package dev.auriya.shared.status

import dev.auriya.shared.model.Cmd
import dev.auriya.shared.model.DndFilter
import java.io.File

private const val TAG = "CmdFormat"

/**
 * Encodes a [Cmd] to the wire format (one line per field)
 * and decodes it back.
 *
 * Wire format (mirrors `src/core/cmd_writer/mod.rs`):
 *
 *     seq 42
 *     dnd 1              # 0=off, 1=priority
 *     refresh_rate 90    # Hz; 0 means "restore previous"
 */
object CmdFormat {

    fun encode(cmd: Cmd, sb: StringBuilder = StringBuilder()): String {
        sb.append("seq ").append(cmd.seq).append('\n')
        cmd.dnd?.let { sb.append("dnd ").append(it.wire).append('\n') }
        cmd.refreshRate?.let { sb.append("refresh_rate ").append(it).append('\n') }
        return sb.toString()
    }

    fun decode(file: File): Cmd? {
        if (!file.exists()) return null
        val lines = try {
            file.readLines()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "failed to read cmd file", e)
            return null
        }
        var s = 0L
        var dnd: DndFilter? = null
        var refreshRate: Int? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(' ', limit = 2)
            if (parts.size < 2) continue
            val key = parts[0]
            val value = parts[1]
            when (key) {
                "seq" -> s = value.toLongOrNull() ?: continue
                "dnd" -> dnd = value.toIntOrNull()?.let(DndFilter::fromWire)
                "refresh_rate" -> refreshRate = value.toIntOrNull()
            }
        }
        return Cmd(seq = s, dnd = dnd, refreshRate = refreshRate)
    }
}
