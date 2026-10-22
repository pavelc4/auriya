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
// Output is `ScalingAction` (BoostGpu/BoostCpu/BoostBalanced/Maintain/Reduce).
// The internal control signal is computed in nanoseconds (and would map to kHz
// on a freq-table backend); the conversion to enum buckets is done at the
// bottom of `tick`, gated by bottleneck classification.

use crate::core::{
    fas::bottleneck::{BottleneckDetector, BottleneckType},
    fas::buffer::{BufferState, FrameBuffer, TargetFps},
    fas::source::FrameSource,
    scaling::{PlatformCapabilities, ScalingAction},
    thermal::ThermalMonitor,
};
use anyhow::Result;
use std::time::{Duration, Instant};

const WAITING_DELAY: Duration = Duration::from_secs(3);
const FRAME_TIMEOUT: Duration = Duration::from_millis(100);
const UTIL_SAMPLE_INTERVAL: Duration = Duration::from_secs(1);
pub const KP_DEFAULT: f64 = 1.5e-4;
pub const MARGIN_FPS: f64 = 1.5;
const JANK_DELTA_FPS: f64 = 2.0;
pub const BOOST_THRESHOLD_KHZ: f64 = 50_000.0;
pub const REDUCE_THRESHOLD_KHZ: f64 = -50_000.0;

/// Runtime-tunable FAS parameters, resolved from `settings.toml` at daemon
/// construction (`[fas]`, `[dynamic_governor]`, and the active `[modes.*]`
/// entry selected by `fas.default_mode`). `Default` reproduces the historical
/// hardcoded constants so the unit tests stay behaviour-identical without a
/// settings file.
#[derive(Debug, Clone, Copy)]
pub struct FasTuning {
    /// FPS headroom subtracted from the target (per-mode `margin`). Higher =
    /// the controller treats "exactly target" as slower, biasing toward boosts.
    pub margin_fps: f64,
    /// Skin-temperature ceiling (°C); above it FAS forces `Reduce`.
    pub thermal_threshold: f32,
    /// Bottleneck-detector coefficient-of-variation split
    pub cv_threshold: f64,
    /// Frames a new bottleneck class must persist before it is accepted.
    pub debounce_frames: u32,
    /// When false, bottleneck classification is skipped and every boost is
    /// `BoostBalanced` (full profile) instead
    pub dynamic_governor_enabled: bool,
}

impl Default for FasTuning {
    fn default() -> Self {
        Self {
            margin_fps: MARGIN_FPS,
            thermal_threshold: 90.0,
            cv_threshold: 0.15,
            debounce_frames: 3,
            dynamic_governor_enabled: true,
        }
    }
}

impl FasTuning {
    /// Resolve tuning from `settings.toml`.
    ///
    /// `fas.default_mode` names which `[modes.*]` entry is active; that entry
    /// supplies `margin` and (preferentially) the thermal ceiling, with
    /// `fas.thermal_threshold` as the fallback when the mode omits it or is
    /// unknown. `[dynamic_governor]` supplies the bottleneck-detector knobs.
    /// This is what makes `fas.default_mode` and every `[modes.*]` block
    /// (including `fast`) actually affect FAS behaviour.
    pub fn from_settings(settings: &crate::core::config::Settings) -> Self {
        let mode = settings.modes.get(&settings.fas.default_mode);
        if mode.is_none() {
            tracing::warn!(
                target: "auriya::fas",
                "fas.default_mode = {:?} has no matching [modes.*] entry; using default margin",
                settings.fas.default_mode,
            );
        }
        let default = Self::default();
        Self {
            margin_fps: mode.map(|m| m.margin).unwrap_or(default.margin_fps),
            thermal_threshold: mode
                .map(|m| m.thermal_threshold)
                .unwrap_or(settings.fas.thermal_threshold) as f32,
            cv_threshold: settings.dynamic_governor.cv_threshold,
            debounce_frames: settings.dynamic_governor.debounce_frames,
            dynamic_governor_enabled: settings.dynamic_governor.enabled,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FasState {
    NotWorking,
    Waiting,
    Working,
}

pub struct FasController {
    source: FrameSource,
    buffer: FrameBuffer,
    bottleneck: BottleneckDetector,
    thermal: ThermalMonitor,
    caps: PlatformCapabilities,
    package: String,
    pid: Option<i32>,
    last_attached_pkg: String,
    state: FasState,
    waiting_since: Option<Instant>,
    util_sampler: UtilSampler,
    target_fps_offset: f64,
    kp: f64,
    tuning: FasTuning,
}

impl FasController {
    /// Construct a FAS controller. Takes a broadcast receiver from the
    /// shared `EbpfFrameStream` — the eBPF probe is already loaded by the
    /// caller so FAS creation cannot fail.
    pub fn new(
        rx: tokio::sync::broadcast::Receiver<Duration>,
        target_fps_config: TargetFps,
        tuning: FasTuning,
    ) -> Self {
        let source = FrameSource::new(rx);
        let caps = PlatformCapabilities::detect();
        tracing::debug!(
            target: "auriya::fas",
            "FAS Controller initialized (gpu={} cpu={} margin={} thermal={} dyn_gov={})",
            caps.gpu_controllable,
            caps.cpu_controllable,
            tuning.margin_fps,
            tuning.thermal_threshold,
            tuning.dynamic_governor_enabled,
        );
        if !caps.gpu_controllable {
            tracing::warn!(
                target: "auriya::fas",
                "GPU sysfs not found — BoostGpu will fall back to all-out Performance"
            );
        }
        Self {
            source,
            buffer: FrameBuffer::new(target_fps_config),
            bottleneck: BottleneckDetector::new(tuning.cv_threshold, tuning.debounce_frames),
            thermal: ThermalMonitor::new(),
            caps,
            package: String::new(),
            pid: None,
            last_attached_pkg: String::new(),
            state: FasState::NotWorking,
            waiting_since: None,
            util_sampler: UtilSampler::new(),
            target_fps_offset: 0.0,
            kp: KP_DEFAULT,
            tuning,
        }
    }

    pub fn reset(&mut self) {
        self.package.clear();
        self.pid = None;
        self.last_attached_pkg.clear();
        self.buffer.clear();
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn set_package(&mut self, package: String, pid: Option<i32>) {
        if self.package != package {
            tracing::debug!(target: "auriya::fas", "Switching to package: {}", package);
            self.package = package;
            self.pid = pid;
            self.last_attached_pkg.clear();
            self.buffer.clear();
            self.bottleneck.reset();
            self.transition_not_working();
        }
    }

    pub fn set_tuning(&mut self, tuning: FasTuning) {
        self.tuning = tuning;
        self.bottleneck =
            BottleneckDetector::new(self.tuning.cv_threshold, self.tuning.debounce_frames);
    }

    pub fn set_target_fps(&mut self, fps: u32) {
        self.buffer = FrameBuffer::new(TargetFps::Single(fps));
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn set_target_fps_config(&mut self, config: TargetFps) {
        self.buffer = FrameBuffer::new(config);
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn get_target_fps(&self) -> u32 {
        self.buffer.target_fps.unwrap_or(60)
    }

    /// Windowed FPS stats (avg/peak/1%-low/jank) for IPC telemetry APIs,
    /// computed from the most recent 1-second window of real presented frames.
    pub fn fps_stats(&self) -> crate::core::stats::FpsStats {
        if self.package.is_empty()
            || self.buffer.time_since_last_frame() >= Duration::from_millis(1500)
        {
            return crate::core::stats::FpsStats {
                avg: 0.0,
                peak: 0.0,
                low_1pct: 0.0,
                jank: 0,
                frames: 0,
            };
        }
        let target = self.get_target_fps();
        let window_size = (target as usize).clamp(30, 240);
        crate::core::stats::fps_stats_from_frametimes(
            &self.buffer.recent_real_frametimes(window_size),
            target,
        )
    }

    pub async fn tick(&mut self) -> Result<ScalingAction> {
        if let Some(pid) = self.pid.filter(|_| !self.package.is_empty())
            && self.last_attached_pkg != self.package
        {
            tracing::debug!(
                target: "auriya::fas",
                "Attaching to package: {} (PID: {})",
                self.package, pid
            );
            match self.source.attach(&self.package, pid) {
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

        let frames = self.source.drain_frame_times();
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
        if temp > self.tuning.thermal_threshold {
            tracing::debug!(target: "auriya::fas", "Thermal throttle: {:.1}°C (limit {:.1})", temp, self.tuning.thermal_threshold);
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
            (f64::from(target_fps) + self.target_fps_offset - self.tuning.margin_fps).max(1.0);

        let last_frame = match self.buffer.last_frametime() {
            Some(f) => f,
            None => return Ok(ScalingAction::Maintain),
        };

        let control_khz = compute_control_khz(last_frame, adjusted_target_fps, self.kp);
        let is_janked = self.buffer.current_fps_long < f64::from(target_fps) - JANK_DELTA_FPS;

        // Bottleneck classification is what lets FAS issue *targeted* CPU-only
        // or GPU-only boosts. When dynamic_governor is disabled, skip it and
        // treat every boost as Balanced (full profile) — a coarser but
        // predictable fallback that never mis-attributes the bottleneck.
        let bottleneck = if self.tuning.dynamic_governor_enabled {
            let frametimes = self.buffer.recent_frametimes(60);
            self.bottleneck.classify(&frametimes, target_fps)
        } else {
            BottleneckType::Balanced
        };

        tracing::debug!(
            target: "auriya::fas",
            "fps_long={:.1} fps_short={:.1} target={} adj_target={:.2} jank={} ctl={:.0}kHz off={:.2} bneck={}",
            self.buffer.current_fps_long,
            self.buffer.current_fps_short,
            target_fps,
            adjusted_target_fps,
            is_janked,
            control_khz,
            self.target_fps_offset,
            match bottleneck {
                BottleneckType::Gpu => "GPU",
                BottleneckType::Cpu => "CPU",
                BottleneckType::Balanced => "BAL",
                BottleneckType::Unknown => "?",
            }
        );

        let mut action = decide_action(control_khz, is_janked, bottleneck);
        let raw_action = action;
        action = self.caps.fallback_action(action);
        tracing::debug!(
            target: "auriya::fas",
            "decision={}{}",
            match action {
                ScalingAction::BoostGpu => "BOOST_GPU",
                ScalingAction::BoostCpu => "BOOST_CPU",
                ScalingAction::BoostBalanced => "BOOST_BAL",
                ScalingAction::Maintain => "MAINTAIN",
                ScalingAction::Reduce => "REDUCE",
            },
            if raw_action != action {
                format!(" (fallback from {})", match raw_action {
                    ScalingAction::BoostGpu => "GPU",
                    ScalingAction::BoostCpu => "CPU",
                    _ => "",
                })
            } else {
                String::new()
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
#[derive(Default)]
pub struct UtilSampler {
    last_sample: Option<Instant>,
    last_total: u64,
    last_idle: u64,
    cached: f64,
}

impl UtilSampler {
    pub fn new() -> Self {
        Self {
            last_sample: None,
            last_total: 0,
            last_idle: 0,
            cached: 0.0,
        }
    }

    pub fn sample(&mut self, interval: Duration) -> Option<f64> {
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

/// Map a control signal + jank flag + bottleneck type to a discrete ScalingAction.
/// Pulled out of `tick` so replay tests can exercise the bucket boundaries
/// without spinning up a full controller.
pub fn decide_action(
    control_khz: f64,
    is_janked: bool,
    bottleneck: BottleneckType,
) -> ScalingAction {
    if is_janked || control_khz > BOOST_THRESHOLD_KHZ {
        match bottleneck {
            BottleneckType::Gpu => ScalingAction::BoostGpu,
            BottleneckType::Cpu => ScalingAction::BoostCpu,
            BottleneckType::Balanced => ScalingAction::BoostBalanced,
            BottleneckType::Unknown => ScalingAction::BoostBalanced,
        }
    } else if control_khz < REDUCE_THRESHOLD_KHZ {
        ScalingAction::Reduce
    } else {
        ScalingAction::Maintain
    }
}
