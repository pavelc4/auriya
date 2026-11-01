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

pub const SYSFS_POLL_INTERVAL: Duration = Duration::from_millis(250);

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
            last_poll: Instant::now()
                .checked_sub(SYSFS_POLL_INTERVAL)
                .unwrap_or_else(Instant::now),
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
        if self.path.is_none() {
            self.path = Self::detect_sysfs();
        }
        match self.cached.is_some() && now.duration_since(self.last_poll) < SYSFS_POLL_INTERVAL {
            true => self.cached,
            false => {
                self.last_poll = now;
                let path = self.path.as_ref()?;
                let content = fs::read_to_string(path).ok()?;
                let fps = parse_sysfs_fps(&content)?;

                let max_hz = crate::platform::binder::get_active_display_refresh_rate();
                let clamped = match max_hz {
                    hz if hz > 0.0 && (fps - hz).abs() <= 0.75 => hz,
                    hz if hz > 0.0 => fps.min(hz),
                    _ => fps,
                };

                let reading = FpsReading {
                    fps: clamped,
                    source: FpsSource::Sysfs,
                };
                self.cached = Some(reading);
                Some(reading)
            }
        }
    }

    pub fn clear(&mut self) {
        self.cached = None;
        self.last_poll = Instant::now()
            .checked_sub(SYSFS_POLL_INTERVAL)
            .unwrap_or_else(Instant::now);
    }
}

/// Zero-allocation sysfs FPS parser supporting raw floats ("60.0") and
/// vendor key-value formats ("fps: 117.7 duration:1000000 frame_count:118").
pub fn parse_sysfs_fps(content: &str) -> Option<f64> {
    let trimmed = content.trim();
    if trimmed.is_empty() {
        return None;
    }
    if let Ok(v) = trimmed.parse::<f64>() {
        return (v > 0.0 && v <= 500.0).then_some(v);
    }
    for part in trimmed.split(|c: char| c.is_whitespace() || c == ',' || c == ';') {
        let part = part.trim();
        if let Some(val_str) = part
            .strip_prefix("fps:")
            .or_else(|| part.strip_prefix("FPS:"))
            .or_else(|| part.strip_prefix("fps="))
            .or_else(|| part.strip_prefix("FPS="))
            && let Ok(v) = val_str.trim().parse::<f64>()
        {
            return (v > 0.0 && v <= 500.0).then_some(v);
        }
    }
    let mut words = trimmed.split_whitespace();
    while let Some(word) = words.next() {
        let lower = word.to_ascii_lowercase();
        if (lower == "fps:" || lower == "fps")
            && let Some(next) = words.next()
            && let Ok(v) = next.parse::<f64>()
        {
            return (v > 0.0 && v <= 500.0).then_some(v);
        }
    }
    None
}
