package dev.auriya.app.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
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
import dev.auriya.app.MainActivity
import dev.auriya.app.data.OverlayPrefs
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.stats.Stats
import dev.auriya.app.data.stats.StatsParser
import dev.auriya.app.ui.overlay.components.CpuClusterInfo
import dev.auriya.app.ui.overlay.components.CpuCoreInfo
import dev.auriya.app.ui.overlay.components.OverlayHud
import dev.auriya.app.ui.overlay.components.OverlayTelemetry
import dev.auriya.app.ui.theme.AuriyaTheme
import dev.auriya.app.ui.theme.GoogleSansRounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object CpuStatReader {
    private var prevTotal = 0L
    private var prevIdle = 0L
    private val prevCoreTotals = mutableMapOf<Int, Long>()
    private val prevCoreIdles = mutableMapOf<Int, Long>()

    data class CpuLoadResult(
        val totalLoadPct: Float,
        val coreLoads: Map<Int, Float>,
    )

    private fun parseNumbers(line: String, startOffset: Int): LongArray {
        val result = LongArray(10)
        var count = 0
        var current = 0L
        var inNum = false
        for (i in startOffset until line.length) {
            val c = line[i]
            if (c in '0'..'9') {
                current = current * 10L + (c - '0')
                inNum = true
            } else if (inNum) {
                if (count < 10) result[count++] = current
                current = 0L
                inNum = false
            }
        }
        if (inNum && count < 10) result[count++] = current
        return result
    }

    fun readCpuLoads(): CpuLoadResult {
        var totalLoad = 0f
        val coreLoads = mutableMapOf<Int, Float>()

        try {
            val file = java.io.File("/proc/stat")
            if (!file.exists()) return CpuLoadResult(0f, emptyMap())

            file.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("cpu ")) {
                        val nums = parseNumbers(line, 4)
                        val total = nums.sum()
                        val idle = nums.getOrElse(3) { 0L } + nums.getOrElse(4) { 0L }
                        if (prevTotal > 0L) {
                            val dTotal = total - prevTotal
                            val dIdle = idle - prevIdle
                            if (dTotal > 0L) {
                                totalLoad = (100f * (dTotal - dIdle).coerceAtLeast(0L) / dTotal.toFloat()).coerceIn(0f, 100f)
                            }
                        }
                        prevTotal = total
                        prevIdle = idle
                    } else if (line.startsWith("cpu")) {
                        val spaceIdx = line.indexOf(' ')
                        if (spaceIdx > 3) {
                            val coreIdStr = line.substring(3, spaceIdx)
                            val coreId = coreIdStr.toIntOrNull()
                            if (coreId != null) {
                                val nums = parseNumbers(line, spaceIdx + 1)
                                val total = nums.sum()
                                val idle = nums.getOrElse(3) { 0L } + nums.getOrElse(4) { 0L }
                                val pTotal = prevCoreTotals[coreId] ?: 0L
                                val pIdle = prevCoreIdles[coreId] ?: 0L
                                if (pTotal > 0L) {
                                    val dTotal = total - pTotal
                                    val dIdle = idle - pIdle
                                    if (dTotal > 0L) {
                                        val pct = (100f * (dTotal - dIdle).coerceAtLeast(0L) / dTotal.toFloat()).coerceIn(0f, 100f)
                                        coreLoads[coreId] = pct
                                    }
                                }
                                prevCoreTotals[coreId] = total
                                prevCoreIdles[coreId] = idle
                            }
                        }
                    } else if (!line.startsWith("cpu")) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return CpuLoadResult(totalLoad, coreLoads)
    }
}

class OverlayService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
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
        val cpuClusterList: List<CpuClusterInfo> = emptyList(),
        val cpuCoreList: List<CpuCoreInfo> = emptyList(),
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

    data class OverlayConfigState(
        val showFps: Boolean = false,
        val roundFps: Boolean = true,
        val showCpu: Boolean = false,
        val showGpu: Boolean = false,
        val showRam: Boolean = false,
        val showTemp: Boolean = false,
        val showBattery: Boolean = false,
        val textSizeSp: Float = 12f,
        val bgOpacity: Float = 1f,
        val paddingDp: Float = 8f,
        val cornerRadiusDp: Float = 16f,
        val overlayMode: String = "Full",
        val cpuStyle: String = "tags",
    )

    private val telemetryState = mutableStateOf(TelemetryData())
    private val configState = mutableStateOf(OverlayConfigState())

    private fun refreshConfig() {
        val prefs = OverlayPrefs.getInstance(this)
        configState.value =
            OverlayConfigState(
                showFps = prefs.showFps,
                roundFps = prefs.roundFps,
                showCpu = prefs.showCpu,
                showGpu = prefs.showGpu,
                showRam = prefs.showRam,
                showTemp = prefs.showTemp,
                showBattery = prefs.showBattery,
                textSizeSp = prefs.textSizeSp,
                bgOpacity = prefs.bgOpacity,
                paddingDp = prefs.paddingDp,
                cornerRadiusDp = prefs.cornerRadiusDp,
                overlayMode = prefs.overlayMode,
                cpuStyle = prefs.cpuStyle,
            )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshConfig()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        startAsForeground()
        refreshConfig()
        if (overlayView == null) {
            createOverlay()
        }
        pollingJob?.cancel()
        startPolling()
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "auriya_overlay_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    "Auriya Floating Overlay",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows live system telemetry HUD"
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Auriya Telemetry HUD")
                .setContentText("Performance overlay is active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        startForeground(1003, notification)
    }

    private fun createOverlay() {
        val prefs = OverlayPrefs.getInstance(this)
        overlayView =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    AuriyaTheme(prefs = null) {
                        val config = configState.value
                        val showFps = config.showFps
                        val showCpu = config.showCpu
                        val showGpu = config.showGpu
                        val showRam = config.showRam
                        val showTemp = config.showTemp
                        val showBattery = config.showBattery
                        val hasAnyMetric = showFps || showCpu || showGpu || showRam || showTemp || showBattery

                        val textSizeSp = config.textSizeSp
                        val bgOpacity = config.bgOpacity
                        val paddingDp = config.paddingDp
                        val cornerRadiusDp = config.cornerRadiusDp
                        val overlayMode = config.overlayMode
                        val cpuStyle = config.cpuStyle

                        if (hasAnyMetric) {
                            val data = telemetryState.value
                            val isMinimal = overlayMode == "Minimal"
                            val cpuText =
                                if (showCpu && (data.cpuClusters.isNotEmpty() || data.cpuLoadPct > 0f)) {
                                    when (cpuStyle) {
                                        "tags" -> {
                                            if (data.cpuClusterLabels.isNotEmpty()) {
                                                data.cpuClusterLabels.zip(data.cpuClusters).joinToString(" ") { (lbl, frq) -> "$lbl$frq" }
                                            } else {
                                                data.cpuClusters.joinToString(" ")
                                            }
                                        }

                                        "load_peak" -> {
                                            if (isMinimal) {
                                                "%.0f%% @ %.1fG".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                            } else {
                                                "%.0f%% @ %.1f GHz".format(data.cpuLoadPct, data.maxCpuFreqGHz)
                                            }
                                        }

                                        "pipe" -> {
                                            data.cpuClusters.joinToString(" | ") + "G"
                                        }

                                        else -> {
                                            if (isMinimal) {
                                                data.cpuClusters.joinToString("/") + "G"
                                            } else {
                                                data.cpuClusters.joinToString("/") + " GHz"
                                            }
                                        }
                                    }
                                } else {
                                    ""
                                }

                            val gpuText =
                                if (showGpu && data.hasGpu && data.gpuFreq != "--") {
                                    "${data.gpuFreq} (${data.gpuLoad})"
                                } else {
                                    ""
                                }

                            val ramText = if (showRam && data.ram != "--") data.ram else ""

                            val tempText =
                                if (showTemp && data.cpuTemp != "--") {
                                    if (isMinimal) data.cpuTemp.removeSuffix("C") else data.cpuTemp
                                } else {
                                    ""
                                }

                            val batText =
                                if (showBattery && data.batTemp != "--") {
                                    if (isMinimal) data.batTemp.removeSuffix("C") else data.batTemp
                                } else {
                                    ""
                                }

                            val telemetry =
                                OverlayTelemetry(
                                    fps = data.fps,
                                    rawFps = data.rawFps,
                                    cpuText = cpuText,
                                    cpuClusters = data.cpuClusters,
                                    cpuClusterLabels = data.cpuClusterLabels,
                                    cpuClusterList = data.cpuClusterList,
                                    cpuCoreList = data.cpuCoreList,
                                    cpuStyle = cpuStyle,
                                    cpuLoadPct = data.cpuLoadPct,
                                    maxCpuFreqGHz = data.maxCpuFreqGHz,
                                    gpuText = gpuText,
                                    ramText = ramText,
                                    tempText = tempText,
                                    batText = batText,
                                    rawCpuTemp = data.rawCpuTemp,
                                    rawBatTemp = data.rawBatTemp,
                                )

                            OverlayHud(
                                telemetry = telemetry,
                                showFps = showFps,
                                showCpu = showCpu,
                                showGpu = showGpu,
                                showRam = showRam,
                                showTemp = showTemp,
                                showBattery = showBattery,
                                textSizeSp = textSizeSp,
                                bgOpacity = bgOpacity,
                                paddingDp = paddingDp,
                                cornerRadiusDp = cornerRadiusDp,
                                overlayMode = overlayMode,
                                modifier =
                                    Modifier.pointerInput(Unit) {
                                        var accX = 0f
                                        var accY = 0f
                                        var screenW = 0
                                        var screenH = 0

                                        detectDragGestures(
                                            onDragStart = {
                                                val dm = resources.displayMetrics
                                                screenW = dm.widthPixels
                                                screenH = dm.heightPixels
                                                accX = params.x.toFloat()
                                                accY = params.y.toFloat()
                                            },
                                            onDragEnd = {
                                                prefs.overlayX = params.x
                                                prefs.overlayY = params.y
                                            },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            accX += dragAmount.x
                                            accY += dragAmount.y
                                            val viewW = overlayView?.width ?: 0
                                            val viewH = overlayView?.height ?: 0
                                            val maxX = (screenW - viewW).coerceAtLeast(0)
                                            val maxY = (screenH - viewH).coerceAtLeast(0)

                                            val newX = accX.roundToInt().coerceIn(0, maxX)
                                            val newY = accY.roundToInt().coerceIn(0, maxY)

                                            if (newX != params.x || newY != params.y) {
                                                params.x = newX
                                                params.y = newY
                                                overlayView?.let { wm.updateViewLayout(it, params) }
                                            }
                                        }
                                    },
                            )
                        }
                    }
                }
            }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val posX = prefs.overlayX.coerceIn(0, (screenWidth - 100).coerceAtLeast(0))
        val posY = prefs.overlayY.coerceIn(0, (screenHeight - 100).coerceAtLeast(0))

        params =
            WindowManager
                .LayoutParams(
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
        val prefs = OverlayPrefs.getInstance(this)
        val interval = prefs.updateIntervalMs.coerceIn(200L, 10000L)

        pollingJob =
            CoroutineScope(Dispatchers.IO + Job()).launch {
                while (isActive) {
                    try {
                        StatsParser.streamStats(interval).collect { stats ->
                            val data = queryTelemetry(stats)
                            withContext(Dispatchers.Main) {
                                telemetryState.value = data
                            }
                        }
                    } catch (e: CancellationException) {
                        break
                    } catch (_: Throwable) {
                        delay(1000L)
                    }
                }
            }
    }

    private fun queryTelemetry(stats: Stats? = null): TelemetryData {
        // 1. FPS
        var fpsVal = "0"
        var rawFpsNum = 0f
        val roundFps = OverlayPrefs.getInstance(this).roundFps
        if (stats?.fps != null && stats.fps.avg > 0) {
            fpsVal = dev.auriya.app.data.AppPrefs.formatFps(stats.fps.avg, roundFps)
            rawFpsNum = stats.fps.avg.toFloat()
        }

        // 3. CPU Clusters & Load
        val cpuClusters = mutableListOf<String>()
        val cpuClusterLabels = mutableListOf<String>()
        val cpuClusterList = mutableListOf<CpuClusterInfo>()
        val cpuCoreList = mutableListOf<CpuCoreInfo>()
        var maxFreqKHz = 0L

        val procStat = CpuStatReader.readCpuLoads()
        val cpuLoadPct = if (stats?.cpu?.load_pct != null && stats.cpu.load_pct > 0f) stats.cpu.load_pct else procStat.totalLoadPct

        stats?.cpu?.cores?.let { cores ->
            val clustersMap = mutableMapOf<Int, MutableList<Long>>()
            val clusterLoadsMap = mutableMapOf<Int, MutableList<Float>>()
            val clusterNameMap = mutableMapOf<Int, String>()
            val clusterLabelMap = mutableMapOf<Int, String>()

            cores.forEach { core ->
                val mhz = core.khz / 1000L
                val coreLoad = if (core.load_pct > 0f) core.load_pct else (procStat.coreLoads[core.id] ?: cpuLoadPct)
                cpuCoreList.add(CpuCoreInfo(core.id, mhz, coreLoad))

                val (clusterId, name, label) =
                    when (core.cluster.lowercase().trim()) {
                        "little", "0" -> Triple(0, "little", "L")
                        "big", "mid", "1" -> Triple(1, "big", if (cores.any { it.cluster.equals("prime", ignoreCase = true) }) "M" else "B")
                        "prime", "2" -> Triple(2, "prime", "P")
                        else -> Triple(core.cluster.toIntOrNull() ?: 0, "cpu${core.cluster}", "C${core.cluster}")
                    }
                if (core.khz > 0) {
                    clustersMap.getOrPut(clusterId) { mutableListOf() }.add(core.khz)
                    clusterLoadsMap.getOrPut(clusterId) { mutableListOf() }.add(coreLoad)
                    clusterNameMap[clusterId] = name
                    clusterLabelMap[clusterId] = label
                    if (core.khz > maxFreqKHz) maxFreqKHz = core.khz
                }
            }
            clustersMap.keys.sorted().forEach { cId ->
                val freqs = clustersMap[cId]!!
                val avgFreqKHz = freqs.average()
                val freqGHz = avgFreqKHz / 1_000_000.0
                val avgLoad = clusterLoadsMap[cId]?.average()?.toFloat() ?: cpuLoadPct
                val avgMhz = (avgFreqKHz / 1000.0).toLong()

                cpuClusters.add("%.1f".format(freqGHz))
                cpuClusterLabels.add(clusterLabelMap[cId] ?: "C")
                cpuClusterList.add(CpuClusterInfo(clusterNameMap[cId] ?: "cluster", avgMhz, avgLoad))
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
            cpuClusterList = cpuClusterList,
            cpuCoreList = cpuCoreList,
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
            hasGpu = hasGpuDevice,
        )
    }

    override fun onDestroy() {
        isRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        pollingJob?.cancel()
        overlayView?.let { wm.removeView(it) }
        super.onDestroy()
    }

    companion object {
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startForegroundService(intent) }.onFailure {
                    dev.auriya.app.data.RootShell.run(
                        "am start-foreground-service -n ${context.packageName}/dev.auriya.app.service.OverlayService",
                    )
                }
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, OverlayService::class.java))
            }
        }
    }
}
