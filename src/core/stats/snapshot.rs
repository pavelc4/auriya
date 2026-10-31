use super::fps::FpsStats;
use crate::core::profile::ProfileMode;
use crate::core::telemetry::battery::BatterySnapshot;
use crate::core::telemetry::cpu::{ClusterType, CpuSnapshot};
use crate::core::telemetry::gpu::GpuSnapshot;
use crate::core::telemetry::thermal::ThermalSnapshot;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ThermalCard {
    pub cpu_c: Option<f32>,
    pub gpu_c: Option<f32>,
    pub battery_c: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BatteryCard {
    pub pct: Option<u8>,
    pub current_ma: Option<i32>,
    pub voltage_v: Option<f32>,
    pub status: Option<String>,
    pub health: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CoreDto {
    pub id: usize,
    pub khz: u64,
    pub gov: String,
    pub cluster: String,
    pub online: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CpuCard {
    pub load_pct: f32,
    pub cores: Vec<CoreDto>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub available_governors: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GpuCard {
    pub mhz: Option<u64>,
    pub load_pct: Option<u32>,
    pub vendor: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionCard {
    pub pkg: Option<String>,
    pub profile: String,
    pub active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DaemonCard {
    pub version: String,
    pub pid: u32,
    pub rss_mb: f32,
    pub uptime_secs: u64,
    pub active_profile: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BinderCard {
    pub operational: bool,
    pub screen_interactive: bool,
    pub is_floating: bool,
    pub wifi_low_latency: bool,
    pub statusbar_locked: bool,
    pub dnd_mode: u32,
    pub refresh_rate_hz: Option<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemCard {
    pub uptime_secs: u64,
    pub ram_total_mb: u64,
    pub ram_avail_mb: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StatsSnapshot {
    pub fps: Option<FpsStats>,
    pub thermal: ThermalCard,
    pub battery: BatteryCard,
    pub cpu: Option<CpuCard>,
    pub gpu: Option<GpuCard>,
    pub session: SessionCard,
    pub daemon: DaemonCard,
    pub binder: BinderCard,
    pub system: Option<SystemCard>,
}

fn cluster_str(c: &ClusterType) -> &'static str {
    match c {
        ClusterType::Little => "Little",
        ClusterType::Big => "Big",
        ClusterType::Prime => "Prime",
    }
}

fn sample_self_rss_mb() -> f32 {
    #[cfg(target_os = "android")]
    {
        if let Ok(status) = std::fs::read_to_string("/proc/self/status") {
            for line in status.lines() {
                if let Some(rest) = line.strip_prefix("VmRSS:")
                    && let Some(val_str) = rest.split_whitespace().next()
                    && let Ok(kb) = val_str.parse::<f32>()
                {
                    return kb / 1024.0;
                }
            }
        }
    }
    0.0
}

fn sample_system_card() -> Option<SystemCard> {
    #[cfg(target_os = "android")]
    {
        let mut info = std::mem::MaybeUninit::<libc::sysinfo>::uninit();
        if unsafe { libc::sysinfo(info.as_mut_ptr()) } == 0 {
            let info = unsafe { info.assume_init() };
            let unit = info.mem_unit as u64;
            let total_mb = (info.totalram.saturating_mul(unit)) / (1024 * 1024);
            let free_mb = (info
                .freeram
                .saturating_add(info.bufferram)
                .saturating_mul(unit))
                / (1024 * 1024);
            return Some(SystemCard {
                uptime_secs: info.uptime as u64,
                ram_total_mb: total_mb,
                ram_avail_mb: free_mb,
            });
        }
    }
    None
}

fn sample_binder_card() -> BinderCard {
    #[cfg(target_os = "android")]
    {
        let client = crate::platform::binder::BinderClient::new();
        let operational = client.is_operational();
        let screen_interactive = client.is_screen_interactive().unwrap_or(true);
        let is_floating = client
            .get_foreground_task()
            .ok()
            .flatten()
            .map(|t| t.is_floating)
            .unwrap_or(false);
        let wifi_low_latency = crate::platform::binder::is_wifi_low_latency_active();
        let statusbar_locked = crate::platform::binder::is_statusbar_locked();
        let refresh_rate_hz = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();

        BinderCard {
            operational,
            screen_interactive,
            is_floating,
            wifi_low_latency,
            statusbar_locked,
            dnd_mode: 0,
            refresh_rate_hz,
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        BinderCard {
            operational: true,
            screen_interactive: true,
            is_floating: false,
            wifi_low_latency: false,
            statusbar_locked: false,
            dnd_mode: 0,
            refresh_rate_hz: Some(120.0),
        }
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
        uptime_secs: u64,
    ) -> Self {
        let pid = std::process::id();
        let rss_mb = sample_self_rss_mb();
        let binder = sample_binder_card();
        let system = sample_system_card();
        let active_profile = profile.to_string().to_lowercase();

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
                available_governors: crate::platform::cpu::governor::available_governors().to_vec(),
            }),
            gpu: gpu.map(|g| GpuCard {
                mhz: g.cur_freq_mhz,
                load_pct: g.load_pct,
                vendor: g.vendor.clone(),
            }),
            session: SessionCard {
                pkg: pkg.map(str::to_string),
                profile: active_profile.clone(),
                active,
            },
            daemon: DaemonCard {
                version: env!("CARGO_PKG_VERSION").to_string(),
                pid,
                rss_mb,
                uptime_secs,
                active_profile,
            },
            binder,
            system,
        }
    }
}
