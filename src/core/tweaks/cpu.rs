use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};
use tracing::{debug, info, warn};

static LAST_TASKSET_WARN_MS: AtomicU64 = AtomicU64::new(0);
static LAST_RENICE_WARN_MS: AtomicU64 = AtomicU64::new(0);
const WARN_DEBOUNCE_MS: u64 = 30000;

#[inline]
fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|_| std::time::Duration::from_secs(0))
        .as_millis() as u64
}

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

pub fn get_online_cores() -> Result<Vec<usize>> {
    let content = fs::read_to_string("/sys/devices/system/cpu/online")
        .context("Failed to read cpu online file")?;
    Ok(parse_online_cores(&content))
}

pub fn parse_online_cores(s: &str) -> Vec<usize> {
    let mut cores = Vec::new();
    for part in s.trim().split(',') {
        if part.contains('-') {
            let bounds: Vec<&str> = part.split('-').collect();
            if bounds.len() == 2
                && let (Ok(start), Ok(end)) =
                    (bounds[0].parse::<usize>(), bounds[1].parse::<usize>())
            {
                for c in start..=end {
                    cores.push(c);
                }
            }
        } else if let Ok(c) = part.parse::<usize>() {
            cores.push(c);
        }
    }
    cores
}

pub fn read_core_max_freq(core: usize) -> Option<u32> {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
        core
    );
    if Path::new(&path).exists()
        && let Ok(s) = fs::read_to_string(&path)
        && let Ok(freq) = s.trim().parse::<u32>()
    {
        return Some(freq);
    }
    None
}

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

pub fn get_affinity_mask_for_profile(profile: &str) -> u64 {
    match get_online_cores() {
        Ok(cores) => {
            let (little, big, prime) = classify_cores(&cores);
            match profile {
                "performance" => big | prime,
                "balance" => big,
                "powersave" => little,
                _ => big | prime,
            }
        }
        Err(_) => 0xffff_ffff_ffff_ffff,
    }
}

pub fn set_game_affinity_dynamic(pid: i32, profile: &str) -> Result<()> {
    let mask = get_affinity_mask_for_profile(profile);
    if mask == 0xffff_ffff_ffff_ffff {
        debug!(target: "auriya:cpu", "Skipping affinity: invalid mask (get_online_cores failed)");
        return Ok(());
    }

    if mask == 0 {
        debug!(target: "auriya:cpu", "Skipping affinity: mask is zero for profile={}", profile);
        return Ok(());
    }

    let mut cpu_set: libc::cpu_set_t = unsafe { std::mem::zeroed() };

    for i in 0..64 {
        if (mask >> i) & 1 == 1 {
            unsafe { libc::CPU_SET(i, &mut cpu_set) };
        }
    }

    let result =
        unsafe { libc::sched_setaffinity(pid, std::mem::size_of::<libc::cpu_set_t>(), &cpu_set) };

    if result == 0 {
        info!(
            target: "auriya:cpu",
            "Set CPU affinity pid={} mask={:x} profile={}",
            pid, mask, profile
        );
    } else {
        let now = now_ms();
        let last = LAST_TASKSET_WARN_MS.load(Ordering::Relaxed);
        if now.saturating_sub(last) > WARN_DEBOUNCE_MS {
            warn!(
                target: "auriya:cpu",
                "sched_setaffinity failed: errno={}",
                std::io::Error::last_os_error()
            );
            LAST_TASKSET_WARN_MS.store(now, Ordering::Relaxed);
        }
    }

    Ok(())
}

pub fn set_process_priority(pid: i32) -> Result<()> {
    let result = unsafe { libc::setpriority(libc::PRIO_PROCESS, pid as libc::id_t, -20) };

    if result != 0 {
        let now = now_ms();
        let last = LAST_RENICE_WARN_MS.load(Ordering::Relaxed);
        if now.saturating_sub(last) > WARN_DEBOUNCE_MS {
            debug!(target: "auriya:cpu", "setpriority failed: errno={}", std::io::Error::last_os_error());
            LAST_RENICE_WARN_MS.store(now, Ordering::Relaxed);
        }
    }

    let oom_path = format!("/proc/{}/oom_score_adj", pid);
    if Path::new(&oom_path).exists() {
        let _ = fs::write(oom_path, "-800");
    }

    info!(target: "auriya:cpu", "Process priority set for PID {}", pid);
    Ok(())
}
