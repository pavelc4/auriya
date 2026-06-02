package dev.auriya.service.actuator

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsHelper {
    private companion object {
        private const val TAG = "AuriyaSettings"
    }

    fun getFloat(key: String, default: Float): Float = getFloatOrNull(key) ?: default

    fun getFloatOrNull(key: String): Float? = shellGet("system", key)?.toFloatOrNull()

    fun getInt(key: String, default: Int): Int = getIntOrNull(key) ?: default

    fun getIntOrNull(key: String): Int? = shellGet("system", key)?.toIntOrNull()

    fun putFloat(key: String, value: Float) = shellPut("system", key, value.toString())

    fun putInt(key: String, value: Int) = shellPut("system", key, value.toString())

    fun remove(key: String) = shellDelete("system", key)

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

    private fun shellDelete(namespace: String, key: String) {
        try {
            val proc = ProcessBuilder("settings", "delete", namespace, key)
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "shell delete exec failed: `settings delete $namespace $key`", e)
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
