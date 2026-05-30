package dev.auriya.service.actuator

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Drives the device's preferred refresh rate via Settings.System.
 *
 * Two values are written together so framework code (SurfaceFlinger,
 * DisplayManagerService) actually honours the request without us having
 * to attach a window:
 *
 *   - `min_refresh_rate`  → the floor (prevents idle drop)
 *   - `peak_refresh_rate` → the cap (forces the desired top mode)
 *
 * `refresh_rate = 0` is the "restore" signal. We don't just clear the
 * keys — that would lose the user's Display Settings preference. The
 * first time we set a non-zero override we snapshot the current values
 * and restore them on the next zero, falling back to clearing the keys
 * if we never saw a real value to snapshot (e.g. fresh device, OEM
 * defaults missing).
 *
 * The companion runs as system uid, so write permission to Settings.System
 * is granted automatically — no WRITE_SETTINGS or signature grant
 * required.
 */
class DisplayActuator(context: Context) {
    private companion object {
        private const val TAG = "AuriyaDisplay"
        private const val KEY_PEAK = "peak_refresh_rate"
        private const val KEY_MIN = "min_refresh_rate"
    }

    private val resolver = context.contentResolver
    private var lastApplied: Int? = null
    private var savedMin: Float? = null
    private var savedPeak: Float? = null
    private var snapshotTaken = false

    /**
     * Apply [rateHz]. Pass `0` to restore the user's pre-override rate.
     * No-ops when the requested rate equals the last one we wrote.
     */
    fun apply(rateHz: Int) {
        if (rateHz < 0) {
            Log.w(TAG, "ignoring negative refresh rate $rateHz")
            return
        }
        if (rateHz == lastApplied) return
        try {
            if (rateHz == 0) {
                restoreSnapshot()
            } else {
                captureSnapshotIfNeeded()
                Settings.System.putFloat(resolver, KEY_PEAK, rateHz.toFloat())
                Settings.System.putFloat(resolver, KEY_MIN, rateHz.toFloat())
                Log.i(TAG, "set refresh rate to ${rateHz}Hz (min+peak)")
            }
            lastApplied = rateHz
        } catch (t: Throwable) {
            Log.e(TAG, "failed to apply refresh rate $rateHz", t)
        }
    }

    private fun captureSnapshotIfNeeded() {
        if (snapshotTaken) return
        savedMin = readFloatOrNull(KEY_MIN)
        savedPeak = readFloatOrNull(KEY_PEAK)
        snapshotTaken = true
        Log.i(TAG, "captured original rates: min=$savedMin peak=$savedPeak")
    }

    private fun restoreSnapshot() {
        val min = savedMin
        val peak = savedPeak
        if (min != null) {
            Settings.System.putFloat(resolver, KEY_MIN, min)
        } else {
            Settings.System.putString(resolver, KEY_MIN, null)
        }
        if (peak != null) {
            Settings.System.putFloat(resolver, KEY_PEAK, peak)
        } else {
            Settings.System.putString(resolver, KEY_PEAK, null)
        }
        Log.i(TAG, "restored refresh rate (min=$min peak=$peak)")
    }

    private fun readFloatOrNull(key: String): Float? = try {
        Settings.System.getFloat(resolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        null
    }
}
