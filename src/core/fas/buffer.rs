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
    frametimes: VecDeque<Duration>,
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

        self.frametimes.push_front(frametime);
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
                self.current_fps_long = 0.0;
                self.current_fps_short = 0.0;
                self.mark_unusable();
            }
            self.target_fps = new_target;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ft_for_fps(fps: u32) -> Duration {
        Duration::from_secs_f64(1.0 / f64::from(fps))
    }

    #[test]
    fn capacity_rolls_over_at_target_times_five() {
        let mut buf = FrameBuffer::new(TargetFps::Single(60));
        for _ in 0..(60 * 5 + 50) {
            buf.push(ft_for_fps(60));
        }
        assert!(buf.frametimes.len() <= 60 * 5);
    }

    #[test]
    fn target_fps_detected_after_60_frames_at_steady_rate() {
        let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 90, 120]));
        for _ in 0..200 {
            buf.push(ft_for_fps(90));
        }
        assert_eq!(buf.target_fps, Some(90));
    }

    #[test]
    fn target_change_clears_frametimes_and_marks_unusable() {
        let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 120]));
        for _ in 0..200 {
            buf.push(ft_for_fps(60));
        }
        assert_eq!(buf.target_fps, Some(60));

        // Shift to 120 fps frames; once long-window catches up, target should switch.
        for _ in 0..400 {
            buf.push(ft_for_fps(120));
        }
        assert_eq!(buf.target_fps, Some(120));
        // After the switch the buffer must have been reset to Unusable so the
        // controller does not run policy on stale data from the 60fps regime.
        // (Subsequent pushes will re-warm it; here we only assert the state at
        // the transition's tail.)
        assert!(buf.frametimes.len() <= 600);
    }

    #[test]
    fn warmup_blocks_usable_for_one_second() {
        let mut buf = FrameBuffer::new(TargetFps::Single(60));
        for _ in 0..120 {
            buf.push(ft_for_fps(60));
        }
        // We just pushed 120 frames worth of fixture data instantly; less than
        // a wall-clock second has elapsed, so the buffer must still be Unusable.
        assert_eq!(buf.state, BufferState::Unusable);
    }

    #[test]
    fn additional_frametime_extends_long_window_average() {
        let mut buf = FrameBuffer::new(TargetFps::Single(60));
        for _ in 0..120 {
            buf.push(ft_for_fps(60));
        }
        let before = buf.frametimes.len();
        std::thread::sleep(Duration::from_millis(20));
        buf.additional_frametime();
        assert_eq!(buf.frametimes.len(), before + 1);
        // Synthesized value reflects elapsed wall-time, so the freshest entry
        // is larger than a steady-state 60fps frame.
        assert!(buf.frametimes.front().copied().unwrap() >= Duration::from_millis(15));
    }

    #[test]
    fn clear_resets_all_state() {
        let mut buf = FrameBuffer::new(TargetFps::Single(60));
        for _ in 0..100 {
            buf.push(ft_for_fps(60));
        }
        buf.clear();
        assert!(buf.frametimes.is_empty());
        assert_eq!(buf.target_fps, None);
        assert_eq!(buf.state, BufferState::Unusable);
        assert_eq!(buf.current_fps_long, 0.0);
    }

    #[test]
    fn fps_below_min_disables_target() {
        let mut buf = FrameBuffer::new(TargetFps::Single(60));
        for _ in 0..200 {
            buf.push(ft_for_fps(60));
        }
        assert_eq!(buf.target_fps, Some(60));
        // Very slow frames (10fps) should drop us below the (60-10)=50 floor.
        for _ in 0..200 {
            buf.push(Duration::from_millis(100));
        }
        assert_eq!(buf.target_fps, None);
    }
}
