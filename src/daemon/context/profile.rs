use super::DaemonContext;
use crate::core::profile::ProfileMode;
use crate::core::stats::FpsStats;
use anyhow::{Result, anyhow};
use std::collections::BTreeSet;

impl DaemonContext {
    /// Apply profile mode with concurrency protection.
    pub fn set_profile(&self, mode: ProfileMode) -> Result<String> {
        let _profile_guard = self
            .profile_lock
            .lock()
            .map_err(|_| anyhow!("profile lock poisoned"))?;
        let gov_guard = self.balance_governor.read();
        let balance_gov = gov_guard
            .as_deref()
            .map(|s| s.as_str())
            .unwrap_or("schedutil");

        let r = match mode {
            ProfileMode::Performance => crate::core::profile::apply_performance(),
            ProfileMode::Balance => crate::core::profile::apply_balance(balance_gov),
            ProfileMode::Powersave => crate::core::profile::apply_powersave(),
            ProfileMode::Fast => crate::core::profile::apply_fast(),
        };
        match r {
            Ok(_) => Ok(format!("OK SET_PROFILE {mode:?}\n")),
            Err(e) => Ok(format!("ERR SET_PROFILE {e:?}\n")),
        }
    }

    /// Set target FPS on FAS controller if active.
    pub async fn set_fps(&self, fps: u32) {
        if let Some(fas) = &self.fas_controller {
            let mut guard = fas.lock().await;
            guard.set_target_fps(fps);
        }
    }

    /// Get current target FPS from FAS controller (or 60 default).
    pub async fn get_fps(&self) -> u32 {
        if let Some(fas) = &self.fas_controller {
            let guard = fas.lock().await;
            return guard.get_target_fps();
        }
        60
    }

    /// Retrieve FPS window stats from FAS controller.
    pub async fn get_fps_stats(&self) -> Option<FpsStats> {
        match &self.fas_controller {
            Some(fas) => Some(fas.lock().await.fps_stats()),
            None => None,
        }
    }

    /// Get current measured and target FPS formatted response.
    pub async fn get_fps_response(&self) -> String {
        let target = self.get_fps().await;
        let is_gaming = self
            .current_state
            .read()
            .ok()
            .map(|s| s.game_session)
            .unwrap_or(false);
        let measured = if is_gaming {
            self.current_state.read().ok().and_then(|s| s.fps)
        } else {
            None
        };
        match measured {
            Some(m) => format!("FPS={m:.1} TARGET={target}\n"),
            None => format!("FPS=0 TARGET={target}\n"),
        }
    }

    /// Get display supported refresh rates formatted as JSON array.
    pub fn get_supported_rates(&self) -> String {
        let rates: Vec<u32> = self
            .supported_modes
            .iter()
            .map(|m| m.fps.round() as u32)
            .collect::<BTreeSet<_>>()
            .into_iter()
            .collect();

        serde_json::to_string(&rates)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR JSON {e:?}\n"))
    }
}
