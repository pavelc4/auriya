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
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.config.TomlParser
import dev.auriya.shared.model.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppInfoItem(val packageName: String, val label: String)

data class SystemInfo(
    val version: String = "...",
    val commit: String = "...",
    val arch: String = "...",
    val deviceArch: String = "...",
    val updateTime: String = "...",
    val profile: String = "...",
    val kernel: String = "...",
    val chipset: String = "...",
    val codename: String = "...",
    val sdk: String = "...",
    val battery: String = "...",
    val temp: String = "...",
    val daemonStatus: String = "stopped",
    val pid: String? = null,
    val ram: String = "-",
)

class UiViewModel(application: Application) : AndroidViewModel(application) {

    private val benchmarkRecorder = BenchmarkRecorder.getInstance(application)

    private val _liveStats = MutableStateFlow<Stats?>(null)
    val liveStats: StateFlow<Stats?> = _liveStats.asStateFlow()

    val isRecording: StateFlow<Boolean> = benchmarkRecorder.isRecording
    val currentRecordingDurationSec: StateFlow<Long> = benchmarkRecorder.currentRecordingDurationSec
    val currentSamplesCount: StateFlow<Int> = benchmarkRecorder.currentSamplesCount
    val benchmarkSessions: StateFlow<List<BenchmarkSession>> = benchmarkRecorder.sessions

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _gameList = MutableStateFlow(GameList())
    val gameList: StateFlow<GameList> = _gameList.asStateFlow()

    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    private val _daemonActive = MutableStateFlow(false)
    val daemonActive: StateFlow<Boolean> = _daemonActive.asStateFlow()

    private val _currentProfile = MutableStateFlow("2")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    private val _foregroundApp = MutableStateFlow<String?>(null)
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

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

    fun loadInstalledApps(pm: PackageManager) {
        if (_installedApps.value.isNotEmpty() || _isAppsLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isAppsLoading.value = true
            try {
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { appInfo ->
                        val label = runCatching {
                            pm.getApplicationLabel(appInfo).toString()
                        }.getOrDefault(appInfo.packageName)
                        AppInfoItem(packageName = appInfo.packageName, label = label)
                    }
                    .sortedBy { it.label.lowercase() }
                _installedApps.value = apps
                _isAppsLoading.value = false

                // Pre-cache all icons in RAM in background so every icon renders instantly
                for (app in apps) {
                    AppIconCache.load(pm, app.packageName)
                }
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

    fun refresh(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootShell.hasRoot()
            _hasRoot.value = root
            if (root) {
                loadAvailableGovernors()
                loadConfigurations()
                runCatching { pollOnce() }
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
        // Sysfs is world-readable for governor list, but reading via
        // RootShell keeps a single code path for /sys access.
        val raw = RootShell.run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null")
        val parsed = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        _availableGovernors.value = parsed.ifEmpty {
            // Fallback so the dropdown is never empty on weirder kernels.
            listOf("performance", "schedutil", "powersave")
        }
    }

    fun loadConfigurations() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                RootShell.readText(ConfigPaths.SETTINGS_FILE)?.let {
                    _settings.value = TomlParser.parseSettings(it)
                }
                RootShell.readText(ConfigPaths.GAMELIST_FILE)?.let {
                    _gameList.value = TomlParser.parseGameList(it)
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun initSystemInfoStatic() {
        viewModelScope.launch(Dispatchers.IO) {
            val modPath = "/data/adb/modules/auriya"
            val out = RootShell.run(
                """
                grep "^version=" $modPath/module.prop | cut -d= -f2; echo "|||";
                grep "^versionCode=" $modPath/module.prop | cut -d= -f2; echo "|||";
                getprop ro.product.cpu.abi; echo "|||";
                stat -c %Y $modPath/module.prop
                """.trimIndent(),
            )
            if (out.isEmpty()) return@launch
            val parts = out.split("|||").map { it.trim() }
            val version = parts.getOrNull(0)?.ifEmpty { "Unknown" } ?: "Unknown"
            val commit = parts.getOrNull(1)?.ifEmpty { "Unknown" } ?: "Unknown"
            var arch = parts.getOrNull(2)?.ifEmpty { "Unknown" } ?: "Unknown"
            arch = when {
                arch.contains("arm64") -> "v8a"
                arch.contains("armeabi") -> "v7a"
                arch.contains("x86_64") -> "x64"
                arch.contains("x86") -> "x86"
                else -> arch
            }

            var updateTimeStr = "Unknown"
            parts.getOrNull(3)?.toLongOrNull()?.let { modTime ->
                val diff = (System.currentTimeMillis() / 1000) - modTime
                updateTimeStr = when {
                    diff < 3600 -> "Updated ${diff / 60}m ago"
                    diff < 86400 -> "Updated ${diff / 3600}h ago"
                    else -> "Updated ${diff / 86400}d ago"
                }
            }

            _systemInfo.value = _systemInfo.value.copy(
                version = version,
                commit = commit,
                arch = arch,
                deviceArch = arch,
                updateTime = updateTimeStr,
            )
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (!_isActive.value) {
                    delay(500)
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
        val configPath = "/data/adb/.config/auriya"
        // Pull the active mode from [daemon].default_mode in settings.toml.
        // The legacy /current_profile (1/2/3 codes) is no longer the
        // source of truth — user edits settings.toml directly.
        val cmd = """
            awk '/^\[daemon\]/{flag=1;next}/^\[/{flag=0}flag && /default_mode/{gsub(/.*= *"/,"");gsub(/".*/,"");print;exit}' $configPath/settings.toml 2>/dev/null; echo "|||";
            uname -r 2>/dev/null; echo "|||";
            getprop ro.board.platform; echo "|||";
            getprop ro.product.device; echo "|||";
            getprop ro.build.version.sdk; echo "|||";
            cat /sys/class/power_supply/battery/capacity 2>/dev/null; echo "|||";
            cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 5; echo "|||";
            PID=${'$'}(pidof auriya || echo "null"); echo ${'$'}PID; echo "|||";
            if [ "${'$'}PID" != "null" ]; then grep VmRSS /proc/${'$'}PID/status 2>/dev/null | awk '{print ${'$'}2}'; else echo "-"; fi
        """.trimIndent()

        val out = RootShell.run(cmd)
        if (out.isNotEmpty()) {
            val parts = out.split("|||").map { it.trim() }
            val rawProfile = parts.getOrNull(0) ?: ""
            // settings.toml stores the mode as a TOML string
            // ("balance" / "performance" / "powersave" / "fast"). The
            // old 0..3 numeric mapping is gone — just title-case it.
            val profileStr = when (rawProfile.lowercase()) {
                "performance" -> "Performance"
                "balance" -> "Balance"
                "powersave" -> "Powersave"
                "fast" -> "Fast"
                "" -> "Unknown"
                else -> rawProfile.replaceFirstChar { it.uppercase() }
            }

            val kernel = parts.getOrNull(1)?.ifEmpty { "Unknown" } ?: "Unknown"
            val chipset = parts.getOrNull(2)?.ifEmpty { "Unknown" } ?: "Unknown"
            val codename = parts.getOrNull(3)?.ifEmpty { "Unknown" } ?: "Unknown"
            val sdk = parts.getOrNull(4)?.ifEmpty { "Unknown" } ?: "Unknown"

            val batteryPercent = parts.getOrNull(5)
            val battery =
                if (batteryPercent != null && batteryPercent.toIntOrNull() != null) "$batteryPercent%" else "Unknown"

            var temp = "Unknown"
            parts.getOrNull(6)?.split("\n")?.forEach { t ->
                val v = t.trim().toIntOrNull()
                if (v != null && v > 1000) {
                    temp = "${v / 1000}°C"
                    return@forEach
                }
            }

            val pid = parts.getOrNull(7)?.ifEmpty { "null" } ?: "null"
            val rss = parts.getOrNull(8)

            val daemonActiveBool = pid != "null" && pid.isNotEmpty()
            _daemonActive.value = daemonActiveBool

            val ram = if (daemonActiveBool && rss != null && rss != "-" && rss.toIntOrNull() != null) {
                "${String.format("%.1f", rss.toDouble() / 1024.0)} MB"
            } else {
                "-"
            }

            _systemInfo.value = _systemInfo.value.copy(
                profile = profileStr,
                kernel = kernel,
                chipset = chipset,
                codename = codename,
                sdk = sdk,
                battery = battery,
                temp = temp,
                pid = if (pid == "null") null else pid,
                daemonStatus = if (daemonActiveBool) "working" else "stopped",
                ram = ram,
            )
            if (rawProfile.isNotEmpty()) _currentProfile.value = rawProfile
        }

        // Foreground app from status file (root-only read).
        RootShell.readText(ConfigPaths.STATUS_FILE)?.let { contents ->
            val focusedAppLine = contents.lineSequence().firstOrNull { it.startsWith("focused_app") }
            _foregroundApp.value = focusedAppLine
                ?.split(" ")
                ?.filter { it.isNotEmpty() }
                ?.getOrNull(1)
        }

        // Daemon log tail.
        val logPath = "/data/adb/auriya/daemon.log"
        if (RootShell.exists(logPath)) {
            _logs.value = RootShell.tail(logPath, 100)
        } else {
            _logs.value = "No daemon log at $logPath"
        }

        // Live performance stats & telemetry for Recording / Benchmark
        runCatching {
            val stats = StatsParser.fetchStats()
            _liveStats.value = stats
            stats?.let { benchmarkRecorder.processStats(it) }
        }
    }

    fun startRecording(pkg: String? = null, profile: String = "balance") {
        benchmarkRecorder.startManualRecording(pkg, profile)
    }

    fun stopRecording() {
        benchmarkRecorder.stopManualRecording()
    }

    fun deleteBenchmarkSession(id: String) {
        benchmarkRecorder.deleteSession(id)
    }

    fun clearAllBenchmarkSessions() {
        benchmarkRecorder.clearAllSessions()
    }

    fun refreshBenchmarkSessions() {
        benchmarkRecorder.loadSessions()
    }

    fun updateProfile(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val modeString = when (mode.lowercase()) {
                "1", "performance" -> "performance"
                "2", "balance" -> "balance"
                "3", "powersave" -> "powersave"
                "4", "fast" -> "fast"
                else -> "balance"
            }

            // 1. Notify the daemon immediately via UDS Unix Socket
            RootShell.exec("echo 'SET_PROFILE ${modeString.uppercase()}' | nc -U /dev/socket/auriya.sock")

            // 2. Persist profile choice directly to settings.toml
            val current = _settings.value
            val updated = current.copy(
                daemon = current.daemon.copy(defaultMode = modeString),
                fas = current.fas.copy(defaultMode = modeString)
            )
            val content = TomlParser.serializeSettings(updated)
            if (RootShell.writeText(ConfigPaths.SETTINGS_FILE, content)) {
                _settings.value = updated
            }

            // 3. Keep current profile state updated in UI
            _currentProfile.value = mode

            // 4. Poll immediately to update systemInfo profile state on the UI
            runCatching { pollOnce() }
        }
    }

    fun saveSettings(newSettings: Settings) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = TomlParser.serializeSettings(newSettings)
            if (RootShell.writeText(ConfigPaths.SETTINGS_FILE, content)) {
                _settings.value = newSettings
            }
            val gov = newSettings.cpu.defaultGovernor
            RootShell.exec(
                "for p in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \"\$p\"; done",
            )
            RootShell.exec(
                "for p in /sys/devices/system/cpu/cpufreq/policy*/scaling_governor; do echo $gov > \"\$p\"; done",
            )
        }
    }

    fun addGame(profile: GameProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val games = _gameList.value.games.toMutableList().also {
                it.removeAll { g -> g.packageName == profile.packageName }
                it.add(profile)
            }
            val newList = GameList(games)
            val content = TomlParser.serializeGameList(newList)
            if (RootShell.writeText(ConfigPaths.GAMELIST_FILE, content)) {
                _gameList.value = newList
            }
            RootShell.exec("echo 'ADD_GAME ${profile.packageName}' | nc -U /dev/socket/auriya.sock")
        }
    }

    fun removeGame(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val games = _gameList.value.games.toMutableList().also {
                it.removeAll { g -> g.packageName == packageName }
            }
            val newList = GameList(games)
            val content = TomlParser.serializeGameList(newList)
            if (RootShell.writeText(ConfigPaths.GAMELIST_FILE, content)) {
                _gameList.value = newList
            }
            RootShell.exec("echo 'REMOVE_GAME $packageName' | nc -U /dev/socket/auriya.sock")
        }
    }

    fun restartDaemon() {
        viewModelScope.launch(Dispatchers.IO) {
            RootShell.exec("/data/adb/modules/auriya/system/bin/auriyactl restart")
        }
    }
}
