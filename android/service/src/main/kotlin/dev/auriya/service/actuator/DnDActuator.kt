package dev.auriya.service.actuator

import android.app.NotificationManager
import android.util.Log
import dev.auriya.service.SystemServices
import dev.auriya.shared.model.DndFilter

class DnDActuator {
    private companion object {
        private const val TAG = "AuriyaDnD"
    }

    private val nm = SystemServices.iNotificationManager()

    fun apply(filter: DndFilter) {
        val target = when (filter) {
            DndFilter.ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
            DndFilter.PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            DndFilter.NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
            DndFilter.ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        }
        try {
            val current = SystemServices.callInt(nm, "getInterruptionFilter")
            if (current == target) return
            SystemServices.callVoid(nm, "setInterruptionFilter", target)
            Log.i(TAG, "set interruption filter to $filter")
        } catch (t: Throwable) {
            Log.e(TAG, "setInterruptionFilter($filter) failed", t)
        }
    }
}
