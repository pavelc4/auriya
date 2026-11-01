package dev.auriya.app.data.stats

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val PREFS_NAME = "auriya_auto_record"
private val Context.autoRecordDataStore by preferencesDataStore(
    name = PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFS_NAME))
    },
)

class AutoRecordPrefs private constructor(
    context: Context,
) {
    private val dataStore = context.applicationContext.autoRecordDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabledPackages = MutableStateFlow<Set<String>>(emptySet())
    val enabledPackages: StateFlow<Set<String>> = _enabledPackages.asStateFlow()

    init {
        val initial = mutableSetOf<String>()
        sharedPrefs.all.forEach { (k, v) ->
            if (k.startsWith("auto_rec_") && v is Boolean && v) {
                initial.add(k.removePrefix("auto_rec_"))
            }
        }
        _enabledPackages.value = initial

        scope.launch {
            dataStore.data.collectLatest { prefs ->
                val fromDataStore = mutableSetOf<String>()
                prefs.asMap().forEach { (k, v) ->
                    if (k.name.startsWith("auto_rec_") && v is Boolean && v) {
                        fromDataStore.add(k.name.removePrefix("auto_rec_"))
                    }
                }
                _enabledPackages.value = fromDataStore
                sharedPrefs.edit().apply {
                    clear()
                    fromDataStore.forEach { pkg ->
                        putBoolean("auto_rec_$pkg", true)
                    }
                    apply()
                }
            }
        }
    }

    fun isAutoRecordEnabled(packageName: String): Boolean =
        when (_enabledPackages.value.contains(packageName)) {
            true -> true
            false -> false
        }

    fun observeAutoRecord(packageName: String): Flow<Boolean> =
        _enabledPackages
            .map { set ->
                when (set.contains(packageName)) {
                    true -> true
                    false -> false
                }
            }
            .distinctUntilChanged()

    fun setAutoRecordEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        val current = _enabledPackages.value.toMutableSet()
        when (enabled) {
            true -> current.add(packageName)
            false -> current.remove(packageName)
        }
        _enabledPackages.value = current

        sharedPrefs.edit().apply {
            when (enabled) {
                true -> putBoolean("auto_rec_$packageName", true)
                false -> remove("auto_rec_$packageName")
            }
            apply()
        }

        scope.launch {
            dataStore.edit { prefs ->
                val key = booleanPreferencesKey("auto_rec_$packageName")
                when (enabled) {
                    true -> prefs[key] = true
                    false -> prefs.remove(key)
                }
            }
        }
    }

    fun getAllAutoRecordPackages(): Set<String> = _enabledPackages.value

    fun hasAnyAutoRecordEnabled(): Boolean =
        when (_enabledPackages.value.isNotEmpty()) {
            true -> true
            false -> false
        }

    companion object {
        @Volatile
        private var instance: AutoRecordPrefs? = null

        fun getInstance(context: Context): AutoRecordPrefs =
            instance ?: synchronized(this) {
                instance ?: AutoRecordPrefs(context.applicationContext).also { instance = it }
            }
    }
}
