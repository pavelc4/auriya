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
 * `refresh_rate = 0` is the "restore" signal: clear both knobs so the
 * platform falls back to whatever the user picked in Display settings.
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

    /**
     * Apply [rateHz]. Pass `0` to restore the system default.
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
                Settings.System.putString(resolver, KEY_PEAK, null)
                Settings.System.putString(resolver, KEY_MIN, null)
                Log.i(TAG, "restored system default refresh rate")
            } else {
                Settings.System.putFloat(resolver, KEY_PEAK, rateHz.toFloat())
                Settings.System.putFloat(resolver, KEY_MIN, rateHz.toFloat())
                Log.i(TAG, "set refresh rate to ${rateHz}Hz (min+peak)")
            }
            lastApplied = rateHz
        } catch (t: Throwable) {
            Log.e(TAG, "failed to apply refresh rate $rateHz", t)
        }
    }
}
