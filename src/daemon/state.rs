#[derive(Debug, Default, Clone)]
pub struct CurrentState {
    pub pkg: Option<String>,
    pub pid: Option<i32>,
    pub screen_awake: bool,
    pub battery_saver: bool,
    pub profile: crate::core::profile::ProfileMode,
    /// Whether the Android companion service (AuriyaSysMon) is
    /// believed to be alive. Set to `false` when the status file
    /// has not been updated for too long.
    pub companion_alive: bool,
    /// Telemetry fields updated each tick.
    pub cpu_telemetry: Option<crate::core::telemetry::cpu::CpuSnapshot>,
    pub gpu_telemetry: Option<crate::core::telemetry::gpu::GpuSnapshot>,
    pub thermal_telemetry: Option<crate::core::telemetry::thermal::ThermalSnapshot>,
    /// Measured FPS from eBPF (FAS) or sysfs fallback.
    pub fps: Option<f64>,
    pub fps_source: Option<crate::core::fps_meter::FpsSource>,
}

#[derive(Debug, Default, Clone)]
pub struct LastState {
    pub pkg: Option<String>,
    pub pid: Option<i32>,
    pub screen_awake: Option<bool>,
    pub battery_saver: Option<bool>,
    pub last_log_ms: Option<u128>,
    pub profile_mode: Option<crate::core::profile::ProfileMode>,
}
