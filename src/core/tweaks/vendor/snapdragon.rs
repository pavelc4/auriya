use anyhow::Result;
use std::{fs, path::Path};
use tracing::{debug, warn};

use crate::core::tweaks::paths::snapdragon_paths;

mod perf {
    pub const GPU_PWRLEVEL: &str = "0";
    pub const GPU_IDLE_TIMER: &str = "10000";
    pub const MEMLAT_SAMPLE_MS: &str = "4";
}

fn apply_gpu_performance() {
    let Some(kgsl) = &snapdragon_paths().kgsl else {
        debug!("kgsl path not found, skipping GPU tweaks");
        return;
    };

    if fs::write(kgsl.join("min_pwrlevel"), perf::GPU_PWRLEVEL).is_err() {
        warn!("Failed to set GPU min_pwrlevel");
    }
    if fs::write(kgsl.join("max_pwrlevel"), perf::GPU_PWRLEVEL).is_err() {
        warn!("Failed to set GPU max_pwrlevel");
    }

    let _ = fs::write(kgsl.join("idle_timer"), perf::GPU_IDLE_TIMER);
    let _ = fs::write(kgsl.join("bus_split"), "0");
    let _ = fs::write(kgsl.join("force_clk_on"), "1");

    debug!("Applied GPU performance tweaks");
}

fn apply_gpu_normal() {
    let paths = snapdragon_paths();
    let Some(kgsl) = &paths.kgsl else {
        return;
    };

    // In KGSL: max_pwrlevel <= current_pwrlevel <= min_pwrlevel.
    // When restoring normal mode: write min_pwrlevel FIRST, so that
    // max_pwrlevel (0) <= min_pwrlevel (e.g. 8) is valid.
    // Setting min_pwrlevel to num_pwrlevels - 1 unlocks the full dynamic range down to
    // the lowest OPP (e.g. 20 MHz on KonaBess or stock lowest).
    let min_pwr = fs::read_to_string(kgsl.join("num_pwrlevels"))
        .ok()
        .and_then(|n| n.trim().parse::<u32>().ok())
        .filter(|&n| n > 0)
        .map(|n| (n - 1).to_string())
        .or_else(|| paths.orig_min_pwrlevel.clone().filter(|s| s != "0"))
        .unwrap_or_else(|| "8".to_string());
    let _ = fs::write(kgsl.join("min_pwrlevel"), &min_pwr);

    let max_pwr = paths.orig_max_pwrlevel.as_deref().unwrap_or("0");
    let _ = fs::write(kgsl.join("max_pwrlevel"), max_pwr);

    let idle_timer = paths
        .orig_idle_timer
        .as_deref()
        .filter(|&s| s != "10000")
        .unwrap_or("80");
    let _ = fs::write(kgsl.join("idle_timer"), idle_timer);

    let _ = fs::write(kgsl.join("bus_split"), "1");
    let _ = fs::write(kgsl.join("force_clk_on"), "0");
    let _ = fs::write(kgsl.join("force_bus_on"), "0");
    debug!(
        "Restored GPU original settings (min_pwrlevel={}, max_pwrlevel={}, idle_timer={})",
        min_pwr, max_pwr, idle_timer
    );
}

fn apply_memlat_performance() {
    let Some(memlat) = &snapdragon_paths().memlat_settings else {
        return;
    };

    let _ = fs::write(memlat.join("sample_ms"), perf::MEMLAT_SAMPLE_MS);
    debug!("Applied memlat performance tweaks");
}

fn apply_memlat_normal() {
    let paths = snapdragon_paths();
    let Some(memlat) = &paths.memlat_settings else {
        return;
    };

    if let Some(ref val) = paths.orig_memlat_sample_ms {
        let _ = fs::write(memlat.join("sample_ms"), val);
    }

    debug!("Restored memlat original settings");
}

pub fn apply_performance() -> Result<()> {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let path = entry.path();
            let name = path.file_name().unwrap_or_default().to_string_lossy();

            if (name.contains("cpu-lat")
                || name.contains("cpu-bw")
                || name.contains("llccbw")
                || name.contains("bus_llcc")
                || name.contains("bus_ddr")
                || name.contains("memlat")
                || name.contains("cpubw")
                || name.contains("kgsl-ddr-qos"))
                && let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
                && let Some(max) = avail
                    .split_whitespace()
                    .max_by_key(|x| x.parse::<u64>().unwrap_or(0))
            {
                let _ = fs::write(path.join("max_freq"), max);
                let _ = fs::write(path.join("min_freq"), max);
            }
        }
    }

    for component in ["DDR", "LLCC", "L3"] {
        let path = Path::new("/sys/devices/system/cpu/bus_dcvs").join(component);
        if path.exists()
            && let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
            && let Some(max) = avail
                .split_whitespace()
                .max_by_key(|x| x.parse::<u64>().unwrap_or(0))
        {
            let _ = fs::write(path.join("hw_max_freq"), max);
            let _ = fs::write(path.join("hw_min_freq"), max);
        }
    }
    apply_gpu_performance();
    apply_memlat_performance();

    debug!("Applied Snapdragon performance tweaks");
    Ok(())
}

pub fn apply_normal() -> Result<()> {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let path = entry.path();
            let name = path.file_name().unwrap_or_default().to_string_lossy();

            if (name.contains("cpu-lat")
                || name.contains("cpu-bw")
                || name.contains("llccbw")
                || name.contains("bus_llcc")
                || name.contains("bus_ddr")
                || name.contains("memlat")
                || name.contains("cpubw")
                || name.contains("kgsl-ddr-qos"))
                && let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
            {
                let freqs: Vec<u64> = avail
                    .split_whitespace()
                    .filter_map(|x| x.parse().ok())
                    .collect();

                if let (Some(&min), Some(&max)) = (freqs.iter().min(), freqs.iter().max()) {
                    let _ = fs::write(path.join("max_freq"), max.to_string());
                    let _ = fs::write(path.join("min_freq"), min.to_string());
                }
            }
        }
    }

    for component in ["DDR", "LLCC", "L3"] {
        let path = Path::new("/sys/devices/system/cpu/bus_dcvs").join(component);
        if path.exists()
            && let Ok(avail) = fs::read_to_string(path.join("available_frequencies"))
        {
            let freqs: Vec<u64> = avail
                .split_whitespace()
                .filter_map(|x| x.parse().ok())
                .collect();

            if let (Some(&min), Some(&max)) = (freqs.iter().min(), freqs.iter().max()) {
                let _ = fs::write(path.join("hw_max_freq"), max.to_string());
                let _ = fs::write(path.join("hw_min_freq"), min.to_string());
            }
        }
    }
    apply_gpu_normal();
    apply_memlat_normal();

    debug!("Restored Snapdragon normal tweaks");
    Ok(())
}
