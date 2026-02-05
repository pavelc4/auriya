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
            && since.elapsed() >= Duration::from_secs(1)
            && self.frametimes.len() >= 60
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
                tracing::debug!(target: "auriya::fas", "FAS    | Target FPS: {} (current: {:.1})", t, current_fps);
            }
            self.target_fps = new_target;
        }
    }
}
