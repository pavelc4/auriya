use anyhow::Result;
use crate::core::{frame::FrameSource, thermal::ThermalMonitor, scaling};

pub struct FasController {
    frame_source: Box<dyn FrameSource>,
    thermal: ThermalMonitor,
}

impl FasController {
    pub fn new() -> Self {
        Self {
            frame_source: crate::core::frame::create_frame_source(),
            thermal: ThermalMonitor::new(),
        }
    }

    pub fn tick(
        &mut self,
        margin: f32,
        thermal_thresh: f32,
    ) -> Result<scaling::ScalingAction> {
        let gpu_util = self.frame_source.get_metric()?.unwrap_or(50.0);
        let temp = self.thermal.get_max_temp()?;
        let throttle = temp > thermal_thresh;

        let action = scaling::decide_from_gpu_util(gpu_util, margin, throttle);

        tracing::debug!(
            target: "auriya::fas",
            "GPU={:.1}% Margin={} Temp={:.1}°C Action={:?}",
            gpu_util, margin, temp, action
        );

        Ok(action)
    }
}
