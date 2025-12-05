use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GameList {
    #[serde(default)]
    pub game: Vec<GameProfile>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GameProfile {
    pub package: String,
    pub cpu_governor: String,
    pub enable_dnd: bool,
    #[serde(default)]
    pub target_fps: Option<u32>,
}

impl GameList {
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let path = path.as_ref();

        if !path.exists() {
            tracing::warn!("Gamelist file not found, using empty list");
            return Ok(Self { game: vec![] });
        }

        let content =
            std::fs::read_to_string(path).context(format!("Failed to read {}", path.display()))?;

        toml::from_str(&content).context("Failed to parse gamelist.toml")
    }
    #[allow(dead_code)]
    pub fn save<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let path = path.as_ref();

        let toml_string = toml::to_string_pretty(self).context("Failed to serialize gamelist")?;

        let temp_path = path.with_extension("toml.tmp");
        std::fs::write(&temp_path, toml_string).context("Failed to write temporary file")?;

        std::fs::rename(&temp_path, path).context("Failed to rename to final file")?;

        tracing::info!("Gamelist saved to {}", path.display());
        Ok(())
    }

    pub fn find(&self, package: &str) -> Option<&GameProfile> {
        self.game.iter().find(|g| g.package == package)
    }

    #[allow(dead_code)]
    pub fn add(&mut self, profile: GameProfile) -> Result<()> {
        if self.find(&profile.package).is_some() {
            anyhow::bail!("Game {} already exists", profile.package);
        }

        self.game.push(profile);
        Ok(())
    }

    #[allow(dead_code)]
    pub fn remove(&mut self, package: &str) -> Result<()> {
        let initial_len = self.game.len();
        self.game.retain(|g| g.package != package);

        if self.game.len() == initial_len {
            anyhow::bail!("Game {} not found", package);
        }

        Ok(())
    }

    #[allow(dead_code)]
    pub fn update(
        &mut self,
        package: &str,
        governor: Option<String>,
        dnd: Option<bool>,
        target_fps: Option<u32>,
    ) -> Result<()> {
        if let Some(profile) = self.game.iter_mut().find(|g| g.package == package) {
            if let Some(gov) = governor {
                profile.cpu_governor = gov;
            }
            if let Some(d) = dnd {
                profile.enable_dnd = d;
            }
            if target_fps.is_some() {
                profile.target_fps = target_fps;
            }
            Ok(())
        } else {
            anyhow::bail!("Game {} not found", package)
        }
    }
}
