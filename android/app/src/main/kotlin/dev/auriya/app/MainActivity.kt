package dev.auriya.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auriya.app.ui.navigation.AuriyaNavigation
import dev.auriya.app.ui.theme.AuriyaTheme
import dev.auriya.app.util.LocaleHelper
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: UiViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    // Observable configuration state to trigger instant Compose recomposition on system QS toggle
    private var configurationState by mutableStateOf(Configuration())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationState = Configuration(newConfig)
        LocaleHelper.applySavedLocale(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleHelper.applySavedLocale(this)
        enableEdgeToEdge()
        configurationState = Configuration(resources.configuration)
        viewModel.loadInstalledApps(packageManager)
        setContent {
            val config = configurationState
            val prefs by themeViewModel.prefs.collectAsStateWithLifecycle()
            val currentLanguage by LocaleHelper.currentLanguage.collectAsStateWithLifecycle()
            val baseContext = LocalContext.current
            val localizedContext =
                remember(baseContext, currentLanguage, config) {
                    LocaleHelper.wrapContext(baseContext, currentLanguage)
                }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides config,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity,
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides this@MainActivity,
            ) {
                AuriyaTheme(prefs = prefs) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        AuriyaNavigation(
                            viewModel = viewModel,
                            themeViewModel = themeViewModel,
                        )
                    }
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
