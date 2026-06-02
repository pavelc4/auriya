package dev.auriya.service.sensor

import android.app.ActivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import dev.auriya.service.SystemServices
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class TaskStackSensor(
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
            emitCurrentForeground()
        } else {
            Log.w(TAG, "binder API unavailable; falling back to polling")
        }
        schedulePoll()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        binderRegistered = false
    }

    private fun registerBinderListener(): Boolean = try {
        val atm = SystemServices.iActivityTaskManager() ?: return false

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

    private val binderToken = android.os.Binder()

    private inner class TaskStackInvocationHandler : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any?>?): Any? {
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

    private fun schedulePoll() {
        handler.postDelayed(pollRunnable, FALLBACK_POLL_MS)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            emitCurrentForeground()
            handler.postDelayed(this, FALLBACK_POLL_MS)
        }
    }

    private fun queryForegroundProcess(): ForegroundInfo? {
        val am = SystemServices.iActivityManager() ?: return null
        val processes = try {
            val method = am.javaClass.getMethod("getRunningAppProcesses")
            (method.invoke(am) as? List<*>) ?: return null
        } catch (_: Exception) {
            return null
        }

        val fg = processes.firstOrNull { p ->
            val importance = p?.javaClass?.getField("importance")?.getInt(p) ?: -1
            importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: return null

        val pkgList = fg.javaClass.getField("pkgList").get(fg) as? Array<*> ?: return null
        val pkg = (pkgList.firstOrNull() as? String)
            ?: fg.javaClass.getField("processName").get(fg) as? String
            ?: return null
        val pid = fg.javaClass.getField("pid").getInt(fg)
        val uid = fg.javaClass.getField("uid").getInt(fg)
        return ForegroundInfo(pkg = pkg, pid = pid, uid = uid)
    }

    private data class ForegroundInfo(val pkg: String, val pid: Int, val uid: Int)

    init {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "TaskStackSensor requires Android 11+"
        }
    }
}
