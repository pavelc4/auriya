package dev.auriya.service.actuator

import android.util.Log
import dev.auriya.service.SystemServices
import dev.auriya.shared.model.DndFilter

class DnDActuator {
    private companion object {
        private const val TAG = "AuriyaDnD"
        private const val CALLING_PKG = "dev.auriya.service"
    }

    private val nm = SystemServices.iNotificationManager()

    fun apply(filter: DndFilter) {
        val target =
            when (filter) {
                DndFilter.ALL -> 1
                DndFilter.PRIORITY -> 2
                DndFilter.NONE -> 3
                DndFilter.ALARMS -> 4
            }
        try {
            SystemServices.callVoid(nm, "setInterruptionFilter", CALLING_PKG, target, false)
            Log.i(TAG, "set interruption filter to $filter")
        } catch (t: Throwable) {
            Log.e(TAG, "setInterruptionFilter($filter) failed", t)
        }
    }
}
