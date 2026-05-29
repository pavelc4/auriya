package dev.auriya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.auriya.app.ui.navigation.AuriyaNavigation
import dev.auriya.app.ui.theme.AuriyaTheme
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: UiViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by themeViewModel.prefs.collectAsState()
            AuriyaTheme(prefs = prefs) {
                AuriyaNavigation(
                    viewModel = viewModel,
                    themeViewModel = themeViewModel,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setActive(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setActive(false)
    }
}
