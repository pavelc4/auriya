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
            }
        val zenVal = if (filter == DndFilter.PRIORITY) "1" else "0"
        val dndArg = if (filter == DndFilter.PRIORITY) "priority" else "all"

        try {
            SystemServices.callVoid(nm, "setInterruptionFilter", "android", target, false)
            Log.i(TAG, "set interruption filter to $filter")
        } catch (_: Throwable) {
            try {
                SystemServices.callVoid(nm, "setInterruptionFilter", CALLING_PKG, target, false)
            } catch (_: Throwable) {}
        }

        // Always ensure system command fallback for absolute reliability
        try {
            Runtime.getRuntime().exec(arrayOf("cmd", "notification", "set_dnd", dndArg)).waitFor()
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "zen_mode", zenVal)).waitFor()
        } catch (t: Throwable) {
            Log.e(TAG, "exec fallback failed", t)
        }
    }
}
