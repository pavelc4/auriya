use anyhow::Result;
use std::fs;

pub struct ThermalMonitor;

impl ThermalMonitor {
    pub fn new() -> Self {
        Self
    }

    pub fn get_max_temp(&self) -> Result<f32> {
        let mut max = 0.0f32;

        for i in 0..20 {
            let path = format!("/sys/class/thermal/thermal_zone{}/temp", i);
            if let Ok(s) = fs::read_to_string(&path)
                && let Ok(millicelsius) = s.trim().parse::<i32>()
            {
                let celsius = millicelsius as f32 / 1000.0;
                if celsius > 0.0 && celsius < 150.0 && celsius > max {
                    max = celsius;
                }
            }
        }

        Ok(max)
    }
}
