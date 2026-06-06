use std::fs;

use tracing::debug;

const THERMAL_ZONES: &[(&str, &str)] = &[
    ("cpu", "cpu"),
    ("cpu", "cpu-"),
    ("cpu", "cpu_"),
    ("gpu", "gpu"),
    ("gpu", "kgsl"),
    ("gpu", "mali"),
    ("gpu", "adreno"),
    ("x5-usr", "x5-usr"),
];

#[derive(Debug, Clone, Default)]
pub struct ThermalSnapshot {
    pub cpu_temp_c: Option<f32>,
    pub gpu_temp_c: Option<f32>,
}

#[derive(Default)]
pub struct ThermalCollector;

impl ThermalCollector {
    pub fn snapshot(&self) -> ThermalSnapshot {
        let mut cpu_temp_c: Option<f32> = None;
        let mut gpu_temp_c: Option<f32> = None;

        for i in 0..30 {
            let path = format!("/sys/class/thermal/thermal_zone{}/temp", i);
            let name_path = format!("/sys/class/thermal/thermal_zone{}/type", i);

            let name = match fs::read_to_string(&name_path) {
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
                if name.starts_with(prefix) {
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

        if cpu_temp_c.is_none() && gpu_temp_c.is_none() {
            debug!(target: "auriya::telemetry", "No thermal zones found");
        }

        ThermalSnapshot {
            cpu_temp_c,
            gpu_temp_c,
        }
    }
}
