use std::fs;

use crate::core::tweaks::ceiling::CoreLayout;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ClusterType {
    Little,
    Big,
    Prime,
}

#[derive(Debug, Clone)]
pub struct CoreTelemetry {
    pub core_id: usize,
    pub online: bool,
    pub cur_freq_khz: u64,
    pub min_freq_khz: u64,
    pub max_freq_khz: u64,
    pub governor: String,
    pub cluster: ClusterType,
}

#[derive(Debug, Clone)]
pub struct CpuSnapshot {
    pub cores: Vec<CoreTelemetry>,
    pub load_pct: f32,
}

#[derive(Default)]
struct CpuLoadState {
    prev_total: u64,
    prev_idle: u64,
}

#[derive(Default)]
pub struct CpuCollector {
    load: CpuLoadState,
}

impl CpuCollector {
    pub fn snapshot(&mut self, layout: &CoreLayout) -> CpuSnapshot {
        let cores = self.read_cores(layout);
        let load_pct = self.compute_load();
        CpuSnapshot { cores, load_pct }
    }

    fn read_cores(&self, layout: &CoreLayout) -> Vec<CoreTelemetry> {
        let mut cores = Vec::with_capacity(layout.all_core_ids.len());

        for &id in &layout.all_core_ids {
            let online = read_online(id);
            let cur_freq_khz = read_cur_freq(id).unwrap_or(0);
            let min_freq_khz = read_min_freq(id).unwrap_or(0);
            let max_freq_khz = read_max_freq(id).unwrap_or(0);
            let governor = read_governor(id);

            let cluster = if layout.prime_ids.contains(&id) {
                ClusterType::Prime
            } else if layout.big_ids.contains(&id) {
                ClusterType::Big
            } else {
                ClusterType::Little
            };

            cores.push(CoreTelemetry {
                core_id: id,
                online,
                cur_freq_khz,
                min_freq_khz,
                max_freq_khz,
                governor,
                cluster,
            });
        }

        cores
    }

    fn compute_load(&mut self) -> f32 {
        if let Some(total) = read_proc_stat_total()
            && let Some(idle) = read_proc_stat_idle()
        {
            let prev_total = self.load.prev_total;
            let prev_idle = self.load.prev_idle;
            self.load.prev_total = total;
            self.load.prev_idle = idle;

            if prev_total > 0 {
                let d_total = total.saturating_sub(prev_total);
                let d_idle = idle.saturating_sub(prev_idle);
                if d_total > 0 {
                    return 100.0 * (d_total.saturating_sub(d_idle)) as f32 / d_total as f32;
                }
            }
        }
        0.0
    }
}

fn read_online(core: usize) -> bool {
    let path = format!("/sys/devices/system/cpu/cpu{}/online", core);
    fs::read_to_string(&path)
        .ok()
        .and_then(|s| s.trim().parse::<u8>().ok())
        .map(|v| v == 1)
        .unwrap_or(true)
}

fn read_cur_freq(core: usize) -> Option<u64> {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_cur_freq",
        core
    );
    fs::read_to_string(&path)
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
}

fn read_min_freq(core: usize) -> Option<u64> {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_min_freq",
        core
    );
    fs::read_to_string(&path)
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
}

fn read_max_freq(core: usize) -> Option<u64> {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq",
        core
    );
    fs::read_to_string(&path)
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
}

fn read_governor(core: usize) -> String {
    let path = format!(
        "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_governor",
        core
    );
    fs::read_to_string(&path)
        .ok()
        .map(|s| s.trim().to_string())
        .unwrap_or_default()
}

fn read_proc_stat_total() -> Option<u64> {
    let content = fs::read_to_string("/proc/stat").ok()?;
    let line = content.lines().next()?;
    let val: u64 = line
        .split_whitespace()
        .skip(1)
        .filter_map(|s| s.parse::<u64>().ok())
        .sum();
    Some(val)
}

fn read_proc_stat_idle() -> Option<u64> {
    let content = fs::read_to_string("/proc/stat").ok()?;
    let line = content.lines().next()?;
    let parts: Vec<&str> = line.split_whitespace().collect();
    if parts.len() > 4 {
        parts[4].parse::<u64>().ok()
    } else {
        None
    }
}
