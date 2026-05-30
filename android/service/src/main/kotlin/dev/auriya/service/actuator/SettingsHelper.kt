package dev.auriya.service.actuator

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Wraps [Settings.System] reads/writes with a shell fallback via
 * `settings` (system binary).
 *
 * The companion runs as **system uid** via `app_process` but has **no**
 * package name.  On Android 16+ the ActivityManager checks each content
 * provider request against a registered app identity, and a nameless
 * caller gets a `SecurityException`:
 *
 *   "Unable to find app for caller … when getting content provider settings"
 *
 * The `settings` CLI avoids this because it talks to the settings
 * provider over Binder from `system` shell identity, which is always
 * granted.
 */
class SettingsHelper(context: Context) {
    private companion object {
        private const val TAG = "AuriyaSettings"
    }

    private val resolver = context.contentResolver

    fun getFloat(key: String, default: Float): Float = getFloatOrNull(key) ?: default

    fun getFloatOrNull(key: String): Float? = try {
        Settings.System.getFloat(resolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        null
    } catch (t: SecurityException) {
        Log.w(TAG, "Settings.System fallback to shell", t)
        shellGet("system", key)?.toFloatOrNull()
    }

    fun getInt(key: String, default: Int): Int = getIntOrNull(key) ?: default

    fun getIntOrNull(key: String): Int? = try {
        Settings.System.getInt(resolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        null
    } catch (t: SecurityException) {
        Log.w(TAG, "Settings.System fallback to shell", t)
        shellGet("system", key)?.toIntOrNull()
    }

    fun putFloat(key: String, value: Float) = try {
        Settings.System.putFloat(resolver, key, value)
    } catch (t: SecurityException) {
        Log.w(TAG, "Settings.System putFloat fallback to shell", t)
        shellPut("system", key, value.toString())
    }

    fun putInt(key: String, value: Int) = try {
        Settings.System.putInt(resolver, key, value)
    } catch (t: SecurityException) {
        Log.w(TAG, "Settings.System putInt fallback to shell", t)
        shellPut("system", key, value.toString())
    }

    fun remove(key: String) = try {
        Settings.System.putString(resolver, key, null)
    } catch (t: SecurityException) {
        Log.w(TAG, "Settings.System remove fallback to shell", t)
        shellPut("system", key, null)
    }

    // --- shell fallbacks (system uid, no su needed) ---

    private fun shellGet(namespace: String, key: String): String? {
        val proc = try {
            ProcessBuilder("settings", "get", namespace, key)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "shell get exec failed", e)
            return null
        }
        return BufferedReader(InputStreamReader(proc.inputStream)).use {
            it.readLine()?.trim()
        }.also {
            try { proc.waitFor() } catch (_: InterruptedException) {}
        }
    }

    private fun shellPut(namespace: String, key: String, value: String?) {
        val args = mutableListOf("settings", "put", namespace, key)
        if (value != null) args.add(value)
        try {
            val proc = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "shell put exec failed: `settings put $namespace $key $value`", e)
        }
    }
}
