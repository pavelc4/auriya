package dev.auriya.service.actuator

import android.util.Log

/**
 * Toggles the device's accelerometer-based rotation via Settings.System.
 *
 * Android maps the user's auto-rotate preference to a single integer:
 *   - `ACCELEROMETER_ROTATION = 1` → orientation follows the sensor.
 *   - `ACCELEROMETER_ROTATION = 0` → orientation is pinned ("locked").
 *
 * The daemon's `lock_rotation` boolean maps to that knob:
 *   - `true`  → lock (write 0).
 *   - `false` → release, restoring whatever the user had set before
 *               the first lock; falls back to 1 (the platform default)
 *               if no snapshot has been captured yet.
 *
 * The companion runs as system uid, so the Settings.System write
 * doesn't require WRITE_SETTINGS or a signature grant.
 */
class RotationActuator(private val settings: SettingsHelper) {
    private companion object {
        private const val TAG = "AuriyaRotation"
        private const val KEY = "accelerometer_rotation"
    }

    private var lastApplied: Boolean? = null
    private var savedValue: Int? = null
    private var snapshotTaken = false

    /**
     * Apply [lock]. No-ops when the requested state matches the last
     * value we wrote, so repeated re-ticks don't churn Settings.System.
     */
    fun apply(lock: Boolean) {
        if (lastApplied == lock) return
        try {
            if (lock) {
                captureSnapshotIfNeeded()
                settings.putInt(KEY, 0)
                Log.i(TAG, "locked rotation (auto-rotate=0)")
            } else {
                restoreSnapshot()
            }
            lastApplied = lock
        } catch (t: Throwable) {
            Log.e(TAG, "failed to apply lock_rotation=$lock", t)
        }
    }

    private fun captureSnapshotIfNeeded() {
        if (snapshotTaken) return
        savedValue = settings.getIntOrNull(KEY)
        snapshotTaken = true
        Log.i(TAG, "captured original ACCELEROMETER_ROTATION=$savedValue")
    }

    private fun restoreSnapshot() {
        val restore = savedValue ?: 1
        settings.putInt(KEY, restore)
        Log.i(TAG, "released rotation lock (restored auto-rotate=$restore)")
    }
}
