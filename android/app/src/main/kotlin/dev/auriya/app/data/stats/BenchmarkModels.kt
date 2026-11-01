package dev.auriya.app.data.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

data class BenchmarkSample(
    val timestampOffsetMs: Long,
    val fps: Double,
    val low1Pct: Double,
    val low01Pct: Double = 0.0,
    val stdDevMs: Double = 0.0,
    val jank: Int,
    val cpuLoad: Float,
    val cpuTemp: Float?,
    val batteryTemp: Float?,
)

data class BenchmarkSession(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appLabel: String,
    val profile: String,
    val startTimeEpoch: Long,
    val endTimeEpoch: Long,
    val durationSeconds: Long,
    val samplesCount: Int,
    val avgFps: Double,
    val minLow1Pct: Double,
    val low01Pct: Double = 0.0,
    val stdDevMs: Double = 0.0,
    val maxFps: Double,
    val totalJank: Int,
    val avgCpuLoad: Float,
    val maxCpuTemp: Float?,
    val maxBatteryTemp: Float?,
    val samples: List<BenchmarkSample> = emptyList(),
)

data class SessionStatsResult(
    val avgFps: Double,
    val minLow1Pct: Double,
    val low01Pct: Double,
    val stdDevMs: Double,
    val maxFps: Double,
    val totalJank: Int,
    val avgCpuLoad: Float,
)

fun computeSessionStats(samples: List<BenchmarkSample>): SessionStatsResult {
    if (samples.isEmpty()) {
        return SessionStatsResult(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0f)
    }

    // Filter active rendering samples (> 15 FPS to filter launch/pause stalls)
    val activeSamples = samples.filter { it.fps > 15.0 }.ifEmpty { samples }
    val n = activeSamples.size

    // 1. Sort samples ascendingly (lowest FPS first)
    val sortedFps = activeSamples.map { it.fps }.sorted()

    // 2. Dominant Target Detection:
    // Determine if gameplay was phase-locked to a standard VSync target rate (144, 120, 90, 60, 45, 30)
    val targets = listOf(144.0, 120.0, 90.0, 60.0, 45.0, 30.0)
    val dominantTarget =
        targets.find { t ->
            val clusterCount = sortedFps.count { kotlin.math.abs(it - t) <= 3.0 }
            clusterCount >= (n * 0.55)
        }

    // 3. Robust Gameplay Isolation:
    // When a dominant high-refresh target exists (e.g. 120 FPS), pre-match loading screens/menus (e.g. 60 FPS)
    // belong to a lower phase and are isolated from steady-state match rendering calculations
    val gameplaySamples =
        if (dominantTarget != null && dominantTarget > 60.0) {
            val filtered = sortedFps.filter { it >= dominantTarget * 0.65 }
            if (filtered.size >= (n * 0.50)) filtered else sortedFps
        } else {
            sortedFps
        }

    val gn = gameplaySamples.size
    val trimK = (gn * 0.05).toInt()
    val trimmedFps =
        if (gn >= 10 && trimK > 0) {
            gameplaySamples.subList(trimK, gn - trimK)
        } else {
            gameplaySamples
        }

    val harmonicSum = trimmedFps.sumOf { 1.0 / it.coerceAtLeast(1.0) }
    val rawAvgFps = if (harmonicSum > 0.0) trimmedFps.size / harmonicSum else trimmedFps.average()

    // 4. VSync Target Phase Alignment:
    val avgFps =
        if (dominantTarget != null && kotlin.math.abs(rawAvgFps - dominantTarget) <= 1.5) {
            dominantTarget
        } else {
            rawAvgFps
        }

    // 5. Peak FPS clamped to physical scanout ceiling
    val rawMaxFps = activeSamples.maxOfOrNull { it.fps } ?: 0.0
    val maxFps =
        if (dominantTarget != null && rawMaxFps > dominantTarget) {
            dominantTarget
        } else {
            rawMaxFps
        }

    // 6. True 1% Low (P1 via empirical CDF percentile of gameplay):
    val idx1Pct = ((gn * 0.01).roundToInt() - 1).coerceIn(0, gn - 1)
    val low1Pct = gameplaySamples[idx1Pct].coerceAtMost(avgFps)

    // 7. True 0.1% Low (P0.1 via empirical CDF percentile of gameplay):
    val idx01Pct = ((gn * 0.001).roundToInt() - 1).coerceIn(0, gn - 1)
    val low01Pct = gameplaySamples[idx01Pct].coerceAtMost(low1Pct)

    // 8. Population Standard Deviation (sigma) in milliseconds:
    val frameTimesMs = gameplaySamples.map { 1000.0 / it.coerceAtLeast(1.0) }
    val meanFtMs = frameTimesMs.average()
    val varianceMs = frameTimesMs.sumOf { (it - meanFtMs) * (it - meanFtMs) } / gn
    val stdDevMs = kotlin.math.sqrt(varianceMs)

    val totalJank = samples.maxOfOrNull { it.jank } ?: 0
    val avgCpu = activeSamples.map { it.cpuLoad }.average().toFloat()

    return SessionStatsResult(
        avgFps = (avgFps * 10.0).roundToInt() / 10.0,
        minLow1Pct = (low1Pct * 10.0).roundToInt() / 10.0,
        low01Pct = (low01Pct * 10.0).roundToInt() / 10.0,
        stdDevMs = (stdDevMs * 100.0).roundToInt() / 100.0,
        maxFps = (maxFps * 10.0).roundToInt() / 10.0,
        totalJank = totalJank,
        avgCpuLoad = (avgCpu * 10f).roundToInt() / 10f,
    )
}

class BenchmarkRepository(
    private val context: Context,
) {
    private val sessionsFile: File
        get() = File(context.filesDir, "benchmark_sessions.json")

    fun getAllSessions(): List<BenchmarkSession> {
        return try {
            if (!sessionsFile.exists()) return emptyList()
            val text = sessionsFile.readText()
            if (text.isBlank()) return emptyList()

            val array = JSONArray(text)
            val list = mutableListOf<BenchmarkSession>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(deserializeSession(obj))
            }
            list.sortedByDescending { it.startTimeEpoch }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSession(session: BenchmarkSession) {
        try {
            val current = getAllSessions().toMutableList()
            current.removeAll { it.id == session.id }
            current.add(0, session)

            val array = JSONArray()
            current.forEach { array.put(serializeSession(it)) }
            sessionsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSession(id: String) {
        deleteSessions(setOf(id))
    }

    fun deleteSessions(ids: Set<String>) {
        try {
            val current = getAllSessions().filter { it.id !in ids }
            val array = JSONArray()
            current.forEach { array.put(serializeSession(it)) }
            sessionsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAll() {
        try {
            if (sessionsFile.exists()) {
                sessionsFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serializeSession(s: BenchmarkSession): JSONObject =
        JSONObject().apply {
            put("id", s.id)
            put("packageName", s.packageName)
            put("appLabel", s.appLabel)
            put("profile", s.profile)
            put("startTimeEpoch", s.startTimeEpoch)
            put("endTimeEpoch", s.endTimeEpoch)
            put("durationSeconds", s.durationSeconds)
            put("samplesCount", s.samplesCount)
            put("avgFps", s.avgFps)
            put("minLow1Pct", s.minLow1Pct)
            put("low01Pct", s.low01Pct)
            put("stdDevMs", s.stdDevMs)
            put("maxFps", s.maxFps)
            put("totalJank", s.totalJank)
            put("avgCpuLoad", s.avgCpuLoad.toDouble())
            if (s.maxCpuTemp != null) put("maxCpuTemp", s.maxCpuTemp.toDouble())
            if (s.maxBatteryTemp != null) put("maxBatteryTemp", s.maxBatteryTemp.toDouble())

            val samplesArray = JSONArray()
            s.samples.forEach { sample ->
                val sampleObj =
                    JSONObject().apply {
                        put("t", sample.timestampOffsetMs)
                        put("fps", sample.fps)
                        put("low1Pct", sample.low1Pct)
                        put("low01Pct", sample.low01Pct)
                        put("stdDevMs", sample.stdDevMs)
                        put("jank", sample.jank)
                        put("cpu", sample.cpuLoad.toDouble())
                        if (sample.cpuTemp != null) put("cpuTemp", sample.cpuTemp.toDouble())
                        if (sample.batteryTemp != null) put("batTemp", sample.batteryTemp.toDouble())
                    }
                samplesArray.put(sampleObj)
            }
            put("samples", samplesArray)
        }

    private fun deserializeSession(obj: JSONObject): BenchmarkSession {
        val samplesArray = obj.optJSONArray("samples")
        val samplesList = mutableListOf<BenchmarkSample>()
        if (samplesArray != null) {
            for (i in 0 until samplesArray.length()) {
                val sObj = samplesArray.getJSONObject(i)
                samplesList.add(
                    BenchmarkSample(
                        timestampOffsetMs = sObj.optLong("t", 0L),
                        fps = sObj.optDouble("fps", 0.0),
                        low1Pct = sObj.optDouble("low1Pct", 0.0),
                        low01Pct = sObj.optDouble("low01Pct", 0.0),
                        stdDevMs = sObj.optDouble("stdDevMs", 0.0),
                        jank = sObj.optInt("jank", 0),
                        cpuLoad = sObj.optDouble("cpu", 0.0).toFloat(),
                        cpuTemp = if (sObj.has("cpuTemp")) sObj.getDouble("cpuTemp").toFloat() else null,
                        batteryTemp = if (sObj.has("batTemp")) sObj.getDouble("batTemp").toFloat() else null,
                    ),
                )
            }
        }

        // Rigorous statistical calculus computed from raw samples
        val computed = computeSessionStats(samplesList)

        // Read stored metrics with VSync scanout validation and robust fallback
        val standardTargets = listOf(30.0, 45.0, 60.0, 90.0, 120.0, 144.0)
        val finalAvgFps = if (samplesList.isNotEmpty()) {
            computed.avgFps
        } else if (obj.has("avgFps") && obj.getDouble("avgFps") > 0.0) {
            val raw = obj.getDouble("avgFps")
            standardTargets.find { kotlin.math.abs(raw - it) <= 1.5 } ?: raw
        } else {
            computed.avgFps
        }

        val finalLow1Pct = if (samplesList.isNotEmpty()) {
            computed.minLow1Pct
        } else if (obj.has("minLow1Pct") && obj.getDouble("minLow1Pct") > 0.0) {
            obj.getDouble("minLow1Pct").coerceAtMost(finalAvgFps)
        } else {
            computed.minLow1Pct
        }

        val finalLow01Pct = if (samplesList.isNotEmpty()) {
            computed.low01Pct
        } else if (obj.has("low01Pct") && obj.getDouble("low01Pct") > 0.0) {
            obj.getDouble("low01Pct").coerceAtMost(finalLow1Pct)
        } else {
            computed.low01Pct
        }

        val finalStdDevMs = if (samplesList.isNotEmpty()) {
            computed.stdDevMs
        } else if (obj.has("stdDevMs") && obj.getDouble("stdDevMs") > 0.0) {
            obj.getDouble("stdDevMs")
        } else {
            computed.stdDevMs
        }

        val finalMaxFps = if (samplesList.isNotEmpty()) {
            computed.maxFps
        } else if (obj.has("maxFps") && obj.getDouble("maxFps") > 0.0) {
            val raw = obj.getDouble("maxFps")
            val matchedTarget = standardTargets.find { kotlin.math.abs(finalAvgFps - it) <= 2.0 }
            if (matchedTarget != null && raw > matchedTarget) matchedTarget else raw
        } else {
            computed.maxFps
        }
        val finalJank = if (obj.has("totalJank")) obj.optInt("totalJank") else computed.totalJank

        return BenchmarkSession(
            id = obj.optString("id", UUID.randomUUID().toString()),
            packageName = obj.optString("packageName", ""),
            appLabel = obj.optString("appLabel", obj.optString("packageName", "Game")),
            profile = obj.optString("profile", "balance"),
            startTimeEpoch = obj.optLong("startTimeEpoch", 0L),
            endTimeEpoch = obj.optLong("endTimeEpoch", 0L),
            durationSeconds = obj.optLong("durationSeconds", 0L),
            samplesCount = obj.optInt("samplesCount", samplesList.size),
            avgFps = finalAvgFps,
            minLow1Pct = finalLow1Pct,
            low01Pct = finalLow01Pct,
            stdDevMs = finalStdDevMs,
            maxFps = finalMaxFps,
            totalJank = finalJank,
            avgCpuLoad = if (obj.has("avgCpuLoad")) obj.getDouble("avgCpuLoad").toFloat() else computed.avgCpuLoad,
            maxCpuTemp = if (obj.has("maxCpuTemp")) obj.getDouble("maxCpuTemp").toFloat() else null,
            maxBatteryTemp = if (obj.has("maxBatteryTemp")) obj.getDouble("maxBatteryTemp").toFloat() else null,
            samples = samplesList,
        )
    }
}
