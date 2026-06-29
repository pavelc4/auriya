// FpsMeter — dual-source FPS reader, fully independent from FAS.
//
// Sources (priority order):
//   1. Sysfs (via measured_fps paths) — always read first if available
//   2. eBPF (broadcast::Receiver from EbpfFrameStream) — fallback per-frame deltas
//
// Sysfs is the primary source because it reports the actual display refresh,
// which is more reliable than eBPF frame deltas (which can be noisy on
// triple-buffering, vsync lock, or when the app isn't actively rendering).

use std::collections::VecDeque;
use std::fs;
use std::path::Path;
use std::time::{Duration, Instant};
use tokio::sync::broadcast;

const FPS_SYSFS_PATHS: &[&str] = &[
    "/sys/class/drm/sde-crtc-0/measured_fps",
    "/sys/class/drm/card0/sde-crtc-0/measured_fps",
    "/sys/class/drm/card0/sde_crtc_fps",
    "/sys/class/drm/card0/fbc/fps",
    "/sys/class/graphics/fb0/measured_fps",
    "/sys/class/graphics/fb0/fps",
    "/sys/kernel/debug/mali/fps",
    "/sys/class/misc/mali0/device/fps",
];

const SYSFS_POLL_INTERVAL: Duration = Duration::from_secs(2);

/// Number of frames for the short-window EMA (≈ ½s at 60 fps).
const SHORT_WINDOW: usize = 30;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FpsSource {
    Ebpf,
    Sysfs,
}

#[derive(Debug, Clone)]
pub struct FpsReading {
    pub fps: f64,
    pub source: FpsSource,
}

pub struct FpsMeter {
    ebpf_rx: Option<broadcast::Receiver<Duration>>,
    frametimes: VecDeque<Duration>,

    sysfs_path: Option<String>,
    last_poll: Instant,
    cached_sysfs: Option<FpsReading>,

    last_ebpf_frame: Instant,
    ebpf_timeout: Duration,
}

impl FpsMeter {
    pub fn new(ebpf_rx: Option<broadcast::Receiver<Duration>>) -> Self {
        let sysfs_path = Self::detect_sysfs();
        tracing::debug!(
            target: "auriya::fps",
            "FPS     | sysfs={:?} ebpf={}",
            sysfs_path,
            ebpf_rx.is_some(),
        );
        Self {
            ebpf_rx,
            frametimes: VecDeque::with_capacity(SHORT_WINDOW),
            sysfs_path,
            last_poll: Instant::now(),
            cached_sysfs: None,
            last_ebpf_frame: Instant::now(),
            ebpf_timeout: Duration::from_secs(3),
        }
    }

    fn detect_sysfs() -> Option<String> {
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

    /// Read the current FPS from the best available source.
    /// Tries sysfs first, falls back to eBPF if unavailable.
    pub fn read(&mut self) -> Option<FpsReading> {
        if let Some(sysfs) = self.read_sysfs() {
            return Some(sysfs);
        }

        self.drain_ebpf();

        if self.frametimes.is_empty() || self.last_ebpf_frame.elapsed() >= self.ebpf_timeout {
            return None;
        }

        let avg: Duration = self.frametimes.iter().sum();
        let n = self.frametimes.len() as f64;
        let avg_secs = avg.as_secs_f64() / n;
        if avg_secs <= 0.0 {
            return None;
        }

        let fps = 1.0 / avg_secs;
        if fps <= 0.0 || fps > 500.0 {
            return None;
        }

        Some(FpsReading {
            fps,
            source: FpsSource::Ebpf,
        })
    }

    fn drain_ebpf(&mut self) {
        let Some(rx) = &mut self.ebpf_rx else { return };

        let mut new_frames = false;
        loop {
            match rx.try_recv() {
                Ok(dt) => {
                    if dt < Duration::from_millis(500) {
                        self.frametimes.push_front(dt);
                        new_frames = true;
                    }
                }
                Err(broadcast::error::TryRecvError::Empty) => break,
                Err(broadcast::error::TryRecvError::Closed) => {
                    self.ebpf_rx = None;
                    break;
                }
                Err(broadcast::error::TryRecvError::Lagged(n)) => {
                    tracing::debug!(
                        target: "auriya::fps",
                        "eBPF broadcast lagged by {n} frames"
                    );
                    continue;
                }
            }
        }

        if new_frames {
            self.last_ebpf_frame = Instant::now();
            while self.frametimes.len() > SHORT_WINDOW {
                self.frametimes.pop_back();
            }
        }
    }

    fn read_sysfs(&mut self) -> Option<FpsReading> {
        let now = Instant::now();
        if now.duration_since(self.last_poll) < SYSFS_POLL_INTERVAL {
            return self.cached_sysfs.clone();
        }
        self.last_poll = now;

        let path = self.sysfs_path.as_ref()?;
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
        self.cached_sysfs = Some(reading.clone());
        Some(reading)
    }
}
