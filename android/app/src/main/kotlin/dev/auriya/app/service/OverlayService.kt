package dev.auriya.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.auriya.app.data.RootShell
import dev.auriya.app.ui.theme.AuriyaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var wm: WindowManager
    private var overlayView: ComposeView? = null
    private var pollingJob: Job? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var fpsText by mutableStateOf("--")
    private var tempText by mutableStateOf("--°C")
    private var showFps by mutableStateOf(true)
    private var showTemp by mutableStateOf(true)
    private var overlaySize by mutableStateOf("Medium")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        intent?.let {
            showFps = it.getBooleanExtra("show_fps", true)
            showTemp = it.getBooleanExtra("show_temp", true)
            overlaySize = it.getStringExtra("size") ?: "Medium"
        }
        if (overlayView == null) {
            createOverlay()
        }
        pollingJob?.cancel()
        startPolling()
        return START_STICKY
    }

    private fun createOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                AuriyaTheme(prefs = null) {
                    OverlayChip(
                        fps = fpsText,
                        temp = tempText,
                        showFps = showFps,
                        showTemp = showTemp,
                        size = overlaySize,
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        wm.addView(overlayView, params)
    }

    companion object {
        @Composable
        fun OverlayChip(
            fps: String,
            temp: String,
            showFps: Boolean = true,
            showTemp: Boolean = true,
            size: String = "Medium",
        ) {
            val textSize = when (size) {
                "Small" -> 10.sp
                "Large" -> 16.sp
                else -> 13.sp
            }
            val subTextSize = when (size) {
                "Small" -> 9.sp
                "Large" -> 13.sp
                else -> 11.sp
            }
            val hPad = when (size) {
                "Small" -> 10.dp
                "Large" -> 20.dp
                else -> 16.dp
            }
            val vPad = when (size) {
                "Small" -> 5.dp
                "Large" -> 10.dp
                else -> 8.dp
            }

            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = hPad, vertical = vPad),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showFps) {
                        Text(
                            text = "${fps}FPS",
                            fontSize = textSize,
                            color = Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    if (showTemp) {
                        Text(
                            text = temp,
                            fontSize = subTextSize,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            while (isActive) {
                val fps = queryFps()
                val temp = queryTemp()
                withContext(Dispatchers.Main) {
                    fpsText = fps
                    tempText = temp
                }
                delay(1000L)
            }
        }
    }

    private fun queryFps(): String {
        val out = RootShell.run("printf 'GET_FPS\nQUIT\n' | timeout 3 nc -U /dev/socket/auriya.sock 2>/dev/null")
        val fpsLine = out.lines().find { it.startsWith("FPS=") } ?: return "-"
        val fps = fpsLine.split(" ").firstOrNull()?.removePrefix("FPS=")?.toFloatOrNull() ?: return "-"
        if (fps <= 0f) return "-"
        return "%.1f".format(fps)
    }

    private fun queryTemp(): String {
        val out = RootShell.run("printf 'STATUS\nQUIT\n' | timeout 3 nc -U /dev/socket/auriya.sock 2>/dev/null")
        val tempLine = out.lines().find { it.startsWith("TEMP_CPU=") } ?: return "--°C"
        val raw = tempLine.removePrefix("TEMP_CPU=").substringBefore(" ").toFloatOrNull()
        return if (raw != null) "%.0f°C".format(raw) else "--°C"
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        pollingJob?.cancel()
        overlayView?.let { wm.removeView(it) }
        super.onDestroy()
    }
}
