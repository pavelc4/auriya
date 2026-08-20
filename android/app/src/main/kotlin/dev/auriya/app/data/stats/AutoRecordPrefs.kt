package dev.auriya.app.data.stats

import android.content.Context
import android.content.SharedPreferences

class AutoRecordPrefs(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auriya_auto_record", Context.MODE_PRIVATE)

    fun isAutoRecordEnabled(packageName: String): Boolean = prefs.getBoolean("auto_rec_$packageName", false)

    fun setAutoRecordEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        prefs.edit().putBoolean("auto_rec_$packageName", enabled).apply()
    }

    fun getAllAutoRecordPackages(): Set<String> =
        prefs.all.keys
            .filter { it.startsWith("auto_rec_") && prefs.getBoolean(it, false) }
            .map { it.removePrefix("auto_rec_") }
            .toSet()

    fun hasAnyAutoRecordEnabled(): Boolean = getAllAutoRecordPackages().isNotEmpty()
}
