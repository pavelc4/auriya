pub mod daemon;
pub mod ebpf;
pub mod hardware;
pub mod props;

pub use daemon::{DaemonRuntimeInfo, collect_daemon_runtime, find_daemon_pid};
pub use ebpf::{EbpfDiagnostics, collect_ebpf};
pub use hardware::{HardwareInfo, collect_hardware};
pub use props::get_system_property;

#[derive(Debug, Clone)]
pub struct SystemReport {
    pub hw: HardwareInfo,
    pub ebpf: EbpfDiagnostics,
    pub daemon: DaemonRuntimeInfo,
}

/// Collect full system report natively without spawning shell commands.
pub fn collect_report(
    mut daemon_pid: Option<i32>,
    supported_rates: Option<String>,
    daemon_fps_source: Option<&str>,
) -> SystemReport {
    if daemon_pid.is_none() {
        daemon_pid = find_daemon_pid();
    }
    let hw = collect_hardware(supported_rates);
    let ebpf = collect_ebpf(daemon_fps_source);
    let daemon = collect_daemon_runtime(daemon_pid);
    SystemReport { hw, ebpf, daemon }
}
