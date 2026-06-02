package dev.auriya.service

import android.os.IBinder
import android.util.Log

object SystemServices {
    private const val TAG = "AuriyaSysSvc"

    private val getService by lazy {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getMethod("getService", String::class.java)
    }

    private fun rawService(name: String): IBinder? =
        try {
            getService.invoke(null, name) as? IBinder
        } catch (t: Throwable) {
            Log.w(TAG, "ServiceManager.getService($name) failed", t)
            null
        }

    fun iPowerManager(): Any? {
        val binder = rawService("power") ?: return null
        return try {
            val cl = Class.forName("android.os.IPowerManager\$Stub")
            cl.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            Log.w(TAG, "IPowerManager.Stub.asInterface failed", t)
            null
        }
    }

    fun iNotificationManager(): Any? {
        val binder = rawService("notification") ?: return null
        return try {
            val cl = Class.forName("android.app.INotificationManager\$Stub")
            cl.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            Log.w(TAG, "INotificationManager.Stub.asInterface failed", t)
            null
        }
    }

    fun iActivityManager(): Any? {
        val binder = rawService("activity") ?: return null
        return try {
            val cl = Class.forName("android.app.IActivityManager\$Stub")
            cl.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            Log.w(TAG, "IActivityManager.Stub.asInterface failed", t)
            null
        }
    }

    fun iActivityTaskManager(): Any? {
        val binder = rawService("activity_task") ?: return null
        return try {
            val cl = Class.forName("android.app.IActivityTaskManager\$Stub")
            cl.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            Log.w(TAG, "IActivityTaskManager.Stub.asInterface failed", t)
            null
        }
    }

    fun callBoolean(
        service: Any?,
        method: String,
    ): Boolean? =
        try {
            service?.javaClass?.getMethod(method)?.invoke(service) as? Boolean
        } catch (t: Throwable) {
            Log.w(TAG, "$method failed", t)
            null
        }

    fun callInt(
        service: Any?,
        method: String,
    ): Int? =
        try {
            service?.javaClass?.getMethod(method)?.invoke(service) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "$method failed", t)
            null
        }

    fun callInt(
        service: Any?,
        method: String,
        vararg args: Any?,
    ): Int? =
        try {
            val types = args.map { unbox(it?.javaClass ?: Any::class.java) }.toTypedArray()
            service?.javaClass?.getMethod(method, *types)?.invoke(service, *args) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "$method failed", t)
            null
        }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun unbox(c: Class<*>): Class<*> =
        when (c) {
            java.lang.Integer::class.java -> Int::class.javaPrimitiveType!!
            java.lang.Boolean::class.java -> Boolean::class.javaPrimitiveType!!
            java.lang.Long::class.java -> Long::class.javaPrimitiveType!!
            java.lang.Float::class.java -> Float::class.javaPrimitiveType!!
            java.lang.Double::class.java -> Double::class.javaPrimitiveType!!
            java.lang.Short::class.java -> Short::class.javaPrimitiveType!!
            java.lang.Byte::class.java -> Byte::class.javaPrimitiveType!!
            java.lang.Character::class.java -> Char::class.javaPrimitiveType!!
            else -> c
        }

    fun callVoid(
        service: Any?,
        method: String,
        vararg args: Any?,
    ) {
        try {
            val types = args.map { unbox(it?.javaClass ?: Any::class.java) }.toTypedArray()
            service?.javaClass?.getMethod(method, *types)?.invoke(service, *args)
        } catch (t: Throwable) {
            Log.w(TAG, "$method failed", t)
        }
    }
}
