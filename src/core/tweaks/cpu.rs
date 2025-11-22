use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::{debug, info, warn};

/// Enable CPU boost if available
pub fn enable_boost() -> Result<()> {
    let boost_paths = [
        "/sys/module/cpu_boost/parameters/input_boost_enabled",
        "/sys/module/cpu_boost/parameters/sched_boost_on_input",
    ];

    let mut found = false;
    for path in &boost_paths {
        if Path::new(path).exists() {
            fs::write(path, "1").context(format!("Failed to enable boost at {}", path))?;
            debug!("CPU boost enabled at {}", path);
            found = true;
        }
    }

    if !found {
        debug!("CPU boost module not available, skipping activation");
    }

    Ok(())
}

/// Disable CPU boost if available
pub fn disable_boost() -> Result<()> {
    let boost_paths = [
        "/sys/module/cpu_boost/parameters/input_boost_enabled",
        "/sys/module/cpu_boost/parameters/sched_boost_on_input",
    ];

    for path in &boost_paths {
        if Path::new(path).exists() {
            let _ = fs::write(path, "0");
        }
    }

    Ok(())
}

/// Check if a CPU core is really online (reads /sys/devices/system/cpu/cpuX/online)
fn is_core_online(core: usize) -> bool {
    let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
    fs::read_to_string(path)
        .map(|s| s.trim() == "1")
        // core 0 sometimes doesn't have online file but is always online
        .unwrap_or(core == 0)
}

/// Get list of truly online cores by verifying realtime online status
pub fn get_online_cores() -> Result<Vec<usize>> {
    let content = fs::read_to_string("/sys/devices/system/cpu/online")
        .context("Failed to read cpu online")?;
    let parsed = parse_online_cores(&content);
    let filtered = parsed.into_iter().filter(|&c| is_core_online(c)).collect();
    Ok(filtered)
}

/// Parse string range list like "0-3,5,7" into vector of core indexes
pub fn parse_online_cores(s: &str) -> Vec<usize> {
    let mut cores = Vec::new();
    for part in s.trim().split(',') {
        if part.contains('-') {
            let bounds: Vec<&str> = part.split('-').collect();
            if bounds.len() == 2 {
                if let (Ok(start), Ok(end)) = (bounds[0].parse(), bounds[1].parse()) {
                    for c in start..=end {
                        cores.push(c);
                    }
                }
            }
        } else if let Ok(c) = part.parse() {
            cores.push(c);
        }
    }
    cores
}

/// Read max frequency CPU core (in KHz), if available
pub fn read_core_max_freq(core: usize) -> Option<u32> {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
        core
    );
    if Path::new(&path).exists() {
        if let Ok(s) = fs::read_to_string(&path) {
            if let Ok(freq) = s.trim().parse::<u32>() {
                return Some(freq);
            }
        }
    }
    None
}

/// Classify cores into little, big, and prime clusters based on max frequency
pub fn classify_cores(cores: &[usize]) -> (u64, u64, u64) {
    let mut little_mask = 0u64;
    let mut big_mask = 0u64;
    let mut prime_mask = 0u64;

    let mut core_freqs: Vec<(usize, u32)> = cores
        .iter()
        .filter_map(|&core| read_core_max_freq(core).map(|freq| (core, freq)))
        .collect();

    if core_freqs.is_empty() {
        for &core in cores {
            big_mask |= 1 << core;
        }
        return (0, big_mask, 0);
    }

    core_freqs.sort_by(|a, b| b.1.cmp(&a.1));

    let prime_core = core_freqs[0].0;
    prime_mask |= 1 << prime_core;

    let median_freq = if core_freqs.len() > 2 {
        core_freqs[core_freqs.len() / 2].1
    } else {
        0
    };

    for &(core, freq) in &core_freqs {
        if core == prime_core {
            continue;
        }
        if freq >= median_freq && freq >= 1_000_000 {
            big_mask |= 1 << core;
        } else {
            little_mask |= 1 << core;
        }
    }

    (little_mask, big_mask, prime_mask)
}

/// Get CPU affinity mask based on profile type, considering only online cores
pub fn get_affinity_mask_for_profile(profile: &str) -> u64 {
    match get_online_cores() {
        Ok(cores) => {
            let (little, big, prime) = classify_cores(&cores);
            match profile {
                "performance" => {
                    if prime != 0 {
                        prime
                    } else {
                        big | prime
                    }
                }
                "balance" => big,
                "powersave" => little,
                _ => big | prime,
            }
        }
        Err(_) => {
            // Fallback: all cores enabled mask (up to 64 cores)
            0xffff_ffff_ffff_ffff
        }
    }
}

/// Set CPU affinity dynamically for game process by pid and profile
pub fn set_game_affinity_dynamic(pid: i32, profile: &str) -> Result<()> {
    // Helper to run taskset
    let run_taskset = |mask: u64| -> Result<std::process::Output> {
        let mask_hex = format!("0x{:x}", mask);
        std::process::Command::new("taskset")
            .args(&["-p", &mask_hex, &pid.to_string()])
            .output()
            .map_err(|e| anyhow::anyhow!("Failed to execute taskset: {}", e))
    };

    let mask = get_affinity_mask_for_profile(profile);
    let output = run_taskset(mask)?;

    if output.status.success() {
        info!(
            "Set CPU affinity pid={} mask=0x{:x} profile={}",
            pid, mask, profile
        );
        return Ok(());
    }

    // If failed, it might be due to a core going offline.
    // Retry once with fresh online cores.
    warn!(
        "taskset failed with mask 0x{:x}, retrying with fresh online cores...",
        mask
    );

    // Small delay to let state settle
    std::thread::sleep(std::time::Duration::from_millis(50));

    let new_mask = get_affinity_mask_for_profile(profile);
    let retry_output = run_taskset(new_mask)?;

    if retry_output.status.success() {
        info!(
            "Retry successful: Set CPU affinity pid={} mask=0x{:x} profile={}",
            pid, new_mask, profile
        );
        Ok(())
    } else {
        warn!(
            "taskset retry failed: {}",
            String::from_utf8_lossy(&retry_output.stderr)
        );
        Ok(())
    }
}

/// Check if a specific core is online
pub fn get_core_online_status(core: usize) -> Result<bool> {
    let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
    if !Path::new(&path).exists() {
        // cpu0 is usually always online and might not have the file
        if core == 0 {
            return Ok(true);
        }
        return Ok(false);
    }

    let content = fs::read_to_string(&path)
        .context(format!("Failed to read online status for cpu{}", core))?;

    Ok(content.trim() == "1")
}

/// Set core online status with verification
/// Returns true if the status was successfully set (or was already correct)
pub fn set_core_online_status(core: usize, online: bool) -> Result<bool> {
    let target_val = if online { "1" } else { "0" };
    let path = format!("/sys/devices/system/cpu/cpu{}/online", core);

    if !Path::new(&path).exists() {
        if core == 0 && online {
            return Ok(true);
        }
        debug!("CPU{} online file not found, cannot change status", core);
        return Ok(false);
    }

    // 1. Check current status first
    if get_core_online_status(core)? == online {
        return Ok(true);
    }

    // 2. Force write
    if let Err(e) = fs::write(&path, target_val) {
        warn!("Failed to write {} to cpu{}: {}", target_val, core, e);
        return Ok(false);
    }

    // 3. Verify (Double Check)
    // Small delay to let kernel process the request
    std::thread::sleep(std::time::Duration::from_millis(10));

    let new_status = get_core_online_status(core)?;
    if new_status == online {
        debug!(
            "Successfully set cpu{} to {}",
            core,
            if online { "online" } else { "offline" }
        );
        Ok(true)
    } else {
        warn!(
            "Failed to set cpu{} to {} (kernel rejected)",
            core,
            if online { "online" } else { "offline" }
        );
        Ok(false)
    }
}

/// Online all cpu cores (except cpu0)
pub fn online_all_cores() -> Result<()> {
    let mut success_count = 0;
    for i in 1..8 {
        match set_core_online_status(i, true) {
            Ok(true) => success_count += 1,
            Ok(false) => debug!("Could not online cpu{}", i),
            Err(e) => warn!("Error checking cpu{}: {}", i, e),
        }
    }
    debug!("Onlined {}/7 secondary cores", success_count);
    Ok(())
}

/// Set process priority with renice and oom_score_adj
pub fn set_process_priority(pid: i32) -> Result<()> {
    let _ = std::process::Command::new("renice")
        .args(&["-n", "-20", "-p", &pid.to_string()])
        .output();

    let oom_path = format!("/proc/{}/oom_score_adj", pid);
    let _ = fs::write(&oom_path, "-1000");

    info!("Process priority set for PID {}", pid);
    Ok(())
}
