use anyhow::Result;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

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
            super::topology::classify_cores(&cores_for_classify);

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

/// Parse a `scaling_available_frequencies` payload into a sorted list of
/// kHz values. Garbage tokens are dropped so a partially-rewritten file still
/// yields a usable list.
fn parse_available_freqs(text: &str) -> Vec<u64> {
    let mut freqs: Vec<u64> = text
        .split_whitespace()
        .filter_map(|f| f.parse::<u64>().ok())
        .collect();
    freqs.sort_unstable();
    freqs
}

fn read_available_freqs(core_ids: &[usize]) -> Vec<u64> {
    for &id in core_ids {
        let path = format!(
            "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_available_frequencies",
            id
        );
        if let Ok(s) = fs::read_to_string(&path) {
            return parse_available_freqs(&s);
        }
    }
    Vec::new()
}

/// Extract the core number from a cpufreq sysfs path.
/// e.g. `/sys/devices/system/cpu/cpu3/cpufreq/scaling_max_freq` → `3`
fn core_id_from_freq_path(path: &str) -> Option<usize> {
    let marker = "system/cpu/cpu";
    let idx = path.find(marker)?;
    let rest = &path[idx + marker.len()..];
    let end = rest.find('/').unwrap_or(rest.len());
    rest[..end].parse().ok()
}

/// Read the hardware max frequency for a single core. Tries
/// `cpuinfo_max_freq` first; falls back to `max(scaling_available_frequencies)`
/// when the former is unreadable (e.g. mode 0000 on some MediaTek devices).
pub fn hw_max_freq_for_core(core: usize) -> Option<u64> {
    let cpuinfo = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
        core
    );
    if let Some(v) = fs::read_to_string(&cpuinfo)
        .ok()
        .and_then(|s| s.split_whitespace().next()?.parse::<u64>().ok())
    {
        return Some(v);
    }
    // Fallback: the highest frequency in scaling_available_frequencies.
    let avail = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_available_frequencies",
        core
    );
    fs::read_to_string(&avail).ok().and_then(|s| {
        s.split_whitespace()
            .filter_map(|t| t.parse::<u64>().ok())
            .max()
    })
}

fn hardware_limit_for(path: &str) -> Option<String> {
    let core = core_id_from_freq_path(path)?;
    match path.rsplit('/').next() {
        Some("scaling_max_freq") => hw_max_freq_for_core(core).map(|v| v.to_string()),
        Some("scaling_min_freq") => {
            let p = path.replace("scaling_min_freq", "cpuinfo_min_freq");
            fs::read_to_string(&p)
                .ok()
                .and_then(|s| s.split_whitespace().next().map(str::to_string))
        }
        _ => None,
    }
}

/// Tear down any leftover ceiling mount-binds from legacy daemon instances.
fn cleanup_stale_mounts() {
    if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
        for line in mounts.lines() {
            let mut fields = line.split_whitespace();
            let _src = fields.next();
            let Some(dest) = fields.next() else {
                continue;
            };
            if dest.ends_with("scaling_max_freq") || dest.ends_with("scaling_min_freq") {
                if let Ok(dest_c) = std::ffi::CString::new(dest) {
                    unsafe {
                        libc::umount2(dest_c.as_ptr(), libc::MNT_DETACH);
                    }
                }
                let cache = format!(
                    "/cache/.auriya_ceiling_{}",
                    dest.replace('/', "_").trim_end_matches('_')
                );
                let _ = fs::remove_file(&cache);
                info!(target: "auriya::ceiling", "Cleaned stale ceiling mount: {}", dest);
            }
        }
    }

    // Reset any freq node still marked read-only (0o444) from older releases
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

pub struct CeilingController {
    pub layout: CoreLayout,
    applied_nodes: Vec<String>,
    current_level: Option<CeilingLevel>,
}

impl Default for CeilingController {
    fn default() -> Self {
        Self::new()
    }
}

impl CeilingController {
    pub fn new() -> Self {
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
            applied_nodes: Vec::new(),
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
                self.uncap_all_scaling_max();
                self.restore_all_scaling_min();
                self.current_level = Some(CeilingLevel::Balance);
                Ok(())
            }
            CeilingLevel::High => self.apply_high(config),
        }
    }

    fn write_freq_node(&mut self, path: &str, value: u64) {
        if Path::new(path).exists() {
            let _ = fs::set_permissions(path, PermissionsExt::from_mode(0o644));
            let val_str = value.to_string();
            if let Err(e) = fs::write(path, &val_str) {
                warn!(target: "auriya::ceiling", "Failed to write {} to {}: {}", val_str, path, e);
            } else {
                debug!(target: "auriya::ceiling", "Set {} = {} kHz", path, val_str);
                if !self.applied_nodes.contains(&path.to_string()) {
                    self.applied_nodes.push(path.to_string());
                }
            }
        }
    }

    fn apply_low(&mut self, config: &CeilingConfig) -> Result<()> {
        // Modern Auriya: 100% cores remain online. DynamIQ C-states provide superior
        // power saving and latency compared to core hotplug offlining.
        self.online_all();

        let little_freq = config
            .low_freq_little_khz
            .or_else(|| {
                self.layout
                    .little_freqs_khz
                    .get(self.layout.little_freqs_khz.len() / 2)
                    .copied()
            })
            .or_else(|| self.layout.little_freqs_khz.first().copied());
        let big_freq = config
            .low_freq_big_khz
            .or_else(|| {
                self.layout
                    .big_freqs_khz
                    .get(self.layout.big_freqs_khz.len() / 2)
                    .copied()
            })
            .or_else(|| self.layout.big_freqs_khz.first().copied());

        let little_ids = self.layout.little_ids.clone();
        let big_ids = self.layout.big_ids.clone();
        let prime_ids = self.layout.prime_ids.clone();

        if let Some(freq) = little_freq {
            for core in little_ids {
                let path = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
                self.write_freq_node(&path, freq);
            }
        }

        if let Some(freq) = big_freq {
            for core in big_ids {
                let path = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
                self.write_freq_node(&path, freq);
            }
        }

        // Cap prime cores to the same efficiency sweet-spot as big cores
        if let Some(freq) = big_freq {
            for core in prime_ids {
                let path = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
                self.write_freq_node(&path, freq);
            }
        }

        self.current_level = Some(CeilingLevel::Low);
        debug!(target: "auriya::ceiling", "Applied LOW ceiling (all cores online, direct sysfs scaling)");
        Ok(())
    }

    fn apply_high(&mut self, _config: &CeilingConfig) -> Result<()> {
        self.online_all();

        let all_core_ids = self.layout.all_core_ids.clone();
        for core in all_core_ids {
            if let Some(max_val) = hw_max_freq_for_core(core) {
                let min_path =
                    format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq");
                self.write_freq_node(&min_path, max_val);
            }
        }

        self.current_level = Some(CeilingLevel::High);
        debug!(target: "auriya::ceiling", "Applied HIGH ceiling (all cores online, min freq boosted)");
        Ok(())
    }

    pub fn restore(&mut self) {
        for path in self.applied_nodes.drain(..) {
            let _ = fs::set_permissions(&path, PermissionsExt::from_mode(0o644));
            if let Some(limit) = hardware_limit_for(&path) {
                let _ = fs::write(&path, &limit);
                debug!(target: "auriya::ceiling", "Restored {} = {} kHz", path, limit);
            }
        }
        self.uncap_all_scaling_max();
        self.restore_all_scaling_min();
        self.online_all();
        self.current_level = None;
    }

    pub fn online_all(&self) {
        for &core in &self.layout.all_core_ids {
            let path = format!("/sys/devices/system/cpu/cpu{core}/online");
            if Path::new(&path).exists() {
                let _ = fs::write(&path, "1");
            }
        }
    }

    /// Explicitly release scaling_max_freq on every core to hardware max limit.
    fn uncap_all_scaling_max(&self) {
        for &core in &self.layout.all_core_ids {
            let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
            let _ = fs::set_permissions(&p, PermissionsExt::from_mode(0o644));
            if let Some(limit) = hw_max_freq_for_core(core) {
                let _ = fs::write(&p, limit.to_string());
            }
        }
    }

    /// Explicitly release scaling_min_freq on every core to hardware min limit.
    fn restore_all_scaling_min(&self) {
        for &core in &self.layout.all_core_ids {
            let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq");
            let _ = fs::set_permissions(&p, PermissionsExt::from_mode(0o644));
            if let Some(limit) = hardware_limit_for(&p) {
                let _ = fs::write(&p, limit);
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
