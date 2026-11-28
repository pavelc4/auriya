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

        let override_pkg = self
            .override_foreground
            .read()
            .ok()
            .and_then(|o| o.as_deref().map(String::from));

        let (initial_pkg, initial_source) = match override_pkg {
            Some(pkg) => (Some(pkg), "override"),
            None => match self.status_cache.focused_package() {
                Some(pkg) => (Some(pkg), "status_cache"),
                None => (None, "none"),
            },
        };

        let (pkg_opt, source, focused_pid, package_pids, is_floating) = match initial_pkg {
            None => {
                #[cfg(target_os = "android")]
                match crate::platform::common::procfs::query_current_foreground_task() {
                    Some(task) => {
                        let pids = crate::platform::common::procfs::find_all_pids_by_package(
                            &task.package,
                        );
                        let pid = pids.first().copied().or(task.pid);
                        let floating = task.is_floating;
                        let pkg_name = task.package;
                        self.status_cache.store(crate::core::status::SystemStatus {
                            focused_app: Some(pkg_name.as_str().into()),
                            focused_pid: task.pid,
                            focused_uid: None,
                            screen_awake: Some(true),
                            battery_saver: None,
                            zen_mode: None,
                            is_floating: Some(floating),
                        });
                        (Some(pkg_name), "foreground_query", pid, pids, floating)
                    }
                    None => (None, initial_source, None, Vec::new(), false),
                }
                #[cfg(not(target_os = "android"))]
                {
                    (None, initial_source, None, Vec::new(), false)
                }
            }
            Some(pkg) => {
                let floating = self.status_cache.is_floating();

                #[cfg(target_os = "android")]
                let (mut pids, mut pid) = {
                    let pids = crate::platform::common::procfs::find_all_pids_by_package(&pkg);
                    let pid = pids.first().copied();
                    (pids, pid)
                };
                #[cfg(not(target_os = "android"))]
                let (mut pids, mut pid): (Vec<i32>, Option<i32>) = (Vec::new(), None);

                // Fall back to cached PID if still valid and alive, or merge if not yet present.
                match (pid, self.status_cache.focused_pid()) {
                    (None, Some(cached))
                        if crate::platform::common::procfs::is_pid_valid(cached)
                            && crate::platform::common::procfs::verify_pid_package(
                                cached, &pkg,
                            ) =>
                    {
                        pid = Some(cached);
                        pids.push(cached);
                    }
                    (Some(_), Some(cached))
                        if !pids.contains(&cached)
                            && crate::platform::common::procfs::is_pid_valid(cached)
                            && crate::platform::common::procfs::verify_pid_package(
                                cached, &pkg,
                            ) =>
                    {
                        pids.push(cached);
                    }
                    _ => {}
                }

                (Some(pkg), initial_source, pid, pids, floating)
            }
        };

        let is_pid_valid =
            !package_pids.is_empty() || self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        tracing::debug!(
            target: "auriya::pipeline",
            "Ingest: source={} pkg={:?} focused_pid={:?} pids={:?} pid_valid={} floating={} screen={} saver={}",
            source,
            pkg_opt,
            focused_pid,
            package_pids,
            is_pid_valid,
            is_floating,
            power.screen_awake,
            power.battery_saver,
        );

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
