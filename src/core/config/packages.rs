use serde::{Deserialize, Serialize};
use std::path::Path;
use anyhow::{Context, Result};

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct PackageList {
    #[serde(default)]
    pub settings: Settings,
    #[serde(default)]
    pub packages: LegacyPackages,
    #[serde(default)]
    pub games: Vec<GameConfig>,
    #[serde(default)]
    pub modes: FasModes,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct Settings {
    #[serde(default)]
    pub default_governor: Option<String>,
    #[serde(default = "default_enable_dnd")]
    pub default_enable_dnd: bool,
    #[serde(default)]
    pub fas_enabled: bool,
    #[serde(default = "default_fas_mode")]
    pub fas_mode: String,
    #[serde(default = "default_thermal")]
    pub fas_thermal_threshold: f32,
    #[serde(default = "default_poll_interval")]
    pub fas_poll_interval_ms: u64,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct LegacyPackages {
    #[serde(default)]
    pub list: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GameConfig {
    pub package: String,

    #[serde(default)]
    pub cpu_governor: Option<String>,

    #[serde(default)]
    pub enable_dnd: Option<bool>,

    #[serde(default)]
    pub profile: Option<String>,
    #[serde(default)]
    pub fas_override_margin: Option<f32>,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct FasModes {
    #[serde(default)]
    pub powersave: FasModeConfig,
    #[serde(default)]
    pub balance: FasModeConfig,
    #[serde(default)]
    pub performance: FasModeConfig,
    #[serde(default)]
    pub fast: FasModeConfig,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct FasModeConfig {
    #[serde(default = "default_margin")]
    pub margin: f32,
    #[serde(default = "default_thermal")]
    pub thermal_threshold: f32,
}

impl Default for FasModeConfig {
    fn default() -> Self {
        Self {
            margin: 2.0,
            thermal_threshold: 90.0,
        }
    }
}

fn default_enable_dnd() -> bool { true }
fn default_fas_mode() -> String { "balance".into() }
fn default_thermal() -> f32 { 90.0 }
fn default_poll_interval() -> u64 { 300 }
fn default_margin() -> f32 { 2.0 }

impl PackageList {
    pub fn load_from_toml<P: AsRef<Path>>(path: P) -> Result<Self> {
        let content = std::fs::read_to_string(&path)
            .with_context(|| format!("Failed to read {:?}", path.as_ref()))?;

        let mut list: PackageList = toml::from_str(&content)
            .with_context(|| format!("Failed to parse TOML from {:?}", path.as_ref()))?;

        if !list.packages.list.is_empty() && list.games.is_empty() {
            list.games = list.packages.list
                .iter()
                .map(|pkg| GameConfig {
                    package: pkg.clone(),
                    cpu_governor: None,
                    enable_dnd: None,
                    profile: None,
                    fas_override_margin: None,
                })
                .collect();
        }

        Ok(list)
    }

    pub fn get_packages(&self) -> Vec<String> {
        self.games.iter().map(|g| g.package.clone()).collect()
    }

    pub fn get_game_config(&self, package: &str) -> Option<&GameConfig> {
        self.games.iter().find(|g| g.package == package)
    }

    pub fn get_governor(&self, package: &str) -> String {
        self.get_game_config(package)
            .and_then(|g| g.cpu_governor.clone())
            .or_else(|| self.settings.default_governor.clone())
            .unwrap_or_else(|| "schedutil".to_string())
    }

    pub fn should_enable_dnd(&self, package: &str) -> bool {
        self.get_game_config(package)
            .and_then(|g| g.enable_dnd)
            .unwrap_or(self.settings.default_enable_dnd)
    }

    pub fn get_fas_margin(&self, package: Option<&str>) -> f32 {
        if let Some(pkg) = package {
            if let Some(game) = self.get_game_config(pkg) {
                if let Some(margin) = game.fas_override_margin {
                    return margin;
                }
            }
        }

        match self.settings.fas_mode.as_str() {
            "powersave" => self.modes.powersave.margin,
            "balance" => self.modes.balance.margin,
            "performance" => self.modes.performance.margin,
            "fast" => self.modes.fast.margin,
            _ => 2.0,
        }
    }

    pub fn get_fas_thermal(&self) -> f32 {
        match self.settings.fas_mode.as_str() {
            "powersave" => self.modes.powersave.thermal_threshold,
            "balance" => self.modes.balance.thermal_threshold,
            "performance" => self.modes.performance.thermal_threshold,
            "fast" => self.modes.fast.thermal_threshold,
            _ => self.settings.fas_thermal_threshold,
        }
    }
}
