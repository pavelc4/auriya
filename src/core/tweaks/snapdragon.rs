use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::info;

pub fn apply_performance() -> Result<()> {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
                let path = entry.path();
                let name = path.file_name().unwrap_or_default().to_string_lossy();

                if name.contains("cpu-lat")
                    || name.contains("cpu-bw")
                    || name.contains("llccbw")
                    || name.contains("bus_llcc")
                    || name.contains("bus_ddr")
                    || name.contains("memlat")
                    || name.contains("cpubw")
                    || name.contains("kgsl-ddr-qos")
                {
                    if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) && let Some(max) = avail
                        .split_whitespace()
                        .max_by_key(|x| x.parse::<u64>().unwrap_or(0)){
                        {
                            let _ = fs::write(path.join("max_freq"), max);
                            let _ = fs::write(path.join("min_freq"), max);
                        }
                    }
                }
        }
    }

    for component in ["DDR", "LLCC", "L3"] {
        let path = Path::new("/sys/devices/system/cpu/bus_dcvs").join(component);
        if path.exists() {
            if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) {
                if let Some(max) = avail
                    .split_whitespace()
                    .max_by_key(|x| x.parse::<u64>().unwrap_or(0))
                {
                    let _ = fs::write(path.join("hw_max_freq"), max);
                    let _ = fs::write(path.join("hw_min_freq"), max);
                }
            }
        }
    }

    info!("Applied Snapdragon performance tweaks");
    Ok(())
}

pub fn apply_normal() -> Result<()> {
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let path = entry.path();
            let name = path.file_name().unwrap_or_default().to_string_lossy();

                if name.contains("cpu-lat")
                    || name.contains("cpu-bw")
                    || name.contains("llccbw")
                    || name.contains("bus_llcc")
                    || name.contains("bus_ddr")
                    || name.contains("memlat")
                    || name.contains("cpubw")
                    || name.contains("kgsl-ddr-qos")
                {
                    if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) {
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
    }

    for component in ["DDR", "LLCC", "L3"] {
        let path = Path::new("/sys/devices/system/cpu/bus_dcvs").join(component);
        if path.exists() {
            if let Ok(avail) = fs::read_to_string(path.join("available_frequencies")) {
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
    }

    info!("Restored Snapdragon normal tweaks");
    Ok(())
}
