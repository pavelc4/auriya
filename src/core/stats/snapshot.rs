use super::fps::FpsStats;
use crate::core::profile::ProfileMode;
use crate::core::telemetry::battery::BatterySnapshot;
use crate::core::telemetry::cpu::{ClusterType, CpuSnapshot};
use crate::core::telemetry::gpu::GpuSnapshot;
use crate::core::telemetry::thermal::ThermalSnapshot;
use serde::{Deserialize, Serialize};
use std::borrow::Cow;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct ThermalCard {
    #[serde(default)]
    pub cpu_c: Option<f32>,
    #[serde(default)]
    pub gpu_c: Option<f32>,
    #[serde(default)]
    pub battery_c: Option<f32>,
    #[serde(default)]
    pub sconfig_supported: bool,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct BatteryCard {
    #[serde(default)]
    pub pct: Option<u8>,
    #[serde(default)]
    pub current_ma: Option<i32>,
    #[serde(default)]
    pub voltage_v: Option<f32>,
    #[serde(default)]
    pub status: Option<String>,
    #[serde(default)]
    pub health: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct CoreDto {
    #[serde(default)]
    pub id: usize,
    #[serde(default)]
    pub khz: u64,
    #[serde(default)]
    pub gov: String,
    #[serde(default)]
    pub cluster: String,
    #[serde(default)]
    pub online: bool,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct CpuCard {
    #[serde(default)]
    pub load_pct: f32,
    #[serde(default)]
    pub cores: Vec<CoreDto>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub available_governors: Vec<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct GpuCard {
    #[serde(default)]
    pub mhz: Option<u64>,
    #[serde(default)]
    pub load_pct: Option<u32>,
    #[serde(default)]
    pub vendor: Option<Cow<'static, str>>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SessionCard {
    #[serde(default)]
    pub pkg: Option<String>,
    #[serde(default)]
    pub profile: Cow<'static, str>,
    #[serde(default)]
    pub active: bool,
    #[serde(default)]
    pub is_game: bool,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct DaemonCard {
    #[serde(default)]
    pub version: Cow<'static, str>,
    #[serde(default)]
    pub pid: u32,
    #[serde(default)]
    pub rss_mb: f32,
    #[serde(default)]
    pub uptime_secs: u64,
    #[serde(default)]
    pub active_profile: Cow<'static, str>,
    #[serde(default)]
    pub version_code: Cow<'static, str>,
    #[serde(default)]
    pub commit: Cow<'static, str>,
    #[serde(default)]
    pub arch: Cow<'static, str>,
    #[serde(default)]
    pub mod_updated_secs: Option<u64>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct BinderCard {
    #[serde(default)]
    pub operational: bool,
    #[serde(default)]
    pub screen_interactive: bool,
    #[serde(default)]
    pub is_floating: bool,
    #[serde(default)]
    pub wifi_low_latency: bool,
    #[serde(default)]
    pub statusbar_locked: bool,
    #[serde(default)]
    pub dnd_mode: u32,
    #[serde(default)]
    pub refresh_rate_hz: Option<f32>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct SystemCard {
    #[serde(default)]
    pub uptime_secs: u64,
    #[serde(default)]
    pub ram_total_mb: u64,
    #[serde(default)]
    pub ram_avail_mb: u64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct StatsSnapshot {
    #[serde(default)]
    pub fps: Option<FpsStats>,
    #[serde(default)]
    pub thermal: ThermalCard,
    #[serde(default)]
    pub battery: BatteryCard,
    #[serde(default)]
    pub cpu: Option<CpuCard>,
    #[serde(default)]
    pub gpu: Option<GpuCard>,
    #[serde(default)]
    pub session: SessionCard,
    #[serde(default)]
    pub daemon: DaemonCard,
    #[serde(default)]
    pub binder: BinderCard,
    #[serde(default)]
    pub system: Option<SystemCard>,
}

fn cluster_str(c: &ClusterType) -> &'static str {
    match c {
        ClusterType::Little => "Little",
        ClusterType::Big => "Big",
        ClusterType::Prime => "Prime",
    }
}

#[derive(Debug, Clone)]
struct ModulePropInfo {
    version: String,
    version_code: String,
    commit: String,
    mod_updated_secs: Option<u64>,
}

fn get_module_prop_info() -> &'static ModulePropInfo {
    static INFO: std::sync::OnceLock<ModulePropInfo> = std::sync::OnceLock::new();
    INFO.get_or_init(|| {
        let prop_path = std::path::Path::new("/data/adb/modules/auriya/module.prop");
        let mut version = env!("CARGO_PKG_VERSION").to_string();
        let mut version_code = String::new();
        let mut commit = String::new();

        if let Ok(content) = std::fs::read_to_string(prop_path) {
            for line in content.lines() {
                let line = line.trim();
                if let Some(val) = line.strip_prefix("version=") {
                    version = val.trim().to_string();
                } else if let Some(val) = line.strip_prefix("versionCode=") {
                    version_code = val.trim().to_string();
                }
            }
        }

        if let Some(open) = version.find('(')
            && let Some(close) = version.find(')')
            && close > open
        {
            let inside = &version[open + 1..close];
            let parts: Vec<&str> = inside.split('-').collect();
            if parts.len() >= 2 {
                commit = parts[1].to_string();
            }
        }
        if commit.is_empty() {
            commit = if !version_code.is_empty() {
                version_code.clone()
            } else {
                "release".to_string()
            };
        }

        let mod_updated_secs = std::fs::metadata(prop_path)
            .ok()
            .and_then(|m| m.modified().ok())
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs());

        ModulePropInfo {
            version,
            version_code,
            commit,
            mod_updated_secs,
        }
    })
}

fn get_target_arch() -> &'static str {
    match std::env::consts::ARCH {
        "aarch64" => "v8a",
        "arm" => "v7a",
        "x86_64" => "x64",
        "x86" => "x86",
        other => other,
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
        battery: BatterySnapshot,
        pkg: Option<&str>,
        profile: ProfileMode,
        active: bool,
        is_game: bool,
        uptime_secs: u64,
    ) -> Self {
        let pid = std::process::id();
        let rss_mb = sample_self_rss_mb();
        let binder = sample_binder_card();
        let system = sample_system_card();
        let mod_info = get_module_prop_info();
        let daemon_version: Cow<'static, str> = if mod_info.version.is_empty() {
            env!("CARGO_PKG_VERSION").into()
        } else {
            Cow::Borrowed(mod_info.version.as_str())
        };

        StatsSnapshot {
            fps,
            thermal: ThermalCard {
                cpu_c: thermal.and_then(|t| t.cpu_temp_c),
                gpu_c: thermal.and_then(|t| t.gpu_temp_c),
                battery_c: battery.temp_c,
                sconfig_supported: crate::platform::thermal::sconfig_exists(),
            },
            battery: BatteryCard {
                pct: battery.pct,
                current_ma: battery.current_ma,
                voltage_v: battery.voltage_v,
                status: battery.status,
                health: battery.health,
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
                vendor: g.vendor.map(Cow::Borrowed),
            }),
            session: SessionCard {
                pkg: pkg.map(str::to_string),
                profile: Cow::Borrowed(profile.as_str()),
                active,
                is_game,
            },
            daemon: DaemonCard {
                version: daemon_version,
                pid,
                rss_mb,
                uptime_secs,
                active_profile: Cow::Borrowed(profile.as_str()),
                version_code: Cow::Borrowed(mod_info.version_code.as_str()),
                commit: Cow::Borrowed(mod_info.commit.as_str()),
                arch: Cow::Borrowed(get_target_arch()),
                mod_updated_secs: mod_info.mod_updated_secs,
            },
            binder,
            system,
        }
    }
}
