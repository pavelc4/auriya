package dev.auriya.app.data.stats

import dev.auriya.app.data.RootShell
import org.json.JSONObject

data class Stats(
    val fps: Fps? = null,
    val thermal: Thermal = Thermal(),
    val battery: Battery = Battery(),
    val cpu: Cpu? = null,
    val gpu: Gpu? = null,
    val session: Session = Session(profile = "balance", active = false)
)

data class Fps(
    val avg: Double,
    val peak: Double,
    val low_1pct: Double,
    val jank: Int,
    val frames: Int
)

data class Thermal(
    val cpu_c: Float? = null,
    val gpu_c: Float? = null,
    val battery_c: Float? = null
)

data class Battery(
    val pct: Int? = null,
    val current_ma: Int? = null,
    val voltage_v: Float? = null,
    val status: String? = null,
    val health: String? = null
)

data class Core(
    val id: Int,
    val khz: Long,
    val gov: String,
    val cluster: String,
    val online: Boolean
)

data class Cpu(
    val load_pct: Float,
    val cores: List<Core> = emptyList()
)

data class Gpu(
    val mhz: Long? = null,
    val load_pct: Int? = null,
    val vendor: String? = null
)

data class Session(
    val pkg: String? = null,
    val profile: String = "balance",
    val active: Boolean = false
)

object StatsParser {

    fun fetchStats(): Stats? {
        val raw = RootShell.run("printf 'GET_STATS\\nQUIT\\n' | timeout 2 nc -U /dev/socket/auriya.sock 2>/dev/null")
        val jsonLine = raw.lineSequence().firstOrNull { it.trimStart().startsWith("{") } ?: return null
        return parseStatsJson(jsonLine)
    }

    fun parseStatsJson(jsonStr: String): Stats? {
        return runCatching {
            val root = JSONObject(jsonStr)

            val fpsObj = root.optJSONObject("fps")
            val fps = if (fpsObj != null) {
                Fps(
                    avg = fpsObj.optDouble("avg", 0.0),
                    peak = fpsObj.optDouble("peak", 0.0),
                    low_1pct = fpsObj.optDouble("low_1pct", 0.0),
                    jank = fpsObj.optInt("jank", 0),
                    frames = fpsObj.optInt("frames", 0)
                )
            } else null

            val thermalObj = root.optJSONObject("thermal")
            val thermal = if (thermalObj != null) {
                Thermal(
                    cpu_c = if (thermalObj.has("cpu_c") && !thermalObj.isNull("cpu_c")) thermalObj.getDouble("cpu_c").toFloat() else null,
                    gpu_c = if (thermalObj.has("gpu_c") && !thermalObj.isNull("gpu_c")) thermalObj.getDouble("gpu_c").toFloat() else null,
                    battery_c = if (thermalObj.has("battery_c") && !thermalObj.isNull("battery_c")) thermalObj.getDouble("battery_c").toFloat() else null
                )
            } else Thermal()

            val batteryObj = root.optJSONObject("battery")
            val battery = if (batteryObj != null) {
                Battery(
                    pct = if (batteryObj.has("pct") && !batteryObj.isNull("pct")) batteryObj.getInt("pct") else null,
                    current_ma = if (batteryObj.has("current_ma") && !batteryObj.isNull("current_ma")) batteryObj.getInt("current_ma") else null,
                    voltage_v = if (batteryObj.has("voltage_v") && !batteryObj.isNull("voltage_v")) batteryObj.getDouble("voltage_v").toFloat() else null,
                    status = if (batteryObj.has("status") && !batteryObj.isNull("status")) batteryObj.getString("status") else null,
                    health = if (batteryObj.has("health") && !batteryObj.isNull("health")) batteryObj.getString("health") else null
                )
            } else Battery()

            val cpuObj = root.optJSONObject("cpu")
            val cpu = if (cpuObj != null) {
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
                                online = cObj.optBoolean("online", true)
                            )
                        )
                    }
                }
                Cpu(
                    load_pct = cpuObj.optDouble("load_pct", 0.0).toFloat(),
                    cores = coresList
                )
            } else null

            val gpuObj = root.optJSONObject("gpu")
            val gpu = if (gpuObj != null) {
                Gpu(
                    mhz = if (gpuObj.has("mhz") && !gpuObj.isNull("mhz")) gpuObj.getLong("mhz") else null,
                    load_pct = if (gpuObj.has("load_pct") && !gpuObj.isNull("load_pct")) gpuObj.getInt("load_pct") else null,
                    vendor = if (gpuObj.has("vendor") && !gpuObj.isNull("vendor")) gpuObj.getString("vendor") else null
                )
            } else null

            val sessionObj = root.optJSONObject("session")
            val session = if (sessionObj != null) {
                Session(
                    pkg = if (sessionObj.has("pkg") && !sessionObj.isNull("pkg")) sessionObj.getString("pkg") else null,
                    profile = sessionObj.optString("profile", "balance"),
                    active = sessionObj.optBoolean("active", false)
                )
            } else Session()

            Stats(
                fps = fps,
                thermal = thermal,
                battery = battery,
                cpu = cpu,
                gpu = gpu,
                session = session
            )
        }.getOrNull()
    }
}
