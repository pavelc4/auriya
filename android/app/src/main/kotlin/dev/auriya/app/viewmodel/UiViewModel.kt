package dev.auriya.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.auriya.shared.config.ConfigPaths
import dev.auriya.shared.config.TomlParser
import dev.auriya.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
    val ram: String = "-"
)

class UiViewModel : ViewModel() {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _gameList = MutableStateFlow(GameList())
    val gameList: StateFlow<GameList> = _gameList.asStateFlow()

    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    private val _daemonActive = MutableStateFlow(false)
    val daemonActive: StateFlow<Boolean> = _daemonActive.asStateFlow()

    private val _currentProfile = MutableStateFlow("2") // Default to Balance ("2")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    private val _foregroundApp = MutableStateFlow<String?>(null)
    val foregroundApp: StateFlow<String?> = _foregroundApp.asStateFlow()

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    init {
        loadConfigurations()
        initSystemInfoStatic()
        startMonitoring()
    }

    fun loadConfigurations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settingsFile = File(ConfigPaths.SETTINGS_FILE)
                if (settingsFile.exists()) {
                    val content = settingsFile.readText()
                    _settings.value = TomlParser.parseSettings(content)
                }

                val gamelistFile = File(ConfigPaths.GAMELIST_FILE)
                if (gamelistFile.exists()) {
                    val content = gamelistFile.readText()
                    _gameList.value = TomlParser.parseGameList(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun executeShellCommand(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = process.inputStream.bufferedReader()
            val output = reader.use { it.readText() }.trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun initSystemInfoStatic() {
        viewModelScope.launch(Dispatchers.IO) {
            val modPath = "/data/adb/modules/auriya"
            val cmd1 = """
                grep "^version=" $modPath/module.prop | cut -d= -f2; echo "|||";
                grep "^versionCode=" $modPath/module.prop | cut -d= -f2; echo "|||";
                getprop ro.product.cpu.abi; echo "|||";
                stat -c %Y $modPath/module.prop
            """.trimIndent()

            val output = executeShellCommand(cmd1)
            if (output.isNotEmpty()) {
                val parts = output.split("|||").map { it.trim() }
                val version = parts.getOrNull(0)?.ifEmpty { "Unknown" } ?: "Unknown"
                val commit = parts.getOrNull(1)?.ifEmpty { "Unknown" } ?: "Unknown"
                var arch = parts.getOrNull(2)?.ifEmpty { "Unknown" } ?: "Unknown"

                if (arch.contains("arm64")) arch = "v8a"
                else if (arch.contains("armeabi")) arch = "v7a"
                else if (arch.contains("x86_64")) arch = "x64"
                else if (arch.contains("x86")) arch = "x86"

                var updateTimeStr = "Unknown"
                val modTime = parts.getOrNull(3)
                if (modTime != null && modTime.toLongOrNull() != null) {
                    val diff = (System.currentTimeMillis() / 1000) - modTime.toLong()
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
                    updateTime = updateTimeStr
                )
            }
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (!_isActive.value) {
                    delay(500)
                    continue
                }
                try {
                    val configPath = "/data/adb/.config/auriya"
                    val cmd2 = """
                        cat $configPath/current_profile 2>/dev/null; echo "|||";
                        uname -r 2>/dev/null; echo "|||";
                        getprop ro.board.platform; echo "|||";
                        getprop ro.product.device; echo "|||";
                        getprop ro.build.version.sdk; echo "|||";
                        cat /sys/class/power_supply/battery/capacity 2>/dev/null; echo "|||";
                        cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 5; echo "|||";
                        PID=$(pidof auriya || echo "null"); echo ${'$'}PID; echo "|||";
                        if [ "${'$'}PID" != "null" ]; then grep VmRSS /proc/${'$'}PID/status 2>/dev/null | awk '{print ${'$'}2}'; else echo "-"; fi
                    """.trimIndent()

                    val output = executeShellCommand(cmd2)
                    if (output.isNotEmpty()) {
                        val parts = output.split("|||").map { it.trim() }
                        val rawProfile = parts.getOrNull(0) ?: ""
                        val profiles = mapOf(
                            "0" to "Init",
                            "1" to "Performance",
                            "2" to "Balance",
                            "3" to "Powersave"
                        )
                        val profileStr = profiles[rawProfile] ?: "Unknown"

                        val kernel = parts.getOrNull(1)?.ifEmpty { "Unknown" } ?: "Unknown"
                        val chipset = parts.getOrNull(2)?.ifEmpty { "Unknown" } ?: "Unknown"
                        val codename = parts.getOrNull(3)?.ifEmpty { "Unknown" } ?: "Unknown"
                        val sdk = parts.getOrNull(4)?.ifEmpty { "Unknown" } ?: "Unknown"

                        val batteryPercent = parts.getOrNull(5)
                        val battery =
                            if (batteryPercent != null && batteryPercent.toIntOrNull() != null) "$batteryPercent%" else "Unknown"

                        var temp = "Unknown"
                        val rawTemp = parts.getOrNull(6)
                        if (rawTemp != null && rawTemp.isNotEmpty()) {
                            val lines = rawTemp.split("\n")
                            for (t in lines) {
                                val v = t.trim().toIntOrNull()
                                if (v != null && v > 1000) {
                                    temp = "${v / 1000}°C"
                                    break
                                }
                            }
                        }

                        val pid = parts.getOrNull(7)?.ifEmpty { "null" } ?: "null"
                        val rss = parts.getOrNull(8)

                        val daemonActiveBool = pid != "null" && pid.isNotEmpty()
                        _daemonActive.value = daemonActiveBool
                        val daemonStatus = if (daemonActiveBool) "working" else "stopped"

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
                            daemonStatus = daemonStatus,
                            ram = ram
                        )

                        if (rawProfile.isNotEmpty()) {
                            _currentProfile.value = rawProfile
                        }
                    }

                    // Check foreground app from status file
                    val statusFile = File(ConfigPaths.STATUS_FILE)
                    if (statusFile.exists()) {
                        val lines = statusFile.readLines()
                        val focusedAppLine = lines.firstOrNull { it.startsWith("focused_app") }
                        if (focusedAppLine != null) {
                            val parts = focusedAppLine.split(" ").filter { it.isNotEmpty() }
                            if (parts.size >= 2) {
                                _foregroundApp.value = parts[1]
                            }
                        } else {
                            _foregroundApp.value = null
                        }
                    }

                    // Load last 100 lines of daemon logs if available
                    val logFile = File("${ConfigPaths.CONFIG_DIR}/auriya.log")
                    if (logFile.exists()) {
                        _logs.value = logFile.readLines().takeLast(100).joinToString("\n")
                    } else {
                        // Alternate location from webui
                        val daemonLogFile = File("/data/adb/auriya/daemon.log")
                        if (daemonLogFile.exists()) {
                            _logs.value = daemonLogFile.readLines().takeLast(100).joinToString("\n")
                        } else {
                            _logs.value =
                                "No active logs found at /data/adb/auriya/daemon.log or ${ConfigPaths.CONFIG_DIR}/auriya.log"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000)
            }
        }
    }

    fun updateProfile(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                executeShellCommand("echo 'SET_PROFILE ${getProfileString(mode)}' | nc -U /dev/socket/auriya.sock")
                val profileFile = File(ConfigPaths.CURRENT_PROFILE_FILE)
                profileFile.writeText(mode)
                _currentProfile.value = mode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getProfileString(mode: String): String = when (mode) {
        "1" -> "PERFORMANCE"
        "2" -> "BALANCE"
        "3" -> "POWERSAVE"
        else -> "BALANCE"
    }

    fun saveSettings(newSettings: Settings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settingsFile = File(ConfigPaths.SETTINGS_FILE)
                val content = TomlParser.serializeSettings(newSettings)
                settingsFile.writeText(content)
                _settings.value = newSettings

                val gov = newSettings.cpu.defaultGovernor
                executeShellCommand("for path in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $gov > \"${'$'}path\"; done")
                executeShellCommand("for path in /sys/devices/system/cpu/cpufreq/policy*/scaling_governor; do echo $gov > \"${'$'}path\"; done")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addGame(profile: GameProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentGames = _gameList.value.games.toMutableList()
                currentGames.removeAll { it.packageName == profile.packageName }
                currentGames.add(profile)
                val newList = GameList(currentGames)

                val gamelistFile = File(ConfigPaths.GAMELIST_FILE)
                val content = TomlParser.serializeGameList(newList)
                gamelistFile.writeText(content)
                _gameList.value = newList

                executeShellCommand("echo 'ADD_GAME ${profile.packageName}' | nc -U /dev/socket/auriya.sock")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeGame(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentGames = _gameList.value.games.toMutableList()
                currentGames.removeAll { it.packageName == packageName }
                val newList = GameList(currentGames)

                val gamelistFile = File(ConfigPaths.GAMELIST_FILE)
                val content = TomlParser.serializeGameList(newList)
                gamelistFile.writeText(content)
                _gameList.value = newList

                // Sync with socket
                executeShellCommand("echo 'REMOVE_GAME $packageName' | nc -U /dev/socket/auriya.sock")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restartDaemon() {
        viewModelScope.launch(Dispatchers.IO) {
            executeShellCommand("/data/adb/modules/auriya/system/bin/auriyactl restart")
        }
    }
}
