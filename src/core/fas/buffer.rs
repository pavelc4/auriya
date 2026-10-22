use std::collections::VecDeque;
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub enum TargetFps {
    Single(u32),
    Array(Vec<u32>),
}

impl Default for TargetFps {
    fn default() -> Self {
        Self::Single(60)
    }
}

impl TargetFps {
    pub fn values(&self) -> Vec<u32> {
        match self {
            Self::Single(v) => vec![*v],
            Self::Array(arr) => {
                let mut sorted = arr.clone();
                sorted.sort();
                sorted
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BufferState {
    Unusable,
    Usable,
}

const WARMUP: Duration = Duration::from_secs(1);
const MIN_FRAMES_FOR_USABLE: usize = 60;

#[derive(Debug)]
pub struct FrameBuffer {
    pub frametimes: VecDeque<Duration>,
    pub real_frametimes: VecDeque<Duration>,
    pub current_fps_long: f64,
    pub current_fps_short: f64,
    pub target_fps: Option<u32>,
    target_fps_config: TargetFps,
    pub state: BufferState,
    last_update: Instant,
    unusable_since: Option<Instant>,
}

impl FrameBuffer {
    pub fn new(target_fps_config: TargetFps) -> Self {
        Self {
            frametimes: VecDeque::with_capacity(720),
            real_frametimes: VecDeque::with_capacity(720),
            current_fps_long: 0.0,
            current_fps_short: 0.0,
            target_fps: None,
            target_fps_config,
            state: BufferState::Unusable,
            last_update: Instant::now(),
            unusable_since: Some(Instant::now()),
        }
    }

    pub fn push(&mut self, frametime: Duration) {
        self.last_update = Instant::now();

        let max_frames = self.target_fps.unwrap_or(144) as usize * 5;
        while self.frametimes.len() >= max_frames {
            self.frametimes.pop_back();
        }
        while self.real_frametimes.len() >= max_frames {
            self.real_frametimes.pop_back();
        }

        self.frametimes.push_front(frametime);
        self.real_frametimes.push_front(frametime);
        self.update_fps();
        self.try_become_usable();
        self.detect_target_fps();
    }

    /// Synthesize a frametime from the time elapsed since the last real frame.
    /// Called by the controller's jank path (no frame received within timeout)
    /// — keeps the buffer responsive to stalls instead of silently freezing.
    /// Mirrors fas-rs `additional_frametime()`.
    pub fn additional_frametime(&mut self) {
        let dt = self.last_update.elapsed();
        if dt.is_zero() {
            return;
        }
        // Bound the synthesized value so a long idle gap (app paused, screen off)
        // does not poison the long-window average for several seconds.
        let capped = dt.min(Duration::from_millis(500));
        self.frametimes.push_front(capped);

        let max_frames = self.target_fps.unwrap_or(144) as usize * 5;
        while self.frametimes.len() > max_frames {
            self.frametimes.pop_back();
        }

        self.update_fps();
    }

    pub fn mark_unusable(&mut self) {
        self.state = BufferState::Unusable;
        self.unusable_since = Some(Instant::now());
    }

    pub fn clear(&mut self) {
        self.frametimes.clear();
        self.real_frametimes.clear();
        self.current_fps_long = 0.0;
        self.current_fps_short = 0.0;
        self.target_fps = None;
        self.mark_unusable();
    }

    pub fn time_since_last_frame(&self) -> Duration {
        self.last_update.elapsed()
    }

    pub fn recent_frametimes(&self, n: usize) -> Vec<Duration> {
        self.frametimes.iter().take(n).copied().collect()
    }

    /// Returns up to `n` recent real presented frametimes.
    /// Excludes synthetic stalls injected by FAS governor (`additional_frametime`),
    /// ensuring telemetry APIs (`GET_STATS`, `STREAM_STATS`) reflect true render rate.
    pub fn recent_real_frametimes(&self, n: usize) -> Vec<Duration> {
        self.real_frametimes.iter().take(n).copied().collect()
    }

    /// Newest frame in the ring (front), or None if empty.
    /// The controller's P-loop normalises against this single sample, not the
    /// long-window average, so frame spikes drive an immediate response.
    pub fn last_frametime(&self) -> Option<Duration> {
        self.frametimes.front().copied()
    }

    fn update_fps(&mut self) {
        if self.frametimes.is_empty() {
            return;
        }

        let total_long: Duration = self.frametimes.iter().sum();
        let avg_long = total_long.as_secs_f64() / self.frametimes.len() as f64;
        self.current_fps_long = if avg_long > 0.0 { 1.0 / avg_long } else { 0.0 };

        let short_count = self
            .target_fps
            .unwrap_or(60)
            .min(self.frametimes.len() as u32) as usize;
        let total_short: Duration = self.frametimes.iter().take(short_count).sum();
        let avg_short = total_short.as_secs_f64() / short_count as f64;
        self.current_fps_short = if avg_short > 0.0 {
            1.0 / avg_short
        } else {
            0.0
        };
    }

    fn try_become_usable(&mut self) {
        if self.state == BufferState::Unusable
            && let Some(since) = self.unusable_since
            && since.elapsed() >= WARMUP
            && self.frametimes.len() >= MIN_FRAMES_FOR_USABLE
        {
            self.state = BufferState::Usable;
            self.unusable_since = None;
        }
    }

    fn detect_target_fps(&mut self) {
        let targets = self.target_fps_config.values();
        if targets.is_empty() {
            return;
        }

        let current_fps = self.current_fps_long;

        let min_fps = targets.first().unwrap_or(&30).saturating_sub(10).max(10);
        if current_fps < min_fps as f64 {
            if self.target_fps.is_some() {
                self.target_fps = None;
                self.frametimes.clear();
                self.real_frametimes.clear();
                self.current_fps_long = 0.0;
                self.current_fps_short = 0.0;
                self.mark_unusable();
            }
            return;
        }

        let new_target = targets
            .iter()
            .find(|&&t| current_fps <= f64::from(t) + 3.0)
            .or(targets.last())
            .copied();

        if new_target != self.target_fps {
            if let Some(t) = new_target {
                tracing::debug!(
                    target: "auriya::fas",
                    "FAS    | Target FPS: {} (current: {:.1})",
                    t,
                    current_fps
                );
            }
            // Invariant §6.1: switching target_fps invalidates buffered samples
            // because the short window length and policy thresholds change.
            // Reset frametimes and re-arm the warmup window.
            if self.target_fps.is_some() && new_target != self.target_fps {
                self.frametimes.clear();
                self.real_frametimes.clear();
                self.current_fps_long = 0.0;
                self.current_fps_short = 0.0;
                self.mark_unusable();
            }
            self.target_fps = new_target;
        }
    }
}
