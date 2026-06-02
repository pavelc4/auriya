package dev.auriya.service.sensor

import android.util.Log
import dev.auriya.service.actuator.SettingsHelper

class ZenSensor(
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaZen"
        private const val POLL_MS = 1000L
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val settings = SettingsHelper()
    private var lastZen: Int = Int.MIN_VALUE

    fun start() {
        handler.post(pollRunnable)
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable =
        object : Runnable {
            override fun run() {
                val zen = settings.getIntOrNull("zen_mode") ?: 0
                if (zen != lastZen) {
                    lastZen = zen
                    sink.push(SensorSnapshot(zenMode = zen))
                }
                handler.postDelayed(this, POLL_MS)
            }
        }
}
