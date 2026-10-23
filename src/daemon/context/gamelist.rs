use super::DaemonContext;
use crate::core::config::gamelist::{GameList, GameProfile, GameProfileUpdate};
use anyhow::{Result, anyhow};
use std::sync::Arc;

impl DaemonContext {
    /// Reload gamelist from disk into shared config.
    pub fn reload_gamelist(&self) -> Result<usize> {
        let new_cfg = GameList::load(crate::core::config::gamelist_path())?;
        let mut g = self
            .shared_config
            .write()
            .map_err(|_| anyhow!("Gamelist lock poisoned"))?;
        let count = new_cfg.game.len();
        *g = Arc::new(new_cfg);
        Ok(count)
    }

    /// Process reload command: reloads gamelist and balance governor.
    pub fn reload(&self) -> String {
        let gl_result = self.reload_gamelist();
        if let (Ok(new_settings), Ok(mut g)) = (
            crate::core::config::Settings::load(crate::core::config::settings_path()),
            self.balance_governor.write(),
        ) {
            *g = new_settings.cpu.default_governor;
        }
        match gl_result {
            Ok(n) => format!("OK RELOADED {n}\n"),
            Err(e) => format!("ERR RELOAD {e:?}\n"),
        }
    }

    /// Add a new game package to gamelist.
    pub fn add_game(&self, pkg: &str) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let profile = GameProfile {
            package: pkg.to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: self.dnd_default,
            target_fps: None,
            refresh_rate: None,
            mode: Some("performance".to_string()),
            ceiling: None,
            thermal_preset: None,
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.add(profile) {
            return format!("ERR ADD_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK ADD_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Remove a game package from gamelist.
    pub fn remove_game(&self, pkg: &str) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.remove(pkg) {
            return format!("ERR REMOVE_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK REMOVE_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Update game package settings in gamelist.
    pub fn update_game(&self, pkg: &str, upd: GameProfileUpdate) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.update(pkg, upd) {
            return format!("ERR UPDATE_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK UPDATE_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Get current gamelist serialized as JSON.
    pub fn get_gamelist(&self) -> String {
        let Ok(gl) = self.shared_config.read() else {
            return "ERR lock poisoned\n".into();
        };
        serde_json::to_string(&gl.game)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR GET_GAMELIST {e:?}\n"))
    }
}
