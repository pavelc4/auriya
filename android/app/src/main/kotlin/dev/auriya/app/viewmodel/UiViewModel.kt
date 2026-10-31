package dev.auriya.app.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.auriya.app.data.AppIconCache
import dev.auriya.app.data.RootShell
import dev.auriya.app.data.stats.BenchmarkRecorder
import dev.auriya.app.data.stats.BenchmarkSession
import dev.auriya.app.data.stats.Stats
import dev.auriya.app.data.stats.StatsParser
import dev.auriya.app.service.OverlayService
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.config.TomlParser
import dev.auriya.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class AppInfoItem(
    val packageName: String,
    val label: String,
)

data class SystemInfo(
    val version: String = "...",
    val commit: String = "...",
    val arch: String = "...",
    val deviceArch: String = "...",
    val updateTime: String = "...",
    val profile: String = "...",
    val kernel: String = System.getProperty("os.version") ?: "...",
    val chipset: String =
        android.os.Build.BOARD
            .ifEmpty { android.os.Build.HARDWARE },
    val codename: String = android.os.Build.DEVICE,
    val sdk: String =
        android.os.Build.VERSION.SDK_INT
            .toString(),
    val androidVersion: String = "Android ${android.os.Build.VERSION.RELEASE}",
    val battery: String = "...",
    val temp: String = "...",
    val daemonStatus: String = "stopped",
    val pid: String? = null,
    val ram: String = "-",
)

class UiViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val benchmarkRecorder = BenchmarkRecorder.getInstance(application)
    private val appPrefs =
        dev.auriya.app.data.AppPrefs
            .getInstance(application)

    val liveStats: StateFlow<Stats?> = benchmarkRecorder.currentStats

    val roundFps: StateFlow<Boolean> = appPrefs.roundFps

    val isRecording: StateFlow<Boolean> = benchmarkRecorder.isRecording
    val currentRecordingDurationSec: StateFlow<Long> = benchmarkRecorder.currentRecordingDurationSec
    val currentSamplesCount: StateFlow<Int> = benchmarkRecorder.currentSamplesCount
    val benchmarkSessions: StateFlow<List<BenchmarkSession>> = benchmarkRecorder.sessions

    fun startUiObserving() {
        benchmarkRecorder.startUiObserving()
    }

    fun stopUiObserving() {
        benchmarkRecorder.stopUiObserving()
    }

    fun setRoundFps(enabled: Boolean) {
        appPrefs.setRoundFps(enabled)
    }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _gameList = MutableStateFlow(GameList())
    val gameList: StateFlow<GameList> = _gameList.asStateFlow()

    private val _thermalAvailable = MutableStateFlow(false)
    val thermalAvailable: StateFlow<Boolean> = _thermalAvailable.asStateFlow()

    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    private val _daemonActive = MutableStateFlow(false)
    val daemonActive: StateFlow<Boolean> = _daemonActive.asStateFlow()

    private val _currentProfile = MutableStateFlow("2")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _hasRoot = MutableStateFlow(false)
    val hasRoot: StateFlow<Boolean> = _hasRoot.asStateFlow()

    private val _isCheckingRoot = MutableStateFlow(false)
    val isCheckingRoot: StateFlow<Boolean> = _isCheckingRoot.asStateFlow()

    // Set to true when checkRoot() is called while a check is already in flight.
    // The running coroutine reads this in its finally block and immediately
    // fires one more check — covering the "press button → go to manager →
    // grant → come back" workflow without needing a polling loop.
    private val _pendingRootRetry = AtomicBoolean(false)

    private val _availableGovernors = MutableStateFlow<List<String>>(emptyList())
    val availableGovernors: StateFlow<List<String>> = _availableGovernors.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val installedApps: StateFlow<List<AppInfoItem>> = _installedApps.asStateFlow()

    private val _isAppsLoading = MutableStateFlow(false)
    val isAppsLoading: StateFlow<Boolean> = _isAppsLoading.asStateFlow()

    private var saveSettingsJob: Job? = null
    private var updateProfileJob: Job? = null
    private var gameListJob: Job? = null

    fun loadInstalledApps(pm: PackageManager) {
        if (_installedApps.value.isNotEmpty() || _isAppsLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isAppsLoading.value = true
            try {
                val apps =
                    pm
                        .getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                        .map { appInfo ->
                            val label =
                                runCatching {
                                    pm.getApplicationLabel(appInfo).toString()
                                }.getOrDefault(appInfo.packageName)
                            AppInfoItem(packageName = appInfo.packageName, label = label)
                        }.sortedBy { it.label.lowercase() }
                _installedApps.value = apps
                _isAppsLoading.value = false
            } catch (e: Throwable) {
                e.printStackTrace()
                _isAppsLoading.value = false
            }
        }
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    fun checkRoot() {
        if (_isCheckingRoot.value) {
            // A check is already blocking in Shell.getShell(). Schedule a retry
            // so it fires the moment that check finishes. This covers the common
            // workflow: user presses Grant → switches to SU manager → grants
            // always-allow → returns to app (ON_RESUME) while the original
            // Shell.getShell() is still waiting for its 10-second timeout.
            _pendingRootRetry.set(true)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingRoot.value = true
            try {
                val root = RootShell.hasRoot()
                _hasRoot.value = root
                if (root) {
                    loadAvailableGovernors()
                    loadConfigurations()
                    initSystemInfoStatic()
                }
            } finally {
                _isCheckingRoot.value = false
                // If a retry was queued (e.g. ON_RESUME fired while we were
                // blocked), run one more check now. Root may be already granted
                // in the manager so Shell.getShell() will return instantly.
                if (_pendingRootRetry.getAndSet(false) && !_hasRoot.value) {
                    checkRoot()
                }
            }
        }
    }

    /** Passive poll — checks only the cached shell, never triggers a new SU prompt. */
    fun checkRootPassive() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootShell.hasCachedRoot()
            if (root && !_hasRoot.value) {
                _hasRoot.value = true
                loadAvailableGovernors()
                loadConfigurations()
                initSystemInfoStatic()
            }
        }
    }

    fun refresh(
        pm: PackageManager? = null,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val root = RootShell.hasRoot()
            _hasRoot.value = root
            if (root) {
                loadAvailableGovernors()
                loadConfigurationsSync()
                initSystemInfoStaticSync()
                runCatching { pollOnce() }
            }
            val targetPm = pm ?: getApplication<Application>().packageManager
            runCatching {
                val apps =
                    targetPm
                        .getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                        .map { appInfo ->
                            val label =
                                runCatching {
                                    targetPm.getApplicationLabel(appInfo).toString()
                                }.getOrDefault(appInfo.packageName)
                            AppInfoItem(packageName = appInfo.packageName, label = label)
                        }.sortedBy { it.label.lowercase() }
                _installedApps.value = apps
            }
            runCatching {
                benchmarkRecorder.loadSessions()
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 650) {
                kotlinx.coroutines.delay(650 - elapsed)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    init {
        checkRoot()
        loadConfigurations()
        initSystemInfoStatic()
        startMonitoring()
    }

    private fun loadAvailableGovernors() {
        val stats = dev.auriya.app.data.stats.StatsParser.fetchStats()
        if (stats?.cpu?.available_governors?.isNotEmpty() == true) {
            _availableGovernors.value = stats.cpu.available_governors
            return
        }

        val candidate = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
        if (candidate.exists() && candidate.canRead()) {
            val list = candidate.readText().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
            if (list.isNotEmpty()) {
                _availableGovernors.value = list
                return
            }
        }

        _availableGovernors.value = listOf("performance", "schedutil", "powersave")
    }

    private fun loadConfigurationsSync() {
        val stats = dev.auriya.app.data.stats.StatsParser.fetchStats()
        _thermalAvailable.value = stats?.thermal?.sconfig_supported
            ?: java.io.File("/sys/class/thermal/thermal_message/sconfig").exists()
        runCatching {
            RootShell.readText(ConfigPaths.SETTINGS_FILE)?.let {
                _settings.value = TomlParser.parseSettings(it)
            }
            RootShell.readText(ConfigPaths.GAMELIST_FILE)?.let {
                _gameList.value = TomlParser.parseGameList(it)
            }
        }.onFailure { it.printStackTrace() }
    }

    fun loadConfigurations() {
        viewModelScope.launch(Dispatchers.IO) {
            loadConfigurationsSync()
        }
    }

    private fun initSystemInfoStatic() {
        viewModelScope.launch(Dispatchers.IO) {
            initSystemInfoStaticSync()
        }
    }

    private fun initSystemInfoStaticSync() {
        val stats = dev.auriya.app.data.stats.StatsParser.fetchStats()
        var version = stats?.daemon?.version?.ifEmpty { null }
        var commit = stats?.daemon?.commit?.ifEmpty { null }
        var arch = stats?.daemon?.arch?.ifEmpty { null }
        var modTime = stats?.daemon?.mod_updated_secs

        if (version == null || commit == null || arch == null) {
            val modProp = java.io.File("/data/adb/modules/auriya/module.prop")
            val content =
                if (modProp.canRead()) {
                    runCatching { modProp.readText() }.getOrNull()
                } else {
                    RootShell.readText("/data/adb/modules/auriya/module.prop")
                }

            if (content != null) {
                for (line in content.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("version=")) {
                        version = trimmed.substringAfter("version=").trim()
                    } else if (trimmed.startsWith("versionCode=")) {
                        val vc = trimmed.substringAfter("versionCode=").trim()
                        if (commit == null) commit = vc
                    }
                }
                if (modTime == null && modProp.exists()) {
                    val m = modProp.lastModified()
                    if (m > 0) modTime = m / 1000
                }
            }
        }

        val primaryAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        arch =
            arch ?: when {
                primaryAbi.contains("arm64") -> "v8a"
                primaryAbi.contains("armeabi") -> "v7a"
                primaryAbi.contains("x86_64") -> "x64"
                primaryAbi.contains("x86") -> "x86"
                else -> primaryAbi
            }

        var updateTimeStr = "Unknown"
        modTime?.let { timeSecs ->
            val diff = (System.currentTimeMillis() / 1000) - timeSecs
            updateTimeStr =
                when {
                    diff < 3600 -> "Updated ${diff / 60}m ago"
                    diff < 86400 -> "Updated ${diff / 3600}h ago"
                    else -> "Updated ${diff / 86400}d ago"
                }
        }

        _systemInfo.value =
            _systemInfo.value.copy(
                version = version ?: "Unknown",
                commit = commit ?: "Unknown",
                arch = arch,
                deviceArch = arch,
                updateTime = updateTimeStr,
                kernel = System.getProperty("os.version") ?: "Unknown",
                chipset =
                    android.os.Build.BOARD
                        .ifEmpty { android.os.Build.HARDWARE },
                codename = android.os.Build.DEVICE,
                sdk =
                    android.os.Build.VERSION.SDK_INT
                        .toString(),
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            )
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (!_isActive.value) {
                    delay(1000)
                    continue
                }
                val cachedRoot = RootShell.hasCachedRoot()
                if (cachedRoot && !_hasRoot.value) {
                    _hasRoot.value = true
                    loadAvailableGovernors()
                    loadConfigurations()
                    initSystemInfoStatic()
                }
                runCatching { pollOnce() }.onFailure { it.printStackTrace() }
                delay(2000)
            }
        }
    }

    private fun pollOnce() {
        val stats = dev.auriya.app.data.stats.StatsParser.fetchStats()

        // Battery query via Android system service as fallback
        val bm =
            getApplication<Application>()
                .getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val sysBattery = if (pct in 0..100) "$pct%" else "Unknown"

        if (stats != null) {
            if (stats.cpu?.available_governors?.isNotEmpty() == true) {
                _availableGovernors.value = stats.cpu.available_governors
            }
            _thermalAvailable.value = stats.thermal.sconfig_supported
            val profileStr = stats.session.profile.replaceFirstChar { it.uppercase() }
            val battery = stats.battery.pct?.let { "$it%" } ?: sysBattery
            val temp = stats.thermal.cpu_c?.let { "${it.toInt()}°C" }
                ?: stats.thermal.battery_c?.let { "${it.toInt()}°C" }
                ?: "Unknown"
            val pid = stats.daemon?.pid?.takeIf { it > 0L }?.toString()
            val ram = stats.daemon?.rss_mb?.let {
                String.format(java.util.Locale.US, "%.1f MB", it)
            } ?: "-"

            val daemonVersion = stats.daemon?.version?.ifEmpty { null } ?: _systemInfo.value.version
            val daemonCommit = stats.daemon?.commit?.ifEmpty { null } ?: _systemInfo.value.commit
            val daemonArch = stats.daemon?.arch?.ifEmpty { null } ?: _systemInfo.value.arch
            val modTime = stats.daemon?.mod_updated_secs
            val updateTimeStr =
                modTime?.let { timeSecs ->
                    val diff = (System.currentTimeMillis() / 1000) - timeSecs
                    when {
                        diff < 3600 -> "Updated ${diff / 60}m ago"
                        diff < 86400 -> "Updated ${diff / 3600}h ago"
                        else -> "Updated ${diff / 86400}d ago"
                    }
                } ?: _systemInfo.value.updateTime

            _daemonActive.value = true
            _systemInfo.value =
                _systemInfo.value.copy(
                    version = daemonVersion,
                    commit = daemonCommit,
                    arch = daemonArch,
                    deviceArch = daemonArch,
                    updateTime = updateTimeStr,
                    profile = profileStr,
                    battery = battery,
                    temp = temp,
                    pid = pid,
                    daemonStatus = "working",
                    ram = ram,
                )
            _currentProfile.value = stats.session.profile.lowercase()
        } else {
            val currentProfileFile = RootShell.readText(ConfigPaths.CURRENT_PROFILE_FILE)?.trim()
            val rawProfile =
                if (!currentProfileFile.isNullOrEmpty()) {
                    when (currentProfileFile) {
                        "1" -> "Performance"
                        "2" -> "Balance"
                        "3" -> "Powersave"
                        "4" -> "Fast"
                        else -> currentProfileFile.replaceFirstChar { it.uppercase() }
                    }
                } else {
                    val fromSettings =
                        _settings.value.daemon.defaultMode
                            .ifEmpty { _currentProfile.value }
                    when (fromSettings.lowercase()) {
                        "performance" -> "Performance"
                        "balance" -> "Balance"
                        "powersave" -> "Powersave"
                        "fast" -> "Fast"
                        "" -> "Unknown"
                        else -> fromSettings.replaceFirstChar { it.uppercase() }
                    }
                }

            _daemonActive.value = false
            _systemInfo.value =
                _systemInfo.value.copy(
                    profile = rawProfile,
                    battery = sysBattery,
                    temp = "Unknown",
                    pid = null,
                    daemonStatus = "stopped",
                    ram = "-",
                )
            if (rawProfile.isNotEmpty()) _currentProfile.value = rawProfile.lowercase()
        }
    }

    fun refreshLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val logPath = "/data/adb/auriya/daemon.log"
            _logs.value =
                if (RootShell.exists(logPath)) {
                    RootShell.tail(logPath, 100)
                } else {
                    "No daemon log at $logPath"
                }
        }
    }

    fun startRecording(
        pkg: String? = null,
        profile: String = "balance",
    ) {
        benchmarkRecorder.startManualRecording(pkg, profile)
    }

    fun stopRecording() {
        benchmarkRecorder.stopManualRecording()
    }

    fun deleteBenchmarkSession(id: String) {
        benchmarkRecorder.deleteSession(id)
    }

    fun deleteBenchmarkSessions(ids: Set<String>) {
        benchmarkRecorder.deleteSessions(ids)
    }

    fun clearAllBenchmarkSessions() {
        benchmarkRecorder.clearAllSessions()
    }

    fun refreshBenchmarkSessions() {
        benchmarkRecorder.loadSessions()
    }

    fun updateProfile(mode: String) {
        val modeString =
            when (mode.lowercase()) {
                "1", "performance" -> "performance"
                "2", "balance" -> "balance"
                "3", "powersave" -> "powersave"
                "4", "fast" -> "fast"
                else -> "balance"
            }
        _currentProfile.value = modeString
        val current = _settings.value
        val updated =
            current.copy(
                daemon = current.daemon.copy(defaultMode = modeString),
                fas = current.fas.copy(defaultMode = modeString),
            )
        _settings.value = updated
        _systemInfo.value =
            _systemInfo.value.copy(
                profile = modeString.replaceFirstChar { it.uppercase() },
            )

        updateProfileJob?.cancel()
        updateProfileJob =
            viewModelScope.launch(Dispatchers.IO) {
                val content = TomlParser.serializeSettings(updated)
                RootShell.writeText(ConfigPaths.SETTINGS_FILE, content)
                RootShell.exec("${ConfigPaths.AURIYACTL_BIN} set-profile $modeString")
            }
    }

    fun saveSettings(newSettings: Settings) {
        _settings.value = newSettings
        val gov = newSettings.cpu.defaultGovernor
        saveSettingsJob?.cancel()
        saveSettingsJob =
            viewModelScope.launch(Dispatchers.IO) {
                val content = TomlParser.serializeSettings(newSettings)
                RootShell.writeText(ConfigPaths.SETTINGS_FILE, content)
                RootShell.exec("${ConfigPaths.AURIYACTL_BIN} reload")
            }
    }

    fun addGame(profile: GameProfile) {
        val games =
            _gameList.value.games.toMutableList().also {
                it.removeAll { g -> g.packageName == profile.packageName }
                it.add(profile)
            }
        val newList = GameList(games)
        _gameList.value = newList

        gameListJob?.cancel()
        gameListJob =
            viewModelScope.launch(Dispatchers.IO) {
                val content = TomlParser.serializeGameList(newList)
                RootShell.writeText(ConfigPaths.GAMELIST_FILE, content)
                RootShell.exec("${ConfigPaths.AURIYACTL_BIN} reload")
            }
    }

    fun removeGame(packageName: String) {
        val games =
            _gameList.value.games.toMutableList().also {
                it.removeAll { g -> g.packageName == packageName }
            }
        val newList = GameList(games)
        _gameList.value = newList

        gameListJob?.cancel()
        gameListJob =
            viewModelScope.launch(Dispatchers.IO) {
                val content = TomlParser.serializeGameList(newList)
                RootShell.writeText(ConfigPaths.GAMELIST_FILE, content)
                RootShell.exec("${ConfigPaths.AURIYACTL_BIN} reload")
            }
    }

    fun restartDaemon() {
        viewModelScope.launch(Dispatchers.IO) {
            RootShell.exec("/data/adb/modules/auriya/system/bin/auriyactl restart")
        }
    }
}
