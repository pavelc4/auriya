use std::path::PathBuf;
pub use super::settings::*;
pub use super::gamelist::*;

pub const CONFIG_DIR: &str = "/data/adb/.config/auriya";

pub fn settings_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("settings.toml")
}

pub fn gamelist_path() -> PathBuf {
    PathBuf::from(CONFIG_DIR).join("gamelist.toml")
}

pub fn load_all() -> anyhow::Result<(crate::core::config::Settings, crate::core::config::GameList)> {
    let settings = crate::core::config::Settings::load(settings_path())?;
    let gamelist = crate::core::config::GameList::load(gamelist_path())?;
    Ok((settings, gamelist))
}
