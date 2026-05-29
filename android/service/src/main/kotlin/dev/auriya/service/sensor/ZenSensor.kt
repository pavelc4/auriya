package dev.auriya.service.sensor

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Polls DnD / zen mode at a coarse interval.
 *
 * Unlike screen state, the platform does not broadcast a stable action
 * we can subscribe to for `setInterruptionFilter` changes (the system
 * sends `ACTION_INTERRUPTION_FILTER_CHANGED` only to notification
 * listeners, which is a much heavier permission than we need). A
 * one-second poll is plenty for what the daemon uses this signal for:
 * deciding whether to enable DnD when a game starts.
 */
class ZenSensor(
    private val context: Context,
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaZen"
        private const val POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val nm: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastZen: Int = Int.MIN_VALUE

    fun start() {
        handler.post(pollRunnable)
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val filter = nm.currentInterruptionFilter
                if (filter != lastZen) {
                    lastZen = filter
                    sink.push(SensorSnapshot(zenMode = mapFilter(filter)))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "currentInterruptionFilter failed: ${t.message}")
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    /**
     * Map NotificationManager interruption filter constants to the
     * daemon's zen_mode value:
     *   0 = INTERRUPTION_FILTER_ALL (no DnD)
     *   1 = PRIORITY
     *   2 = NONE (total silence)
     *   3 = ALARMS
     */
    private fun mapFilter(filter: Int): Int = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL,
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> 0
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> 1
        NotificationManager.INTERRUPTION_FILTER_NONE -> 2
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> 3
        else -> 0
    }
}
