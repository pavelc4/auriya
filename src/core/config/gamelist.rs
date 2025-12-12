use anyhow::{Context, Result};
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use std::path::Path;
#[derive(Debug, Clone)]
pub enum TargetFpsConfig {
    Single(u32),
    Array(Vec<u32>),
}

impl Default for TargetFpsConfig {
    fn default() -> Self {
        Self::Single(60)
    }
}

impl<'de> Deserialize<'de> for TargetFpsConfig {
    fn deserialize<D>(deserializer: D) -> std::result::Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        use serde::de::{self, Visitor};

        struct TargetFpsVisitor;

        impl<'de> Visitor<'de> for TargetFpsVisitor {
            type Value = TargetFpsConfig;

            fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
                formatter.write_str("a single FPS value or an array of FPS values")
            }

            fn visit_u64<E>(self, v: u64) -> std::result::Result<Self::Value, E>
            where
                E: de::Error,
            {
                Ok(TargetFpsConfig::Single(v as u32))
            }

            fn visit_i64<E>(self, v: i64) -> std::result::Result<Self::Value, E>
            where
                E: de::Error,
            {
                Ok(TargetFpsConfig::Single(v as u32))
            }

            fn visit_seq<A>(self, mut seq: A) -> std::result::Result<Self::Value, A::Error>
            where
                A: de::SeqAccess<'de>,
            {
                let mut values = Vec::new();
                while let Some(v) = seq.next_element::<u32>()? {
                    values.push(v);
                }
                Ok(TargetFpsConfig::Array(values))
            }
        }

        deserializer.deserialize_any(TargetFpsVisitor)
    }
}

impl Serialize for TargetFpsConfig {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match self {
            Self::Single(v) => serializer.serialize_u32(*v),
            Self::Array(arr) => arr.serialize(serializer),
        }
    }
}

impl TargetFpsConfig {
    pub fn to_buffer_config(&self) -> crate::core::fas::buffer::TargetFps {
        match self {
            Self::Single(v) => crate::core::fas::buffer::TargetFps::Single(*v),
            Self::Array(arr) => crate::core::fas::buffer::TargetFps::Array(arr.clone()),
        }
    }
}

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
    pub target_fps: Option<TargetFpsConfig>,
    #[serde(default)]
    pub refresh_rate: Option<u32>,
    #[serde(default)]
    pub mode: Option<String>,
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
    pub fn save<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let path = path.as_ref();

        let toml_string = toml::to_string(self).context("Failed to serialize gamelist")?;

        let temp_path = path.with_extension("toml.tmp");
        std::fs::write(&temp_path, toml_string).context("Failed to write temporary file")?;

        std::fs::rename(&temp_path, path).context("Failed to rename to final file")?;

        tracing::info!("Gamelist saved to {}", path.display());
        Ok(())
    }

    pub fn find(&self, package: &str) -> Option<&GameProfile> {
        self.game.iter().find(|g| g.package == package)
    }

    pub fn add(&mut self, profile: GameProfile) -> Result<()> {
        if self.find(&profile.package).is_some() {
            anyhow::bail!("Game {} already exists", profile.package);
        }

        self.game.push(profile);
        Ok(())
    }

    pub fn remove(&mut self, package: &str) -> Result<()> {
        let initial_len = self.game.len();
        self.game.retain(|g| g.package != package);

        if self.game.len() == initial_len {
            anyhow::bail!("Game {} not found", package);
        }
        Ok(())
    }

    pub fn update_with_array(
        &mut self,
        package: &str,
        governor: Option<String>,
        dnd: Option<bool>,
        target_fps: Option<u32>,
        refresh_rate: Option<u32>,
        mode: Option<String>,
        fps_array: Option<Vec<u32>>,
    ) -> Result<()> {
        if let Some(profile) = self.game.iter_mut().find(|g| g.package == package) {
            if let Some(gov) = governor {
                profile.cpu_governor = gov;
            }
            if let Some(d) = dnd {
                profile.enable_dnd = d;
            }
            if let Some(arr) = fps_array {
                profile.target_fps = Some(TargetFpsConfig::Array(arr));
            } else if let Some(fps) = target_fps {
                profile.target_fps = Some(TargetFpsConfig::Single(fps));
            }
            if refresh_rate.is_some() {
                profile.refresh_rate = refresh_rate;
            }
            if let Some(m) = mode {
                profile.mode = Some(m);
            }
            Ok(())
        } else {
            anyhow::bail!("Game {} not found", package)
        }
    }
}
