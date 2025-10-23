use serde::Deserialize;
use std::path::Path;

#[derive(Debug, Deserialize)]
pub struct PackageList {
    pub packages: Vec<String>,
}

impl PackageList {
    pub fn load_from_toml(path: &Path) -> anyhow::Result<Self> {
        let content = std::fs::read_to_string(path)?;
        let v: PackageList = toml::from_str(&content)?;
        Ok(v)
    }
}
