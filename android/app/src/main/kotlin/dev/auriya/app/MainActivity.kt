package dev.auriya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.auriya.app.ui.home.PlaceholderScreen
import dev.auriya.app.ui.theme.AuriyaTheme

/**
 * Single Activity host. The placeholder screen is the only content
 * the scaffold ships; real navigation graph + screens land after the
 * UI design discussion is closed.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuriyaTheme {
                PlaceholderScreen()
            }
        }
    }
}
