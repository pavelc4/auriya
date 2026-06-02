package dev.auriya.service.sensor

import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.auriya.service.SystemServices

class ZenSensor(
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaZen"
        private const val POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val nm = SystemServices.iNotificationManager()
    private var lastZen: Int = Int.MIN_VALUE

    fun start() {
        handler.post(pollRunnable)
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val filter = SystemServices.callInt(nm, "getInterruptionFilter")
            if (filter != null && filter != lastZen) {
                lastZen = filter
                sink.push(SensorSnapshot(zenMode = mapFilter(filter)))
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun mapFilter(filter: Int): Int = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL,
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> 0
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> 1
        NotificationManager.INTERRUPTION_FILTER_NONE -> 2
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> 3
        else -> 0
    }
}
