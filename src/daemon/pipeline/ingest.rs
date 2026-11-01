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
        let mut package_pids = Vec::new();
        let mut is_floating = false;
        if pkg_opt.is_none() {
            #[cfg(target_os = "android")]
            if let Some(task) = crate::platform::common::procfs::query_current_foreground_task() {
                self.status_cache.store(crate::core::status::SystemStatus {
                    focused_app: Some(task.package.clone()),
                    focused_pid: task.pid,
                    focused_uid: None,
                    screen_awake: Some(true),
                    battery_saver: None,
                    zen_mode: None,
                    is_floating: Some(task.is_floating),
                });
                pkg_opt = Some(task.package.clone());
                focused_pid = task.pid;
                is_floating = task.is_floating;
                package_pids =
                    crate::platform::common::procfs::find_all_pids_by_package(&task.package);
                if let Some(&first) = package_pids.first() {
                    focused_pid = Some(first);
                }
            }
        } else {
            is_floating = self.status_cache.is_floating();
            let pkg = pkg_opt.as_deref().unwrap_or("");

            #[cfg(target_os = "android")]
            {
                match self.attached_ebpf_pids.as_slice() {
                    [first, ..]
                        if !pkg.is_empty()
                            && self.attached_ebpf_pids.iter().all(|&p| {
                                crate::platform::common::procfs::is_pid_valid(p)
                                    && crate::platform::common::procfs::verify_pid_package(p, pkg)
                            }) =>
                    {
                        package_pids.extend_from_slice(&self.attached_ebpf_pids);
                        focused_pid = Some(*first);
                    }
                    _ => {
                        package_pids =
                            crate::platform::common::procfs::find_all_pids_by_package(pkg);
                        focused_pid = package_pids.first().copied();
                    }
                }
            }

            // Fall back to cached PID if still valid and alive.
            if focused_pid.is_none() {
                focused_pid = self.status_cache.focused_pid().filter(|&p| {
                    crate::platform::common::procfs::is_pid_valid(p)
                        && crate::platform::common::procfs::verify_pid_package(p, pkg)
                });
                if let Some(p) = focused_pid {
                    package_pids.push(p);
                }
            }
        }

        let is_pid_valid = self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

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
