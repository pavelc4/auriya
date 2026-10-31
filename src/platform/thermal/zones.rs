use std::fs;

use tracing::debug;

const THERMAL_ZONES: &[(&str, &str)] = &[
    ("cpu", "cpu"),
    ("cpu", "cpu-"),
    ("cpu", "cpu_"),
    ("cpu", "cpuss"),
    ("cpu", "mtkts_cpu"),
    ("cpu", "exynos"),
    ("gpu", "gpu"),
    ("gpu", "kgsl"),
    ("gpu", "mali"),
    ("gpu", "adreno"),
    ("gpu", "gpuss"),
    ("gpu", "mtkts_gpu"),
    ("gpu", "g3d"),
    ("x5-usr", "x5-usr"),
];

#[derive(Debug, Clone, Default, PartialEq)]
pub struct ThermalSnapshot {
    pub cpu_temp_c: Option<f32>,
    pub gpu_temp_c: Option<f32>,
}

impl ThermalSnapshot {
    pub fn max_temp(&self) -> Option<f32> {
        match (self.cpu_temp_c, self.gpu_temp_c) {
            (Some(c), Some(g)) => Some(c.max(g)),
            (Some(c), None) => Some(c),
            (None, Some(g)) => Some(g),
            (None, None) => None,
        }
    }
}

#[derive(Default)]
pub struct ThermalCollector;

impl ThermalCollector {
    pub fn new() -> Self {
        Self
    }

    pub fn get_max_temp(&self) -> Option<f32> {
        self.snapshot().max_temp()
    }

    pub fn snapshot(&self) -> ThermalSnapshot {
        #[cfg(target_os = "android")]
        if let Some(snap) = read_thermal_service() {
            if snap.cpu_temp_c.is_some() && snap.gpu_temp_c.is_some() {
                return snap;
            }
            let sysfs = read_sysfs_zones();
            return ThermalSnapshot {
                cpu_temp_c: snap.cpu_temp_c.or(sysfs.cpu_temp_c),
                gpu_temp_c: snap.gpu_temp_c.or(sysfs.gpu_temp_c),
            };
        }

        read_sysfs_zones()
    }
}

#[cfg(target_os = "android")]
fn read_thermal_service() -> Option<ThermalSnapshot> {
    if !crate::platform::binder::ensure_initialized() {
        return None;
    }

    let thermal_svc = rsbinder::hub::check_service("thermalservice")?;
    let proxy = thermal_svc.as_proxy()?;

    // TRANSACTION_getCurrentTemperatures = 4
    let data = proxy.prepare_transact(true).ok()?;
    let reply = proxy.submit_transact(4, &data, 0).ok()??;

    let mut rep = reply;
    let ex = rep.read_i32().ok()?;
    if ex != 0 {
        return None;
    }

    let count = rep.read_i32().ok()?;
    if count <= 0 {
        return None;
    }

    let mut cpu_temp_c: Option<f32> = None;
    let mut gpu_temp_c: Option<f32> = None;

    for _ in 0..count {
        let is_present = rep.read_i32().ok()?;
        if is_present == 0 {
            continue;
        }
        let val = rep.read_f32().ok()?;
        let typ = rep.read_i32().ok()?;
        let _name: Option<String> = rep.read().ok();
        let _status = rep.read_i32().ok()?;

        if val <= 0.0 || val >= 150.0 {
            continue;
        }

        match typ {
            0 => {
                // Type 0 = CPU
                cpu_temp_c = Some(cpu_temp_c.unwrap_or(0.0).max(val));
            }
            1 => {
                // Type 1 = GPU
                gpu_temp_c = Some(gpu_temp_c.unwrap_or(0.0).max(val));
            }
            _ => {}
        }
    }

    if cpu_temp_c.is_some() || gpu_temp_c.is_some() {
        Some(ThermalSnapshot {
            cpu_temp_c,
            gpu_temp_c,
        })
    } else {
        None
    }
}

fn read_sysfs_zones() -> ThermalSnapshot {
    let mut cpu_temp_c: Option<f32> = None;
    let mut gpu_temp_c: Option<f32> = None;

    if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
        for entry in entries.flatten() {
            let file_name = entry.file_name();
            let name_str = file_name.to_string_lossy();
            if !name_str.starts_with("thermal_zone") {
                continue;
            }

            let path = entry.path().join("temp");
            let type_path = entry.path().join("type");

            let zone_type = match fs::read_to_string(&type_path) {
                Ok(s) => s.trim().to_lowercase(),
                Err(_) => continue,
            };

            let millicelsius = match fs::read_to_string(&path)
                .ok()
                .and_then(|s| s.trim().parse::<i32>().ok())
            {
                Some(v) if v > 0 && v < 150_000 => v as f32 / 1000.0,
                _ => continue,
            };

            for (category, prefix) in THERMAL_ZONES {
                if zone_type.starts_with(prefix) {
                    match *category {
                        "cpu" => {
                            cpu_temp_c = Some(cpu_temp_c.unwrap_or(0.0).max(millicelsius));
                        }
                        "gpu" => {
                            gpu_temp_c = Some(gpu_temp_c.unwrap_or(0.0).max(millicelsius));
                        }
                        _ => {}
                    }
                    break;
                }
            }
        }
    }

    if cpu_temp_c.is_none() && gpu_temp_c.is_none() {
        debug!(target: "auriya::telemetry", "No thermal zones found in sysfs");
    }

    ThermalSnapshot {
        cpu_temp_c,
        gpu_temp_c,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_thermal_snapshot_max() {
        let snap = ThermalSnapshot {
            cpu_temp_c: Some(45.0),
            gpu_temp_c: Some(52.5),
        };
        assert_eq!(snap.max_temp(), Some(52.5));

        let snap_cpu_only = ThermalSnapshot {
            cpu_temp_c: Some(40.0),
            gpu_temp_c: None,
        };
        assert_eq!(snap_cpu_only.max_temp(), Some(40.0));
    }
}
