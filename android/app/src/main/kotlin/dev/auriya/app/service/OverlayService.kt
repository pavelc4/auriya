package dev.auriya.app.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import dev.auriya.app.ui.theme.GoogleSansRounded
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
    private lateinit var params: WindowManager.LayoutParams
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
        val ram: String = "--",
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
        val prefs = getSharedPreferences("auriya_overlay", MODE_PRIVATE)
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                AuriyaTheme(prefs = null) {
                    val showFps = prefs.getBoolean("show_fps", true)
                    val showCpu = prefs.getBoolean("show_cpu", true)
                    val showGpu = prefs.getBoolean("show_gpu", true)
                    val showRam = prefs.getBoolean("show_ram", true)
                    val showTemp = prefs.getBoolean("show_temp", true)
                    val showBattery = prefs.getBoolean("show_battery", true)
                    val hasAnyMetric = showFps || showCpu || showGpu || showRam || showTemp || showBattery
                    val monetEnabled = prefs.getBoolean("monet_enabled", true)
                    
                    val overlayPreset = prefs.getString("overlay_preset", "green_default") ?: "green_default"
                    val customPrimary = prefs.getString("custom_primary", "#AAD2A4") ?: "#AAD2A4"
                    val customSecondary = prefs.getString("custom_secondary", "#385E38") ?: "#385E38"
                    val customTertiary = prefs.getString("custom_tertiary", "#8A9A5B") ?: "#8A9A5B"

                    val textSizeSp = prefs.getFloat("text_size_sp", 12f)
                    val bgOpacity = prefs.getFloat("bg_opacity", 0.7f)
                    val paddingDp = prefs.getFloat("padding_dp", 12f)
                    val cornerRadiusDp = prefs.getFloat("corner_radius_dp", 16f)
                    val layoutStyle = prefs.getString("layout_style", "Horizontal") ?: "Horizontal"
                    val overlayMode = prefs.getString("overlay_mode", "Full") ?: "Full"

                    if (hasAnyMetric) {
                        OverlayChip(
                            data = telemetryState.value,
                            showFps = showFps,
                            showCpu = showCpu,
                            showGpu = showGpu,
                            showRam = showRam,
                            showTemp = showTemp,
                            showBattery = showBattery,
                            monetEnabled = monetEnabled,
                            overlayPreset = overlayPreset,
                            customPrimary = customPrimary,
                            customSecondary = customSecondary,
                            customTertiary = customTertiary,
                            textSizeSp = textSizeSp,
                            bgOpacity = bgOpacity,
                            paddingDp = paddingDp,
                            cornerRadiusDp = cornerRadiusDp,
                            layoutStyle = layoutStyle,
                            overlayMode = overlayMode,
                            onDrag = { dx, dy ->
                                val displayMetrics = resources.displayMetrics
                                val screenWidth = displayMetrics.widthPixels
                                val screenHeight = displayMetrics.heightPixels
                                val viewWidth = overlayView?.width ?: 0
                                val viewHeight = overlayView?.height ?: 0
                                val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
                                val maxY = (screenHeight - viewHeight).coerceAtLeast(0)

                                params.x = (params.x + dx.toInt()).coerceIn(0, maxX)
                                params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
                                overlayView?.let { wm.updateViewLayout(it, params) }
                            },
                            onDragEnd = {
                                prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply()
                            }
                        )
                    }
                }
            }
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val posX = prefs.getInt("overlay_x", 50).coerceIn(0, (screenWidth - 100).coerceAtLeast(0))
        val posY = prefs.getInt("overlay_y", 200).coerceIn(0, (screenHeight - 100).coerceAtLeast(0))

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
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
        var fpsVal = "0"
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

        // 3. Battery Temp
        var batTempVal = "--"
        var rawBatTempNum = 0f
        runCatching {
            val file = java.io.File("/sys/class/power_supply/battery/temp")
            if (file.exists()) {
                val raw = file.readText().trim().toFloatOrNull() ?: 0f
                val c = if (raw > 1000f) raw / 10f else raw
                batTempVal = "%.0f°C".format(c)
                rawBatTempNum = c
            }
        }

        // 4. Memory (RAM)
        var ramVal = "--"
        runCatching {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val usedBytes = memInfo.totalMem - memInfo.availMem
            val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
            ramVal = "%.1fG".format(usedGB)
        }

        return TelemetryData(
            fps = fpsVal,
            cpuClusters = cpuClusters,
            gpuFreq = gpuFreqVal,
            gpuLoad = gpuLoadVal,
            cpuTemp = cpuTempVal,
            batTemp = batTempVal,
            ram = ramVal,
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
            showRam: Boolean,
            showTemp: Boolean,
            showBattery: Boolean,
            monetEnabled: Boolean,
            overlayPreset: String,
            customPrimary: String,
            customSecondary: String,
            customTertiary: String,
            textSizeSp: Float,
            bgOpacity: Float,
            paddingDp: Float,
            cornerRadiusDp: Float,
            layoutStyle: String,
            overlayMode: String,
            onDrag: (Float, Float) -> Unit,
            onDragEnd: () -> Unit
        ) {
            val hasAnyMetric = showFps || showCpu || showGpu || showRam || showTemp || showBattery
            if (!hasAnyMetric) {
                return
            }

            val textSize = textSizeSp.sp
            val subTextSize = (textSizeSp - 1f).coerceAtLeast(8f).sp
            val padding = paddingDp.dp
            val cornerRadius = cornerRadiusDp.dp

            // Theme Preset color mapping
            val (basePrimary, baseSecondary, baseTertiary) = remember(monetEnabled, overlayPreset, customPrimary, customSecondary, customTertiary) {
                if (monetEnabled) {
                    Triple(Color.Unspecified, Color.Unspecified, Color.Unspecified)
                } else {
                    when (overlayPreset) {
                        "gaming" -> Triple(Color(0xFF00FF66), Color(0xFF00E5FF), Color(0xFFFFE600))
                        "ocean" -> Triple(Color(0xFF0099FF), Color(0xFF00E5FF), Color(0xFF38B6FF))
                        "violet" -> Triple(Color(0xFFB026FF), Color(0xFFD946EF), Color(0xFFFF007F))
                        "solar" -> Triple(Color(0xFFFF6600), Color(0xFFFF1A53), Color(0xFFFFB703))
                        "volt" -> Triple(Color(0xFFFFE600), Color(0xFFFFB703), Color(0xFFFF6600))
                        "monochrome" -> Triple(Color(0xFFFFFFFF), Color(0xFFCCCCCC), Color(0xFF888888))
                        "sage" -> Triple(Color(0xFFC2D5C6), Color(0xFF4A5D4E), Color(0xFF8FA393))
                        "rust" -> Triple(Color(0xFFFF6600), Color(0xFFFF1A53), Color(0xFF5C3A21))
                        "custom" -> {
                            val prim = runCatching { Color(android.graphics.Color.parseColor(customPrimary)) }.getOrDefault(Color(0xFF00FF66))
                            val sec = runCatching { Color(android.graphics.Color.parseColor(customSecondary)) }.getOrDefault(Color(0xFF00E5FF))
                            val tert = runCatching { Color(android.graphics.Color.parseColor(customTertiary)) }.getOrDefault(Color(0xFFFFE600))
                            Triple(prim, sec, tert)
                        }
                        else -> Triple(Color(0xFFAAD2A4), Color(0xFF385E38), Color(0xFF8A9A5B)) // default green_default
                    }
                }
            }

            // Dynamic warning status color or fallback to preset colors
            val fpsDotColor = when {
                data.rawFps >= 57f -> Color(0xFF00FF66) // Neon Green
                data.rawFps >= 45f -> Color(0xFFFFE600) // Cyber Yellow
                data.rawFps > 0f -> Color(0xFFFF1A53)   // Neon Red
                else -> Color(0xFF888888)
            }

            val fpsColor = if (monetEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                basePrimary
            }

            val cpuColor = if (monetEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                baseSecondary
            }

            val gpuColor = if (monetEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                baseTertiary
            }

            val ramColor = if (monetEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                baseSecondary
            }

            val cpuTempDotColor = when {
                data.rawCpuTemp >= 48f -> Color(0xFFFF1A53) // Hot Red
                data.rawCpuTemp >= 40f -> Color(0xFFFFE600) // Warm Yellow
                data.rawCpuTemp > 0f -> Color(0xFF00E5FF)   // Cool Cyan
                else -> Color(0xFF888888)
            }

            val cpuTempColor = if (monetEnabled) {
                MaterialTheme.colorScheme.secondary
            } else if (overlayPreset == "custom") {
                baseTertiary
            } else {
                cpuTempDotColor
            }

            val batTempDotColor = when {
                data.rawBatTemp >= 43f -> Color(0xFFFF1A53) // Hot Red
                data.rawBatTemp >= 38f -> Color(0xFFFFE600) // Warm Yellow
                data.rawBatTemp > 0f -> Color(0xFF00E5FF)   // Cool Cyan
                else -> Color(0xFF888888)
            }

            val batTempColor = if (monetEnabled) {
                MaterialTheme.colorScheme.tertiary
            } else if (overlayPreset == "custom") {
                baseTertiary
            } else {
                batTempDotColor
            }

            val isMinimal = overlayMode == "Minimal"
            val badgeBgColor = if (monetEnabled) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)
            val badgeTextColor = if (monetEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f)

            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onDragEnd
                        ) { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF0F1115).copy(alpha = bgOpacity))
                    .padding(horizontal = padding, vertical = (padding * 0.65f).coerceAtLeast(6.dp)),
            ) {
                if (layoutStyle == "Horizontal") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var first = true

                        if (showFps) {
                            Text(
                                text = if (isMinimal) data.fps else "${data.fps} FPS",
                                fontSize = textSize,
                                color = fpsColor,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showCpu && data.cpuClusters.isNotEmpty()) {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            Text(
                                text = if (isMinimal) data.cpuClusters.joinToString("·") else "CPU " + data.cpuClusters.joinToString("·"),
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = cpuColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showGpu && data.gpuFreq != "--") {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            Text(
                                text = if (isMinimal) "${data.gpuFreq} (${data.gpuLoad})" else "GPU ${data.gpuFreq} (${data.gpuLoad})",
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = gpuColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showRam && data.ram != "--") {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            Text(
                                text = if (isMinimal) data.ram else "RAM ${data.ram}",
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = ramColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showTemp && data.cpuTemp != "--") {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            Text(
                                text = if (isMinimal) data.cpuTemp.removeSuffix("C") else "CPU ${data.cpuTemp}",
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = cpuTempColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showBattery && data.batTemp != "--") {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            Text(
                                text = if (isMinimal) data.batTemp.removeSuffix("C") else "BAT ${data.batTemp}",
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = batTempColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (showFps) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "FPS",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) data.fps else "${data.fps} FPS",
                                    fontSize = textSize,
                                    color = fpsColor,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showCpu && data.cpuClusters.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "CPU",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) data.cpuClusters.joinToString(" · ") else data.cpuClusters.joinToString(" · "),
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium,
                                    color = cpuColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showGpu && data.gpuFreq != "--") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "GPU",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) "${data.gpuFreq} (${data.gpuLoad})" else "${data.gpuFreq} (${data.gpuLoad})",
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium,
                                    color = gpuColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showRam && data.ram != "--") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "RAM",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) data.ram else data.ram,
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium,
                                    color = ramColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showTemp && data.cpuTemp != "--") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "TEMP",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) data.cpuTemp.removeSuffix("C") else data.cpuTemp,
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cpuTempColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showBattery && data.batTemp != "--") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isMinimal) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = badgeBgColor,
                                    ) {
                                        Text(
                                            text = "BAT",
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (subTextSize.value * 0.85f).sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMinimal) data.batTemp.removeSuffix("C") else data.batTemp,
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = batTempColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
