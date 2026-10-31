use super::{PipelineInput, PowerSnapshot};
use crate::core::pid::PidTracker;
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

        let mut focused_pid = None;
        if pkg_opt.is_none() {
            #[cfg(target_os = "android")]
            if let Some((pkg, pid)) = crate::platform::common::procfs::query_current_foreground() {
                self.status_cache.store(crate::core::status::SystemStatus {
                    focused_app: Some(pkg.clone()),
                    focused_pid: pid,
                    focused_uid: None,
                    screen_awake: Some(true),
                    battery_saver: None,
                    zen_mode: None,
                });
                pkg_opt = Some(pkg);
                focused_pid = pid;
            }
        } else {
            focused_pid = self.status_cache.focused_pid().filter(|&p| {
                crate::platform::common::procfs::is_pid_valid(p)
                    && crate::platform::common::procfs::verify_pid_package(
                        p,
                        pkg_opt.as_ref().unwrap(),
                    )
            });
            if focused_pid.is_none() {
                #[cfg(target_os = "android")]
                if let Some(ref pkg) = pkg_opt {
                    focused_pid = crate::platform::common::procfs::find_pid_by_package(pkg);
                }
            }
        }

        let is_pid_valid = self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        PipelineInput {
            power,
            foreground_pkg: pkg_opt,
            focused_pid,
            is_pid_valid,
        }
    }
}
