package dev.auriya.shared.model

/**
 * Snapshot of system state the daemon needs to make profile decisions.
 * All fields are optional: the writer may emit a partial update if
 * only a subset has changed.
 *
 * Mirrors the `SystemStatus` struct on the Rust side. Wire format is
 * defined in [dev.auriya.shared.status.StatusFormat].
 */
data class SystemStatus(
    val focusedApp: String? = null,
    val focusedPid: Int? = null,
    val focusedUid: Int? = null,
    val screenAwake: Boolean? = null,
    val batterySaver: Boolean? = null,
    val zenMode: Int? = null,
) {
    /** True when at least one field is populated. */
    fun isPopulated(): Boolean =
        focusedApp != null ||
            screenAwake != null ||
            batterySaver != null ||
            zenMode != null
}
