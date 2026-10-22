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

/// Numeric min, not first entry: some devices list
/// available_frequencies descending (max first), which would otherwise
/// lock the GPU at the top frequency.
fn lowest_available_freq(text: &str) -> Option<u64> {
    text.split_whitespace()
        .filter_map(|f| f.parse::<u64>().ok())
        .min()
}

fn highest_available_freq(text: &str) -> Option<u64> {
    text.split_whitespace()
        .filter_map(|f| f.parse::<u64>().ok())
        .max()
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

    let avail_path = format!("{}/devfreq/available_frequencies", base);
    let max_freq = if let Ok(avail) = fs::read_to_string(&avail_path)
        && let Some(max) = highest_available_freq(&avail)
    {
        max.to_string()
    } else {
        fs::read_to_string(format!("{}/devfreq/max_freq", base))
            .unwrap_or_default()
            .trim()
            .to_string()
    };

    if !max_freq.is_empty() {
        let min_freq_path = format!("{}/devfreq/min_freq", base);
        fs::write(&min_freq_path, &max_freq).context("Cannot lock GPU to max freq")?;
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
    set_devfreq_gpu_performance();

    Ok(())
}

fn set_devfreq_gpu_performance() {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_lowercase();
            if (name.contains("gpu") || name.contains("mali")) && !name.contains("kgsl") {
                let path = entry.path();
                if let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
                    && let Some(max) = highest_available_freq(&avail)
                {
                    let _ = fs::write(path.join("min_freq"), max.to_string());
                    let _ = fs::write(path.join("max_freq"), max.to_string());
                }
            }
        }
    }
}

fn set_devfreq_gpu_balanced() {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_lowercase();
            if (name.contains("gpu") || name.contains("mali")) && !name.contains("kgsl") {
                let path = entry.path();
                if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) {
                    if let Some(min) = lowest_available_freq(&avail) {
                        let _ = fs::write(path.join("min_freq"), min.to_string());
                    }
                    if let Some(max) = highest_available_freq(&avail) {
                        let _ = fs::write(path.join("max_freq"), max.to_string());
                    }
                }
            }
        }
    }
}

pub fn set_balanced_mode() -> Result<()> {
    match detect_vendor() {
        GpuVendor::Adreno => {
            let base = "/sys/class/kgsl/kgsl-3d0";

            let _ = fs::write(format!("{}/devfreq/governor", base), "msm-adreno-tz");

            let avail_path = format!("{}/devfreq/available_frequencies", base);
            if let Ok(avail) = fs::read_to_string(&avail_path) {
                if let Some(min) = lowest_available_freq(&avail) {
                    let _ = fs::write(format!("{}/devfreq/min_freq", base), min.to_string());
                }
                if let Some(max) = highest_available_freq(&avail) {
                    let _ = fs::write(format!("{}/devfreq/max_freq", base), max.to_string());
                }
            }

            // Always unlock KGSL power levels to full dynamic range:
            // max_pwrlevel = 0 (highest frequency index)
            // min_pwrlevel = num_pwrlevels - 1 (lowest frequency index, e.g. 20 MHz or lowest OPP in KonaBess/stock)
            let min_pwr = fs::read_to_string(format!("{}/num_pwrlevels", base))
                .ok()
                .and_then(|n| n.trim().parse::<u32>().ok())
                .filter(|&n| n > 0)
                .map(|n| (n - 1).to_string())
                .unwrap_or_else(|| "8".to_string());
            let _ = fs::write(format!("{}/min_pwrlevel", base), &min_pwr);
            let _ = fs::write(format!("{}/max_pwrlevel", base), "0");

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
            set_devfreq_gpu_balanced();
        }
        GpuVendor::Unknown => {
            set_devfreq_gpu_balanced();
            debug!("GPU vendor unknown, applied generic devfreq restore");
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
