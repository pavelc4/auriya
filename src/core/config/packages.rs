use serde::Deserialize;
use std::path::Path;
use anyhow::{Context, Result};

#[derive(Debug, Clone, Deserialize)]
pub struct PackageList {
    #[serde(default)]
    pub settings: Settings,
    #[serde(default, deserialize_with = "deserialize_packages")]
    pub packages: Vec<String>,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct Settings {
    #[serde(default)]
    pub default_governor: Option<String>,
}

impl PackageList {
    pub fn load_from_toml<P: AsRef<Path>>(path: P) -> Result<Self> {
        let content = std::fs::read_to_string(&path)
            .with_context(|| format!("Failed to read {:?}", path.as_ref()))?;

        let list: PackageList = toml::from_str(&content)
            .with_context(|| format!("Failed to parse TOML from {:?}", path.as_ref()))?;

        Ok(list)
    }
}

fn deserialize_packages<'de, D>(deserializer: D) -> Result<Vec<String>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum PackagesFormat {
        List(Vec<String>),
        Table { list: Vec<String> },
    }

    let value = Option::<PackagesFormat>::deserialize(deserializer)?;
    Ok(match value {
        Some(PackagesFormat::List(list)) => list,
        Some(PackagesFormat::Table { list }) => list,
        None => Vec::new(),
    })
}
