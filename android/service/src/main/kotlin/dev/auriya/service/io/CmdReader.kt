package dev.auriya.service.io

import android.os.Build
import android.os.FileObserver
import android.util.Log
import dev.auriya.shared.model.Cmd
import dev.auriya.shared.status.CmdFormat
import java.io.File

/**
 * Watches the daemon's command file via inotify (FileObserver under
 * the hood) and dispatches every fresh [Cmd] to a sink.
 *
 * Deduplication: each command carries a monotonically increasing
 * `seq`. The reader remembers the last seq it dispatched and ignores
 * anything not strictly newer. This handles three cases at once:
 *
 *   1. inotify fires CLOSE_WRITE twice for a single atomic replace
 *      (which it sometimes does on tmpfs);
 *   2. the daemon rewrites the same payload for a different reason;
 *   3. the reader is forced to re-parse the file on startup before
 *      the daemon has written anything new.
 *
 * The file is parsed in full on each event — it is small (3 short
 * lines) and only changes a handful of times per day, so there is no
 * point optimising for incremental reads.
 */
class CmdReader(
    private val target: File,
    private val sink: (Cmd) -> Unit,
) {
    private companion object {
        private const val TAG = "AuriyaCmdReader"
    }

    private val parent: File = target.parentFile
        ?: error("cmd file ${target.path} has no parent directory")
    private val filename = target.name
    private var lastSeq: Long = Long.MIN_VALUE
    private var observer: FileObserver? = null

    /**
     * Begin watching. If the file already exists we parse it once so
     * the actuator sees any state left over from a previous boot —
     * the daemon clears it on every start, but better safe than
     * sorry.
     */
    fun start() {
        if (target.exists()) {
            parseAndDispatch()
        }

        val obs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The File-based constructor watches the directory passed
            // and reports events with the entry name in onEvent().
            object : FileObserver(parent, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == filename) parseAndDispatch()
                }
            }
        } else {
            // API < 29 only has the deprecated path-based constructor.
            @Suppress("DEPRECATION")
            object : FileObserver(parent.path, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == filename) parseAndDispatch()
                }
            }
        }

        obs.startWatching()
        observer = obs
        Log.i(TAG, "watching ${target.path}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
    }

    private fun parseAndDispatch() {
        val text = try {
            target.readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "failed to read ${target.path}: ${t.message}")
            return
        }
        val cmd = CmdFormat.decode(text) ?: run {
            Log.w(TAG, "discarded malformed cmd payload")
            return
        }
        if (cmd.seq <= lastSeq) {
            Log.d(TAG, "ignoring stale cmd seq=${cmd.seq} (last=$lastSeq)")
            return
        }
        lastSeq = cmd.seq
        try {
            sink(cmd)
        } catch (t: Throwable) {
            Log.e(TAG, "actuator threw on cmd seq=${cmd.seq}", t)
        }
    }
}
