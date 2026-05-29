package dev.auriya.service.sensor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Subscribes to foreground-app changes via the hidden
 * `IActivityTaskManager.registerTaskStackListener` API.
 *
 * The companion runs in the system uid (spawned by `app_process` from
 * service.sh), so the binder call is permitted without signature
 * permissions. We rely on stable internal class names — these have not
 * changed since Android 10 and are checked at build time on every
 * release the project targets.
 *
 * When the binder API is unavailable (very locked-down ROM, future
 * API rename, …) we fall back to polling
 * `ActivityManager.getRunningAppProcesses()` once a second. That's
 * still vastly cheaper than the dumpsys-shell path we replaced.
 */
class TaskStackSensor(
    private val context: Context,
    private val sink: SensorSink,
) {
    private companion object {
        private const val TAG = "AuriyaTaskStack"
        private const val FALLBACK_POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var binderRegistered = false
    private var lastEmittedPkg: String? = null

    fun start() {
        if (registerBinderListener()) {
            binderRegistered = true
            Log.i(TAG, "TaskStackListener registered via binder")
            // Emit an initial snapshot so the daemon doesn't wait for
            // the first app switch to populate focused_app.
            emitCurrentForeground()
        } else {
            Log.w(TAG, "binder API unavailable; falling back to polling")
        }
        // Always schedule the poll fallback. On Android 16 the binder
        // proxy's asBinder() may be silently discarded, leaving the
        // process deaf to task-switch events. The poll keeps us alive.
        schedulePoll()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        binderRegistered = false
    }

    // ----- binder path -----

    private fun registerBinderListener(): Boolean = try {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManager.getMethod("getService", String::class.java)
        val atmBinder = getServiceMethod.invoke(null, "activity_task") ?: return false

        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
        val atm = asInterface.invoke(null, atmBinder) ?: return false

        val listenerInterface = Class.forName("android.app.ITaskStackListener")
        val listenerProxy = Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface),
            TaskStackInvocationHandler(),
        )

        val registerMethod = atm.javaClass.methods.firstOrNull {
            it.name == "registerTaskStackListener" && it.parameterCount == 1
        } ?: return false
        registerMethod.invoke(atm, listenerProxy)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "binder registration failed: ${t.message}")
        false
    }

    // A real Binder token so registerTaskStackListener doesn't discard
    // the proxy silently when it calls listener.asBinder().
    private val binderToken = android.os.Binder()

    private inner class TaskStackInvocationHandler : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any?>?): Any? {
            // We only care about state changes — every callback in
            // ITaskStackListener implies that the foreground task may
            // have moved. Re-query and emit if it actually changed.
            when (method?.name) {
                "onTaskStackChanged",
                "onTaskMovedToFront",
                "onTaskFocusChanged",
                "onTaskCreated",
                "onTaskRemoved" -> emitCurrentForeground()
                "asBinder" -> return binderToken
                "toString" -> return "AuriyaTaskStackListener"
                "hashCode" -> return System.identityHashCode(this)
                "equals" -> return proxy === args?.firstOrNull()
                else -> Unit
            }
            return defaultReturn(method)
        }

        private fun defaultReturn(method: Method?): Any? = when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            Void.TYPE -> null
            else -> null
        }
    }

    private fun emitCurrentForeground() {
        val info = queryForegroundProcess() ?: return
        if (info.pkg == lastEmittedPkg) return
        lastEmittedPkg = info.pkg
        sink.push(
            SensorSnapshot(
                focusedApp = info.pkg,
                focusedPid = info.pid,
                focusedUid = info.uid,
            ),
        )
    }

    // ----- fallback poll -----

    private fun schedulePoll() {
        handler.postDelayed(pollRunnable, FALLBACK_POLL_MS)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            emitCurrentForeground()
            handler.postDelayed(this, FALLBACK_POLL_MS)
        }
    }

    // ----- shared query -----

    private fun queryForegroundProcess(): ForegroundInfo? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        // Prefer the modern hidden API where available.
        runCatching {
            val method = am.javaClass.getMethod("getCurrentUser")
            method.invoke(am)
        }
        // RunningAppProcesses is restricted to "your own" since Android
        // 5.x for normal apps, but the system uid can still see the
        // full list. The first entry with importance FOREGROUND wins.
        val processes = try {
            am.runningAppProcesses ?: return null
        } catch (_: SecurityException) {
            return null
        }

        val fg = processes.firstOrNull {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: return null

        val pkg = fg.pkgList?.firstOrNull() ?: fg.processName ?: return null
        return ForegroundInfo(pkg = pkg, pid = fg.pid, uid = fg.uid)
    }

    private data class ForegroundInfo(val pkg: String, val pid: Int, val uid: Int)

    // Touch Build to silence unused-import lint and document the
    // minSdk assumption — registerTaskStackListener has been the same
    // shape since API 26 but we target API 30+.
    init {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "TaskStackSensor requires Android 11+"
        }
    }
}
