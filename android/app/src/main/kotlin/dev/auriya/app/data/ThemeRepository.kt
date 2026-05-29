package dev.auriya.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "auriya_theme")

enum class NavMode { STANDARD, FLOATING }

data class ThemePrefs(
    val seedColor: Int,
    val useDynamicColor: Boolean,
    val navMode: NavMode,
)

class ThemeRepository(private val context: Context) {
    companion object {
        private val SEED = intPreferencesKey("seed_color")
        private val DYNAMIC = intPreferencesKey("use_dynamic")
        private val NAV = stringPreferencesKey("nav_mode")
        const val DEFAULT_SEED = 0xFFFFB68E.toInt()
    }

    val prefs: Flow<ThemePrefs> = context.themeDataStore.data.map { p ->
        ThemePrefs(
            seedColor = p[SEED] ?: DEFAULT_SEED,
            useDynamicColor = (p[DYNAMIC] ?: 1) == 1,
            navMode = runCatching { NavMode.valueOf(p[NAV] ?: NavMode.STANDARD.name) }
                .getOrDefault(NavMode.STANDARD),
        )
    }

    suspend fun setSeedColor(color: Int) {
        context.themeDataStore.edit { it[SEED] = color; it[DYNAMIC] = 0 }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.themeDataStore.edit { it[DYNAMIC] = if (enabled) 1 else 0 }
    }

    suspend fun setNavMode(mode: NavMode) {
        context.themeDataStore.edit { it[NAV] = mode.name }
    }
}
