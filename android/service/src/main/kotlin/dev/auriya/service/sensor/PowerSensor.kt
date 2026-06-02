package dev.auriya.service.sensor

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.auriya.service.SystemServices

class PowerSensor(
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaPower"
        private const val POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val powerManager = SystemServices.iPowerManager()

    fun start() {
        seedInitial()
        schedulePoll()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun seedInitial() {
        val awake = SystemServices.callBoolean(powerManager, "isInteractive")
        val saver = SystemServices.callBoolean(powerManager, "isPowerSaveMode")
        if (awake != null || saver != null) {
            sink.push(SensorSnapshot(screenAwake = awake, batterySaver = saver))
        }
    }

    private fun schedulePoll() {
        handler.postDelayed(pollRunnable, POLL_MS)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val awake = SystemServices.callBoolean(powerManager, "isInteractive")
            val saver = SystemServices.callBoolean(powerManager, "isPowerSaveMode")
            sink.push(SensorSnapshot(screenAwake = awake, batterySaver = saver))
            handler.postDelayed(this, POLL_MS)
        }
    }
}
