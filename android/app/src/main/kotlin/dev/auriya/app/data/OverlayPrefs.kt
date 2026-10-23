package dev.auriya.app.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val PREFS_NAME = "auriya_overlay"
private val Context.overlayDataStore by preferencesDataStore(
    name = PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFS_NAME))
    },
)

class OverlayPrefs private constructor(
    context: Context,
) {
    private val dataStore = context.applicationContext.overlayDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory cache for ultra-fast sync access in Service and UI
    private val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        scope.launch {
            dataStore.data.collectLatest { _ ->
                // Keep datastore synchronized
            }
        }
    }

    var enabled: Boolean
        get() = legacyPrefs.getBoolean("enabled", false)
        set(value) {
            legacyPrefs.edit().putBoolean("enabled", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("enabled")] = value } }
        }

    var showFps: Boolean
        get() = legacyPrefs.getBoolean("show_fps", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_fps", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_fps")] = value } }
        }

    var showCpu: Boolean
        get() = legacyPrefs.getBoolean("show_cpu", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_cpu", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_cpu")] = value } }
        }

    var showGpu: Boolean
        get() = legacyPrefs.getBoolean("show_gpu", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_gpu", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_gpu")] = value } }
        }

    var showRam: Boolean
        get() = legacyPrefs.getBoolean("show_ram", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_ram", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_ram")] = value } }
        }

    var showTemp: Boolean
        get() = legacyPrefs.getBoolean("show_temp", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_temp", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_temp")] = value } }
        }

    var showBattery: Boolean
        get() = legacyPrefs.getBoolean("show_battery", true)
        set(value) {
            legacyPrefs.edit().putBoolean("show_battery", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("show_battery")] = value } }
        }

    var monetEnabled: Boolean
        get() = legacyPrefs.getBoolean("monet_enabled", true)
        set(value) {
            legacyPrefs.edit().putBoolean("monet_enabled", value).apply()
            scope.launch { dataStore.edit { it[booleanPreferencesKey("monet_enabled")] = value } }
        }

    var overlayPreset: String
        get() = legacyPrefs.getString("overlay_preset", "gaming") ?: "gaming"
        set(value) {
            legacyPrefs.edit().putString("overlay_preset", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("overlay_preset")] = value } }
        }

    var layoutStyle: String
        get() = legacyPrefs.getString("layout_style", "Horizontal") ?: "Horizontal"
        set(value) {
            legacyPrefs.edit().putString("layout_style", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("layout_style")] = value } }
        }

    var overlayMode: String
        get() = legacyPrefs.getString("overlay_mode", "Full") ?: "Full"
        set(value) {
            legacyPrefs.edit().putString("overlay_mode", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("overlay_mode")] = value } }
        }

    var cpuStyle: String
        get() = legacyPrefs.getString("cpu_style", "tags") ?: "tags"
        set(value) {
            legacyPrefs.edit().putString("cpu_style", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("cpu_style")] = value } }
        }

    var updateIntervalMs: Long
        get() = legacyPrefs.getLong("update_interval_ms", 1000L)
        set(value) {
            legacyPrefs.edit().putLong("update_interval_ms", value).apply()
            scope.launch { dataStore.edit { it[longPreferencesKey("update_interval_ms")] = value } }
        }

    var textSizeSp: Float
        get() = legacyPrefs.getFloat("text_size_sp", 12f)
        set(value) {
            legacyPrefs.edit().putFloat("text_size_sp", value).apply()
            scope.launch { dataStore.edit { it[floatPreferencesKey("text_size_sp")] = value } }
        }

    var bgOpacity: Float
        get() = legacyPrefs.getFloat("bg_opacity", 0.7f)
        set(value) {
            legacyPrefs.edit().putFloat("bg_opacity", value).apply()
            scope.launch { dataStore.edit { it[floatPreferencesKey("bg_opacity")] = value } }
        }

    var paddingDp: Float
        get() = legacyPrefs.getFloat("padding_dp", 12f)
        set(value) {
            legacyPrefs.edit().putFloat("padding_dp", value).apply()
            scope.launch { dataStore.edit { it[floatPreferencesKey("padding_dp")] = value } }
        }

    var cornerRadiusDp: Float
        get() = legacyPrefs.getFloat("corner_radius_dp", 16f)
        set(value) {
            legacyPrefs.edit().putFloat("corner_radius_dp", value).apply()
            scope.launch { dataStore.edit { it[floatPreferencesKey("corner_radius_dp")] = value } }
        }

    var customPrimary: String
        get() = legacyPrefs.getString("custom_primary", "#AAD2A4") ?: "#AAD2A4"
        set(value) {
            legacyPrefs.edit().putString("custom_primary", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("custom_primary")] = value } }
        }

    var customSecondary: String
        get() = legacyPrefs.getString("custom_secondary", "#385E38") ?: "#385E38"
        set(value) {
            legacyPrefs.edit().putString("custom_secondary", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("custom_secondary")] = value } }
        }

    var customTertiary: String
        get() = legacyPrefs.getString("custom_tertiary", "#8A9A5B") ?: "#8A9A5B"
        set(value) {
            legacyPrefs.edit().putString("custom_tertiary", value).apply()
            scope.launch { dataStore.edit { it[stringPreferencesKey("custom_tertiary")] = value } }
        }

    var overlayX: Int
        get() = legacyPrefs.getInt("overlay_x", 50)
        set(value) {
            legacyPrefs.edit().putInt("overlay_x", value).apply()
            scope.launch { dataStore.edit { it[intPreferencesKey("overlay_x")] = value } }
        }

    var overlayY: Int
        get() = legacyPrefs.getInt("overlay_y", 200)
        set(value) {
            legacyPrefs.edit().putInt("overlay_y", value).apply()
            scope.launch { dataStore.edit { it[intPreferencesKey("overlay_y")] = value } }
        }

    companion object {
        @Volatile
        private var instance: OverlayPrefs? = null

        fun getInstance(context: Context): OverlayPrefs =
            instance ?: synchronized(this) {
                instance ?: OverlayPrefs(context.applicationContext).also { instance = it }
            }
    }
}
