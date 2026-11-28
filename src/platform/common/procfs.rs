// Lightweight /proc utilities for process validation.
//
// The companion service supplies focused PID via the status file; this
// module provides cheap liveness and package verification via /proc/<pid>/cmdline.

use memchr::memchr;
use std::fs;

/// True when `/proc/<pid>` exists. Used by the tick loop to drop
/// a stale PID reference cheaply.
#[inline]
pub fn is_pid_valid(pid: i32) -> bool {
    if pid <= 0 {
        return false;
    }
    std::path::Path::new(&format!("/proc/{pid}")).exists()
}

/// Best-effort: confirm that the running process at `pid` corresponds
/// to `package`. Reads `/proc/<pid>/cmdline` directly.
pub fn verify_pid_package(pid: i32, package: &str) -> bool {
    if pid <= 0 {
        return false;
    }
    let cmdline_path = format!("/proc/{pid}/cmdline");
    let Ok(cmdline) = fs::read(&cmdline_path) else {
        return false;
    };
    let end = memchr(b'\0', &cmdline)
        .or_else(|| memchr(b':', &cmdline))
        .unwrap_or(cmdline.len());
    let cmdline_pkg = &cmdline[..end];
    if cmdline_pkg == package.as_bytes() {
        return true;
    }
    // Some apps run isolated processes named "<pkg>:<suffix>" — accept
    // those too via substring match.
    String::from_utf8_lossy(&cmdline[..]).contains(package)
}

/// Extract package name from `/proc/<pid>/cmdline`.
pub fn get_process_package(pid: i32) -> Option<String> {
    if pid <= 0 {
        return None;
    }
    let cmdline = fs::read(format!("/proc/{pid}/cmdline")).ok()?;
    let end = memchr(b'\0', &cmdline)
        .or_else(|| memchr(b':', &cmdline))
        .unwrap_or(cmdline.len());
    let pkg = std::str::from_utf8(&cmdline[..end]).ok()?;
    if pkg.is_empty() {
        None
    } else {
        Some(pkg.to_string())
    }
}

/// Parse package name from dumpsys window displays output.
pub fn parse_focused_package_from_dumpsys(dumpsys_output: &str) -> Option<String> {
    for line in dumpsys_output.lines() {
        if let Some(idx) = line.find("mCurrentFocus=") {
            let focus_part = &line[idx + "mCurrentFocus=".len()..];
            if focus_part.starts_with("null") {
                return None;
            }
            if let Some(slash_idx) = focus_part.find('/') {
                let before_slash = &focus_part[..slash_idx];
                let pkg = before_slash.rsplit([' ', '{']).next()?.trim();
                if !pkg.is_empty() {
                    return Some(pkg.to_string());
                }
            }
        }
    }
    None
}

/// Extract cumulative CPU ticks (utime + stime) from `/proc/<pid>/stat`.
/// Used to isolate the active rendering engine thread/process from idle stubs.
pub fn get_pid_cpu_ticks(pid: i32) -> u64 {
    let Ok(stat) = fs::read_to_string(format!("/proc/{pid}/stat")) else {
        return 0;
    };
    let Some(idx) = stat.rfind(')') else {
        return 0;
    };
    let rest = &stat[idx + 1..];
    let mut parts = rest.split_whitespace();
    let utime: u64 = parts.nth(11).and_then(|s| s.parse().ok()).unwrap_or(0);
    let stime: u64 = parts.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    utime + stime
}

/// Find all candidate PIDs for a package without spawning subshells.
/// Inspects top-app cpuset and scans in-memory `/proc` to discover any spawned
/// rendering sub-processes (e.g. `:UnityKillsMe`).
/// Returns PIDs sorted by activity (CPU ticks descending).
pub fn find_all_pids_by_package(pkg: &str) -> Vec<i32> {
    if pkg.is_empty() {
        return Vec::new();
    }
    let mut candidates = Vec::with_capacity(4);

    // 1. Inspect Android top-app and foreground cpusets
    for cpuset_path in [
        "/dev/cpuset/top-app/cgroup.procs",
        "/sys/fs/cgroup/top-app/cgroup.procs",
        "/dev/cpuset/foreground/cgroup.procs",
        "/sys/fs/cgroup/foreground/cgroup.procs",
    ] {
        if let Ok(content) = fs::read_to_string(cpuset_path) {
            for line in content.lines() {
                if let Ok(pid) = line.trim().parse::<i32>()
                    && verify_pid_package(pid, pkg)
                    && !candidates.contains(&pid)
                {
                    candidates.push(pid);
                }
            }
        }
    }

    // 2. Discover any other sub-processes under /proc if fewer than 2 candidates found
    if candidates.len() < 2
        && let Ok(entries) = fs::read_dir("/proc")
    {
        for entry in entries.flatten() {
            if let Ok(file_name) = entry.file_name().into_string()
                && let Ok(pid) = file_name.parse::<i32>()
                && !candidates.contains(&pid)
                && verify_pid_package(pid, pkg)
            {
                candidates.push(pid);
            }
        }
    }

    // Sort by CPU activity descending so the primary render process is first
    if candidates.len() > 1 {
        candidates.sort_unstable_by_key(|&a| std::cmp::Reverse(get_pid_cpu_ticks(a)));
    }
    candidates
}

/// Find PID in Android's top-app cpuset cgroup matching `pkg`.
/// When multiple sub-processes match (e.g. wrapper stub vs `:UnityKillsMe`),
/// selects the process with highest CPU usage.
pub fn find_top_app_pid_pub(pkg: &str) -> Option<i32> {
    find_all_pids_by_package(pkg).into_iter().next()
}

/// Find PID for package without spawning subshells or executing pidof.
/// First inspects top-app cpuset, then scans in-memory `/proc` directly,
/// selecting the most active candidate to avoid idle launcher stubs.
pub fn find_pid_by_package(pkg: &str) -> Option<i32> {
    find_all_pids_by_package(pkg).into_iter().next()
}

/// Query the currently focused foreground package and its PID directly on Android.
/// Prioritizes zero-overhead Native Binder IPC without dumpsys.
pub fn query_current_foreground() -> Option<(String, Option<i32>)> {
    query_current_foreground_task().map(|t| (t.package, t.pid))
}

const CPUSET_TOP_APP_PATHS: &[&str] = &[
    "/dev/cpuset/top-app/cgroup.procs",
    "/dev/cpuset/top-app/tasks",
    "/sys/fs/cgroup/top-app/cgroup.procs",
    "/sys/fs/cgroup/top-app/tasks",
];

#[cfg(target_os = "android")]
fn query_foreground_from_dumpsys_throttled() -> Option<crate::platform::binder::ForegroundTask> {
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::time::{SystemTime, UNIX_EPOCH};

    static LAST_DUMPSYS_MS: AtomicU64 = AtomicU64::new(0);
    let now_ms = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0);
    let prev = LAST_DUMPSYS_MS.load(Ordering::Relaxed);
    if now_ms.saturating_sub(prev) < 2000 {
        return None;
    }
    if LAST_DUMPSYS_MS
        .compare_exchange(prev, now_ms, Ordering::AcqRel, Ordering::Relaxed)
        .is_err()
    {
        return None;
    }

    let output = std::process::Command::new("dumpsys")
        .args(["window", "displays"])
        .output()
        .ok()?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let pkg = match parse_focused_package_from_dumpsys(&stdout) {
        Some(p) => p,
        None => {
            tracing::debug!(
                target: "auriya::procfs",
                "dumpsys window displays returned no focused package (bytes={})",
                stdout.len()
            );
            return None;
        }
    };
    if is_ignorable_system_proc(&pkg) {
        tracing::debug!(
            target: "auriya::procfs",
            "dumpsys focused package ignored as system proc: {}",
            pkg
        );
        return None;
    }
    let pid = find_pid_by_package(&pkg);
    tracing::debug!(
        target: "auriya::procfs",
        "dumpsys fallback detected foreground: pkg={} pid={:?}",
        pkg,
        pid
    );
    Some(crate::platform::binder::ForegroundTask {
        package: pkg,
        pid,
        is_floating: false,
    })
}

/// Query the currently focused foreground task including floating/freeform state.
pub fn query_current_foreground_task() -> Option<crate::platform::binder::ForegroundTask> {
    #[cfg(target_os = "android")]
    {
        if let Some(task) = crate::platform::binder::BinderClient::new()
            .get_foreground_task()
            .ok()
            .flatten()
        {
            return Some(task);
        }

        if let Some(task) = detect_foreground_task_from_cpuset() {
            tracing::debug!(
                target: "auriya::procfs",
                "cpuset fallback detected foreground: pkg={} pid={:?}",
                task.package,
                task.pid
            );
            return Some(task);
        }

        tracing::debug!(
            target: "auriya::procfs",
            "Both Binder and cpuset top-app returned no foreground app; trying dumpsys fallback"
        );
        query_foreground_from_dumpsys_throttled()
    }

    #[cfg(not(target_os = "android"))]
    {
        None
    }
}

fn is_ignorable_system_proc(pkg: &str) -> bool {
    pkg == "system_server"
        || pkg == "surfaceflinger"
        || pkg == "zygote"
        || pkg == "zygote64"
        || pkg == "auriya"
        || pkg == "auriyactl"
        || pkg == "sh"
        || pkg == "su"
        || pkg == "ksud"
        || pkg == "magisk"
        || pkg == "adbd"
        || pkg == "logd"
        || pkg.starts_with("android.hardware.")
        || pkg.starts_with("vendor.")
        || !pkg.contains('.')
}

/// Fallback foreground task detector using Android kernel cgroups (`top-app`).
///
/// On Android, ActivityManager Service (AMS) automatically assigns the focused foreground
/// application's threads and processes into the `top-app` cpuset.
/// This provides a robust, zero-IPC kernel-level fallback when Binder AIDL parcel layouts
/// shift between Android versions or OEM frameworks.
pub fn detect_foreground_task_from_cpuset() -> Option<crate::platform::binder::ForegroundTask> {
    detect_foreground_task_from_cpuset_paths(CPUSET_TOP_APP_PATHS)
}

/// Scan the specified cgroup/cpuset files for the highest-CPU active application process.
pub fn detect_foreground_task_from_cpuset_paths(
    paths: &[&str],
) -> Option<crate::platform::binder::ForegroundTask> {
    for cpuset_path in paths {
        let Ok(content) = fs::read_to_string(cpuset_path) else {
            continue;
        };

        let mut candidates: Vec<(i32, String, u64)> = Vec::new();
        for line in content.lines() {
            let Ok(pid) = line.trim().parse::<i32>() else {
                continue;
            };
            if pid <= 0 {
                continue;
            }
            let Some(pkg) = get_process_package(pid) else {
                continue;
            };

            if is_ignorable_system_proc(&pkg) {
                continue;
            }

            let ticks = get_pid_cpu_ticks(pid);
            candidates.push((pid, pkg, ticks));
        }

        if let Some((pid, pkg, _)) = candidates.into_iter().max_by_key(|&(_, _, ticks)| ticks) {
            return Some(crate::platform::binder::ForegroundTask {
                package: pkg,
                pid: Some(pid),
                is_floating: false,
            });
        }
    }

    None
}
