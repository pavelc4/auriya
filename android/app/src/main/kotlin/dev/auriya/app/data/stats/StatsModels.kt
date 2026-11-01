package dev.auriya.app.data.stats

import android.net.LocalSocket
import android.net.LocalSocketAddress
import dev.auriya.app.data.RootShell
import dev.auriya.shared.config.ConfigPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter

data class ApiResponse<T>(
    val id: String,
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T? = null,
    val timestamp_ms: Long = 0L,
    val timestamp_iso: String = "",
)

data class Stats(
    val fps: Fps? = null,
    val thermal: Thermal = Thermal(),
    val battery: Battery = Battery(),
    val cpu: Cpu? = null,
    val gpu: Gpu? = null,
    val session: Session = Session(profile = "balance", active = false),
    val daemon: DaemonInfo? = null,
    val binder: BinderInfo? = null,
    val system: SystemInfo? = null,
)

data class DaemonInfo(
    val version: String = "",
    val pid: Long = 0L,
    val rss_mb: Float = 0f,
    val uptime_secs: Long = 0L,
    val active_profile: String = "balance",
    val version_code: String = "",
    val commit: String = "",
    val arch: String = "v8a",
    val mod_updated_secs: Long? = null,
)

data class BinderInfo(
    val operational: Boolean = false,
    val screen_interactive: Boolean = true,
    val is_floating: Boolean = false,
    val wifi_low_latency: Boolean = false,
    val statusbar_locked: Boolean = false,
    val dnd_mode: Int = 0,
    val refresh_rate_hz: Float? = null,
)

data class SystemInfo(
    val uptime_secs: Long = 0L,
    val ram_total_mb: Long = 0L,
    val ram_avail_mb: Long = 0L,
)

data class Fps(
    val avg: Double,
    val peak: Double,
    val low_1pct: Double,
    val low_01pct: Double = 0.0,
    val std_dev_ms: Double = 0.0,
    val jank: Int,
    val frames: Int,
)

data class Thermal(
    val cpu_c: Float? = null,
    val gpu_c: Float? = null,
    val battery_c: Float? = null,
    val sconfig_supported: Boolean = false,
)

data class Battery(
    val pct: Int? = null,
    val current_ma: Int? = null,
    val voltage_v: Float? = null,
    val status: String? = null,
    val health: String? = null,
)

data class Core(
    val id: Int,
    val khz: Long,
    val gov: String,
    val cluster: String,
    val online: Boolean,
    val load_pct: Float = 0f,
)

data class Cpu(
    val load_pct: Float,
    val cores: List<Core> = emptyList(),
    val available_governors: List<String> = emptyList(),
)

data class Gpu(
    val mhz: Long? = null,
    val load_pct: Int? = null,
    val vendor: String? = null,
)

data class Session(
    val pkg: String? = null,
    val profile: String = "balance",
    val active: Boolean = false,
    val is_game: Boolean = false,
)

object StatsParser {
    fun fetchStats(): Stats? {
        runCatching {
            val sock = LocalSocket()
            sock.connect(LocalSocketAddress(ConfigPaths.SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))
            sock.soTimeout = 1000
            val writer = sock.outputStream.bufferedWriter()
            val reader = sock.inputStream.bufferedReader()
            writer.write("GET_STATS\nQUIT\n")
            writer.flush()
            val line = reader.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
            sock.close()
            if (line != null) return parseStatsJson(line)
        }

        val raw = RootShell.run("${ConfigPaths.AURIYACTL_BIN} stats --json 2>/dev/null")
        val jsonLine = raw.lineSequence().firstOrNull { it.trimStart().startsWith("{") } ?: return null
        return parseStatsJson(jsonLine)
    }

    /**
     * Streams continuous telemetry snapshots over a single persistent IPC connection,
     * eliminating shell fork overhead.
     */
    fun streamStats(intervalMs: Long = 1000L): Flow<Stats> =
        flow {
            var localSocket: LocalSocket? = null
            var process: Process? = null
            var reader: BufferedReader? = null
            var writer: BufferedWriter? = null

            try {
                val directSocket =
                    runCatching {
                        val sock = LocalSocket()
                        sock.connect(LocalSocketAddress(ConfigPaths.SOCKET_PATH, LocalSocketAddress.Namespace.FILESYSTEM))
                        sock
                    }.getOrNull()

                if (directSocket != null) {
                    localSocket = directSocket
                    reader = directSocket.inputStream.bufferedReader()
                    writer = directSocket.outputStream.bufferedWriter()
                    writer.write("STREAM_STATS $intervalMs\n")
                    writer.flush()
                } else {
                    val proc =
                        ProcessBuilder("su", "-c", "${ConfigPaths.AURIYACTL_BIN} stats --stream --json --interval-ms $intervalMs")
                            .redirectErrorStream(true)
                            .start()
                    process = proc
                    reader = proc.inputStream.bufferedReader()
                }

                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("{") -> parseStatsJson(trimmed)?.let { emit(it) }
                        trimmed == "STREAM_STOPPED" -> break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                try {
                    writer?.write("STOP\n")
                    writer?.flush()
                } catch (_: Throwable) {
                }
                try {
                    reader?.close()
                } catch (_: Throwable) {
                }
                try {
                    writer?.close()
                } catch (_: Throwable) {
                }
                try {
                    localSocket?.close()
                } catch (_: Throwable) {
                }
                try {
                    process?.destroyForcibly()
                } catch (_: Throwable) {
                }
            }
        }.flowOn(Dispatchers.IO)

    fun parseStatsJson(jsonStr: String): Stats? =
        runCatching {
            val rootObj = JSONObject(jsonStr)
            val root = rootObj.optJSONObject("data") ?: rootObj

            val fpsObj = root.optJSONObject("fps")
            val fps =
                if (fpsObj != null) {
                    Fps(
                        avg = fpsObj.optDouble("avg", 0.0),
                        peak = fpsObj.optDouble("peak", 0.0),
                        low_1pct = fpsObj.optDouble("low_1pct", 0.0),
                        low_01pct = fpsObj.optDouble("low_01pct", 0.0),
                        std_dev_ms = fpsObj.optDouble("std_dev_ms", 0.0),
                        jank = fpsObj.optInt("jank", 0),
                        frames = fpsObj.optInt("frames", 0),
                    )
                } else {
                    null
                }

            val thermalObj = root.optJSONObject("thermal")
            val thermal =
                if (thermalObj != null) {
                    Thermal(
                        cpu_c =
                            if (thermalObj.has("cpu_c") &&
                                !thermalObj.isNull("cpu_c")
                            ) {
                                thermalObj.getDouble("cpu_c").toFloat()
                            } else {
                                null
                            },
                        gpu_c =
                            if (thermalObj.has("gpu_c") &&
                                !thermalObj.isNull("gpu_c")
                            ) {
                                thermalObj.getDouble("gpu_c").toFloat()
                            } else {
                                null
                            },
                        battery_c =
                            if (thermalObj.has("battery_c") &&
                                !thermalObj.isNull("battery_c")
                            ) {
                                thermalObj.getDouble("battery_c").toFloat()
                            } else {
                                null
                            },
                        sconfig_supported = thermalObj.optBoolean("sconfig_supported", false),
                    )
                } else {
                    Thermal()
                }

            val batteryObj = root.optJSONObject("battery")
            val battery =
                if (batteryObj != null) {
                    Battery(
                        pct = if (batteryObj.has("pct") && !batteryObj.isNull("pct")) batteryObj.getInt("pct") else null,
                        current_ma =
                            if (batteryObj.has("current_ma") &&
                                !batteryObj.isNull("current_ma")
                            ) {
                                batteryObj.getInt("current_ma")
                            } else {
                                null
                            },
                        voltage_v =
                            if (batteryObj.has("voltage_v") &&
                                !batteryObj.isNull("voltage_v")
                            ) {
                                batteryObj.getDouble("voltage_v").toFloat()
                            } else {
                                null
                            },
                        status = if (batteryObj.has("status") && !batteryObj.isNull("status")) batteryObj.getString("status") else null,
                        health = if (batteryObj.has("health") && !batteryObj.isNull("health")) batteryObj.getString("health") else null,
                    )
                } else {
                    Battery()
                }

            val cpuObj = root.optJSONObject("cpu")
            val cpu =
                if (cpuObj != null) {
                    val coresArray = cpuObj.optJSONArray("cores")
                    val coresList = mutableListOf<Core>()
                    if (coresArray != null) {
                        for (i in 0 until coresArray.length()) {
                            val cObj = coresArray.getJSONObject(i)
                            coresList.add(
                                Core(
                                    id = cObj.optInt("id", i),
                                    khz = cObj.optLong("khz", 0L),
                                    gov = cObj.optString("gov", ""),
                                    cluster = cObj.optString("cluster", ""),
                                    online = cObj.optBoolean("online", true),
                                    load_pct = cObj.optDouble("load_pct", 0.0).toFloat(),
                                ),
                            )
                        }
                    }
                    val govArray = cpuObj.optJSONArray("available_governors")
                    val govList = mutableListOf<String>()
                    if (govArray != null) {
                        for (i in 0 until govArray.length()) {
                            val gov = govArray.optString(i)
                            if (gov.isNotBlank()) {
                                govList.add(gov)
                            }
                        }
                    }
                    Cpu(
                        load_pct = cpuObj.optDouble("load_pct", 0.0).toFloat(),
                        cores = coresList,
                        available_governors = govList,
                    )
                } else {
                    null
                }

            val gpuObj = root.optJSONObject("gpu")
            val gpu =
                if (gpuObj != null) {
                    Gpu(
                        mhz = if (gpuObj.has("mhz") && !gpuObj.isNull("mhz")) gpuObj.getLong("mhz") else null,
                        load_pct = if (gpuObj.has("load_pct") && !gpuObj.isNull("load_pct")) gpuObj.getInt("load_pct") else null,
                        vendor = if (gpuObj.has("vendor") && !gpuObj.isNull("vendor")) gpuObj.getString("vendor") else null,
                    )
                } else {
                    null
                }

            val sessionObj = root.optJSONObject("session")
            val session =
                if (sessionObj != null) {
                    Session(
                        pkg = if (sessionObj.has("pkg") && !sessionObj.isNull("pkg")) sessionObj.getString("pkg") else null,
                        profile = sessionObj.optString("profile", "balance"),
                        active = sessionObj.optBoolean("active", false),
                        is_game = sessionObj.optBoolean("is_game", false),
                    )
                } else {
                    Session()
                }

            val daemonObj = root.optJSONObject("daemon")
            val daemon =
                if (daemonObj != null) {
                    DaemonInfo(
                        version = daemonObj.optString("version", ""),
                        pid = daemonObj.optLong("pid", 0L),
                        rss_mb = daemonObj.optDouble("rss_mb", 0.0).toFloat(),
                        uptime_secs = daemonObj.optLong("uptime_secs", 0L),
                        active_profile = daemonObj.optString("active_profile", "balance"),
                        version_code = daemonObj.optString("version_code", ""),
                        commit = daemonObj.optString("commit", ""),
                        arch = daemonObj.optString("arch", "v8a"),
                        mod_updated_secs =
                            if (daemonObj.has("mod_updated_secs") && !daemonObj.isNull("mod_updated_secs")) {
                                daemonObj.getLong("mod_updated_secs")
                            } else {
                                null
                            },
                    )
                } else {
                    null
                }

            val binderObj = root.optJSONObject("binder")
            val binder =
                if (binderObj != null) {
                    BinderInfo(
                        operational = binderObj.optBoolean("operational", false),
                        screen_interactive = binderObj.optBoolean("screen_interactive", true),
                        is_floating = binderObj.optBoolean("is_floating", false),
                        wifi_low_latency = binderObj.optBoolean("wifi_low_latency", false),
                        statusbar_locked = binderObj.optBoolean("statusbar_locked", false),
                        dnd_mode = binderObj.optInt("dnd_mode", 0),
                        refresh_rate_hz =
                            if (binderObj.has("refresh_rate_hz") && !binderObj.isNull("refresh_rate_hz")) {
                                binderObj.getDouble("refresh_rate_hz").toFloat()
                            } else {
                                null
                            },
                    )
                } else {
                    null
                }

            val sysObj = root.optJSONObject("system")
            val system =
                if (sysObj != null) {
                    SystemInfo(
                        uptime_secs = sysObj.optLong("uptime_secs", 0L),
                        ram_total_mb = sysObj.optLong("ram_total_mb", 0L),
                        ram_avail_mb = sysObj.optLong("ram_avail_mb", 0L),
                    )
                } else {
                    null
                }

            Stats(
                fps = fps,
                thermal = thermal,
                battery = battery,
                cpu = cpu,
                gpu = gpu,
                session = session,
                daemon = daemon,
                binder = binder,
                system = system,
            )
        }.getOrNull()
}
