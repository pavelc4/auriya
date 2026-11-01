use super::{
    bottleneck::{BottleneckDetector, BottleneckType},
    buffer::{BufferState, FrameBuffer, TargetFps},
    compute::{compute_control_khz, decide_action},
    sampler::UtilSampler,
    source::FrameSource,
    tuning::{FasTuning, JANK_DELTA_FPS, KP_DEFAULT},
};
use crate::core::scaling::{PlatformCapabilities, ScalingAction};
use crate::core::telemetry::thermal::ThermalCollector;
use anyhow::Result;
use std::time::{Duration, Instant};

const WAITING_DELAY: Duration = Duration::from_secs(3);
const FRAME_TIMEOUT: Duration = Duration::from_millis(100);
const UTIL_SAMPLE_INTERVAL: Duration = Duration::from_secs(1);

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
    thermal: ThermalCollector,
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
            thermal: ThermalCollector::new(),
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

    pub fn set_package(&mut self, package: &str, pid: Option<i32>) {
        if self.package == package {
            return;
        }
        tracing::debug!(target: "auriya::fas", "Switching to package: {}", package);
        self.package.clear();
        self.package.push_str(package);
        self.pid = pid;
        self.last_attached_pkg.clear();
        // Warm handover: reset governor queues without discarding real frame telemetry,
        // preventing sudden drop to 0 FPS on the UI during app transitions.
        self.buffer.reset_governor();
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn set_tuning(&mut self, tuning: FasTuning) {
        self.tuning = tuning;
        self.bottleneck =
            BottleneckDetector::new(self.tuning.cv_threshold, self.tuning.debounce_frames);
    }

    pub fn set_target_fps(&mut self, fps: u32) {
        self.buffer.set_target_fps_config(TargetFps::Single(fps));
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn set_target_fps_config(&mut self, config: TargetFps) {
        self.buffer.set_target_fps_config(config);
        self.bottleneck.reset();
        self.transition_not_working();
    }

    pub fn get_target_fps(&self) -> u32 {
        self.buffer.target_fps.unwrap_or(60)
    }

    /// Windowed FPS stats (avg/peak/1%-low/jank) for IPC telemetry APIs,
    /// computed from the most recent 1-second window of real presented frames.
    /// Uses stack-allocated buffer for strict zero-heap allocation hot-path execution.
    pub fn fps_stats(&self) -> crate::core::stats::FpsStats {
        if self.buffer.time_since_last_frame() >= Duration::from_millis(1500) {
            return crate::core::stats::FpsStats::empty();
        }
        let target = self.buffer.target_fps.unwrap_or(0);
        let window_size = if target > 0 {
            ((target * 2) as usize).clamp(60, 240)
        } else {
            120
        };
        let mut buf = [0.0f64; 240];
        let count = self
            .buffer
            .copy_recent_real_frametimes_secs(&mut buf[..window_size]);
        if count == 0 {
            return crate::core::stats::FpsStats::empty();
        }
        let display_hz = match crate::platform::binder::get_active_display_refresh_rate() {
            hz if hz > 0.0 => Some(hz),
            _ => None,
        };
        crate::core::stats::fps_stats_compute_in_place(&mut buf[..count], target, display_hz)
    }

    /// Drain per-frame timestamps from the frame stream into the buffer
    /// and advance controller state. Falls back to hardware-sampled sysfs FPS
    /// when eBPF uprobes yield zero frames (e.g. Vulkan or non-hooked direct surface rendering).
    pub fn drain_frames_with_fallback(&mut self, fallback_fps: Option<f64>) {
        let count = self.source.drain_into_buffer(&mut self.buffer);
        match (count, fallback_fps) {
            (0, Some(fps)) if (0.1..=500.0).contains(&fps) => {
                let dt = Duration::from_secs_f64(1.0 / fps);
                self.buffer.push(dt);
            }
            (0, _) if self.buffer.time_since_last_frame() >= FRAME_TIMEOUT => {
                self.buffer.additional_frametime();
            }
            _ => {}
        }

        match (self.buffer.target_fps.is_some(), self.buffer.state) {
            (true, BufferState::Usable) => self.advance_state(),
            _ => self.transition_not_working(),
        }
    }

    /// Drain per-frame timestamps from the frame stream into the buffer
    /// and advance controller state. Zero-heap allocation hot-path.
    pub fn drain_frames(&mut self) {
        self.drain_frames_with_fallback(None);
    }

    pub async fn tick(&mut self, fallback_fps: Option<f64>) -> Result<ScalingAction> {
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

        self.drain_frames_with_fallback(fallback_fps);

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
            match (raw_action != action, raw_action) {
                (true, ScalingAction::BoostGpu) => " (fallback from GPU)",
                (true, ScalingAction::BoostCpu) => " (fallback from CPU)",
                _ => "",
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
                    .is_some_and(|t| t.elapsed() >= WAITING_DELAY)
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
        self.target_fps_offset = self.target_fps_offset.clamp(-3.0, 0.0);
    }
}
