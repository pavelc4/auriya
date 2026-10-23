package dev.auriya.app.data.stats

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "auriya_auto_record"
private val Context.autoRecordDataStore by preferencesDataStore(
    name = PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFS_NAME))
    },
)

class AutoRecordPrefs(
    context: Context,
) {
    private val dataStore = context.applicationContext.autoRecordDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val memoryCache = ConcurrentHashMap<String, Boolean>()

    init {
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        legacyPrefs.all.forEach { (k, v) ->
            if (k.startsWith("auto_rec_") && v is Boolean) {
                memoryCache[k.removePrefix("auto_rec_")] = v
            }
        }
        scope.launch {
            dataStore.data.collectLatest { prefs ->
                memoryCache.clear()
                prefs.asMap().forEach { (k, v) ->
                    if (k.name.startsWith("auto_rec_") && v is Boolean) {
                        memoryCache[k.name.removePrefix("auto_rec_")] = v
                    }
                }
            }
        }
    }

    fun isAutoRecordEnabled(packageName: String): Boolean = memoryCache[packageName] ?: false

    fun setAutoRecordEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        memoryCache[packageName] = enabled
        scope.launch {
            dataStore.edit { prefs ->
                val key = booleanPreferencesKey("auto_rec_$packageName")
                if (enabled) {
                    prefs[key] = true
                } else {
                    prefs.remove(key)
                }
            }
        }
    }

    fun getAllAutoRecordPackages(): Set<String> = memoryCache.filterValues { it }.keys.toSet()

    fun hasAnyAutoRecordEnabled(): Boolean = getAllAutoRecordPackages().isNotEmpty()
}
