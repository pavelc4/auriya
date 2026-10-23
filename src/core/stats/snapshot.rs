use super::fps::FpsStats;
use crate::core::profile::ProfileMode;
use crate::core::telemetry::battery::BatterySnapshot;
use crate::core::telemetry::cpu::{ClusterType, CpuSnapshot};
use crate::core::telemetry::gpu::GpuSnapshot;
use crate::core::telemetry::thermal::ThermalSnapshot;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct ThermalCard {
    pub cpu_c: Option<f32>,
    pub gpu_c: Option<f32>,
    pub battery_c: Option<f32>,
}

#[derive(Debug, Clone, Serialize)]
pub struct BatteryCard {
    pub pct: Option<u8>,
    pub current_ma: Option<i32>,
    pub voltage_v: Option<f32>,
    pub status: Option<String>,
    pub health: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct CoreDto {
    pub id: usize,
    pub khz: u64,
    pub gov: String,
    pub cluster: String,
    pub online: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct CpuCard {
    pub load_pct: f32,
    pub cores: Vec<CoreDto>,
}

#[derive(Debug, Clone, Serialize)]
pub struct GpuCard {
    pub mhz: Option<u64>,
    pub load_pct: Option<u32>,
    pub vendor: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SessionCard {
    pub pkg: Option<String>,
    pub profile: String,
    pub active: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct StatsSnapshot {
    pub fps: Option<FpsStats>,
    pub thermal: ThermalCard,
    pub battery: BatteryCard,
    pub cpu: Option<CpuCard>,
    pub gpu: Option<GpuCard>,
    pub session: SessionCard,
}

fn cluster_str(c: &ClusterType) -> &'static str {
    match c {
        ClusterType::Little => "Little",
        ClusterType::Big => "Big",
        ClusterType::Prime => "Prime",
    }
}

impl StatsSnapshot {
    /// Assemble the response from the pieces the daemon already has in
    /// `CurrentState` plus a fresh battery read. All inputs are `core` types so
    /// this module stays free of any `daemon` dependency.
    #[allow(clippy::too_many_arguments)]
    pub fn build(
        fps: Option<FpsStats>,
        cpu: Option<&CpuSnapshot>,
        gpu: Option<&GpuSnapshot>,
        thermal: Option<&ThermalSnapshot>,
        battery: &BatterySnapshot,
        pkg: Option<&str>,
        profile: ProfileMode,
        active: bool,
    ) -> Self {
        StatsSnapshot {
            fps,
            thermal: ThermalCard {
                cpu_c: thermal.and_then(|t| t.cpu_temp_c),
                gpu_c: thermal.and_then(|t| t.gpu_temp_c),
                battery_c: battery.temp_c,
            },
            battery: BatteryCard {
                pct: battery.pct,
                current_ma: battery.current_ma,
                voltage_v: battery.voltage_v,
                status: battery.status.clone(),
                health: battery.health.clone(),
            },
            cpu: cpu.map(|c| CpuCard {
                load_pct: c.load_pct,
                cores: c
                    .cores
                    .iter()
                    .map(|core| CoreDto {
                        id: core.core_id,
                        khz: core.cur_freq_khz,
                        gov: core.governor.clone(),
                        cluster: cluster_str(&core.cluster).to_string(),
                        online: core.online,
                    })
                    .collect(),
            }),
            gpu: gpu.map(|g| GpuCard {
                mhz: g.cur_freq_mhz,
                load_pct: g.load_pct,
                vendor: g.vendor.clone(),
            }),
            session: SessionCard {
                pkg: pkg.map(str::to_string),
                profile: profile.to_string().to_lowercase(),
                active,
            },
        }
    }
}
