use crate::core::{
    fas::buffer::{BufferState, FrameBuffer, TargetFps},
    fas::source::FrameSource,
    scaling::ScalingAction,
    thermal::ThermalMonitor,
};
use anyhow::Result;
use std::time::Duration;
pub struct FasController {
    source: FrameSource,
    buffer: FrameBuffer,
    thermal: ThermalMonitor,
    package: String,
    pid: Option<i32>,
    kp: f32,
}

impl FasController {
    pub fn new(target_fps_config: TargetFps) -> Self {
        let source = FrameSource::new();
        tracing::debug!(target: "auriya::fas", "FAS Controller initialized");

        Self {
            source,
            buffer: FrameBuffer::new(target_fps_config),
            thermal: ThermalMonitor::new(),
            package: String::new(),
            pid: None,
            kp: 0.05,
        }
    }

    pub fn with_target_fps(fps: u32) -> Self {
        Self::new(TargetFps::Single(fps))
    }

    pub fn set_package(&mut self, package: String, pid: Option<i32>) {
        if self.package != package {
            tracing::debug!(target: "auriya::fas", "Switching to package: {}", package);
            self.package = package;
            self.pid = pid;
            self.buffer.clear();
        }
    }

    pub fn set_target_fps(&mut self, fps: u32) {
        tracing::debug!(target: "auriya::fas", "Target FPS set to {}", fps);
        self.buffer = FrameBuffer::new(TargetFps::Single(fps));
    }

    pub fn set_target_fps_config(&mut self, config: TargetFps) {
        self.buffer = FrameBuffer::new(config);
    }

    pub fn get_target_fps(&self) -> u32 {
        self.buffer.target_fps.unwrap_or(60)
    }

    pub async fn tick(&mut self, thermal_thresh: f32) -> Result<ScalingAction> {
        if let Some(pid) = self.pid.filter(|_| !self.package.is_empty()) {
            let _ = self.source.attach(&self.package, pid).await;
        }

        let frame_time = match self.source.get_frame_time().await {
            Ok(Some(ft)) => ft,
            Ok(None) => {
                let elapsed = self.buffer.time_since_last_frame();
                if elapsed > Duration::from_millis(200) {
                    return Ok(ScalingAction::Maintain);
                }
                return Ok(ScalingAction::Maintain);
            }
            Err(e) => {
                tracing::debug!(target: "auriya::fas", "Frame source error: {:?}", e);
                return Ok(ScalingAction::Maintain);
            }
        };

        self.buffer.push(frame_time);

        let temp = self.thermal.get_max_temp().unwrap_or(0.0);
        if temp > thermal_thresh {
            tracing::debug!(target: "auriya::fas", "Thermal throttle: {:.1}°C", temp);
            return Ok(ScalingAction::Reduce);
        }

        if self.buffer.state != BufferState::Usable {
            return Ok(ScalingAction::Maintain);
        }

        let Some(target_fps) = self.buffer.target_fps else {
            return Ok(ScalingAction::Maintain);
        };

        let current_fps = self.buffer.current_fps_short as f32;
        let target_frame_time = 1000.0 / target_fps as f32;
        let actual_frame_time = 1000.0 / current_fps.max(1.0);

        let error = actual_frame_time - target_frame_time;
        let control = error * self.kp;

        let is_janked = current_fps < target_fps as f32 - 2.0;

        tracing::debug!(
            target: "auriya::fas",
            "FPS={:.1} Target={} Janked={} Control={:.3}",
            current_fps, target_fps, is_janked, control
        );

        let action = if is_janked || control > 0.5 {
            ScalingAction::Boost
        } else if control < -0.5 {
            ScalingAction::Reduce
        } else {
            ScalingAction::Maintain
        };

        Ok(action)
    }
}
