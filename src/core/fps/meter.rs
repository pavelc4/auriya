use super::sysfs::SysfsFpsReader;
use super::types::{FpsReading, FpsSource};
use std::collections::VecDeque;
use std::time::{Duration, Instant};
use tokio::sync::broadcast;

/// Number of frames for the short-window EMA (≈ ½s at 60 fps).
const SHORT_WINDOW: usize = 30;

pub struct FpsMeter {
    ebpf_rx: Option<broadcast::Receiver<Duration>>,
    frametimes: VecDeque<Duration>,
    sysfs: SysfsFpsReader,
    last_ebpf_frame: Instant,
    ebpf_timeout: Duration,
}

impl FpsMeter {
    pub fn new(ebpf_rx: Option<broadcast::Receiver<Duration>>) -> Self {
        let sysfs = SysfsFpsReader::new();
        tracing::debug!(
            target: "auriya::fps",
            "FPS     | sysfs={:?} ebpf={}",
            sysfs.path,
            ebpf_rx.is_some(),
        );
        Self {
            ebpf_rx,
            frametimes: VecDeque::with_capacity(SHORT_WINDOW),
            sysfs,
            last_ebpf_frame: Instant::now(),
            ebpf_timeout: Duration::from_millis(1500),
        }
    }

    /// Clear all cached readings and frame queues when exiting game
    pub fn clear(&mut self) {
        self.frametimes.clear();
        self.sysfs.clear();
    }

    /// Read the current FPS from the best available source.
    /// Tries sysfs first, falls back to eBPF if unavailable.
    pub fn read(&mut self) -> Option<FpsReading> {
        if let Some(sysfs) = self.sysfs.read() {
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
}
