use super::{PipelineInput, PowerSnapshot};
use crate::core::pid_tracker::PidTracker;
use crate::daemon::engine::Daemon;

impl Daemon {
    /// Ingest phase: gather current system power, focused app, and PID validity.
    pub fn ingest(&mut self) -> PipelineInput {
        let (screen_awake, battery_saver) = self.status_cache.power_state();
        let power = PowerSnapshot {
            screen_awake,
            battery_saver,
        };

        let mut pkg_opt: Option<String> =
            self.override_foreground.read().ok().and_then(|o| o.clone());

        if pkg_opt.is_none() {
            pkg_opt = self.status_cache.focused_package();
        }

        let is_pid_valid = self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        let focused_pid = if let Some(ref pkg) = pkg_opt {
            self.status_cache.focused_pid().filter(|&p| {
                crate::platform::common::procfs::is_pid_valid(p)
                    && crate::platform::common::procfs::verify_pid_package(p, pkg)
            })
        } else {
            None
        };

        PipelineInput {
            power,
            foreground_pkg: pkg_opt,
            focused_pid,
            is_pid_valid,
        }
    }
}
