use super::{PipelineDecision, PipelineInput};
use crate::core::config::gamelist::GameList;
use crate::daemon::engine::Daemon;

impl Daemon {
    /// Decide phase: deterministic mapping from ingested input to policy decision.
    pub fn decide(&self, input: &PipelineInput, gamelist: &GameList) -> PipelineDecision {
        if !input.power.screen_awake || input.power.battery_saver {
            return PipelineDecision::Powersave;
        }

        let Some(ref pkg) = input.foreground_pkg else {
            return PipelineDecision::NoForeground;
        };

        if self.last.pkg.as_deref() == Some(pkg.as_str()) && input.is_pid_valid {
            let fas_enabled = self
                ._shared_settings
                .read()
                .map(|s| s.fas.enabled)
                .unwrap_or(true);

            if fas_enabled
                && self.fas_controller.is_some()
                && gamelist.game.iter().any(|a| a.package == *pkg)
            {
                return PipelineDecision::SameGameFas { pkg: pkg.clone() };
            }
            return PipelineDecision::SameGameNoop;
        }

        if !self.cached_whitelist.contains(pkg) {
            return PipelineDecision::NonWhitelisted {
                pkg: pkg.clone(),
                reason: "not whitelisted",
            };
        }

        match input.focused_pid {
            Some(pid) => PipelineDecision::EnterGame {
                pkg: pkg.clone(),
                pid,
            },
            None => PipelineDecision::NonWhitelisted {
                pkg: pkg.clone(),
                reason: "PID not found",
            },
        }
    }
}
