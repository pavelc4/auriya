package dev.auriya.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.auriya.app.ui.navigation.AuriyaNavigation
import dev.auriya.app.ui.theme.AuriyaTheme
import dev.auriya.app.util.LocaleHelper
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: UiViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.applySavedLocale(this)
        enableEdgeToEdge()
        viewModel.loadInstalledApps(packageManager)
        setContent {
            val prefs by themeViewModel.prefs.collectAsState()
            val currentLanguage by LocaleHelper.currentLanguage.collectAsState()
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, currentLanguage) { LocaleHelper.wrapContext(baseContext, currentLanguage) }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity,
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides this@MainActivity,
            ) {
                AuriyaTheme(prefs = prefs) {
                    AuriyaNavigation(
                        viewModel = viewModel,
                        themeViewModel = themeViewModel,
                    )
                }
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
