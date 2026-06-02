#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScalingAction {
    BoostGpu,
    BoostCpu,
    BoostBalanced,
    Maintain,
    Reduce,
}

/// Which hardware subsystems are controllable on this device.
/// Detected once at boot and cached.
#[derive(Debug, Clone, Copy)]
pub struct PlatformCapabilities {
    pub gpu_controllable: bool,
    pub cpu_controllable: bool,
}

impl PlatformCapabilities {
    pub fn detect() -> Self {
        let gpu_controllable = Self::gpu_probe();
        let cpu_controllable = Self::cpu_probe();
        Self {
            gpu_controllable,
            cpu_controllable,
        }
    }

    fn gpu_probe() -> bool {
        // Adreno (via kgsl)
        if std::path::Path::new("/sys/class/kgsl/kgsl-3d0")
            .join("max_gpuclk")
            .exists()
        {
            return true;
        }
        // MediaTek (via proc)
        if std::path::Path::new("/proc/gpufreq")
            .join("gpu_freq")
            .exists()
        {
            return true;
        }
        // Exynos / Mali (pattern: /sys/devices/platform/{anything}.mali/devfreq/{anything}/min_freq)
        if let Ok(dir) = std::fs::read_dir("/sys/devices/platform/") {
            for entry in dir.flatten() {
                let name = entry.file_name();
                let name = name.to_string_lossy();
                if name.contains(".mali") {
                    let devfreq = entry.path().join("devfreq");
                    if let Ok(df) = std::fs::read_dir(&devfreq) {
                        for sub in df.flatten() {
                            if sub.path().join("min_freq").exists() {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        false
    }

    fn cpu_probe() -> bool {
        // Check if we can write to scaling_governor on any CPU
        for i in 0..8 {
            let path = std::path::Path::new("/sys/devices/system/cpu")
                .join(format!("cpu{i}/cpufreq/scaling_governor"));
            if path.exists() {
                return true;
            }
        }
        false
    }

    pub fn fallback_action(&self, action: ScalingAction) -> ScalingAction {
        match action {
            ScalingAction::BoostGpu if !self.gpu_controllable => {
                tracing::warn!(
                    target: "auriya::platform",
                    "GPU boost unavailable, falling back to BoostBalanced"
                );
                ScalingAction::BoostBalanced
            }
            ScalingAction::BoostCpu if !self.cpu_controllable => {
                tracing::warn!(
                    target: "auriya::platform",
                    "CPU boost unavailable, falling back to BoostBalanced"
                );
                ScalingAction::BoostBalanced
            }
            _ => action,
        }
    }
}
