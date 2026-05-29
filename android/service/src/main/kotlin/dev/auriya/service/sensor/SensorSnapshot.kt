package dev.auriya.service.sensor

/**
 * Observable subset of system state. Sensors push partial updates
 * here; the aggregator merges them and triggers a debounced write to
 * the status file.
 */
data class SensorSnapshot(
    val focusedApp: String? = null,
    val focusedPid: Int? = null,
    val focusedUid: Int? = null,
    val screenAwake: Boolean? = null,
    val batterySaver: Boolean? = null,
    val zenMode: Int? = null,
) {
    /**
     * Overlay `update` on top of this snapshot — non-null fields in
     * `update` win, the rest are preserved.
     */
    fun merge(update: SensorSnapshot): SensorSnapshot = SensorSnapshot(
        focusedApp = update.focusedApp ?: focusedApp,
        focusedPid = update.focusedPid ?: focusedPid,
        focusedUid = update.focusedUid ?: focusedUid,
        screenAwake = update.screenAwake ?: screenAwake,
        batterySaver = update.batterySaver ?: batterySaver,
        zenMode = update.zenMode ?: zenMode,
    )
}
