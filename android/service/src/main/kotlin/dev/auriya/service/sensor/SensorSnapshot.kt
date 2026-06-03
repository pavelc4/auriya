package dev.auriya.service.sensor

/**
 * Observable subset of system state. Sensors push partial updates
 * here; the aggregator merges them and triggers a debounced write to
 * the status file.
 *
 * [heartbeatMs] is set by every sensor push to the current wall clock
 * so the aggregator always sees a changed value and flushes the write.
 * This gives the daemon a regular liveness signal (~1 Hz) it can use
 * to detect a killed companion process via staleness tracking.
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

    /**
     * True when the snapshot actually differs from `other` in at least
     * one meaningful field (ignoring identical values). Used by the
     * aggregator to skip redundant writes when only the heartbeat would
     * change.
     */
    fun hasRealChanges(other: SensorSnapshot): Boolean =
        focusedApp != other.focusedApp ||
            focusedPid != other.focusedPid ||
            focusedUid != other.focusedUid ||
            screenAwake != other.screenAwake ||
            batterySaver != other.batterySaver ||
            zenMode != other.zenMode
}
