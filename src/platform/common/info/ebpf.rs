use std::fs;
use std::path::Path;

#[derive(Debug, Clone)]
pub struct EbpfDiagnostics {
    pub bpf_mount: String,
    pub bpf_syscall: String,
    pub jit_compiler: String,
    pub kala_probe: String,
    pub active_engine: String,
}

pub fn collect_ebpf(daemon_fps_source: Option<&str>) -> EbpfDiagnostics {
    // 1. Check BPF mount
    let bpf_mount = if is_bpf_mounted() {
        "Mounted (/sys/fs/bpf)".to_string()
    } else {
        "Not Mounted".to_string()
    };

    // 2. Test BPF Syscall
    let bpf_syscall = test_bpf_syscall();

    // 3. JIT compiler
    let jit_compiler = match fs::read_to_string("/proc/sys/net/core/bpf_jit_enable") {
        Ok(s) => match s.trim() {
            "1" | "2" => format!("Enabled (bpf_jit_enable = {})", s.trim()),
            "0" => "Disabled (bpf_jit_enable = 0)".to_string(),
            v => format!("State ({v})"),
        },
        Err(_) => "Unknown".to_string(),
    };

    // 4. Kala Probe readiness
    let kala_probe = if daemon_fps_source == Some("ebpf") {
        "Supported & Ready (Active in daemon)".to_string()
    } else {
        match kala_ebpf::FrameProbe::new() {
            Ok(_) => "Supported & Ready".to_string(),
            Err(e) => format!("Unavailable ({e})"),
        }
    };

    // 5. Active Frame Engine
    let active_engine = match daemon_fps_source {
        Some("ebpf") => "eBPF Broadcast (Low-overhead hook)".to_string(),
        Some("sysfs") => "sysfs DRM Fallback".to_string(),
        _ => "eBPF Frame Stream".to_string(),
    };

    EbpfDiagnostics {
        bpf_mount,
        bpf_syscall,
        jit_compiler,
        kala_probe,
        active_engine,
    }
}

fn is_bpf_mounted() -> bool {
    if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
        for line in mounts.lines() {
            let mut parts = line.split_whitespace();
            let _dev = parts.next();
            let mount_point = parts.next();
            let fs_type = parts.next();
            if mount_point == Some("/sys/fs/bpf") && fs_type == Some("bpf") {
                return true;
            }
        }
    }
    Path::new("/sys/fs/bpf").exists()
}

fn test_bpf_syscall() -> String {
    let res = unsafe { libc::syscall(libc::SYS_bpf, 999999, std::ptr::null::<libc::c_void>(), 0) };
    if res == -1 {
        let err = std::io::Error::last_os_error();
        if err.raw_os_error() == Some(libc::ENOSYS) {
            return "Unavailable (ENOSYS)".to_string();
        }
        return "Available (SYS_bpf)".to_string();
    }
    "Available (SYS_bpf)".to_string()
}
