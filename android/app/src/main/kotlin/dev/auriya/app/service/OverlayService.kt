package dev.auriya.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

    data class TelemetryData(
        val fps: String = "--",
        val cpuClusters: List<String> = emptyList(),
        val gpuFreq: String = "--",
        val gpuLoad: String = "--",
        val cpuTemp: String = "--",
        val batTemp: String = "--",
        val rawFps: Float = 0f,
        val rawCpuTemp: Float = 0f,
        val rawBatTemp: Float = 0f
    )

    private val telemetryState = mutableStateOf(TelemetryData())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
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
                val prefs = remember { getSharedPreferences("auriya_overlay", MODE_PRIVATE) }
                AuriyaTheme(prefs = null) {
                    val showFps = prefs.getBoolean("show_fps", true)
                    val showCpu = prefs.getBoolean("show_cpu", true)
                    val showGpu = prefs.getBoolean("show_gpu", true)
                    val showTemp = prefs.getBoolean("show_temp", true)
                    val showBattery = prefs.getBoolean("show_battery", true)
                    val monetEnabled = prefs.getBoolean("monet_enabled", true)
                    val textSizeSp = prefs.getFloat("text_size_sp", 12f)
                    val bgOpacity = prefs.getFloat("bg_opacity", 0.7f)
                    val paddingDp = prefs.getFloat("padding_dp", 12f)
                    val cornerRadiusDp = prefs.getFloat("corner_radius_dp", 16f)
                    val layoutStyle = prefs.getString("layout_style", "Horizontal") ?: "Horizontal"

                    OverlayChip(
                        data = telemetryState.value,
                        showFps = showFps,
                        showCpu = showCpu,
                        showGpu = showGpu,
                        showTemp = showTemp,
                        showBattery = showBattery,
                        monetEnabled = monetEnabled,
                        textSizeSp = textSizeSp,
                        bgOpacity = bgOpacity,
                        paddingDp = paddingDp,
                        cornerRadiusDp = cornerRadiusDp,
                        layoutStyle = layoutStyle
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

    private fun startPolling() {
        val prefs = getSharedPreferences("auriya_overlay", MODE_PRIVATE)
        val interval = prefs.getLong("update_interval_ms", 1000L)
        pollingJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            while (isActive) {
                val data = queryTelemetry()
                withContext(Dispatchers.Main) {
                    telemetryState.value = data
                }
                delay(interval)
            }
        }
    }

    private fun queryTelemetry(): TelemetryData {
        // 1. Query FPS
        var fpsVal = "--"
        var rawFpsNum = 0f
        runCatching {
            val out = RootShell.run("printf 'GET_FPS\nQUIT\n' | timeout 2 nc -U /dev/socket/auriya.sock 2>/dev/null")
            val fpsLine = out.lines().find { it.startsWith("FPS=") }
            if (fpsLine != null) {
                val num = fpsLine.split(" ").firstOrNull()?.removePrefix("FPS=")?.toFloatOrNull() ?: 0f
                if (num > 0f) {
                    fpsVal = "%.1f".format(num)
                    rawFpsNum = num
                }
            }
        }

        // 2. Query Status
        var cpuClusters = mutableListOf<String>()
        var gpuFreqVal = "--"
        var gpuLoadVal = "--"
        var cpuTempVal = "--"
        var rawCpuTempNum = 0f

        runCatching {
            val out = RootShell.run("printf 'STATUS\nQUIT\n' | timeout 2 nc -U /dev/socket/auriya.sock 2>/dev/null")
            val lines = out.lines()

            // CPU clusters
            val clustersMap = mutableMapOf<Int, MutableList<Long>>()
            lines.forEach { line ->
                if (line.contains("CORE_") && line.contains("freq=") && line.contains("cluster=")) {
                    val tokens = line.split(" ")
                    val freq = tokens.find { it.startsWith("freq=") }?.removePrefix("freq=")?.toLongOrNull()
                    val clusterStr = tokens.find { it.startsWith("cluster=") }?.removePrefix("cluster=")
                    val cluster = clusterStr?.removeSurrounding("[", "]")?.removeSurrounding("Some(", ")")?.toIntOrNull()
                    if (freq != null && cluster != null) {
                        clustersMap.getOrPut(cluster) { mutableListOf() }.add(freq)
                    }
                }
            }
            // Sort by cluster id and get average freq in GHz
            clustersMap.keys.sorted().forEach { cId ->
                val freqs = clustersMap[cId]!!
                val avgFreqKHz = freqs.average()
                val freqGHz = avgFreqKHz / 1_000_000.0
                cpuClusters.add("%.1fG".format(freqGHz))
            }

            // GPU
            val gpuLine = lines.find { it.contains("GPU_FREQ=") }
            if (gpuLine != null) {
                val tokens = gpuLine.split(" ")
                val freq = tokens.find { it.startsWith("GPU_FREQ=") }?.removePrefix("GPU_FREQ=")?.toIntOrNull()
                val load = tokens.find { it.startsWith("GPU_LOAD=") }?.removePrefix("GPU_LOAD=")?.toIntOrNull()
                if (freq != null) gpuFreqVal = "${freq}M"
                if (load != null) gpuLoadVal = "$load%"
            }

            // CPU Temp
            val tempLine = lines.find { it.contains("TEMP_CPU=") }
            if (tempLine != null) {
                val tokens = tempLine.split(" ")
                val tempCpu = tokens.find { it.startsWith("TEMP_CPU=") }?.removePrefix("TEMP_CPU=")?.toFloatOrNull()
                if (tempCpu != null) {
                    cpuTempVal = "%.0f°C".format(tempCpu)
                    rawCpuTempNum = tempCpu
                }
            }
        }

        // 3. Query Battery Temp
        var batTempVal = "--"
        var rawBatTempNum = 0f
        runCatching {
            val raw = RootShell.readText("/sys/class/power_supply/battery/temp")?.trim()?.toFloatOrNull()
            if (raw != null) {
                val tempC = if (raw > 1000f) raw / 1000f else if (raw > 100f) raw / 10f else raw
                batTempVal = "%.0f°C".format(tempC)
                rawBatTempNum = tempC
            }
        }

        return TelemetryData(
            fps = fpsVal,
            cpuClusters = cpuClusters,
            gpuFreq = gpuFreqVal,
            gpuLoad = gpuLoadVal,
            cpuTemp = cpuTempVal,
            batTemp = batTempVal,
            rawFps = rawFpsNum,
            rawCpuTemp = rawCpuTempNum,
            rawBatTemp = rawBatTempNum
        )
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        pollingJob?.cancel()
        overlayView?.let { wm.removeView(it) }
        super.onDestroy()
    }

    companion object {
        @Composable
        fun OverlayChip(
            data: TelemetryData,
            showFps: Boolean,
            showCpu: Boolean,
            showGpu: Boolean,
            showTemp: Boolean,
            showBattery: Boolean,
            monetEnabled: Boolean,
            textSizeSp: Float,
            bgOpacity: Float,
            paddingDp: Float,
            cornerRadiusDp: Float,
            layoutStyle: String
        ) {
            val textSize = textSizeSp.sp
            val subTextSize = (textSizeSp - 1f).coerceAtLeast(8f).sp
            val padding = paddingDp.dp
            val cornerRadius = cornerRadiusDp.dp

            val fpsColor = if (monetEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                when {
                    data.rawFps >= 57f -> Color(0xFF2ECC71) // Green
                    data.rawFps >= 45f -> Color(0xFFF1C40F) // Yellow
                    data.rawFps > 0f -> Color(0xFFE74C3C)   // Red
                    else -> Color.White
                }
            }

            val cpuTempColor = if (monetEnabled) {
                MaterialTheme.colorScheme.secondary
            } else {
                when {
                    data.rawCpuTemp >= 48f -> Color(0xFFE74C3C) // Hot Red
                    data.rawCpuTemp >= 40f -> Color(0xFFF1C40F) // Warm Yellow
                    data.rawCpuTemp > 0f -> Color(0xFF3498DB)   // Cool Blue
                    else -> Color.White.copy(alpha = 0.7f)
                }
            }

            val batTempColor = if (monetEnabled) {
                MaterialTheme.colorScheme.tertiary
            } else {
                when {
                    data.rawBatTemp >= 43f -> Color(0xFFE74C3C) // Hot Red
                    data.rawBatTemp >= 38f -> Color(0xFFF1C40F) // Warm Yellow
                    data.rawBatTemp > 0f -> Color(0xFF3498DB)   // Cool Blue
                    else -> Color.White.copy(alpha = 0.7f)
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = bgOpacity),
                        shape = RoundedCornerShape(cornerRadius),
                    )
                    .padding(horizontal = padding, vertical = padding * 0.6f),
            ) {
                if (layoutStyle == "Horizontal") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var first = true

                        if (showFps) {
                            Text(
                                text = "${data.fps} FPS",
                                fontSize = textSize,
                                color = fpsColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            first = false
                        }

                        if (showCpu && data.cpuClusters.isNotEmpty()) {
                            if (!first) {
                                Text("|", fontSize = subTextSize, color = Color.White.copy(alpha = 0.2f))
                            }
                            Text(
                                text = "CPU " + data.cpuClusters.joinToString("·"),
                                fontSize = subTextSize,
                                color = if (monetEnabled) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.85f),
                            )
                            first = false
                        }

                        if (showGpu && data.gpuFreq != "--") {
                            if (!first) {
                                Text("|", fontSize = subTextSize, color = Color.White.copy(alpha = 0.2f))
                            }
                            Text(
                                text = "GPU ${data.gpuFreq} (${data.gpuLoad})",
                                fontSize = subTextSize,
                                color = if (monetEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f),
                            )
                            first = false
                        }

                        if (showTemp && data.cpuTemp != "--") {
                            if (!first) {
                                Text("|", fontSize = subTextSize, color = Color.White.copy(alpha = 0.2f))
                            }
                            Text(
                                text = "CPU ${data.cpuTemp}",
                                fontSize = subTextSize,
                                color = cpuTempColor,
                            )
                            first = false
                        }

                        if (showBattery && data.batTemp != "--") {
                            if (!first) {
                                Text("|", fontSize = subTextSize, color = Color.White.copy(alpha = 0.2f))
                            }
                            Text(
                                text = "BAT ${data.batTemp}",
                                fontSize = subTextSize,
                                color = batTempColor,
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (showFps) {
                            Text(
                                text = "FPS: ${data.fps}",
                                fontSize = textSize,
                                color = fpsColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                        }

                        if (showCpu && data.cpuClusters.isNotEmpty()) {
                            Text(
                                text = "CPU: " + data.cpuClusters.joinToString(" | "),
                                fontSize = subTextSize,
                                color = if (monetEnabled) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.85f),
                            )
                        }

                        if (showGpu && data.gpuFreq != "--") {
                            Text(
                                text = "GPU: ${data.gpuFreq} (${data.gpuLoad})",
                                fontSize = subTextSize,
                                color = if (monetEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f),
                            )
                        }

                        if (showTemp && data.cpuTemp != "--") {
                            Text(
                                text = "CPU Temp: ${data.cpuTemp}",
                                fontSize = subTextSize,
                                color = cpuTempColor,
                            )
                        }

                        if (showBattery && data.batTemp != "--") {
                            Text(
                                text = "BAT Temp: ${data.batTemp}",
                                fontSize = subTextSize,
                                color = batTempColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
