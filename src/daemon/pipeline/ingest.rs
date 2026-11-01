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

        let mut pkg_opt: Option<String> = self
            .override_foreground
            .read()
            .ok()
            .and_then(|o| o.as_deref().map(String::from));

        if pkg_opt.is_none() {
            pkg_opt = self.status_cache.focused_package();
        }

        let mut focused_pid = None;
        let mut package_pids = Vec::new();
        let mut is_floating = false;
        if pkg_opt.is_none() {
            #[cfg(target_os = "android")]
            if let Some(task) = crate::platform::common::procfs::query_current_foreground_task() {
                package_pids =
                    crate::platform::common::procfs::find_all_pids_by_package(&task.package);
                if let Some(&first) = package_pids.first() {
                    focused_pid = Some(first);
                } else {
                    focused_pid = task.pid;
                }
                is_floating = task.is_floating;

                let pkg_name = task.package;
                self.status_cache.store(crate::core::status::SystemStatus {
                    focused_app: Some(pkg_name.as_str().into()),
                    focused_pid: task.pid,
                    focused_uid: None,
                    screen_awake: Some(true),
                    battery_saver: None,
                    zen_mode: None,
                    is_floating: Some(task.is_floating),
                });
                pkg_opt = Some(pkg_name);
            }
        } else {
            is_floating = self.status_cache.is_floating();
            let pkg = pkg_opt.as_deref().unwrap_or("");

            #[cfg(target_os = "android")]
            {
                package_pids = crate::platform::common::procfs::find_all_pids_by_package(pkg);
                focused_pid = package_pids.first().copied();
            }

            // Fall back to cached PID if still valid and alive, or merge if not yet present.
            match (focused_pid, self.status_cache.focused_pid()) {
                (None, Some(cached))
                    if crate::platform::common::procfs::is_pid_valid(cached)
                        && crate::platform::common::procfs::verify_pid_package(cached, pkg) =>
                {
                    focused_pid = Some(cached);
                    package_pids.push(cached);
                }
                (Some(_), Some(cached))
                    if !package_pids.contains(&cached)
                        && crate::platform::common::procfs::is_pid_valid(cached)
                        && crate::platform::common::procfs::verify_pid_package(cached, pkg) =>
                {
                    package_pids.push(cached);
                }
                _ => {}
            }
        }

        let is_pid_valid =
            !package_pids.is_empty() || self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        PipelineInput {
            power,
            foreground_pkg: pkg_opt,
            focused_pid,
            package_pids,
            is_pid_valid,
            is_floating,
        }
    }
}
