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

/// Find PID in Android's top-app cpuset cgroup matching `pkg`.
pub fn find_top_app_pid_pub(pkg: &str) -> Option<i32> {
    for cpuset_path in [
        "/dev/cpuset/top-app/cgroup.procs",
        "/sys/fs/cgroup/top-app/cgroup.procs",
    ] {
        if let Ok(content) = fs::read_to_string(cpuset_path) {
            for line in content.lines() {
                if let Ok(pid) = line.trim().parse::<i32>()
                    && verify_pid_package(pid, pkg)
                {
                    return Some(pid);
                }
            }
        }
    }
    None
}

/// Find PID for package without spawning subshells or executing pidof.
/// First inspects top-app cpuset, then scans in-memory `/proc` directly.
pub fn find_pid_by_package(pkg: &str) -> Option<i32> {
    if let Some(pid) = find_top_app_pid_pub(pkg) {
        return Some(pid);
    }

    let Ok(entries) = fs::read_dir("/proc") else {
        return None;
    };
    for entry in entries.flatten() {
        let Ok(file_name) = entry.file_name().into_string() else {
            continue;
        };
        if let Ok(pid) = file_name.parse::<i32>()
            && verify_pid_package(pid, pkg)
        {
            return Some(pid);
        }
    }
    None
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
