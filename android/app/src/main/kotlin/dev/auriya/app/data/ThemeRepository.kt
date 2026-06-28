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
enum class NavType { LEGACY, MODERN }
enum class DarkThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

data class ThemePrefs(
    val seedColor: Int,
    val useDynamicColor: Boolean,
    val navMode: NavMode,
    val navType: NavType,
    val cornerRadius: Int,
    val darkThemeMode: DarkThemeMode,
    val isAmoled: Boolean,
)

class ThemeRepository(private val context: Context) {
    companion object {
        private val SEED = intPreferencesKey("seed_color")
        private val DYNAMIC = intPreferencesKey("use_dynamic")
        private val NAV = stringPreferencesKey("nav_mode")
        private val NAV_TYPE = stringPreferencesKey("nav_type")
        private val CORNER_RADIUS = intPreferencesKey("corner_radius")
        private val DARK_MODE = stringPreferencesKey("dark_theme_mode")
        private val AMOLED = intPreferencesKey("is_amoled")
        const val DEFAULT_SEED = 0xFFFFB68E.toInt()
    }

    val prefs: Flow<ThemePrefs> = context.themeDataStore.data.map { p ->
        ThemePrefs(
            seedColor = p[SEED] ?: DEFAULT_SEED,
            useDynamicColor = (p[DYNAMIC] ?: 1) == 1,
            navMode = runCatching { NavMode.valueOf(p[NAV] ?: NavMode.STANDARD.name) }
                .getOrDefault(NavMode.STANDARD),
            navType = runCatching { NavType.valueOf(p[NAV_TYPE] ?: NavType.LEGACY.name) }
                .getOrDefault(NavType.LEGACY),
            cornerRadius = p[CORNER_RADIUS] ?: 24,
            darkThemeMode = runCatching { DarkThemeMode.valueOf(p[DARK_MODE] ?: DarkThemeMode.FOLLOW_SYSTEM.name) }
                .getOrDefault(DarkThemeMode.FOLLOW_SYSTEM),
            isAmoled = (p[AMOLED] ?: 0) == 1,
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

    suspend fun setNavType(type: NavType) {
        context.themeDataStore.edit { it[NAV_TYPE] = type.name }
    }

    suspend fun setCornerRadius(radius: Int) {
        context.themeDataStore.edit { it[CORNER_RADIUS] = radius }
    }

    suspend fun setDarkThemeMode(mode: DarkThemeMode) {
        context.themeDataStore.edit { it[DARK_MODE] = mode.name }
    }

    suspend fun setAmoled(enabled: Boolean) {
        context.themeDataStore.edit { it[AMOLED] = if (enabled) 1 else 0 }
    }
}
