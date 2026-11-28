use super::action::ScalingAction;

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
        tracing::debug!(
            target: "auriya::platform",
            "PlatformCapabilities::detect -> gpu_controllable={}, cpu_controllable={}",
            gpu_controllable,
            cpu_controllable
        );
        Self {
            gpu_controllable,
            cpu_controllable,
        }
    }

    fn gpu_probe() -> bool {
        Self::probe_gpu_in(
            std::path::Path::new("/sys/class/kgsl/kgsl-3d0"),
            std::path::Path::new("/proc/gpufreq"),
            std::path::Path::new("/sys/devices/platform"),
            std::path::Path::new("/sys/class/devfreq"),
        )
    }

    /// Probe controllable GPU sysfs/procfs nodes in the given root directories.
    pub fn probe_gpu_in(
        kgsl_dir: &std::path::Path,
        proc_gpufreq_dir: &std::path::Path,
        platform_dir: &std::path::Path,
        devfreq_dir: &std::path::Path,
    ) -> bool {
        // Adreno (via kgsl)
        if kgsl_dir.join("max_gpuclk").exists() {
            return true;
        }
        // MediaTek (via proc)
        if proc_gpufreq_dir.join("gpu_freq").exists()
            || proc_gpufreq_dir.join("gpufreq_opp_freq").exists()
            || std::path::Path::new("/proc/gpufreqv2/fix_target_opp_index").exists()
        {
            return true;
        }
        // Exynos Mali / Xclipse (pattern: /sys/devices/platform/*.{mali,sgpu}/devfreq/*/{min_freq,max_freq})
        if let Ok(dir) = std::fs::read_dir(platform_dir) {
            for entry in dir.flatten() {
                let name = entry.file_name();
                let name = name.to_string_lossy();
                if (name.contains(".mali") || name.contains(".sgpu"))
                    && has_devfreq_freq_controls(&entry.path().join("devfreq"))
                {
                    tracing::debug!(
                        target: "auriya::platform",
                        "GPU probe matched platform devfreq: {}",
                        entry.path().display()
                    );
                    return true;
                }
            }
        }
        // Generic GPU devfreq (/sys/class/devfreq/*{gpu,mali,sgpu}*)
        if let Ok(dir) = std::fs::read_dir(devfreq_dir) {
            for entry in dir.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if (name.contains("sgpu") || name.contains("mali") || name.contains("gpu"))
                    && !name.contains("kgsl")
                    && has_freq_controls(&entry.path())
                {
                    tracing::debug!(
                        target: "auriya::platform",
                        "GPU probe matched class devfreq: {}",
                        entry.path().display()
                    );
                    return true;
                }
            }
        }
        tracing::debug!(
            target: "auriya::platform",
            "GPU probe found no controllable GPU sysfs under {} or {}",
            platform_dir.display(),
            devfreq_dir.display()
        );
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

fn has_freq_controls(dir: &std::path::Path) -> bool {
    dir.join("min_freq").exists() || dir.join("max_freq").exists()
}

fn has_devfreq_freq_controls(devfreq_root: &std::path::Path) -> bool {
    let Ok(df) = std::fs::read_dir(devfreq_root) else {
        return false;
    };
    df.flatten().any(|sub| has_freq_controls(&sub.path()))
}
