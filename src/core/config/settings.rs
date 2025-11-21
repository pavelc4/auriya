use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;


#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Settings {
    pub daemon: DaemonConfig,
    pub cpu: CpuConfig,
    pub dnd: DndConfig,
    pub fas: FasConfig,
    pub modes: HashMap<String, FasMode>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DaemonConfig {
    #[serde(default = "default_log_level")]
    pub log_level: String,

    #[serde(default = "default_check_interval")]
    pub check_interval_ms: u64,
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
    pub poll_interval_ms: u64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct FasMode {
    pub margin: f64,
    pub thermal_threshold: f64,
}

impl Settings {
    /// Load settings from TOML file
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let path = path.as_ref();

        let content = std::fs::read_to_string(path)
            .context(format!("Failed to read {}", path.display()))?;

        toml::from_str(&content)
            .context("Failed to parse settings.toml")
    }

    #[allow(dead_code)]
    pub fn get_fas_mode(&self, mode: &str) -> Option<&FasMode> {
        self.modes.get(mode)
    }
}

fn default_log_level() -> String {
    "info".to_string()
}

fn default_check_interval() -> u64 {
    2000
}
