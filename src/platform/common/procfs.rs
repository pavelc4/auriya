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

    // 1. Inspect Android top-app cpuset
    for cpuset_path in [
        "/dev/cpuset/top-app/cgroup.procs",
        "/sys/fs/cgroup/top-app/cgroup.procs",
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
            if !candidates.is_empty() {
                break;
            }
        }
    }

    // 2. Discover any other sub-processes under /proc only if top-app cpuset yielded nothing
    if candidates.is_empty()
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

/// Query the currently focused foreground task including floating/freeform state.
pub fn query_current_foreground_task() -> Option<crate::platform::binder::ForegroundTask> {
    #[cfg(target_os = "android")]
    {
        crate::platform::binder::BinderClient::new()
            .get_foreground_task()
            .ok()
            .flatten()
    }

    #[cfg(not(target_os = "android"))]
    {
        None
    }
}
