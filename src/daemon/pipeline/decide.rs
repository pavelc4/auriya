use super::{PipelineDecision, PipelineInput};
use crate::core::config::gamelist::GameList;
use crate::core::policy::{PolicyContext, PolicyEngine, SystemSnapshot};
use crate::daemon::engine::Daemon;

impl Daemon {
    pub fn decide<'a>(
        &self,
        input: &'a PipelineInput,
        _gamelist: &GameList,
    ) -> PipelineDecision<'a> {
        let snapshot = SystemSnapshot {
            power: input.power,
            foreground_pkg: input.foreground_pkg.as_deref(),
            focused_pid: input.focused_pid,
            package_pids: &input.package_pids,
            is_pid_valid: input.is_pid_valid,
            is_floating: input.is_floating,
        };

        let is_whitelisted = match input.foreground_pkg.as_deref() {
            Some(pkg) => self.cached_whitelist.contains(pkg),
            None => false,
        };

        let fas_enabled = self
            ._shared_settings
            .read()
            .map(|s| s.fas.enabled)
            .unwrap_or(true);

        let ctx = PolicyContext {
            last_pkg: self.last.pkg.as_deref(),
            is_whitelisted,
            fas_enabled,
            fas_available: self.fas_controller.is_some(),
        };

        PolicyEngine::evaluate(&snapshot, &ctx)
    }
}
