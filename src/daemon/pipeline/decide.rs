use super::{PipelineDecision, PipelineInput};
use crate::core::config::gamelist::GameList;
use crate::daemon::engine::Daemon;

impl Daemon {
    pub fn decide<'a>(
        &self,
        input: &'a PipelineInput,
        _gamelist: &GameList,
    ) -> PipelineDecision<'a> {
        match (input.power.screen_awake, input.power.battery_saver) {
            (false, _) | (_, true) => return PipelineDecision::Powersave,
            _ => {}
        }

        let Some(ref pkg) = input.foreground_pkg else {
            return PipelineDecision::NoForeground;
        };

        let is_same_pkg = self.last.pkg.as_deref() == Some(pkg.as_str());
        let is_whitelisted = self.cached_whitelist.contains(pkg);

        match (is_same_pkg, input.is_pid_valid, is_whitelisted) {
            (true, true, _) => {
                let fas_enabled = self
                    ._shared_settings
                    .read()
                    .map(|s| s.fas.enabled)
                    .unwrap_or(true);

                match fas_enabled && self.fas_controller.is_some() {
                    true => PipelineDecision::SameAppFas {
                        pkg: pkg.as_str(),
                        pids: &input.package_pids,
                    },
                    false => PipelineDecision::SameAppMonitor { pkg: pkg.as_str() },
                }
            }
            (false, _, false) => PipelineDecision::NonWhitelisted {
                pkg: pkg.as_str(),
                pid: input.focused_pid,
                pids: &input.package_pids,
                reason: "not whitelisted",
            },
            (false, _, true) => match input.focused_pid {
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
            },
            (true, false, false) => PipelineDecision::NonWhitelisted {
                pkg: pkg.as_str(),
                pid: input.focused_pid,
                pids: &input.package_pids,
                reason: "process dead",
            },
            (true, false, true) => match input.focused_pid {
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
            },
        }
    }
}
