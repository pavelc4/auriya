// FasController — frame-aware scaling decisions for the daemon tick loop.
//
// Implements the fas-rs control model adapted to Auriya's ScalingAction output:
//   1. State machine: NotWorking → Waiting (3s) → Working
//   2. P-controller in normalized space (last_frame * target_fps  vs  1s)
//   3. Jank synthesis path: when no frame arrives within timeout, the buffer
//      is fed `additional_frametime` so the controller still reacts
//   4. util-based target FPS offset (clamped <= 0): never asks above hw limit
//   5. Thermal override: forces Reduce above threshold
//
// Output is `ScalingAction` (Boost/Maintain/Reduce). The internal control
// signal is computed in nanoseconds (and would map to kHz on a freq-table
// backend); the conversion to enum buckets is done at the bottom of `tick`.

use crate::core::{
    fas::buffer::{BufferState, FrameBuffer, TargetFps},
    fas::source::FrameSource,
    scaling::ScalingAction,
    thermal::ThermalMonitor,
};
use anyhow::{Context, Result};
use std::time::{Duration, Instant};

const WAITING_DELAY: Duration = Duration::from_secs(3);
const FRAME_TIMEOUT: Duration = Duration::from_millis(100);
const UTIL_SAMPLE_INTERVAL: Duration = Duration::from_secs(1);
const KP_DEFAULT: f64 = 1.5e-4;
const MARGIN_FPS: f64 = 1.5;
const JANK_DELTA_FPS: f64 = 2.0;
const BOOST_THRESHOLD_KHZ: f64 = 50_000.0;
const REDUCE_THRESHOLD_KHZ: f64 = -50_000.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FasState {
    NotWorking,
    Waiting,
    Working,
}

pub struct FasController {
    source: FrameSource,
    buffer: FrameBuffer,
    thermal: ThermalMonitor,
    package: String,
    pid: Option<i32>,
    last_attached_pkg: String,
    state: FasState,
    waiting_since: Option<Instant>,
    util_sampler: UtilSampler,
    target_fps_offset: f64,
    kp: f64,
}

impl FasController {
    /// Construct a FAS controller. Returns `Err` if the eBPF frame probe
    /// cannot be loaded — the daemon should disable FAS in that case
    /// instead of falling back to a degraded source.
    pub fn new(target_fps_config: TargetFps) -> Result<Self> {
        let source = FrameSource::new()
            .context("FAS    | eBPF frame probe load failed; FAS will be disabled")?;
        tracing::debug!(target: "auriya::fas", "FAS Controller initialized");
        Ok(Self {
            source,
            buffer: FrameBuffer::new(target_fps_config),
            thermal: ThermalMonitor::new(),
            package: String::new(),
            pid: None,
            last_attached_pkg: String::new(),
            state: FasState::NotWorking,
            waiting_since: None,
            util_sampler: UtilSampler::new(),
            target_fps_offset: 0.0,
            kp: KP_DEFAULT,
        })
    }

    pub fn with_target_fps(fps: u32) -> Result<Self> {
        Self::new(TargetFps::Single(fps))
    }

    pub fn set_package(&mut self, package: String, pid: Option<i32>) {
        if self.package != package {
            tracing::debug!(target: "auriya::fas", "Switching to package: {}", package);
            self.package = package;
            self.pid = pid;
            self.last_attached_pkg.clear();
            self.buffer.clear();
            self.transition_not_working();
        }
    }

    pub fn set_target_fps(&mut self, fps: u32) {
        self.buffer = FrameBuffer::new(TargetFps::Single(fps));
        self.transition_not_working();
    }

    pub fn set_target_fps_config(&mut self, config: TargetFps) {
        self.buffer = FrameBuffer::new(config);
        self.transition_not_working();
    }

    pub fn get_target_fps(&self) -> u32 {
        self.buffer.target_fps.unwrap_or(60)
    }

    #[allow(dead_code)]
    pub fn state(&self) -> FasState {
        self.state
    }

    pub async fn tick(&mut self, thermal_thresh: f32) -> Result<ScalingAction> {
        if let Some(pid) = self.pid.filter(|_| !self.package.is_empty())
            && self.last_attached_pkg != self.package
        {
            tracing::debug!(
                target: "auriya::fas",
                "Attaching to package: {} (PID: {})",
                self.package, pid
            );
            match self.source.attach(&self.package, pid).await {
                Ok(()) => {
                    self.last_attached_pkg.clear();
                    self.last_attached_pkg.push_str(&self.package);
                }
                Err(e) => {
                    tracing::warn!(
                        target: "auriya::fas",
                        "source attach failed for {} (pid {}): {e:?}",
                        self.package, pid
                    );
                    self.last_attached_pkg.clear();
                    self.last_attached_pkg.push_str(&self.package);
                }
            }
        }

        let frames = self.source.drain_frame_times().await;
        if frames.is_empty() {
            if self.buffer.time_since_last_frame() >= FRAME_TIMEOUT {
                self.buffer.additional_frametime();
            }
        } else {
            for ft in frames {
                self.buffer.push(ft);
            }
        }

        if self.buffer.target_fps.is_some() && self.buffer.state == BufferState::Usable {
            self.advance_state();
        } else {
            self.transition_not_working();
        }

        let temp = self.thermal.get_max_temp().unwrap_or(0.0);
        if temp > thermal_thresh {
            tracing::debug!(target: "auriya::fas", "Thermal throttle: {:.1}°C", temp);
            return Ok(ScalingAction::Reduce);
        }

        if self.state != FasState::Working {
            return Ok(ScalingAction::Maintain);
        }

        let Some(target_fps) = self.buffer.target_fps else {
            return Ok(ScalingAction::Maintain);
        };

        self.update_target_offset();

        let adjusted_target_fps =
            (f64::from(target_fps) + self.target_fps_offset - MARGIN_FPS).max(1.0);

        let last_frame = match self.buffer.last_frametime() {
            Some(f) => f,
            None => return Ok(ScalingAction::Maintain),
        };

        let control_khz = compute_control_khz(last_frame, adjusted_target_fps, self.kp);
        let is_janked =
            self.buffer.current_fps_long < f64::from(target_fps) - JANK_DELTA_FPS;

        tracing::info!(
            target: "auriya::fas",
            "fps_long={:.1} fps_short={:.1} target={} adj_target={:.2} jank={} ctl={:.0}kHz off={:.2}",
            self.buffer.current_fps_long,
            self.buffer.current_fps_short,
            target_fps,
            adjusted_target_fps,
            is_janked,
            control_khz,
            self.target_fps_offset
        );

        let action = decide_action(control_khz, is_janked);
        tracing::info!(
            target: "auriya::fas",
            "decision={}",
            match action {
                ScalingAction::Boost => "BOOST",
                ScalingAction::Maintain => "MAINTAIN",
                ScalingAction::Reduce => "REDUCE",
            }
        );

        Ok(action)
    }
    fn advance_state(&mut self) {
        match self.state {
            FasState::NotWorking => {
                self.state = FasState::Waiting;
                self.waiting_since = Some(Instant::now());
                tracing::debug!(target: "auriya::fas", "state: NotWorking -> Waiting");
            }
            FasState::Waiting => {
                if self
                    .waiting_since
                    .map(|t| t.elapsed() >= WAITING_DELAY)
                    .unwrap_or(false)
                {
                    self.state = FasState::Working;
                    self.target_fps_offset = 0.0;
                    self.waiting_since = None;
                    tracing::debug!(target: "auriya::fas", "state: Waiting -> Working");
                }
            }
            FasState::Working => {}
        }
    }

    fn transition_not_working(&mut self) {
        if self.state != FasState::NotWorking {
            tracing::debug!(target: "auriya::fas", "state: {:?} -> NotWorking", self.state);
        }
        self.state = FasState::NotWorking;
        self.waiting_since = None;
        self.target_fps_offset = 0.0;
    }

    fn update_target_offset(&mut self) {
        let Some(util) = self.util_sampler.sample(UTIL_SAMPLE_INTERVAL) else {
            return;
        };
        if util <= 0.10 {
            self.target_fps_offset = 0.0;
        } else if util <= 0.55 {
            self.target_fps_offset -= 0.1;
        } else if util >= 0.65 {
            self.target_fps_offset += 0.1;
        }
        // Invariant §6.6: only allow lowering the effective target.
        self.target_fps_offset = self.target_fps_offset.clamp(-3.0, 0.0);
    }
}

/// Coarse system CPU utilisation sampler from /proc/stat.
/// Returns a value in [0.0, 1.0], updated at most once per `interval`.
struct UtilSampler {
    last_sample: Option<Instant>,
    last_total: u64,
    last_idle: u64,
    cached: f64,
}

impl UtilSampler {
    fn new() -> Self {
        Self {
            last_sample: None,
            last_total: 0,
            last_idle: 0,
            cached: 0.0,
        }
    }

    fn sample(&mut self, interval: Duration) -> Option<f64> {
        let now = Instant::now();
        if let Some(last) = self.last_sample
            && now.duration_since(last) < interval
        {
            return Some(self.cached);
        }

        let stat = std::fs::read_to_string("/proc/stat").ok()?;
        let line = stat.lines().next()?;
        let mut fields = line.split_whitespace();
        if fields.next()? != "cpu" {
            return None;
        }
        let mut vals = [0u64; 8];
        for slot in &mut vals {
            *slot = fields.next().and_then(|s| s.parse().ok()).unwrap_or(0);
        }
        let idle = vals[3] + vals[4];
        let total: u64 = vals.iter().sum();

        let util = if self.last_sample.is_some() {
            let dt = total.saturating_sub(self.last_total);
            let di = idle.saturating_sub(self.last_idle);
            if dt == 0 {
                self.cached
            } else {
                1.0 - (di as f64 / dt as f64)
            }
        } else {
            0.0
        };

        self.last_sample = Some(now);
        self.last_total = total;
        self.last_idle = idle;
        self.cached = util.clamp(0.0, 1.0);
        Some(self.cached)
    }
}

/// Pure P-controller core. Extracted for testability.
///
/// `last_frame * adjusted_target_fps` lands on exactly 1 second when the GPU
/// hits target. Anything slower (heavier frame) makes the product overshoot
/// and produces a positive control signal; anything faster undershoots.
pub fn compute_control_khz(last_frame: Duration, adjusted_target_fps: f64, kp: f64) -> f64 {
    let normalized_ns = last_frame.as_nanos() as f64 * adjusted_target_fps;
    let error_ns = normalized_ns - 1_000_000_000.0;
    error_ns * kp
}

/// Map a control signal + jank flag to a discrete ScalingAction.
/// Pulled out of `tick` so replay tests can exercise the bucket boundaries
/// without spinning up a full controller.
pub fn decide_action(control_khz: f64, is_janked: bool) -> ScalingAction {
    if is_janked || control_khz > BOOST_THRESHOLD_KHZ {
        ScalingAction::Boost
    } else if control_khz < REDUCE_THRESHOLD_KHZ {
        ScalingAction::Reduce
    } else {
        ScalingAction::Maintain
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ft_ms(ms: f64) -> Duration {
        Duration::from_secs_f64(ms / 1000.0)
    }

    #[test]
    fn perfect_frame_yields_zero_control() {
        // 60fps means 16.6666...ms; (16.667ms * 60) = 1.000s, error = 0.
        let last = Duration::from_nanos(1_000_000_000 / 60);
        let ctl = compute_control_khz(last, 60.0, KP_DEFAULT);
        assert!(ctl.abs() < 1.0, "expected ~0 control, got {ctl}");
    }

    #[test]
    fn slow_frame_yields_positive_control() {
        // 30ms at target 60 → product = 1.8s → error = 0.8s = 8e8 ns
        // ctl = 8e8 * 1.5e-4 = 120_000 kHz, well above BOOST_THRESHOLD_KHZ
        let ctl = compute_control_khz(ft_ms(30.0), 60.0, KP_DEFAULT);
        assert!(ctl > BOOST_THRESHOLD_KHZ, "expected boost, got {ctl}");
    }

    #[test]
    fn fast_frame_yields_negative_control_below_reduce_threshold() {
        // 10ms at target 60 → product = 0.6s → error = -0.4s = -4e8 ns
        // ctl = -4e8 * 1.5e-4 = -60_000 kHz, below REDUCE_THRESHOLD_KHZ
        let ctl = compute_control_khz(ft_ms(10.0), 60.0, KP_DEFAULT);
        assert!(ctl < REDUCE_THRESHOLD_KHZ, "expected reduce, got {ctl}");
    }

    #[test]
    fn marginal_frame_stays_in_maintain_band() {
        // 17ms at target 60 → product = 1.02s → error = 2e7 ns
        // ctl = 2e7 * 1.5e-4 = 3000 kHz, well within ±50_000 thresholds.
        let ctl = compute_control_khz(ft_ms(17.0), 60.0, KP_DEFAULT);
        assert!(ctl.abs() < BOOST_THRESHOLD_KHZ);
    }

    #[test]
    fn margin_fps_biases_target_lower_so_normal_frames_register_as_slow() {
        // Without margin: 16.67ms * 60 = 1.0s exactly (zero error).
        // With MARGIN_FPS=1.5, adjusted target = 58.5 → 16.67ms * 58.5 = 0.975s
        // → error = -25_000_000 ns → ctl = -3.75 kHz (still maintain band but
        // shifted toward reduce). The intent of margin is headroom: the
        // controller treats "exactly target" as slightly fast.
        let last = Duration::from_nanos(1_000_000_000 / 60);
        let with_margin = compute_control_khz(last, 60.0 - MARGIN_FPS, KP_DEFAULT);
        let without_margin = compute_control_khz(last, 60.0, KP_DEFAULT);
        assert!(with_margin < without_margin);
    }

    #[test]
    fn util_sampler_returns_value_within_bounds() {
        let mut s = UtilSampler::new();
        // First call seeds — value comes from cached default (0.0) but Some.
        let first = s.sample(Duration::ZERO);
        assert!(first.is_some());
        // Second call after delta produces real reading on a Linux host.
        if std::path::Path::new("/proc/stat").exists() {
            std::thread::sleep(Duration::from_millis(20));
            let second = s.sample(Duration::ZERO).unwrap();
            assert!((0.0..=1.0).contains(&second));
        }
    }
}

