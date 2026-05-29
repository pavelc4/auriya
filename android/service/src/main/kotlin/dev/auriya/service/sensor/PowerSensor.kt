package dev.auriya.service.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Tracks screen on/off and battery-saver state via system broadcasts.
 *
 * `PowerManager.isInteractive` is the source of truth for "is the user
 * looking at the screen?" — it stays true through ambient-display
 * states but flips to false when the device truly sleeps. We seed the
 * cache from it on start so the very first snapshot is accurate.
 */
class PowerSensor(
    private val context: Context,
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaPower"
        private const val POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON ->
                    sink.push(SensorSnapshot(screenAwake = true))
                Intent.ACTION_SCREEN_OFF ->
                    sink.push(SensorSnapshot(screenAwake = false))
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    sink.push(SensorSnapshot(batterySaver = powerManager.isPowerSaveMode))
            }
        }
    }

    fun start() {
        // Seed initial values so the daemon never has to wait for the
        // first state change to know the answer.
        sink.push(
            SensorSnapshot(
                screenAwake = powerManager.isInteractive,
                batterySaver = powerManager.isPowerSaveMode,
            ),
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (t: Throwable) {
            Log.e(TAG, "registerReceiver failed", t)
        }
        // BroadcastReceiver delivery is not reliable in headless
        // app_process — ACTION_SCREEN_ON/OFF may never arrive.
        // Poll as a safety net so the daemon stops seeing stale
        // screen_awake values within 1 second.
        schedulePoll()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun schedulePoll() {
        handler.postDelayed(pollRunnable, POLL_MS)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val awake = powerManager.isInteractive
            val saver = powerManager.isPowerSaveMode
            sink.push(SensorSnapshot(screenAwake = awake, batterySaver = saver))
            handler.postDelayed(this, POLL_MS)
        }
    }
}
