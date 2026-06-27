use anyhow::Result;
use std::ffi::CString;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::ptr;

use libc::{MNT_DETACH, MS_BIND, MS_REC, mount, umount2};
use tracing::{debug, info, warn};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CeilingLevel {
    Low,
    Balance,
    High,
}

impl std::str::FromStr for CeilingLevel {
    type Err = ();
    fn from_str(s: &str) -> std::result::Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "low" => Ok(Self::Low),
            "balance" => Ok(Self::Balance),
            "high" => Ok(Self::High),
            _ => Err(()),
        }
    }
}

impl std::fmt::Display for CeilingLevel {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Low => write!(f, "low"),
            Self::Balance => write!(f, "balance"),
            Self::High => write!(f, "high"),
        }
    }
}

pub struct CoreLayout {
    pub all_core_ids: Vec<usize>,
    pub little_ids: Vec<usize>,
    pub big_ids: Vec<usize>,
    pub prime_ids: Vec<usize>,
    pub little_freqs_khz: Vec<u64>,
    pub big_freqs_khz: Vec<u64>,
}

impl CoreLayout {
    pub fn detect() -> Self {
        let mut cores_for_classify: Vec<usize> = (0..16)
            .filter(|i| Path::new(&format!("/sys/devices/system/cpu/cpu{}/cpufreq", i)).exists())
            .collect();

        if cores_for_classify.is_empty() {
            cores_for_classify = (0..8).collect();
        }

        let (little_mask, big_mask, prime_mask) =
            crate::core::tweaks::cpu::classify_cores(&cores_for_classify);

        let mut little_ids = Vec::new();
        let mut big_ids = Vec::new();
        let mut prime_ids = Vec::new();

        for &c in &cores_for_classify {
            if (prime_mask >> c) & 1 == 1 {
                prime_ids.push(c);
            } else if (big_mask >> c) & 1 == 1 {
                big_ids.push(c);
            } else if (little_mask >> c) & 1 == 1 {
                little_ids.push(c);
            }
        }

        if little_ids.is_empty() && prime_ids.is_empty() {
            let split = cores_for_classify.len() / 2;
            little_ids = cores_for_classify.iter().take(split).copied().collect();
            big_ids = cores_for_classify.iter().skip(split).copied().collect();
        }

        let little_freqs_khz = read_available_freqs(&little_ids);
        let big_freqs_khz = read_available_freqs(&big_ids);

        Self {
            all_core_ids: cores_for_classify,
            little_ids,
            big_ids,
            prime_ids,
            little_freqs_khz,
            big_freqs_khz,
        }
    }

}

fn read_available_freqs(core_ids: &[usize]) -> Vec<u64> {
    for &id in core_ids {
        let path = format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_available_frequencies",
            id
        );
        if let Ok(s) = fs::read_to_string(&path) {
            let mut freqs: Vec<u64> = s
                .split_whitespace()
                .filter_map(|f| f.parse::<u64>().ok())
                .collect();
            freqs.sort_unstable();
            return freqs;
        }
    }
    Vec::new()
}

fn do_mount_bind(src: &str, dest: &str) -> bool {
    let src_c = CString::new(src).unwrap();
    let dest_c = CString::new(dest).unwrap();
    unsafe {
        umount2(dest_c.as_ptr(), MNT_DETACH);
        mount(
            src_c.as_ptr().cast(),
            dest_c.as_ptr().cast(),
            ptr::null(),
            MS_BIND | MS_REC,
            ptr::null(),
        ) == 0
    }
}

fn do_unmount(path: &str) -> bool {
    let p = CString::new(path).unwrap();
    unsafe { umount2(p.as_ptr(), MNT_DETACH) == 0 }
}

/// The hardware frequency limit a frozen `scaling_{max,min}_freq` node
/// should be reset to on restore. A max-freq cap restores to
/// `cpuinfo_max_freq`; a min-freq lock restores to `cpuinfo_min_freq`.
fn hardware_limit_for(path: &str) -> Option<String> {
    let sibling = if path.ends_with("scaling_max_freq") {
        path.replace("scaling_max_freq", "cpuinfo_max_freq")
    } else if path.ends_with("scaling_min_freq") {
        path.replace("scaling_min_freq", "cpuinfo_min_freq")
    } else {
        return None;
    };
    fs::read_to_string(&sibling)
        .ok()
        .and_then(|s| s.split_whitespace().next().map(str::to_string))
}

/// Tear down any leftover ceiling mount-binds from a previous daemon
/// instance. A `scaling_{max,min}_freq` node appearing as a mount point in
/// `/proc/mounts` can only be our bind, so unmount it and reset the node to
/// its hardware limit.
fn cleanup_stale_mounts() {
    // 1. Unmount orphaned binds still present in /proc/mounts.
    if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
        for line in mounts.lines() {
            let mut fields = line.split_whitespace();
            let _src = fields.next();
            let Some(dest) = fields.next() else {
                continue;
            };
            if dest.ends_with("scaling_max_freq") || dest.ends_with("scaling_min_freq") {
                do_unmount(dest);
                let cache = format!(
                    "/cache/.auriya_ceiling_{}",
                    dest.replace('/', "_").trim_end_matches('_')
                );
                let _ = fs::remove_file(&cache);
                info!(target: "auriya::ceiling", "Cleaned stale ceiling mount: {}", dest);
            }
        }
    }

    // 2. Reset any freq node still marked read-only (0o444). Normal
    //    scaling_{max,min}_freq nodes are writable; 0o444 is the signature
    //    of our freeze whose bind was already torn down but whose value and
    //    permissions persisted (the cause of a cluster stuck low after a
    //    crash or a buggy restore). Reset perms and value to the hardware
    //    limit so the cluster scales freely again.
    for core in 0..16 {
        for kind in ["scaling_max_freq", "scaling_min_freq"] {
            let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/{kind}");
            let Ok(meta) = fs::metadata(&p) else {
                continue;
            };
            if meta.permissions().mode() & 0o777 == 0o444 {
                let _ = fs::set_permissions(&p, PermissionsExt::from_mode(0o644));
                if let Some(limit) = hardware_limit_for(&p) {
                    let _ = fs::write(&p, &limit);
                }
                info!(target: "auriya::ceiling", "Reset stale read-only freq node: {p}");
            }
        }
    }
}

#[derive(Debug, Clone)]
pub struct CeilingConfig {
    pub default: CeilingLevel,
    pub low_freq_little_khz: Option<u64>,
    pub low_freq_big_khz: Option<u64>,
}

impl Default for CeilingConfig {
    fn default() -> Self {
        Self {
            default: CeilingLevel::Balance,
            low_freq_little_khz: None,
            low_freq_big_khz: None,
        }
    }
}

struct MountEntry {
    path: String,
    mount_point: String,
    /// Whether the mount-bind actually succeeded. When it fails we still
    /// wrote the capped value to the real node, so restore must reset it
    /// regardless.
    bound: bool,
}

pub struct CeilingController {
    pub layout: CoreLayout,
    mounts: Vec<MountEntry>,
    current_level: Option<CeilingLevel>,
}

impl Default for CeilingController {
    fn default() -> Self {
        Self::new()
    }
}

impl CeilingController {
    pub fn new() -> Self {
        // A previous daemon that died mid-ceiling (crash, kill -9, update)
        // leaves mount-binds over scaling_{max,min}_freq nodes. Tear any
        // down and reset the nodes so we don't inherit a stuck cluster.
        cleanup_stale_mounts();

        let layout = CoreLayout::detect();
        if !layout.all_core_ids.is_empty() {
            info!(
                target: "auriya::ceiling",
                "Cores: little={:?}, big={:?}, prime={:?}",
                layout.little_ids, layout.big_ids, layout.prime_ids
            );
        } else {
            warn!(target: "auriya::ceiling", "No CPU cores detected, ceiling will be no-op");
        }
        Self {
            layout,
            mounts: Vec::new(),
            current_level: None,
        }
    }

    pub fn apply(&mut self, level: CeilingLevel, config: &CeilingConfig) -> Result<()> {
        if self.current_level == Some(level) {
            return Ok(());
        }

        self.restore();

        match level {
            CeilingLevel::Low => self.apply_low(config),
            CeilingLevel::Balance => {
                self.online_all();
                self.current_level = Some(CeilingLevel::Balance);
                Ok(())
            }
            CeilingLevel::High => self.apply_high(config),
        }
    }

    fn apply_low(&mut self, config: &CeilingConfig) -> Result<()> {
            let prime_ids = &self.layout.prime_ids;
        let big_ids = self.layout.big_ids.clone();
        let little_ids = self.layout.little_ids.clone();
        let little_freq = config
            .low_freq_little_khz
            .or_else(|| self.layout.little_freqs_khz.first().copied());
        let big_freq = config
            .low_freq_big_khz
            .or_else(|| self.layout.big_freqs_khz.first().copied());

        for &core in prime_ids {
            let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
            if Path::new(&path).exists() {
                let _ = fs::write(&path, "0");
                debug!(target: "auriya::ceiling", "Low: offline prime core {}", core);
            }
        }

        let half = big_ids.len() / 2;
        for &core in big_ids.iter().take(half) {
            let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
            if Path::new(&path).exists() {
                let _ = fs::write(&path, "0");
                debug!(target: "auriya::ceiling", "Low: offline big core {}", core);
            }
        }

        if let Some(freq) = little_freq {
            for core in little_ids {
                let p = format!(
                    "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq",
                    core
                );
                self.freeze_freq(&p, freq);
            }
        }

        if let Some(freq) = big_freq {
            for &core in big_ids.iter().skip(half) {
                let p = format!(
                    "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq",
                    core
                );
                self.freeze_freq(&p, freq);
            }
        }

        self.current_level = Some(CeilingLevel::Low);
        debug!(target: "auriya::ceiling", "Applied LOW ceiling");
        Ok(())
    }

    fn apply_high(&mut self, _config: &CeilingConfig) -> Result<()> {
        self.online_all();

        let all_ids = &self.layout.all_core_ids;
        let mut freq_targets: Vec<(String, u64)> = Vec::new();

        for &core in all_ids {
            let max_path = format!(
                "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
                core
            );
            if let Ok(s) = fs::read_to_string(&max_path)
                && let Some(max_str) = s.split_whitespace().next()
                && let Ok(max_val) = max_str.parse::<u64>()
            {
                let min_path = format!(
                    "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_min_freq",
                    core
                );
                freq_targets.push((min_path, max_val));
            }
        }

        for (path, val) in &freq_targets {
            self.freeze_freq(path, *val);
        }

        self.current_level = Some(CeilingLevel::High);
        debug!(target: "auriya::ceiling", "Applied HIGH ceiling");
        Ok(())
    }

    fn freeze_freq(&mut self, path: &str, value: u64) {
        let mount_point = format!(
            "/cache/.auriya_ceiling_{}",
            path.replace('/', "_").trim_end_matches('_')
        );
        let value_str = value.to_string();
        let _ = fs::write(path, &value_str);
        let _ = fs::set_permissions(path, PermissionsExt::from_mode(0o444));
        let _ = fs::write(&mount_point, &value_str);
        let bound = do_mount_bind(&mount_point, path);
        if bound {
            debug!(target: "auriya::ceiling", "Froze {} = {} kHz", path, value_str);
        } else {
            let _ = fs::set_permissions(path, PermissionsExt::from_mode(0o644));
            warn!(target: "auriya::ceiling", "Mount-bind failed for {}, freq cap active without bind", path);
        }
        // Track the path either way: even without a bind we overwrote the
        // real node and must reset it on restore.
        self.mounts.push(MountEntry {
            path: path.to_string(),
            mount_point,
            bound,
        });
    }

    pub fn restore(&mut self) {
        for entry in self.mounts.drain(..) {
            // Unmount FIRST so the following chmod/write hit the real sysfs
            // node, not the cache file shadowing it. Doing it the other way
            // round leaves the real node read-only (0o444) and stuck at the
            // capped value (e.g. prime min pinned at max after a game).
            if entry.bound {
                do_unmount(&entry.path);
            }
            let _ = fs::remove_file(&entry.mount_point);
            let _ = fs::set_permissions(&entry.path, PermissionsExt::from_mode(0o644));
            // The revealed node still holds the capped value we wrote before
            // binding; reset it to the hardware limit so the governor can
            // scale freely again.
            if let Some(limit) = hardware_limit_for(&entry.path) {
                let _ = fs::write(&entry.path, &limit);
                debug!(target: "auriya::ceiling", "Restored {} = {} kHz", entry.path, limit);
            } else {
                debug!(target: "auriya::ceiling", "Unmounted {}", entry.path);
            }
        }
        self.current_level = None;
    }

    pub fn online_all(&self) {
        for &core in &self.layout.all_core_ids {
            let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
            if Path::new(&path).exists() {
                let _ = fs::write(&path, "1");
            }
        }
    }
}

impl Drop for CeilingController {
    fn drop(&mut self) {
        self.restore();
        self.online_all();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn is_root() -> bool {
        unsafe { libc::geteuid() == 0 }
    }

    fn read_u64(path: &str) -> Option<u64> {
        fs::read_to_string(path)
            .ok()
            .and_then(|s| s.split_whitespace().next().and_then(|t| t.parse().ok()))
    }

    fn mode(path: &str) -> u32 {
        fs::metadata(path)
            .map(|m| m.permissions().mode() & 0o777)
            .unwrap_or(0)
    }

    fn online(core: usize) -> bool {
        fs::read_to_string(format!("/sys/devices/system/cpu/cpu{core}/online"))
            .map(|s| s.trim() == "1")
            .unwrap_or(true) // cpu0 often has no `online` node and is always up
    }

    fn snapshot(label: &str, cores: &[(&str, usize)]) {
        eprintln!("--- {label} ---");
        for (name, core) in cores {
            let d = format!("/sys/devices/system/cpu/cpu{core}/cpufreq");
            let max = read_u64(&format!("{d}/scaling_max_freq")).unwrap_or(0);
            let min = read_u64(&format!("{d}/scaling_min_freq")).unwrap_or(0);
            let mp = mode(&format!("{d}/scaling_max_freq"));
            let np = mode(&format!("{d}/scaling_min_freq"));
            eprintln!(
                "  {name:<6} cpu{core}: min={min:>8} max={max:>8} max_perm={mp:o} min_perm={np:o} online={}",
                online(*core)
            );
        }
    }

    /// Real-device test (root only): drive the ceiling through
    /// balance → sleep(Low) → balance → in-game(High) → balance, capturing
    /// each cluster's scaling_{min,max}_freq and permissions, and asserting
    /// that every release fully restores the hardware limits with writable
    /// permissions — the regression guard for the "cluster stuck low after
    /// screen-off→on / min pinned at max after a game" bug.
    ///
    /// Skipped automatically when not run as root (the default adb runner
    /// runs as the shell user). To exercise it:
    ///   AURIYA_TEST_ROOT=1 cargo test ceiling_scenarios -- --nocapture
    /// Stop the daemon first so it does not fight over the ceiling.
    #[test]
    fn ceiling_scenarios_capture_and_restore() {
        if !is_root() {
            eprintln!("ceiling_scenarios: SKIPPED (needs root; run with AURIYA_TEST_ROOT=1)");
            return;
        }

        let mut ctrl = CeilingController::new();
        let cfg = CeilingConfig::default();

        let mut cores: Vec<(&str, usize)> = Vec::new();
        if let Some(&c) = ctrl.layout.little_ids.first() {
            cores.push(("little", c));
        }
        // Use the LAST big core: apply_low freezes the upper half (the
        // first half is offlined), so this one exercises the freeze→restore
        // path rather than offline→online.
        if let Some(&c) = ctrl.layout.big_ids.last() {
            cores.push(("big", c));
        }
        if let Some(&c) = ctrl.layout.prime_ids.first() {
            cores.push(("prime", c));
        }
        assert!(!cores.is_empty(), "no CPU clusters detected");

        let hw_max = |core: usize| {
            read_u64(&format!(
                "/sys/devices/system/cpu/cpu{core}/cpufreq/cpuinfo_max_freq"
            ))
        };
        let hw_min = |core: usize| {
            read_u64(&format!(
                "/sys/devices/system/cpu/cpu{core}/cpufreq/cpuinfo_min_freq"
            ))
        };

        ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
        snapshot("BALANCE (baseline / free)", &cores);

        // SLEEP / screen-off → Low ceiling caps little + big max.
        ctrl.apply(CeilingLevel::Low, &cfg).unwrap();
        snapshot("LOW (sleep / screen-off)", &cores);

        // Release → every online core's max must be back at the hw limit
        // and writable (not the stuck-low 0o444 we used to leave behind).
        ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
        snapshot("BALANCE after LOW (restore)", &cores);
        for &(name, core) in &cores {
            if !online(core) {
                continue;
            }
            let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
            assert_eq!(read_u64(&p), hw_max(core), "{name} max not restored after LOW");
            assert_ne!(mode(&p) & 0o222, 0, "{name} scaling_max_freq still read-only after LOW");
        }

        // IN-GAME → High ceiling locks min = max on every core.
        ctrl.apply(CeilingLevel::High, &cfg).unwrap();
        snapshot("HIGH (in-game)", &cores);
        for &(name, core) in &cores {
            let min = read_u64(&format!(
                "/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq"
            ));
            assert_eq!(min, hw_max(core), "{name} min not locked to max under HIGH");
        }

        // Release → min must drop back to the hw minimum, writable.
        ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
        snapshot("BALANCE after HIGH (restore)", &cores);
        for &(name, core) in &cores {
            let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq");
            assert_eq!(read_u64(&p), hw_min(core), "{name} min not restored after HIGH");
            assert_ne!(mode(&p) & 0o222, 0, "{name} scaling_min_freq still read-only after HIGH");
        }

        // Drop restores + onlines everything, leaving the device clean.
        drop(ctrl);
    }
}
