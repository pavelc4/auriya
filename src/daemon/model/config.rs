use crate::core::config::{self, GameList, Settings};

#[derive(Debug, Clone)]
pub struct DaemonConfig {
    pub settings: Settings,
    pub gamelist: GameList,
    pub log_debounce_ms: u128,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        let settings = Settings::load(config::settings_path())
            .expect("Failed to load settings.toml - ensure config file exists");
        let gamelist = GameList::load(config::gamelist_path())
            .expect("Failed to load gamelist.toml - ensure config file exists");
        Self {
            settings,
            gamelist,
            log_debounce_ms: 2000,
        }
    }
}
