use serde::Deserialize;
use std::path::Path;
use anyhow::{Context, Result};

#[derive(Debug, Clone, Deserialize)]
pub struct PackageList {
    #[serde(default)]
    pub settings: Settings,
    #[serde(default)]
    pub packages: LegacyPackages,
    #[serde(default)]
    pub games: Vec<GameConfig>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Settings {
    #[serde(default)]
    pub default_governor: Option<String>,
    #[serde(default = "default_enable_dnd")]
    pub default_enable_dnd: bool,
}

fn default_enable_dnd() -> bool {
    true
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct LegacyPackages {
    #[serde(default)]
    pub list: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GameConfig {
    pub package: String,

    #[serde(default)]
    pub cpu_governor: Option<String>,

    #[serde(default)]
    pub enable_dnd: Option<bool>,

    #[serde(default)]
    pub profile: Option<String>,
}

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
}
