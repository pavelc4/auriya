package dev.auriya.service.actuator

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import dev.auriya.shared.model.DndFilter

/**
 * Applies DnD changes through the real notification API, which
 * triggers the platform status-bar icon and respects the user's
 * priority rules. The previous implementation toggled
 * `heads_up_notifications_enabled` via `settings put`, which did
 * neither.
 *
 * The service runs as system uid (spawned by app_process from
 * service.sh), so `setInterruptionFilter` is permitted without any
 * Notification Listener grant from the user.
 */
class DnDActuator(context: Context) {
    private companion object {
        private const val TAG = "AuriyaDnD"
    }

    private val nm: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Apply [filter]. The wire value maps 1:1 to
     * [NotificationManager.INTERRUPTION_FILTER_*]:
     *
     *   ALL      → INTERRUPTION_FILTER_ALL       (no DnD)
     *   PRIORITY → INTERRUPTION_FILTER_PRIORITY  (most common gaming)
     *   NONE     → INTERRUPTION_FILTER_NONE      (total silence)
     *   ALARMS   → INTERRUPTION_FILTER_ALARMS
     */
    fun apply(filter: DndFilter) {
        val target = when (filter) {
            DndFilter.ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
            DndFilter.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            DndFilter.NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
            DndFilter.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        }
        try {
            if (nm.currentInterruptionFilter == target) {
                Log.d(TAG, "filter already $filter; skipping")
                return
            }
            nm.setInterruptionFilter(target)
            Log.i(TAG, "set interruption filter to $filter")
        } catch (t: Throwable) {
            Log.e(TAG, "setInterruptionFilter($filter) failed", t)
        }
    }
}
