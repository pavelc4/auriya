package dev.auriya.app.data.stats

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import dev.auriya.app.data.RootShell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Date
import kotlin.math.roundToInt

class BenchmarkRecorder private constructor(
    private val context: Context,
) {
    private val repository = BenchmarkRepository(context)
    private val autoRecordPrefs = AutoRecordPrefs(context)
    private val pm: PackageManager = context.packageManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentRecordingPkg = MutableStateFlow<String?>(null)
    val currentRecordingPkg: StateFlow<String?> = _currentRecordingPkg.asStateFlow()

    private val _currentRecordingDurationSec = MutableStateFlow(0L)
    val currentRecordingDurationSec: StateFlow<Long> = _currentRecordingDurationSec.asStateFlow()

    private val _currentSamplesCount = MutableStateFlow(0)
    val currentSamplesCount: StateFlow<Int> = _currentSamplesCount.asStateFlow()

    private val _sessions = MutableStateFlow<List<BenchmarkSession>>(emptyList())
    val sessions: StateFlow<List<BenchmarkSession>> = _sessions.asStateFlow()

    // Recording buffer
    private var recordingStartTime = 0L
    private var recordingPackage: String? = null
    private var recordingAppLabel: String = ""
    private var recordingProfile: String = "balance"
    private val bufferSamples = mutableListOf<BenchmarkSample>()
    private var isManual = false

    init {
        loadSessions()
        startBackgroundMonitoring()
    }

    companion object {
        @Volatile
        private var instance: BenchmarkRecorder? = null

        fun getInstance(context: Context): BenchmarkRecorder =
            instance ?: synchronized(this) {
                instance ?: BenchmarkRecorder(context.applicationContext).also { instance = it }
            }

        fun saveSessionToDownloadAuriya(
            context: Context,
            session: BenchmarkSession,
        ): File? =
            runCatching {
                val safeName = session.appLabel.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fileName = "auriya_benchmark_${safeName}_${session.startTimeEpoch}.csv"

                val csvContent =
                    buildString {
                        appendLine("AURIYA BENCHMARK REPORT")
                        appendLine("Game Title,${session.appLabel}")
                        appendLine("Package Name,${session.packageName}")
                        appendLine("Profile,${session.profile}")
                        appendLine("Recorded At,${Date(session.startTimeEpoch)}")
                        appendLine("Duration (Seconds),${session.durationSeconds}")
                        appendLine("Total Samples,${session.samplesCount}")
                        appendLine("Average FPS,${session.avgFps}")
                        appendLine("1% Low FPS,${session.minLow1Pct}")
                        appendLine("Peak FPS,${session.maxFps}")
                        appendLine("Total Jank Frames,${session.totalJank}")
                        appendLine("Average CPU Load (%),${session.avgCpuLoad}")
                        appendLine("Peak CPU Temp (°C),${session.maxCpuTemp ?: "N/A"}")
                        appendLine("Peak Battery Temp (°C),${session.maxBatteryTemp ?: "N/A"}")
                        appendLine()
                        appendLine("Timestamp Offset (ms),FPS,1% Low,Jank Frames,CPU Load (%),CPU Temp (°C),Battery Temp (°C)")
                        session.samples.forEach { s ->
                            appendLine(
                                "${s.timestampOffsetMs},${s.fps},${s.low1Pct},${s.jank},${s.cpuLoad},${s.cpuTemp ?: ""},${s.batteryTemp ?: ""}",
                            )
                        }
                    }

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val downloadAuriyaDir = File(downloadDir, "auriya")

                // Handle case where auriya was an existing regular file
                if (downloadAuriyaDir.exists() && !downloadAuriyaDir.isDirectory) {
                    downloadAuriyaDir.delete()
                }
                if (!downloadAuriyaDir.exists()) {
                    downloadAuriyaDir.mkdirs()
                }

                val targetFile = File(downloadAuriyaDir, fileName)
                var written = false

                try {
                    targetFile.writeText(csvContent)
                    written = targetFile.exists() && targetFile.length() > 0
                } catch (_: Exception) {
                    written = false
                }

                if (!written) {
                    val cacheTmp = File(context.cacheDir, fileName)
                    cacheTmp.writeText(csvContent)
                    val script =
                        """
                        if [ -f /sdcard/Download/auriya ]; then rm -f /sdcard/Download/auriya; fi
                        mkdir -p /sdcard/Download/auriya
                        chmod 777 /sdcard/Download/auriya
                        cp '${cacheTmp.absolutePath}' '/sdcard/Download/auriya/$fileName'
                        chmod 666 '/sdcard/Download/auriya/$fileName'
                        """.trimIndent()
                    RootShell.run(script)
                }

                // Trigger MediaScanner so Android MediaStore & File Manager immediately see the file
                val actualFile = File("/sdcard/Download/auriya/$fileName")
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(actualFile.absolutePath),
                        arrayOf("text/csv"),
                    ) { path, uri -> }
                } catch (_: Exception) {
                }

                actualFile
            }.getOrNull()
    }

    fun startBackgroundMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob =
            scope.launch {
                while (isActive) {
                    if (_isRecording.value || autoRecordPrefs.hasAnyAutoRecordEnabled()) {
                        runCatching {
                            val stats = StatsParser.fetchStats()
                            if (stats != null) {
                                processStats(stats)
                            }
                        }
                        delay(1000)
                    } else {
                        delay(2500)
                    }
                }
            }
    }

    fun loadSessions() {
        _sessions.value = repository.getAllSessions()
    }

    fun deleteSession(id: String) {
        repository.deleteSession(id)
        loadSessions()
    }

    fun clearAllSessions() {
        repository.clearAll()
        loadSessions()
    }

    fun startManualRecording(
        pkg: String? = null,
        profile: String = "balance",
    ) {
        if (_isRecording.value) return
        val targetPkg = pkg ?: "system_monitor"
        val label = getAppLabel(targetPkg)
        startBuffer(targetPkg, label, profile, manual = true)
    }

    fun stopManualRecording() {
        if (!_isRecording.value) return
        finalizeBuffer()
    }

    fun processStats(stats: Stats) {
        val session = stats.session
        val activePkg = session.pkg

        // 1. Check for Auto-Record trigger
        if (!_isRecording.value) {
            if (session.active && !activePkg.isNullOrEmpty()) {
                if (autoRecordPrefs.isAutoRecordEnabled(activePkg)) {
                    val label = getAppLabel(activePkg)
                    startBuffer(activePkg, label, session.profile, manual = false)
                }
            }
        } else {
            // Currently recording
            if (!session.active || (activePkg != null && activePkg != recordingPackage && !isManual)) {
                // Game closed or switched
                finalizeBuffer()
            } else {
                // Record sample
                val now = System.currentTimeMillis()
                val offset = now - recordingStartTime
                _currentRecordingDurationSec.value = (offset / 1000L).coerceAtLeast(0L)

                val fps = stats.fps
                if (fps != null) {
                    val sample =
                        BenchmarkSample(
                            timestampOffsetMs = offset,
                            fps = fps.avg,
                            low1Pct = fps.low_1pct,
                            jank = fps.jank,
                            cpuLoad = stats.cpu?.load_pct ?: 0f,
                            cpuTemp = stats.thermal.cpu_c,
                            batteryTemp = stats.thermal.battery_c,
                        )
                    bufferSamples.add(sample)
                    _currentSamplesCount.value = bufferSamples.size
                }
            }
        }
    }

    private fun startBuffer(
        pkg: String,
        label: String,
        profile: String,
        manual: Boolean,
    ) {
        recordingStartTime = System.currentTimeMillis()
        recordingPackage = pkg
        recordingAppLabel = label
        recordingProfile = profile
        isManual = manual
        bufferSamples.clear()

        _isRecording.value = true
        _currentRecordingPkg.value = pkg
        _currentRecordingDurationSec.value = 0L
        _currentSamplesCount.value = 0

        // Keep app process alive via foreground service during game match
        dev.auriya.app.service.BenchmarkRecordingService
            .start(context, label)
    }

    private fun finalizeBuffer() {
        if (!_isRecording.value) return

        // Stop foreground service
        dev.auriya.app.service.BenchmarkRecordingService
            .stop(context)

        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - recordingStartTime) / 1000L).coerceAtLeast(1L)
        val pkg = recordingPackage ?: "unknown"
        val label = recordingAppLabel.ifEmpty { getAppLabel(pkg) }

        if (bufferSamples.isNotEmpty()) {
            // Filter active rendering samples (> 10 FPS to ignore initial launch stalls / background transitions)
            val activeSamples = bufferSamples.filter { it.fps > 10.0 }.ifEmpty { bufferSamples }

            val avgFps = activeSamples.map { it.fps }.average()
            val maxFps = activeSamples.maxOfOrNull { it.fps } ?: bufferSamples.maxOfOrNull { it.fps } ?: 0.0

            // 1% Low: computed across active gameplay frames
            val low1Pct =
                if (activeSamples.size >= 4) {
                    val sortedFps = activeSamples.map { it.fps }.sorted()
                    val onePctCount = (sortedFps.size * 0.05).toInt().coerceAtLeast(1)
                    sortedFps.take(onePctCount).average()
                } else {
                    activeSamples.map { it.low1Pct }.average().coerceAtLeast(0.0)
                }

            val totalJank = bufferSamples.maxOfOrNull { it.jank } ?: 0
            val avgCpu = activeSamples.map { it.cpuLoad }.average().toFloat()
            val maxCpuTemp = bufferSamples.mapNotNull { it.cpuTemp }.maxOrNull()
            val maxBatTemp = bufferSamples.mapNotNull { it.batteryTemp }.maxOrNull()

            val session =
                BenchmarkSession(
                    packageName = pkg,
                    appLabel = label,
                    profile = recordingProfile,
                    startTimeEpoch = recordingStartTime,
                    endTimeEpoch = endTime,
                    durationSeconds = durationSec,
                    samplesCount = bufferSamples.size,
                    avgFps = (avgFps * 10.0).roundToInt() / 10.0,
                    minLow1Pct = (low1Pct * 10.0).roundToInt() / 10.0,
                    maxFps = (maxFps * 10.0).roundToInt() / 10.0,
                    totalJank = totalJank,
                    avgCpuLoad = (avgCpu * 10f).roundToInt() / 10f,
                    maxCpuTemp = maxCpuTemp,
                    maxBatteryTemp = maxBatTemp,
                    samples = bufferSamples.toList(),
                )
            repository.saveSession(session)

            // Automatically save CSV snapshot into Download/auriya/ folder
            saveSessionToDownloadAuriya(context, session)
        }

        _isRecording.value = false
        _currentRecordingPkg.value = null
        _currentRecordingDurationSec.value = 0L
        _currentSamplesCount.value = 0
        bufferSamples.clear()
        loadSessions()
    }

    private fun getAppLabel(packageName: String): String =
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
}
