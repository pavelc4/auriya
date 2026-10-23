use super::types::{FpsReading, FpsSource};
use std::fs;
use std::path::Path;
use std::time::{Duration, Instant};

pub const FPS_SYSFS_PATHS: &[&str] = &[
    "/sys/class/drm/sde-crtc-0/measured_fps",
    "/sys/class/drm/card0/sde-crtc-0/measured_fps",
    "/sys/class/drm/card0/sde_crtc_fps",
    "/sys/class/drm/card0/fbc/fps",
    "/sys/class/graphics/fb0/measured_fps",
    "/sys/class/graphics/fb0/fps",
    "/sys/kernel/debug/mali/fps",
    "/sys/class/misc/mali0/device/fps",
];

pub const SYSFS_POLL_INTERVAL: Duration = Duration::from_secs(2);

pub struct SysfsFpsReader {
    pub path: Option<String>,
    pub last_poll: Instant,
    pub cached: Option<FpsReading>,
}

impl Default for SysfsFpsReader {
    fn default() -> Self {
        Self::new()
    }
}

impl SysfsFpsReader {
    pub fn new() -> Self {
        Self {
            path: Self::detect_sysfs(),
            last_poll: Instant::now(),
            cached: None,
        }
    }

    pub fn detect_sysfs() -> Option<String> {
        for &p in FPS_SYSFS_PATHS {
            if Path::new(p).exists()
                && let Ok(content) = fs::read_to_string(p)
                && !content.trim().is_empty()
            {
                return Some(p.to_string());
            }
        }
        None
    }

    pub fn read(&mut self) -> Option<FpsReading> {
        let now = Instant::now();
        if now.duration_since(self.last_poll) < SYSFS_POLL_INTERVAL {
            return self.cached.clone();
        }
        self.last_poll = now;

        let path = self.path.as_ref()?;
        let content = fs::read_to_string(path).ok()?;
        let trimmed = content.trim();
        if trimmed.is_empty() {
            return None;
        }

        let fps: f64 = trimmed.parse::<f64>().ok()?;
        if fps <= 0.0 || fps > 500.0 {
            return None;
        }

        let reading = FpsReading {
            fps,
            source: FpsSource::Sysfs,
        };
        self.cached = Some(reading.clone());
        Some(reading)
    }

    pub fn clear(&mut self) {
        self.cached = None;
    }
}
