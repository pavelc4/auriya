package dev.auriya.shared.config

import dev.auriya.shared.model.*

object TomlParser {

    fun parseSettings(content: String): Settings {
        var daemonLogLevel = "info"
        var daemonCheckIntervalMs = 2000L
        var daemonDefaultMode = "balance"

        var cpuDefaultGovernor = "schedutil"

        var dndDefaultEnable = true

        var fasEnabled = true
        var fasDefaultMode = "balance"
        var fasThermalThreshold = 90.0
        var fasPollIntervalMs = 300L
        var fasTargetFps = 60

        val modes = mutableMapOf<String, FasMode>()

        var currentSection = ""
        var currentModeName = ""
        var currentModeMargin: Double? = null
        var currentModeThermalThreshold: Double? = null

        fun commitCurrentMode() {
            if (currentModeName.isNotEmpty() && currentModeMargin != null && currentModeThermalThreshold != null) {
                modes[currentModeName] = FasMode(currentModeMargin!!, currentModeThermalThreshold!!)
            }
        }

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                val sectionName = line.substring(1, line.length - 1).trim()
                if (sectionName.startsWith("modes.")) {
                    commitCurrentMode()
                    currentSection = "modes"
                    currentModeName = sectionName.substringAfter("modes.").trim()
                    currentModeMargin = null
                    currentModeThermalThreshold = null
                } else {
                    commitCurrentMode()
                    currentSection = sectionName
                    currentModeName = ""
                }
                return@forEach
            }

            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()

            when (currentSection) {
                "daemon" -> {
                    when (key) {
                        "log_level" -> daemonLogLevel = parseStringValue(value)
                        "check_interval_ms" -> daemonCheckIntervalMs = value.toLongOrNull() ?: 2000L
                        "default_mode" -> daemonDefaultMode = parseStringValue(value)
                    }
                }
                "cpu" -> {
                    if (key == "default_governor") {
                        cpuDefaultGovernor = parseStringValue(value)
                    }
                }
                "dnd" -> {
                    if (key == "default_enable") {
                        dndDefaultEnable = value.toBooleanStrictOrNull() ?: true
                    }
                }
                "fas" -> {
                    when (key) {
                        "enabled" -> fasEnabled = value.toBooleanStrictOrNull() ?: true
                        "default_mode" -> fasDefaultMode = parseStringValue(value)
                        "thermal_threshold" -> fasThermalThreshold = value.toDoubleOrNull() ?: 90.0
                        "poll_interval_ms" -> fasPollIntervalMs = value.toLongOrNull() ?: 300L
                        "target_fps" -> fasTargetFps = value.toIntOrNull() ?: 60
                    }
                }
                "modes" -> {
                    when (key) {
                        "margin" -> currentModeMargin = value.toDoubleOrNull()
                        "thermal_threshold" -> currentModeThermalThreshold = value.toDoubleOrNull()
                    }
                }
            }
        }
        commitCurrentMode()

        return Settings(
            daemon = DaemonConfig(daemonLogLevel, daemonCheckIntervalMs, daemonDefaultMode),
            cpu = CpuConfig(cpuDefaultGovernor),
            dnd = DndConfig(dndDefaultEnable),
            fas = FasConfig(fasEnabled, fasDefaultMode, fasThermalThreshold, fasPollIntervalMs, fasTargetFps),
            modes = modes
        )
    }

    fun serializeSettings(settings: Settings): String = buildString {
        append("[daemon]\n")
        append("log_level = \"").append(settings.daemon.logLevel).append("\"\n")
        append("check_interval_ms = ").append(settings.daemon.checkIntervalMs).append("\n")
        append("default_mode = \"").append(settings.daemon.defaultMode).append("\"\n\n")

        append("[cpu]\n")
        append("default_governor = \"").append(settings.cpu.defaultGovernor).append("\"\n\n")

        append("[dnd]\n")
        append("default_enable = ").append(settings.dnd.defaultEnable).append("\n\n")

        append("[fas]\n")
        append("enabled = ").append(settings.fas.enabled).append("\n")
        append("default_mode = \"").append(settings.fas.defaultMode).append("\"\n")
        append("thermal_threshold = ").append(settings.fas.thermalThreshold).append("\n")
        append("poll_interval_ms = ").append(settings.fas.pollIntervalMs).append("\n")
        append("target_fps = ").append(settings.fas.targetFps).append("\n\n")

        settings.modes.forEach { (name, mode) ->
            append("[modes.").append(name).append("]\n")
            append("margin = ").append(mode.margin).append("\n")
            append("thermal_threshold = ").append(mode.thermalThreshold).append("\n\n")
        }
    }

    fun parseGameList(content: String): GameList {
        val games = mutableListOf<GameProfile>()
        var currentPkg = ""
        var currentGov = ""
        var currentDnd = false
        var currentFps: Int? = null
        var currentRate: Int? = null
        var currentMode: String? = null
        var currentLockRotation = false

        fun commitCurrentGame() {
            if (currentPkg.isNotEmpty()) {
                games.add(
                    GameProfile(
                        packageName = currentPkg,
                        cpuGovernor = currentGov.ifEmpty { "schedutil" },
                        enableDnd = currentDnd,
                        targetFps = currentFps,
                        refreshRate = currentRate,
                        mode = currentMode,
                        lockRotation = currentLockRotation,
                    )
                )
            }
        }

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            if (line == "[[game]]") {
                commitCurrentGame()
                currentPkg = ""
                currentGov = ""
                currentDnd = false
                currentFps = null
                currentRate = null
                currentMode = null
                currentLockRotation = false
                return@forEach
            }

            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) return@forEach
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()

            when (key) {
                "package" -> currentPkg = parseStringValue(value)
                "cpu_governor" -> currentGov = parseStringValue(value)
                "enable_dnd" -> currentDnd = value.toBooleanStrictOrNull() ?: false
                "target_fps" -> currentFps = value.toIntOrNull()
                "refresh_rate" -> currentRate = value.toIntOrNull()
                "mode" -> currentMode = parseStringValue(value)
                "lock_rotation" -> currentLockRotation = value.toBooleanStrictOrNull() ?: false
            }
        }
        commitCurrentGame()

        return GameList(games)
    }

    fun serializeGameList(gameList: GameList): String = buildString {
        gameList.games.forEach { game ->
            append("[[game]]\n")
            append("package = \"").append(game.packageName).append("\"\n")
            append("cpu_governor = \"").append(game.cpuGovernor).append("\"\n")
            append("enable_dnd = ").append(game.enableDnd).append("\n")
            if (game.targetFps != null) {
                append("target_fps = ").append(game.targetFps).append("\n")
            }
            if (game.refreshRate != null) {
                append("refresh_rate = ").append(game.refreshRate).append("\n")
            }
            if (game.mode != null) {
                append("mode = \"").append(game.mode).append("\"\n")
            }
            if (game.lockRotation) {
                append("lock_rotation = true\n")
            }
            append("\n")
        }
    }

    private fun parseStringValue(value: String): String {
        val s = value.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            return s.substring(1, s.length - 1)
        }
        return s
    }
}
