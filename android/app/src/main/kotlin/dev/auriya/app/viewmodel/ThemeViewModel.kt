package dev.auriya.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.data.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ThemeRepository(app.applicationContext)

    val prefs: StateFlow<ThemePrefs?> = repo.prefs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setSeedColor(color: Int) = viewModelScope.launch { repo.setSeedColor(color) }
    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setUseDynamicColor(enabled) }
    fun setNavMode(mode: NavMode) = viewModelScope.launch { repo.setNavMode(mode) }
}
