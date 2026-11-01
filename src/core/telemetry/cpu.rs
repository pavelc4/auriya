use std::collections::HashMap;
use std::fs;

use crate::platform::cpu::ceiling::CoreLayout;

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
    pub governor: String,
    pub cluster: ClusterType,
    pub load_pct: f32,
}

#[derive(Debug, Clone)]
pub struct CpuSnapshot {
    pub cores: Vec<CoreTelemetry>,
    pub load_pct: f32,
}

#[derive(Default, Clone)]
struct LoadPair {
    prev_total: u64,
    prev_idle: u64,
}

#[derive(Default)]
pub struct CpuCollector {
    total_load: LoadPair,
    core_loads: HashMap<usize, LoadPair>,
}

impl CpuCollector {
    pub fn snapshot(&mut self, layout: &CoreLayout) -> CpuSnapshot {
        let (load_pct, core_load_map) = self.compute_loads();
        let cores = self.read_cores(layout, &core_load_map);
        CpuSnapshot { cores, load_pct }
    }

    fn read_cores(
        &self,
        layout: &CoreLayout,
        core_load_map: &HashMap<usize, f32>,
    ) -> Vec<CoreTelemetry> {
        let mut cores = Vec::with_capacity(layout.all_core_ids.len());

        for &id in &layout.all_core_ids {
            let online = read_online(id);
            let cur_freq_khz = read_cur_freq(id).unwrap_or(0);
            let governor = read_governor(id);

            let cluster = if layout.prime_ids.contains(&id) {
                ClusterType::Prime
            } else if layout.big_ids.contains(&id) {
                ClusterType::Big
            } else {
                ClusterType::Little
            };

            let load_pct = core_load_map.get(&id).copied().unwrap_or(0.0);

            cores.push(CoreTelemetry {
                core_id: id,
                online,
                cur_freq_khz,
                governor,
                cluster,
                load_pct,
            });
        }

        cores
    }

    fn compute_loads(&mut self) -> (f32, HashMap<usize, f32>) {
        let mut total_load = 0.0f32;
        let mut core_map = HashMap::new();

        let Ok(content) = fs::read_to_string("/proc/stat") else {
            return (0.0, core_map);
        };

        for line in content.lines() {
            if let Some(rest) = line.strip_prefix("cpu ") {
                let parts: Vec<&str> = rest.split_whitespace().collect();
                let total: u64 = parts.iter().filter_map(|s| s.parse::<u64>().ok()).sum();
                let idle: u64 = parts
                    .get(3)
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(0)
                    + parts
                        .get(4)
                        .and_then(|s| s.parse::<u64>().ok())
                        .unwrap_or(0);

                if self.total_load.prev_total > 0 {
                    let d_total = total.saturating_sub(self.total_load.prev_total);
                    let d_idle = idle.saturating_sub(self.total_load.prev_idle);
                    if d_total > 0 {
                        total_load = (100.0 * (d_total.saturating_sub(d_idle)) as f32
                            / d_total as f32)
                            .clamp(0.0, 100.0);
                    }
                }
                self.total_load.prev_total = total;
                self.total_load.prev_idle = idle;
            } else if let Some(rest) = line.strip_prefix("cpu") {
                let mut iter = rest.split_whitespace();
                let Some(core_id_str) = iter.next() else {
                    continue;
                };
                let Ok(core_id) = core_id_str.parse::<usize>() else {
                    continue;
                };

                let parts: Vec<&str> = iter.collect();
                let total: u64 = parts.iter().filter_map(|s| s.parse::<u64>().ok()).sum();
                let idle: u64 = parts
                    .get(3)
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(0)
                    + parts
                        .get(4)
                        .and_then(|s| s.parse::<u64>().ok())
                        .unwrap_or(0);

                let pair = self.core_loads.entry(core_id).or_default();
                if pair.prev_total > 0 {
                    let d_total = total.saturating_sub(pair.prev_total);
                    let d_idle = idle.saturating_sub(pair.prev_idle);
                    if d_total > 0 {
                        let pct = (100.0 * (d_total.saturating_sub(d_idle)) as f32
                            / d_total as f32)
                            .clamp(0.0, 100.0);
                        core_map.insert(core_id, pct);
                    }
                }
                pair.prev_total = total;
                pair.prev_idle = idle;
            } else if !line.starts_with("cpu") {
                break;
            }
        }

        (total_load, core_map)
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
