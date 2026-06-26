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
        if do_mount_bind(&mount_point, path) {
            debug!(target: "auriya::ceiling", "Froze {} = {} kHz", path, value_str);
            self.mounts.push(MountEntry {
                path: path.to_string(),
                mount_point,
            });
        } else {
            let _ = fs::set_permissions(path, PermissionsExt::from_mode(0o644));
            warn!(target: "auriya::ceiling", "Mount-bind failed for {}, freq cap active without bind", path);
        }
    }

    pub fn restore(&mut self) {
        for entry in self.mounts.drain(..) {
            let _ = fs::set_permissions(&entry.path, PermissionsExt::from_mode(0o644));
            do_unmount(&entry.path);
            let _ = fs::remove_file(&entry.mount_point);
            debug!(target: "auriya::ceiling", "Unmounted {}", entry.path);
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
