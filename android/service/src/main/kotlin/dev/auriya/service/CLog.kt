package dev.auriya.service

import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * Unified dmesg-style logger for the companion service.
 *
 * Emits log lines formatted identically to the Rust daemon logger:
 * `[   sec.micros] companion: FLAG   | message`
 * Lines printed to stdout are redirected into `companion.log` by `app_process`.
 */
object CLog {
    private const val TAG = "companion"

    @JvmStatic
    fun status(msg: String) {
        val entry = formatEntry("STATUS", msg)
        println(entry)
        Log.i(TAG, msg)
    }

    @JvmStatic
    fun code(msg: String) {
        val entry = formatEntry("CODE", msg)
        println(entry)
        Log.i(TAG, msg)
    }

    @JvmStatic
    fun info(msg: String) {
        status(msg)
    }

    @JvmStatic
    fun debug(msg: String) {
        val entry = formatEntry("DEBUG", msg)
        println(entry)
        Log.d(TAG, msg)
    }

    @JvmStatic
    fun warn(
        msg: String,
        t: Throwable? = null,
    ) {
        val fullMsg = if (t != null) "$msg: ${t.message}" else msg
        val entry = formatEntry("WARN", fullMsg)
        println(entry)
        if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
    }

    @JvmStatic
    fun error(
        msg: String,
        t: Throwable? = null,
    ) {
        val fullMsg = if (t != null) "$msg: ${t.message}" else msg
        val entry = formatEntry("FAIL", fullMsg)
        println(entry)
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    private fun formatEntry(
        flag: String,
        message: String,
    ): String {
        val uptimeSec = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0
        return String.format(Locale.US, "[%12.6f] companion: %-6s | %s", uptimeSec, flag, message)
    }
}
