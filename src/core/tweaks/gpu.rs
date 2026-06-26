use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::debug;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuVendor {
    Adreno,
    Mali,
    Unknown,
}

pub fn detect_vendor() -> GpuVendor {
    if Path::new("/sys/class/kgsl/kgsl-3d0").exists() {
        GpuVendor::Adreno
    } else if Path::new("/proc/gpufreq/gpufreq_opp_freq").exists()
        || Path::new("/proc/gpufreqv2/fix_target_opp_index").exists()
    {
        GpuVendor::Mali
    } else {
        GpuVendor::Unknown
    }
}

pub fn set_performance_mode() -> Result<()> {
    match detect_vendor() {
        GpuVendor::Adreno => set_adreno_performance()?,
        GpuVendor::Mali => set_mali_performance()?,
        GpuVendor::Unknown => {
            debug!("GPU vendor unknown, skipping GPU tweak");
            return Ok(());
        }
    }

    debug!("GPU performance mode enabled");
    Ok(())
}

fn set_adreno_performance() -> Result<()> {
    let base = "/sys/class/kgsl/kgsl-3d0";

    let max_freq_path = format!("{}/devfreq/max_freq", base);
    if let Ok(max_freq) = fs::read_to_string(&max_freq_path) {
        let max_freq = max_freq.trim();

        let min_freq_path = format!("{}/devfreq/min_freq", base);
        fs::write(&min_freq_path, max_freq).context("Cannot lock GPU to max freq")?;

        debug!("Adreno GPU locked at max freq: {} Hz", max_freq);
    }

    let _ = fs::write(format!("{}/force_clk_on", base), "1");
    let _ = fs::write(format!("{}/force_bus_on", base), "1");
    let _ = fs::write(format!("{}/bus_split", base), "0");

    Ok(())
}

fn set_mali_performance() -> Result<()> {
    if Path::new("/proc/gpufreqv2/fix_target_opp_index").exists() {
        let _ = fs::write("/proc/gpufreqv2/fix_target_opp_index", "0"); // 0 usually means max freq index
    } else if Path::new("/proc/gpufreq/gpufreq_opp_freq").exists() {
    }

    // Exynos/Tensor Mali: always_on eliminates GPU wake-up latency
    set_mali_power_policy("always_on");

    Ok(())
}

pub fn set_balanced_mode() -> Result<()> {
    match detect_vendor() {
        GpuVendor::Adreno => {
            let base = "/sys/class/kgsl/kgsl-3d0";

            let _ = fs::write(format!("{}/devfreq/governor", base), "msm-adreno-tz");

            let min_freq_path = format!("{}/devfreq/min_freq", base);
            if let Ok(avail) = fs::read_to_string(format!("{}/devfreq/available_frequencies", base))
                && let Some(min) = avail.split_whitespace().next()
            {
                let _ = fs::write(&min_freq_path, min);
            }

            let _ = fs::write(format!("{}/force_clk_on", base), "0");
            let _ = fs::write(format!("{}/force_bus_on", base), "0");
            let _ = fs::write(format!("{}/bus_split", base), "1");
        }
        GpuVendor::Mali => {
            if Path::new("/proc/gpufreqv2/fix_target_opp_index").exists() {
                let _ = fs::write("/proc/gpufreqv2/fix_target_opp_index", "-1"); // -1 unlocks
            }
            // coarse_demand lets GPU idle between frames
            set_mali_power_policy("coarse_demand");
        }
        GpuVendor::Unknown => {
            debug!("GPU vendor unknown, skipping restore");
        }
    }

    debug!("GPU set to balanced mode");
    Ok(())
}

fn set_mali_power_policy(policy: &str) {
    // Try both common Mali power_policy paths
    let candidates = [
        "/sys/devices/platform/13000000.mali/power_policy",
        "/sys/devices/platform/1c000000.mali/power_policy",
        "/sys/class/misc/mali0/device/power_policy",
    ];

    for path in &candidates {
        if Path::new(path).exists() {
            let _ = fs::write(path, policy);
            debug!("Mali power_policy set to {} via {}", policy, path);
            return;
        }
    }

    // Broader glob-style scan for Mali power_policy
    if let Ok(entries) = fs::read_dir("/sys/devices/platform") {
        for entry in entries.flatten() {
            let mali = entry.path().join("power_policy");
            if mali.exists() {
                let _ = fs::write(&mali, policy);
                debug!("Mali power_policy set to {} via {}", policy, mali.display());
                return;
            }
        }
    }
}
