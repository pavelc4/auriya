pub mod cpu;
pub mod gpu;
pub mod thermal;

use crate::core::tweaks::ceiling::CoreLayout;

#[derive(Debug, Clone, Default)]
pub struct TelemetrySnapshot {
    pub cpu: Option<cpu::CpuSnapshot>,
    pub gpu: Option<gpu::GpuSnapshot>,
    pub thermal: Option<thermal::ThermalSnapshot>,
}

pub struct TelemetryHub {
    pub cpu_collector: cpu::CpuCollector,
    pub gpu_collector: gpu::GpuCollector,
    pub thermal_collector: thermal::ThermalCollector,
    pub has_gpu: bool,
}

impl TelemetryHub {
    pub fn new(_layout: &CoreLayout) -> Self {
        let gpu_collector = gpu::GpuCollector::new();
        let has_gpu = gpu_collector.has_vendor();
        Self {
            cpu_collector: cpu::CpuCollector::default(),
            gpu_collector,
            thermal_collector: thermal::ThermalCollector,
            has_gpu,
        }
    }

    pub fn snapshot(&mut self, _layout: &CoreLayout) -> TelemetrySnapshot {
        TelemetrySnapshot {
            cpu: Some(self.cpu_collector.snapshot(_layout)),
            gpu: if self.has_gpu {
                Some(self.gpu_collector.snapshot())
            } else {
                None
            },
            thermal: Some(self.thermal_collector.snapshot()),
        }
    }
}
