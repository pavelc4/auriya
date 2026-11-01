use super::{PipelineDecision, PipelineInput};
use crate::core::config::gamelist::GameList;
use crate::daemon::engine::Daemon;

impl Daemon {
    pub fn decide<'a>(
        &self,
        input: &'a PipelineInput,
        _gamelist: &GameList,
    ) -> PipelineDecision<'a> {
        if !input.power.screen_awake || input.power.battery_saver {
            return PipelineDecision::Powersave;
        }

        let Some(ref pkg) = input.foreground_pkg else {
            return PipelineDecision::NoForeground;
        };

        let pid_changed = input.focused_pid.is_some() && input.focused_pid != self.last.pid;
        if self.last.pkg.as_deref() == Some(pkg.as_str()) && input.is_pid_valid && !pid_changed {
            let fas_enabled = self
                ._shared_settings
                .read()
                .map(|s| s.fas.enabled)
                .unwrap_or(true);

            if fas_enabled && self.fas_controller.is_some() {
                return PipelineDecision::SameAppFas {
                    pkg: pkg.as_str(),
                    pids: &input.package_pids,
                };
            }
            return PipelineDecision::SameAppMonitor { pkg: pkg.as_str() };
        }

        if !self.cached_whitelist.contains(pkg) {
            return PipelineDecision::NonWhitelisted {
                pkg: pkg.as_str(),
                pid: input.focused_pid,
                pids: &input.package_pids,
                reason: "not whitelisted",
            };
        }

        match input.focused_pid {
            Some(pid) => PipelineDecision::EnterGame {
                pkg: pkg.as_str(),
                pid,
                pids: &input.package_pids,
            },
            None => PipelineDecision::NonWhitelisted {
                pkg: pkg.as_str(),
                pid: None,
                pids: &input.package_pids,
                reason: "PID not found",
            },
        }
    }
}
