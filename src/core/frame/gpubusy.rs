use super::FrameSource;
use anyhow::Result;
use std::fs;
use std::sync::Mutex;

pub struct GpuBusySource {
    last_busy: Mutex<u64>,
    last_total: Mutex<u64>,
    last_time: Mutex<std::time::Instant>,
}
pub fn is_available() -> bool {
    std::path::Path::new("/sys/class/kgsl/kgsl-3d0/gpubusy").exists()
}

impl GpuBusySource {
    pub fn new() -> Self {
        Self {
            last_busy: Mutex::new(0),
            last_total: Mutex::new(0),
            last_time: Mutex::new(std::time::Instant::now()),
        }
    }
}

impl FrameSource for GpuBusySource {
    fn get_metric(&mut self) -> Result<Option<f32>> {
        let data = fs::read_to_string("/sys/class/kgsl/kgsl-3d0/gpubusy")?;
        let parts: Vec<u64> = data
            .split_whitespace()
            .filter_map(|s| s.parse().ok())
            .collect();

        if parts.len() < 2 {
            return Ok(None);
        }

        let (busy, total) = (parts[0], parts[1]);
        if busy == 0 && total == 0 {
            tracing::debug!(target: "auriya::fas", "GPU counter reset, skip sample");
            return Ok(None);
        }

        let now = std::time::Instant::now();

        let mut last_b = self.last_busy.lock().unwrap();
        let mut last_t = self.last_total.lock().unwrap();
        let mut last_time = self.last_time.lock().unwrap();

        let elapsed = now.duration_since(*last_time).as_secs_f32();

        if *last_b > 0 && *last_t > 0 && elapsed > 0.2 {
            if busy < *last_b || total < *last_t {
                tracing::debug!(target: "auriya::fas", "GPU counter wrapped/reset, re-init");
                *last_b = busy;
                *last_t = total;
                *last_time = now;
                return Ok(None);
            }

            let delta_busy = busy.saturating_sub(*last_b) as f32;
            let delta_total = total.saturating_sub(*last_t) as f32;

            if delta_total > 0.0 {
                let util = (delta_busy / delta_total) * 100.0;

                *last_b = busy;
                *last_t = total;
                *last_time = now;

                return Ok(Some(util.clamp(0.0, 100.0)));
            }
        } else if *last_b == 0 {
            *last_b = busy;
            *last_t = total;
            *last_time = now;
        }

        Ok(None)
    }
}
