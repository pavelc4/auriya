package dev.auriya.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class AppPrefs private constructor(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auriya_app_prefs", Context.MODE_PRIVATE)

    private val _roundFps = MutableStateFlow(prefs.getBoolean(KEY_ROUND_FPS, false))
    val roundFps: StateFlow<Boolean> = _roundFps.asStateFlow()

    fun setRoundFps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ROUND_FPS, enabled).apply()
        _roundFps.value = enabled
    }

    companion object {
        private const val KEY_ROUND_FPS = "key_round_fps"

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
