package dev.auriya.shared.model

data class Settings(
    val daemon: DaemonConfig = DaemonConfig(),
    val cpu: CpuConfig = CpuConfig(),
    val dnd: DndConfig = DndConfig(),
    val fas: FasConfig = FasConfig(),
    val modes: Map<String, FasMode> = emptyMap()
)

data class DaemonConfig(
    val logLevel: String = "info",
    val checkIntervalMs: Long = 2000,
    val defaultMode: String = "balance"
)

data class CpuConfig(
    val defaultGovernor: String = "schedutil"
)

data class DndConfig(
    val defaultEnable: Boolean = true
)

data class FasConfig(
    val enabled: Boolean = true,
    val defaultMode: String = "balance",
    val thermalThreshold: Double = 90.0,
    val pollIntervalMs: Long = 300,
    val targetFps: Int = 60
)

data class FasMode(
    val margin: Double,
    val thermalThreshold: Double
)
