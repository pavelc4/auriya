package dev.auriya.service.io

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.auriya.shared.model.Cmd
import dev.auriya.shared.status.CmdFormat
import java.io.File

/**
 * Watches the daemon's command file via polling and dispatches every
 * fresh [Cmd] to a sink.
 *
 * We deliberately **avoid** [android.os.FileObserver] because on
 * Android 16 (API 36) the native FileObserver thread segfaults
 * (`SIGSEGV` in `libandroid_runtime.so`) when used from a headless
 * `app_process` — taking the whole companion down with it. Polling
 * every 500ms is good enough: the command file changes a handful of
 * times per day and a 500ms latency is invisible to the user.
 *
 * Deduplication: each command carries a monotonically increasing
 * `seq`. The reader remembers the last seq it dispatched and ignores
 * anything not strictly newer.
 */
class CmdReader(
    private val target: File,
    private val sink: (Cmd) -> Unit,
) {
    private companion object {
        private const val TAG = "AuriyaCmdReader"
        private const val POLL_MS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastSeq: Long = Long.MIN_VALUE

    fun start() {
        Log.i(TAG, "polling ${target.path} every ${POLL_MS}ms")
        handler.post(pollRunnable)
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            parseAndDispatch()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun parseAndDispatch() {
        if (!target.exists()) return
        val text = try {
            target.readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "failed to read ${target.path}: ${t.message}")
            return
        }
        val cmd = CmdFormat.decode(text) ?: return
        if (cmd.seq <= lastSeq) {
            if (cmd.seq < lastSeq) {
                Log.i(TAG, "seq jumped back from $lastSeq to ${cmd.seq} — daemon restarted; accepting")
                lastSeq = cmd.seq
            }
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
