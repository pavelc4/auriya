use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Settings {
    #[serde(default)]
    pub daemon: DaemonConfig,
    #[serde(default)]
    pub cpu: CpuConfig,
    #[serde(default)]
    pub dnd: DndConfig,
    #[serde(default)]
    pub fas: FasConfig,
    #[serde(default)]
    pub dynamic_governor: DynamicGovernorConfig,
    #[serde(default)]
    pub ceiling: CeilingConfig,
    #[serde(default)]
    pub thermal: ThermalConfig,
    #[serde(default)]
    pub window: WindowConfig,
    #[serde(default)]
    pub walt: WaltConfig,
    #[serde(default = "default_modes")]
    pub modes: HashMap<String, FasMode>,
}

fn default_modes() -> HashMap<String, FasMode> {
    let mut m = HashMap::new();
    m.insert(
        "powersave".into(),
        FasMode {
            margin: 5.0,
            thermal_threshold: 80.0,
        },
    );
    m.insert(
        "balance".into(),
        FasMode {
            margin: 2.0,
            thermal_threshold: 90.0,
        },
    );
    m.insert(
        "performance".into(),
        FasMode {
            margin: 1.0,
            thermal_threshold: 95.0,
        },
    );
    m.insert(
        "fast".into(),
        FasMode {
            margin: 0.0,
            thermal_threshold: 95.0,
        },
    );
    m
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            daemon: DaemonConfig::default(),
            cpu: CpuConfig::default(),
            dnd: DndConfig::default(),
            fas: FasConfig::default(),
            dynamic_governor: DynamicGovernorConfig::default(),
            ceiling: CeilingConfig::default(),
            thermal: ThermalConfig::default(),
            window: WindowConfig::default(),
            walt: WaltConfig::default(),
            modes: default_modes(),
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct DaemonConfig {
    #[serde(default = "default_log_level")]
    pub log_level: String,

    #[serde(default = "default_check_interval")]
    pub check_interval_ms: u64,

    #[serde(default = "default_mode")]
    pub default_mode: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct CpuConfig {
    pub default_governor: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DndConfig {
    pub default_enable: bool,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct FasConfig {
    pub enabled: bool,
    pub default_mode: String,
    pub thermal_threshold: f64,
    #[serde(default = "default_poll_interval")]
    pub poll_interval_ms: u64,
    #[serde(default = "default_target_fps")]
    pub target_fps: u32,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct FasMode {
    pub margin: f64,
    pub thermal_threshold: f64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DynamicGovernorConfig {
    #[serde(default = "default_dg_enabled")]
    pub enabled: bool,
    #[serde(default = "default_dg_cv_threshold")]
    pub cv_threshold: f64,
    #[serde(default = "default_dg_debounce")]
    pub debounce_frames: u32,
}

impl Default for DynamicGovernorConfig {
    fn default() -> Self {
        Self {
            enabled: default_dg_enabled(),
            cv_threshold: default_dg_cv_threshold(),
            debounce_frames: default_dg_debounce(),
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct CeilingConfig {
    #[serde(default = "default_ceiling_level")]
    pub default: String,
    pub low_freq_little_khz: Option<u64>,
    pub low_freq_big_khz: Option<u64>,
}

impl Default for CeilingConfig {
    fn default() -> Self {
        Self {
            default: default_ceiling_level(),
            low_freq_little_khz: None,
            low_freq_big_khz: None,
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ThermalConfig {
    #[serde(default = "default_thermal_enabled")]
    pub enabled: bool,
    /// Raw `sconfig` value to restore after a game exits. Overrides the
    /// auto-captured stock value and is hot-reloaded from `settings.toml`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub default: Option<i32>,
}

impl Default for ThermalConfig {
    fn default() -> Self {
        Self {
            enabled: default_thermal_enabled(),
            default: None,
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WindowConfig {
    #[serde(default = "default_auto_rotation")]
    pub auto_rotation: bool,
}

impl Default for WindowConfig {
    fn default() -> Self {
        Self {
            auto_rotation: default_auto_rotation(),
        }
    }
}

fn default_auto_rotation() -> bool {
    false
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct WaltConfig {
    #[serde(default = "default_walt_enabled")]
    pub enabled: bool,
    #[serde(default = "default_walt_auto_tune")]
    pub auto_tune: bool,
}

fn default_walt_enabled() -> bool {
    true
}

fn default_walt_auto_tune() -> bool {
    true
}

impl Default for WaltConfig {
    fn default() -> Self {
        Self {
            enabled: default_walt_enabled(),
            auto_tune: default_walt_auto_tune(),
        }
    }
}

impl Default for CpuConfig {
    fn default() -> Self {
        Self {
            default_governor: "schedutil".to_string(),
        }
    }
}

impl Default for DndConfig {
    fn default() -> Self {
        Self {
            default_enable: true,
        }
    }
}

impl Default for FasConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            default_mode: "balance".to_string(),
            thermal_threshold: 90.0,
            poll_interval_ms: default_poll_interval(),
            target_fps: default_target_fps(),
        }
    }
}

impl Settings {
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let path = path.as_ref();

        if !path.exists() {
            let default_settings = Self::default();
            if let Some(parent) = path.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            if let Ok(serialized) = toml::to_string_pretty(&default_settings) {
                let _ = std::fs::write(path, serialized);
            }
            return Ok(default_settings);
        }

        let content =
            std::fs::read_to_string(path).context(format!("Failed to read {}", path.display()))?;

        match toml::from_str(&content) {
            Ok(s) => Ok(s),
            Err(e) => {
                tracing::error!(
                    "Failed to parse {}: {e}. Recovering with default settings.",
                    path.display()
                );
                Ok(Self::default())
            }
        }
    }
}

fn default_log_level() -> String {
    "info".to_string()
}

fn default_check_interval() -> u64 {
    2000
}

fn default_poll_interval() -> u64 {
    100
}

fn default_target_fps() -> u32 {
    60
}

fn default_mode() -> String {
    "balance".to_string()
}

fn default_dg_enabled() -> bool {
    true
}

fn default_dg_cv_threshold() -> f64 {
    0.22
}

fn default_dg_debounce() -> u32 {
    3
}

fn default_ceiling_level() -> String {
    "balance".to_string()
}

fn default_thermal_enabled() -> bool {
    true
}
