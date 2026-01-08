use std::path::PathBuf;
use std::sync::OnceLock;

pub struct CpuPaths {
    pub governors_cpu: Vec<PathBuf>,
    pub governors_policy: Vec<PathBuf>,
    pub online: Vec<PathBuf>,
}

impl CpuPaths {
    fn scan() -> Self {
        let mut governors_cpu = Vec::with_capacity(16);
        let mut governors_policy = Vec::with_capacity(8);
        let mut online = Vec::with_capacity(8);

        for i in 0..16 {
            let path = PathBuf::from(format!(
                "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_governor",
                i
            ));
            if path.exists() {
                governors_cpu.push(path);
            }
        }

        for i in 0..8 {
            let path = PathBuf::from(format!(
                "/sys/devices/system/cpu/cpufreq/policy{}/scaling_governor",
                i
            ));
            if path.exists() {
                governors_policy.push(path);
            }
        }

        for i in 1..16 {
            let path = PathBuf::from(format!("/sys/devices/system/cpu/cpu{}/online", i));
            if path.exists() {
                online.push(path);
            }
        }

        tracing::debug!(
            "Cached sysfs paths: {} cpu governors, {} policy governors, {} online",
            governors_cpu.len(),
            governors_policy.len(),
            online.len()
        );

        Self {
            governors_cpu,
            governors_policy,
            online,
        }
    }
}

static CPU_PATHS: OnceLock<CpuPaths> = OnceLock::new();

#[inline]
pub fn cpu_paths() -> &'static CpuPaths {
    CPU_PATHS.get_or_init(CpuPaths::scan)
}

pub fn set_governor_cached(governor: &str) {
    let paths = cpu_paths();

    for path in &paths.governors_cpu {
        let _ = std::fs::write(path, governor);
    }

    for path in &paths.governors_policy {
        let _ = std::fs::write(path, governor);
    }
}

pub fn online_all_cores_cached() {
    let paths = cpu_paths();

    for path in &paths.online {
        let _ = std::fs::write(path, "1");
    }
}
