use crate::core::{
    fas::pid::PidController, frame::monitor::FrameMonitor, scaling::ScalingAction,
    thermal::ThermalMonitor,
};
use anyhow::Result;

pub struct FasController {
    frame_monitor: FrameMonitor,
    pid: PidController,
    thermal: ThermalMonitor,
    target_frame_time: f32,
}

impl FasController {
    pub fn new() -> Self {
        Self {
            frame_monitor: FrameMonitor::new("".to_string()),
            pid: PidController::new(0.05, 0.001, 0.01), // Tunable parameters
            thermal: ThermalMonitor::new(),
            target_frame_time: 16.6, // Default to 60 FPS
        }
    }

    pub fn set_package(&mut self, package: String) {
        if self.frame_monitor.package != package {
            self.frame_monitor.update_package(package);
            self.pid.reset();
        }
    }

    pub fn set_target_fps(&mut self, fps: u32) {
        if fps > 0 {
            self.target_frame_time = 1000.0 / fps as f32;
            tracing::info!(target: "auriya::fas", "Target FPS set to {} ({:.2}ms)", fps, self.target_frame_time);
            self.pid.reset();
        }
    }

    pub fn tick(&mut self, margin: f32, thermal_thresh: f32) -> Result<ScalingAction> {
        let frame_time = self.frame_monitor.get_frame_time().unwrap_or(0.0);
        let temp = self.thermal.get_max_temp()?;
        let throttle = temp > thermal_thresh;

        // If frame time is 0 (no data), we can't do much. Maintain.
        if frame_time <= 0.0 {
            return Ok(ScalingAction::Maintain);
        }

        let output = self.pid.next(self.target_frame_time, frame_time);

        // Simple logic mapping PID output to ScalingAction
        // Positive output means we are under budget (good), negative means over budget (bad)
        // Wait, error = target - actual.
        // If target (16ms) > actual (10ms), error is +6ms. We are fast.
        // If target (16ms) < actual (20ms), error is -4ms. We are slow.

        let action = if throttle {
            ScalingAction::Reduce
        } else if output < -margin {
            // We are too slow (frame time high), boost
            ScalingAction::Boost
        } else if output > margin {
            // We are too fast (frame time low), reduce
            ScalingAction::Reduce
        } else {
            ScalingAction::Maintain
        };

        tracing::debug!(
            target: "auriya::fas",
            "FT={:.2}ms Target={:.2}ms PID={:.4} Temp={:.1}°C Action={:?}",
            frame_time, self.target_frame_time, output, temp, action
        );

        Ok(action)
    }
}
