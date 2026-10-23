package dev.auriya.app.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFS_NAME = "auriya_app_prefs"
private val Context.appPrefsDataStore by preferencesDataStore(
    name = PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFS_NAME))
    },
)

class AppPrefs private constructor(
    context: Context,
) {
    private val dataStore = context.appPrefsDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _roundFps = MutableStateFlow(false)
    val roundFps: StateFlow<Boolean> = _roundFps.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collectLatest { prefs ->
                _roundFps.value = prefs[KEY_ROUND_FPS] ?: false
            }
        }
    }

    fun setRoundFps(enabled: Boolean) {
        _roundFps.value = enabled
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_ROUND_FPS] = enabled
            }
        }
    }

    companion object {
        private val KEY_ROUND_FPS = booleanPreferencesKey("key_round_fps")

        @Volatile
        private var instance: AppPrefs? = null

        fun getInstance(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }

        fun formatFps(
            fps: Double?,
            roundFps: Boolean,
        ): String {
            if (fps == null || fps <= 0.0) return "--"
            return if (roundFps) {
                "${fps.roundToInt()}"
            } else {
                "%.1f".format(fps)
            }
        }

        fun formatFps(
            fps: Float?,
            roundFps: Boolean,
        ): String {
            if (fps == null || fps <= 0f) return "--"
            return if (roundFps) {
                "${fps.roundToInt()}"
            } else {
                "%.1f".format(fps)
            }
        }
    }
}
