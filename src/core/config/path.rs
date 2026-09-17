pub use super::gamelist::*;
pub use super::settings::*;
use std::path::PathBuf;

pub const CONFIG_DIR: &str = "/data/adb/.config/auriya";

pub fn settings_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("settings.toml")
}

pub fn gamelist_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("gamelist.toml")
}

/// Persisted stock thermal preset (the value the vendor shipped, captured
/// once at first daemon start). Restored when leaving a game session.
pub fn thermal_default_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("thermal_default")
}

pub fn load_all() -> anyhow::Result<(crate::core::config::Settings, crate::core::config::GameList)>
{
    let settings = crate::core::config::Settings::load(settings_path())?;
    let gamelist = crate::core::config::GameList::load(gamelist_path())?;
    Ok((settings, gamelist))
}
