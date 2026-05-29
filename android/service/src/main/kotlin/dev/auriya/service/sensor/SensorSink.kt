package dev.auriya.service.sensor

/**
 * Sink that sensors push partial [SensorSnapshot] updates to. The
 * service-level aggregator collects them, merges, debounces, and
 * persists.
 */
fun interface SensorSink {
    fun push(update: SensorSnapshot)
}
