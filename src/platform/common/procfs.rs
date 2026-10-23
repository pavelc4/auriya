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
