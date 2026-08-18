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
    val jank: Int,
    val cpuLoad: Float,
    val cpuTemp: Float?,
    val batteryTemp: Float?
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
    val maxFps: Double,
    val totalJank: Int,
    val avgCpuLoad: Float,
    val maxCpuTemp: Float?,
    val maxBatteryTemp: Float?,
    val samples: List<BenchmarkSample> = emptyList()
)

class BenchmarkRepository(private val context: Context) {

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
        try {
            val current = getAllSessions().filter { it.id != id }
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

    private fun serializeSession(s: BenchmarkSession): JSONObject {
        return JSONObject().apply {
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
            put("maxFps", s.maxFps)
            put("totalJank", s.totalJank)
            put("avgCpuLoad", s.avgCpuLoad.toDouble())
            if (s.maxCpuTemp != null) put("maxCpuTemp", s.maxCpuTemp.toDouble())
            if (s.maxBatteryTemp != null) put("maxBatteryTemp", s.maxBatteryTemp.toDouble())

            val samplesArray = JSONArray()
            s.samples.forEach { sample ->
                val sampleObj = JSONObject().apply {
                    put("t", sample.timestampOffsetMs)
                    put("fps", sample.fps)
                    put("low1Pct", sample.low1Pct)
                    put("jank", sample.jank)
                    put("cpu", sample.cpuLoad.toDouble())
                    if (sample.cpuTemp != null) put("cpuTemp", sample.cpuTemp.toDouble())
                    if (sample.batteryTemp != null) put("batTemp", sample.batteryTemp.toDouble())
                }
                samplesArray.put(sampleObj)
            }
            put("samples", samplesArray)
        }
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
                        jank = sObj.optInt("jank", 0),
                        cpuLoad = sObj.optDouble("cpu", 0.0).toFloat(),
                        cpuTemp = if (sObj.has("cpuTemp")) sObj.getDouble("cpuTemp").toFloat() else null,
                        batteryTemp = if (sObj.has("batTemp")) sObj.getDouble("batTemp").toFloat() else null
                    )
                )
            }
        }

        val activeSamples = samplesList.filter { it.fps > 10.0 }.ifEmpty { samplesList }
        val calcAvgFps = if (activeSamples.isNotEmpty()) {
            (activeSamples.map { it.fps }.average() * 10.0).roundToInt() / 10.0
        } else {
            obj.optDouble("avgFps", 0.0)
        }
        val calcLow1Pct = if (activeSamples.size >= 4) {
            val sorted = activeSamples.map { it.fps }.sorted()
            val count = (sorted.size * 0.05).toInt().coerceAtLeast(1)
            (sorted.take(count).average() * 10.0).roundToInt() / 10.0
        } else if (activeSamples.isNotEmpty()) {
            (activeSamples.map { it.low1Pct }.average() * 10.0).roundToInt() / 10.0
        } else {
            obj.optDouble("minLow1Pct", 0.0)
        }
        val calcMaxFps = if (activeSamples.isNotEmpty()) {
            (activeSamples.maxOf { it.fps } * 10.0).roundToInt() / 10.0
        } else {
            obj.optDouble("maxFps", 0.0)
        }
        val calcJank = if (samplesList.isNotEmpty()) {
            samplesList.maxOfOrNull { it.jank } ?: obj.optInt("totalJank", 0)
        } else {
            obj.optInt("totalJank", 0)
        }

        return BenchmarkSession(
            id = obj.optString("id", UUID.randomUUID().toString()),
            packageName = obj.optString("packageName", ""),
            appLabel = obj.optString("appLabel", obj.optString("packageName", "Game")),
            profile = obj.optString("profile", "balance"),
            startTimeEpoch = obj.optLong("startTimeEpoch", 0L),
            endTimeEpoch = obj.optLong("endTimeEpoch", 0L),
            durationSeconds = obj.optLong("durationSeconds", 0L),
            samplesCount = obj.optInt("samplesCount", samplesList.size),
            avgFps = calcAvgFps,
            minLow1Pct = calcLow1Pct,
            maxFps = calcMaxFps,
            totalJank = calcJank,
            avgCpuLoad = obj.optDouble("avgCpuLoad", 0.0).toFloat(),
            maxCpuTemp = if (obj.has("maxCpuTemp")) obj.getDouble("maxCpuTemp").toFloat() else null,
            maxBatteryTemp = if (obj.has("maxBatteryTemp")) obj.getDouble("maxBatteryTemp").toFloat() else null,
            samples = samplesList
        )
    }
}
