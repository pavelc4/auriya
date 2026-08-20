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
import dev.auriya.app.data.stats.StatsParser
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
        val cpuClusterLabels: List<String> = emptyList(),
        val cpuLoadPct: Float = 0f,
        val maxCpuFreqGHz: Float = 0f,
        val gpuFreq: String = "--",
        val gpuLoad: String = "--",
        val cpuTemp: String = "--",
        val batTemp: String = "--",
        val ram: String = "--",
        val rawFps: Float = 0f,
        val rawCpuTemp: Float = 0f,
        val rawBatTemp: Float = 0f,
        val hasGpu: Boolean = false,
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
                    val cpuStyle = prefs.getString("cpu_style", "tags") ?: "tags"

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
                            cpuStyle = cpuStyle,
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

        pollingJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            while (isActive) {
                val data = queryTelemetry()
                withContext(Dispatchers.Main) {
                    telemetryState.value = data
                }
                val interval = prefs.getLong("update_interval_ms", 1000L).coerceIn(200L, 10000L)
                delay(interval)
            }
        }
    }

    private fun queryTelemetry(): TelemetryData {
        val stats = StatsParser.fetchStats()

        // 2. FPS
        var fpsVal = "0"
        var rawFpsNum = 0f
        if (stats?.fps != null && stats.fps.avg > 0) {
            fpsVal = "%.1f".format(stats.fps.avg)
            rawFpsNum = stats.fps.avg.toFloat()
        } else {
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
        }

        // 3. CPU Clusters & Load
        val cpuClusters = mutableListOf<String>()
        val cpuClusterLabels = mutableListOf<String>()
        var maxFreqKHz = 0L
        val cpuLoadPct = stats?.cpu?.load_pct ?: 0f

        stats?.cpu?.cores?.let { cores ->
            val clustersMap = mutableMapOf<Int, MutableList<Long>>()
            val clusterNameMap = mutableMapOf<Int, String>()
            cores.forEach { core ->
                val (clusterId, label) = when (core.cluster.lowercase().trim()) {
                    "little", "0" -> Pair(0, "L")
                    "big", "mid", "1" -> Pair(1, if (cores.any { it.cluster.equals("prime", ignoreCase = true) }) "M" else "B")
                    "prime", "2" -> Pair(2, "P")
                    else -> Pair(core.cluster.toIntOrNull() ?: 0, "C${core.cluster}")
                }
                if (core.khz > 0) {
                    clustersMap.getOrPut(clusterId) { mutableListOf() }.add(core.khz)
                    clusterNameMap[clusterId] = label
                    if (core.khz > maxFreqKHz) maxFreqKHz = core.khz
                }
            }
            clustersMap.keys.sorted().forEach { cId ->
                val freqs = clustersMap[cId]!!
                val avgFreqKHz = freqs.average()
                val freqGHz = avgFreqKHz / 1_000_000.0
                cpuClusters.add("%.1f".format(freqGHz))
                cpuClusterLabels.add(clusterNameMap[cId] ?: "C")
            }
        }
        val maxCpuFreqGHz = if (maxFreqKHz > 0) maxFreqKHz / 1_000_000f else 0f

        // 4. GPU
        var gpuFreqVal = "--"
        var gpuLoadVal = "--"
        var hasGpuDevice = false
        stats?.gpu?.let { gpu ->
            if (gpu.vendor != null && gpu.vendor != "None" && !gpu.vendor.contains("None")) {
                hasGpuDevice = true
            }
            if (gpu.mhz != null && gpu.mhz > 0) {
                gpuFreqVal = "${gpu.mhz}M"
                hasGpuDevice = true
            }
            if (gpu.load_pct != null) {
                gpuLoadVal = "${gpu.load_pct}%"
            }
        }

        // 5. CPU Temp
        var cpuTempVal = "--"
        var rawCpuTempNum = 0f
        stats?.thermal?.cpu_c?.let { temp ->
            if (temp > 0f) {
                cpuTempVal = "%.0f°C".format(temp)
                rawCpuTempNum = temp
            }
        }

        // 6. Battery Temp (from IPC stats.thermal.battery_c with BatteryManager fallback)
        var batTempVal = "--"
        var rawBatTempNum = 0f
        val ipcBatTemp = stats?.thermal?.battery_c
        if (ipcBatTemp != null && ipcBatTemp > 0f) {
            batTempVal = "%.0f°C".format(ipcBatTemp)
            rawBatTempNum = ipcBatTemp
        } else {
            runCatching {
                val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val raw = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                if (raw > 0) {
                    val c = raw / 10.0f
                    batTempVal = "%.0f°C".format(c)
                    rawBatTempNum = c
                }
            }
        }

        // 7. Memory (RAM)
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
            cpuClusterLabels = cpuClusterLabels,
            cpuLoadPct = cpuLoadPct,
            maxCpuFreqGHz = maxCpuFreqGHz,
            gpuFreq = gpuFreqVal,
            gpuLoad = gpuLoadVal,
            cpuTemp = cpuTempVal,
            batTemp = batTempVal,
            ram = ramVal,
            rawFps = rawFpsNum,
            rawCpuTemp = rawCpuTempNum,
            rawBatTemp = rawBatTempNum,
            hasGpu = hasGpuDevice
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
            cpuStyle: String = "tags",
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

            val cpuColor = when {
                monetEnabled -> MaterialTheme.colorScheme.onSurface
                else -> baseSecondary
            }

            val gpuColor = when {
                monetEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> baseTertiary
            }

            val ramColor = when {
                monetEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> baseSecondary
            }

            val cpuTempDotColor = when {
                data.rawCpuTemp >= 48f -> Color(0xFFFF1A53) // Hot Red
                data.rawCpuTemp >= 40f -> Color(0xFFFFE600) // Warm Yellow
                data.rawCpuTemp > 0f -> Color(0xFF00E5FF)   // Cool Cyan
                else -> Color(0xFF888888)
            }

            val cpuTempColor = when {
                monetEnabled -> MaterialTheme.colorScheme.secondary
                overlayPreset == "custom" -> baseTertiary
                else -> cpuTempDotColor
            }

            val batTempDotColor = when {
                data.rawBatTemp >= 43f -> Color(0xFFFF1A53) // Hot Red
                data.rawBatTemp >= 38f -> Color(0xFFFFE600) // Warm Yellow
                data.rawBatTemp > 0f -> Color(0xFF00E5FF)   // Cool Cyan
                else -> Color(0xFF888888)
            }

            val batTempColor = when {
                monetEnabled -> MaterialTheme.colorScheme.tertiary
                overlayPreset == "custom" -> baseTertiary
                else -> batTempDotColor
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

                        if (showCpu && (data.cpuClusters.isNotEmpty() || data.cpuLoadPct > 0f)) {
                            if (!first) {
                                Text("·", fontSize = subTextSize, color = Color.White.copy(alpha = 0.35f), maxLines = 1, softWrap = false)
                            }
                            val cpuText = when (cpuStyle) {
                                "tags" -> {
                                    val tagged = if (data.cpuClusterLabels.isNotEmpty()) {
                                        data.cpuClusterLabels.zip(data.cpuClusters).joinToString(" ") { (lbl, frq) -> "$lbl$frq" }
                                    } else {
                                        data.cpuClusters.joinToString(" ")
                                    }
                                    if (isMinimal) tagged else "CPU $tagged"
                                }
                                "load_peak" -> {
                                    if (isMinimal) "%.0f%% @ %.1fG".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                    else "CPU %.0f%% @ %.1f GHz".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                }
                                "pipe" -> {
                                    val piped = data.cpuClusters.joinToString(" | ") + "G"
                                    if (isMinimal) piped else "CPU $piped"
                                }
                                else -> {
                                    if (isMinimal) data.cpuClusters.joinToString("/") + "G"
                                    else "CPU " + data.cpuClusters.joinToString("/") + " GHz"
                                }
                            }
                            Text(
                                text = cpuText,
                                fontSize = subTextSize,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = cpuColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            first = false
                        }

                        if (showGpu && data.hasGpu && data.gpuFreq != "--") {
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
                                val cpuText = when (cpuStyle) {
                                    "tags" -> {
                                        if (isMinimal) {
                                            data.cpuClusterLabels.zip(data.cpuClusters).joinToString(" ") { (lbl, frq) -> "$lbl$frq" }
                                        } else {
                                            data.cpuClusterLabels.zip(data.cpuClusters).joinToString("   ") { (lbl, frq) -> "$lbl: ${frq}G" }
                                        }
                                    }
                                    "load_peak" -> {
                                        if (isMinimal) "%.0f%% @ %.1fG".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                        else "%.0f%%  (Peak %.1f GHz)".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                    }
                                    "pipe" -> {
                                        if (isMinimal) data.cpuClusters.joinToString(" | ") + "G"
                                        else data.cpuClusters.joinToString("  |  ") + " GHz"
                                    }
                                    else -> {
                                        if (isMinimal) data.cpuClusters.joinToString("/") + "G"
                                        else data.cpuClusters.joinToString(" / ") + " GHz"
                                    }
                                }
                                Text(
                                    text = cpuText,
                                    fontSize = subTextSize,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Medium,
                                    color = cpuColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (showGpu && data.hasGpu && data.gpuFreq != "--") {
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
