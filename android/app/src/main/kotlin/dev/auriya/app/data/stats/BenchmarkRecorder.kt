package dev.auriya.app.data.stats

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class BenchmarkRecorder(private val context: Context) {

    private val repository = BenchmarkRepository(context)
    private val autoRecordPrefs = AutoRecordPrefs(context)
    private val pm: PackageManager = context.packageManager

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

    fun startManualRecording(pkg: String? = null, profile: String = "balance") {
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
                    val sample = BenchmarkSample(
                        timestampOffsetMs = offset,
                        fps = fps.avg,
                        low1Pct = fps.low_1pct,
                        jank = fps.jank,
                        cpuLoad = stats.cpu?.load_pct ?: 0f,
                        cpuTemp = stats.thermal.cpu_c,
                        batteryTemp = stats.thermal.battery_c
                    )
                    bufferSamples.add(sample)
                    _currentSamplesCount.value = bufferSamples.size
                }
            }
        }
    }

    private fun startBuffer(pkg: String, label: String, profile: String, manual: Boolean) {
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
    }

    private fun finalizeBuffer() {
        if (!_isRecording.value) return

        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - recordingStartTime) / 1000L).coerceAtLeast(1L)
        val pkg = recordingPackage ?: "unknown"
        val label = recordingAppLabel.ifEmpty { getAppLabel(pkg) }

        if (bufferSamples.isNotEmpty()) {
            val avgFps = bufferSamples.map { it.fps }.average()
            val minLow = bufferSamples.minOfOrNull { it.low1Pct } ?: 0.0
            val maxFps = bufferSamples.maxOfOrNull { it.fps } ?: 0.0
            val totalJank = bufferSamples.sumOf { it.jank }
            val avgCpu = bufferSamples.map { it.cpuLoad }.average().toFloat()
            val maxCpuTemp = bufferSamples.mapNotNull { it.cpuTemp }.maxOrNull()
            val maxBatTemp = bufferSamples.mapNotNull { it.batteryTemp }.maxOrNull()

            val session = BenchmarkSession(
                packageName = pkg,
                appLabel = label,
                profile = recordingProfile,
                startTimeEpoch = recordingStartTime,
                endTimeEpoch = endTime,
                durationSeconds = durationSec,
                samplesCount = bufferSamples.size,
                avgFps = (avgFps * 10.0).roundToInt() / 10.0,
                minLow1Pct = (minLow * 10.0).roundToInt() / 10.0,
                maxFps = (maxFps * 10.0).roundToInt() / 10.0,
                totalJank = totalJank,
                avgCpuLoad = (avgCpu * 10f).roundToInt() / 10f,
                maxCpuTemp = maxCpuTemp,
                maxBatteryTemp = maxBatTemp,
                samples = bufferSamples.toList()
            )

            repository.saveSession(session)
            loadSessions()
        }

        bufferSamples.clear()
        _isRecording.value = false
        _currentRecordingPkg.value = null
        _currentRecordingDurationSec.value = 0L
        _currentSamplesCount.value = 0
    }

    private fun getAppLabel(packageName: String): String {
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }
}
