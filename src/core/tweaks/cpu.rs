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
    const BOOST_PATHS: &[&str] = &[
        "/sys/module/cpu_boost/parameters/input_boost_enabled",
        "/sys/module/cpu_boost/parameters/sched_boost_on_input",
    ];

    let mut found = false;
    for path in BOOST_PATHS {
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
    const BOOST_PATHS: &[&str] = &[
        "/sys/module/cpu_boost/parameters/input_boost_enabled",
        "/sys/module/cpu_boost/parameters/sched_boost_on_input",
    ];

    for path in BOOST_PATHS {
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
    let core_freqs: Vec<(usize, u32)> = cores
        .iter()
        .filter_map(|&core| read_core_max_freq(core).map(|freq| (core, freq)))
        .collect();

    if core_freqs.is_empty() {
        let big_mask = cores.iter().fold(0u64, |m, &c| m | (1u64 << c));
        return (0, big_mask, 0);
    }

    classify_from_freqs(&core_freqs)
}

/// Pure classification of `(core, hw_max_freq)` pairs into
/// `(little, big, prime)` bitmasks. Split out from [`classify_cores`] so the
/// tier logic can be unit-tested without sysfs. Assumes a non-empty input.
fn classify_from_freqs(core_freqs: &[(usize, u32)]) -> (u64, u64, u64) {
    let mut little_mask = 0u64;
    let mut big_mask = 0u64;
    let mut prime_mask = 0u64;

    // Group by distinct hardware max frequency (a robust proxy for the
    // cpufreq cluster). Highest tier = prime, lowest = little, anything in
    // between = big. Using distinct *tiers* rather than a median avoids the
    // classic 4+3+1 failure where the little tier ties the median and gets
    // misclassified as big (which then caps the whole little policy when
    // the ceiling freezes a "big" core that actually shares the little
    // policy).
    let mut tiers: Vec<u32> = core_freqs.iter().map(|&(_, f)| f).collect();
    tiers.sort_unstable_by(|a, b| b.cmp(a));
    tiers.dedup();
    let n_tiers = tiers.len();
    let top = tiers[0];
    let bottom = tiers[n_tiers - 1];

    for &(core, freq) in core_freqs {
        let bit = 1u64 << core;
        if n_tiers >= 3 && freq == top {
            prime_mask |= bit;
        } else if n_tiers >= 2 && freq == bottom {
            little_mask |= bit;
        } else {
            // Single-tier (all big), the middle tier of a 3+ layout, or the
            // top tier of a 2-tier layout (no dedicated prime).
            big_mask |= bit;
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

#[cfg(test)]
mod tests {
    use super::classify_from_freqs;

    /// Helper: turn a bitmask into a sorted Vec of core ids for assertions.
    fn ids(mask: u64) -> Vec<usize> {
        (0..64usize).filter(|&i| (mask >> i) & 1 == 1).collect()
    }

    #[test]
    fn classifies_4_3_1_topology() {
        // Snapdragon-style: cpu0-3 @1.8GHz, cpu4-6 @2.5GHz, cpu7 @2.9GHz.
        // Regression: the little tier used to tie the median and land in big.
        let freqs = [
            (0, 1_804_800),
            (1, 1_804_800),
            (2, 1_804_800),
            (3, 1_804_800),
            (4, 2_496_000),
            (5, 2_496_000),
            (6, 2_496_000),
            (7, 2_918_400),
        ];
        let (little, big, prime) = classify_from_freqs(&freqs);
        assert_eq!(ids(little), vec![0, 1, 2, 3], "little");
        assert_eq!(ids(big), vec![4, 5, 6], "big");
        assert_eq!(ids(prime), vec![7], "prime");
    }

    #[test]
    fn classifies_4_4_two_tier_no_prime() {
        let freqs = [
            (0, 1_800_000),
            (1, 1_800_000),
            (2, 1_800_000),
            (3, 1_800_000),
            (4, 2_400_000),
            (5, 2_400_000),
            (6, 2_400_000),
            (7, 2_400_000),
        ];
        let (little, big, prime) = classify_from_freqs(&freqs);
        assert_eq!(ids(little), vec![0, 1, 2, 3]);
        assert_eq!(ids(big), vec![4, 5, 6, 7]);
        assert_eq!(ids(prime), Vec::<usize>::new());
    }

    #[test]
    fn classifies_single_tier_all_big() {
        let freqs = [(0, 2_000_000), (1, 2_000_000), (2, 2_000_000), (3, 2_000_000)];
        let (little, big, prime) = classify_from_freqs(&freqs);
        assert_eq!(ids(little), Vec::<usize>::new());
        assert_eq!(ids(big), vec![0, 1, 2, 3]);
        assert_eq!(ids(prime), Vec::<usize>::new());
    }

    #[test]
    fn classifies_1_3_4_topology() {
        // 4 little, 3 big, 1 prime (different counts, same 3-tier shape).
        let freqs = [
            (0, 2_000_000),
            (1, 2_000_000),
            (2, 2_000_000),
            (3, 2_000_000),
            (4, 2_800_000),
            (5, 2_800_000),
            (6, 2_800_000),
            (7, 3_200_000),
        ];
        let (little, big, prime) = classify_from_freqs(&freqs);
        assert_eq!(ids(little), vec![0, 1, 2, 3]);
        assert_eq!(ids(big), vec![4, 5, 6]);
        assert_eq!(ids(prime), vec![7]);
    }
}
