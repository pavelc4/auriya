use anyhow::{Context, Result};
use std::fs;
use std::path::{Path, PathBuf};
use tracing::{debug, warn};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuVendor {
    Adreno,
    Mali,
    Xclipse,
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

fn has_platform_suffix(platform_dir: &Path, suffix: &str) -> bool {
    let Ok(entries) = fs::read_dir(platform_dir) else {
        return false;
    };
    entries
        .flatten()
        .any(|e| e.file_name().to_string_lossy().contains(suffix))
}

/// Detect GPU vendor from sysfs/procfs hardware nodes under the given roots.
pub fn detect_vendor_in(
    kgsl_dir: &Path,
    platform_dir: &Path,
    devfreq_dir: &Path,
    mali0_dir: &Path,
) -> GpuVendor {
    match () {
        _ if kgsl_dir.exists() => GpuVendor::Adreno,
        _ if has_platform_suffix(platform_dir, ".sgpu")
            || has_platform_suffix(devfreq_dir, ".sgpu") =>
        {
            GpuVendor::Xclipse
        }
        _ if Path::new("/proc/gpufreq/gpufreq_opp_freq").exists()
            || Path::new("/proc/gpufreqv2/fix_target_opp_index").exists()
            || mali0_dir.exists()
            || has_platform_suffix(platform_dir, ".mali") =>
        {
            GpuVendor::Mali
        }
        _ => GpuVendor::Unknown,
    }
}

pub fn detect_vendor() -> GpuVendor {
    detect_vendor_in(
        Path::new("/sys/class/kgsl/kgsl-3d0"),
        Path::new("/sys/devices/platform"),
        Path::new("/sys/class/devfreq"),
        Path::new("/sys/class/misc/mali0"),
    )
}

pub fn set_performance_mode() -> Result<()> {
    match detect_vendor() {
        GpuVendor::Adreno => set_adreno_performance()?,
        GpuVendor::Mali => set_mali_performance()?,
        GpuVendor::Xclipse => {
            set_xclipse_performance_in(
                Path::new("/sys/class/devfreq"),
                Path::new("/sys/devices/platform"),
                Path::new("/sys/kernel/gpu"),
            );
        }
        GpuVendor::Unknown => {
            set_devfreq_gpu_performance_in(
                Path::new("/sys/class/devfreq"),
                Path::new("/sys/devices/platform"),
            );
            debug!("GPU vendor unknown, applied generic devfreq performance fallback");
            return Ok(());
        }
    }

    debug!("GPU performance mode enabled");
    Ok(())
}

pub fn set_powersave_mode() -> Result<()> {
    match detect_vendor() {
        GpuVendor::Adreno => set_adreno_powersave()?,
        GpuVendor::Mali => set_mali_powersave()?,
        GpuVendor::Xclipse => {
            set_xclipse_balanced_in(
                Path::new("/sys/class/devfreq"),
                Path::new("/sys/devices/platform"),
                Path::new("/sys/kernel/gpu"),
            );
        }
        GpuVendor::Unknown => {
            set_devfreq_gpu_balanced_in(
                Path::new("/sys/class/devfreq"),
                Path::new("/sys/devices/platform"),
            );
            debug!("GPU vendor unknown, skipping GPU powersave tweak");
            return Ok(());
        }
    }

    debug!("GPU powersave mode enabled");
    Ok(())
}

fn set_adreno_powersave() -> Result<()> {
    let base = "/sys/class/kgsl/kgsl-3d0";
    let _ = fs::write(format!("{}/devfreq/governor", base), "msm-adreno-tz");

    let num_pwr = fs::read_to_string(format!("{}/num_pwrlevels", base))
        .ok()
        .and_then(|n| n.trim().parse::<u32>().ok())
        .unwrap_or(8);

    // Limit max boost to mid-level (~324 MHz) to ensure smooth 60 FPS UI without wasteful high-clock wattage.
    // In KGSL: 0 is highest clock, num_pwrlevels - 1 is lowest clock.
    let cap_level = (num_pwr / 2).max(1);
    let min_pwr = if num_pwr > 0 { num_pwr - 1 } else { 8 };

    let _ = fs::write(format!("{}/max_pwrlevel", base), cap_level.to_string());
    let _ = fs::write(format!("{}/min_pwrlevel", base), min_pwr.to_string());

    let _ = fs::write(format!("{}/force_clk_on", base), "0");
    let _ = fs::write(format!("{}/force_bus_on", base), "0");
    let _ = fs::write(format!("{}/bus_split", base), "1");
    let _ = fs::write(format!("{}/idle_timer", base), "40");

    Ok(())
}

fn set_mali_powersave() -> Result<()> {
    if Path::new("/proc/gpufreqv2/fix_target_opp_index").exists() {
        let _ = fs::write("/proc/gpufreqv2/fix_target_opp_index", "-1");
    }
    set_mali_power_policy("coarse_demand");
    set_devfreq_gpu_balanced();
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
    let _ = fs::write(format!("{}/idle_timer", base), "2000");

    Ok(())
}

fn set_mali_performance() -> Result<()> {
    if Path::new("/proc/gpufreqv2/fix_target_opp_index").exists() {
        let _ = fs::write("/proc/gpufreqv2/fix_target_opp_index", "0"); // 0 usually means max freq index
    }

    // Exynos/Tensor Mali: always_on eliminates GPU wake-up latency
    set_mali_power_policy("always_on");
    set_devfreq_gpu_performance();

    Ok(())
}

fn is_gpu_devfreq_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    (lower.contains("gpu") || lower.contains("mali") || lower.contains("sgpu"))
        && !lower.contains("kgsl")
}

fn collect_gpu_devfreq_dirs(devfreq_dir: &Path, platform_dir: &Path) -> Vec<PathBuf> {
    let mut dirs = Vec::new();
    let mut seen = Vec::new();

    let mut push_unique = |path: PathBuf| {
        if !path.join("available_frequencies").exists() {
            return;
        }
        let canon = fs::canonicalize(&path).unwrap_or_else(|_| path.clone());
        if !seen.contains(&canon) {
            seen.push(canon);
            dirs.push(path);
        }
    };

    if let Ok(entries) = fs::read_dir(devfreq_dir) {
        for entry in entries.flatten() {
            if is_gpu_devfreq_name(&entry.file_name().to_string_lossy()) {
                push_unique(entry.path());
            }
        }
    }

    if let Ok(entries) = fs::read_dir(platform_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !is_gpu_devfreq_name(&name) {
                continue;
            }
            if let Ok(subs) = fs::read_dir(entry.path().join("devfreq")) {
                for sub in subs.flatten() {
                    push_unique(sub.path());
                }
            }
        }
    }

    dirs
}

fn set_devfreq_gpu_performance() {
    set_devfreq_gpu_performance_in(
        Path::new("/sys/class/devfreq"),
        Path::new("/sys/devices/platform"),
    );
}

fn write_sysfs_logged(path: &Path, val: &str) {
    match fs::write(path, val) {
        Ok(()) => debug!(target: "auriya::gpu", "GPU write {} <- {}", path.display(), val),
        Err(e) => {
            warn!(target: "auriya::gpu", "GPU write failed {} <- {}: {}", path.display(), val, e);
        }
    }
}

/// Pin discovered devfreq GPU nodes to their highest available frequency.
pub fn set_devfreq_gpu_performance_in(devfreq_dir: &Path, platform_dir: &Path) {
    let dirs = collect_gpu_devfreq_dirs(devfreq_dir, platform_dir);
    if dirs.is_empty() {
        debug!(target: "auriya::gpu", "No devfreq GPU nodes found for performance lock");
        return;
    }
    for path in dirs {
        if let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
            && let Some(max) = highest_available_freq(&avail)
        {
            let max_s = max.to_string();
            write_sysfs_logged(&path.join("min_freq"), &max_s);
            write_sysfs_logged(&path.join("max_freq"), &max_s);
        }
    }
}

fn set_devfreq_gpu_balanced() {
    set_devfreq_gpu_balanced_in(
        Path::new("/sys/class/devfreq"),
        Path::new("/sys/devices/platform"),
    );
}

/// Restore full min/max frequency range on discovered devfreq GPU nodes.
pub fn set_devfreq_gpu_balanced_in(devfreq_dir: &Path, platform_dir: &Path) {
    let dirs = collect_gpu_devfreq_dirs(devfreq_dir, platform_dir);
    if dirs.is_empty() {
        debug!(target: "auriya::gpu", "No devfreq GPU nodes found for balanced restore");
        return;
    }
    for path in dirs {
        if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) {
            if let Some(min) = lowest_available_freq(&avail) {
                write_sysfs_logged(&path.join("min_freq"), &min.to_string());
            }
            if let Some(max) = highest_available_freq(&avail) {
                write_sysfs_logged(&path.join("max_freq"), &max.to_string());
            }
        }
    }
}

fn read_xclipse_freq_bounds(devfreq_dir: &Path, platform_dir: &Path) -> Option<(u64, u64)> {
    for path in collect_gpu_devfreq_dirs(devfreq_dir, platform_dir) {
        if let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
            && let (Some(min), Some(max)) = (
                lowest_available_freq(&avail),
                highest_available_freq(&avail),
            )
        {
            return Some((min, max));
        }
    }
    None
}

fn sync_xclipse_kernel_gpu(
    kernel_gpu_dir: &Path,
    min_freq: Option<u64>,
    max_freq: Option<u64>,
    ifpo_val: &str,
) {
    if let Some(freq) = min_freq {
        let min_clock = kernel_gpu_dir.join("gpu_min_clock");
        if min_clock.exists() {
            write_sysfs_logged(&min_clock, &freq.to_string());
        }
    }
    if let Some(freq) = max_freq {
        let max_clock = kernel_gpu_dir.join("gpu_max_clock");
        if max_clock.exists() {
            write_sysfs_logged(&max_clock, &freq.to_string());
        }
    }
    let ifpo = kernel_gpu_dir.join("gpu_ifpo_runtime_control");
    if ifpo.exists() {
        write_sysfs_logged(&ifpo, ifpo_val);
    }
}

/// Apply Samsung Xclipse (`sgpu`) performance clocks and disable runtime IFPO if exposed.
pub fn set_xclipse_performance_in(devfreq_dir: &Path, platform_dir: &Path, kernel_gpu_dir: &Path) {
    debug!(target: "auriya::gpu", "Applying Xclipse (sgpu) performance mode");
    set_devfreq_gpu_performance_in(devfreq_dir, platform_dir);
    let max = read_xclipse_freq_bounds(devfreq_dir, platform_dir).map(|(_min, max)| max);
    sync_xclipse_kernel_gpu(kernel_gpu_dir, max, max, "0");
}

/// Restore Samsung Xclipse (`sgpu`) dynamic clock range and re-enable runtime IFPO if exposed.
pub fn set_xclipse_balanced_in(devfreq_dir: &Path, platform_dir: &Path, kernel_gpu_dir: &Path) {
    debug!(target: "auriya::gpu", "Applying Xclipse (sgpu) balanced mode");
    set_devfreq_gpu_balanced_in(devfreq_dir, platform_dir);
    let bounds = read_xclipse_freq_bounds(devfreq_dir, platform_dir);
    sync_xclipse_kernel_gpu(
        kernel_gpu_dir,
        bounds.map(|(min, _)| min),
        bounds.map(|(_, max)| max),
        "1",
    );
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
            let _ = fs::write(format!("{}/idle_timer", base), "80");
        }
        GpuVendor::Mali => {
            if Path::new("/proc/gpufreqv2/fix_target_opp_index").exists() {
                let _ = fs::write("/proc/gpufreqv2/fix_target_opp_index", "-1"); // -1 unlocks
            }
            // coarse_demand lets GPU idle between frames
            set_mali_power_policy("coarse_demand");
            set_devfreq_gpu_balanced();
        }
        GpuVendor::Xclipse => {
            set_xclipse_balanced_in(
                Path::new("/sys/class/devfreq"),
                Path::new("/sys/devices/platform"),
                Path::new("/sys/kernel/gpu"),
            );
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
