use std::fs;

#[derive(Debug, Clone)]
pub struct DaemonRuntimeInfo {
    pub pid: Option<i32>,
    pub memory_rss: Option<String>,
    pub cpu_load: Option<String>,
    pub workers: Option<usize>,
}

/// Compute CPU load percentage from `/proc/<pid>/stat` content and system uptime.
/// Conforms to Linux procfs semantics (USER_HZ = 100 ticks/sec).
pub fn compute_cpu_load_from_stat(stat: &str, uptime_secs: f64) -> Option<String> {
    let idx = stat.rfind(')')?;
    let rest = &stat[idx + 1..];
    let mut parts = rest.split_whitespace();

    let utime: u64 = parts.nth(11).and_then(|s| s.parse().ok())?;
    let stime: u64 = parts.next().and_then(|s| s.parse().ok())?;
    let starttime: u64 = parts.nth(6).and_then(|s| s.parse().ok())?;

    // USER_HZ is 100 in Linux /proc interfaces (asm-generic/param.h)
    const USER_HZ: f64 = 100.0;
    let starttime_secs = starttime as f64 / USER_HZ;
    let elapsed_secs = uptime_secs - starttime_secs;

    match elapsed_secs <= 0.0 {
        true => Some("<0.1%".to_string()),
        false => {
            let cpu_secs = (utime + stime) as f64 / USER_HZ;
            let load_pct = (cpu_secs / elapsed_secs) * 100.0;
            match load_pct < 0.1 {
                true => Some("<0.1%".to_string()),
                false => Some(format!("{:.1}%", load_pct)),
            }
        }
    }
}

fn read_process_cpu_load(pid: i32) -> Option<String> {
    let stat = fs::read_to_string(format!("/proc/{pid}/stat")).ok()?;
    let uptime_str = fs::read_to_string("/proc/uptime").ok()?;
    let uptime_secs: f64 = uptime_str
        .split_whitespace()
        .next()
        .and_then(|s| s.parse().ok())?;
    compute_cpu_load_from_stat(&stat, uptime_secs)
}

pub fn collect_daemon_runtime(daemon_pid: Option<i32>) -> DaemonRuntimeInfo {
    let pid = match daemon_pid {
        Some(p) => p,
        None => {
            return DaemonRuntimeInfo {
                pid: None,
                memory_rss: None,
                cpu_load: None,
                workers: None,
            };
        }
    };

    let status_path = format!("/proc/{pid}/status");
    let mut memory_rss = None;
    let mut workers = None;

    if let Ok(content) = fs::read_to_string(&status_path) {
        for line in content.lines() {
            if let Some(rest) = line.strip_prefix("VmRSS:") {
                let parts: Vec<&str> = rest.split_whitespace().collect();
                if let Some(kb) = parts.first().and_then(|s| s.parse::<f64>().ok()) {
                    memory_rss = Some(format!("{:.1} MB", kb / 1024.0));
                }
            } else if let Some(rest) = line.strip_prefix("Threads:") {
                let parts: Vec<&str> = rest.split_whitespace().collect();
                if let Some(th) = parts.first().and_then(|s| s.parse::<usize>().ok()) {
                    workers = Some(th);
                }
            }
        }
    }

    let cpu_load = read_process_cpu_load(pid);

    DaemonRuntimeInfo {
        pid: Some(pid),
        memory_rss,
        cpu_load,
        workers,
    }
}

pub fn find_daemon_pid() -> Option<i32> {
    let entries = fs::read_dir("/proc").ok()?;
    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_str()?;
        if let Ok(pid) = name_str.parse::<i32>() {
            let cmdline_path = format!("/proc/{pid}/cmdline");
            if let Ok(cmd) = fs::read_to_string(&cmdline_path) {
                let first = cmd.split('\0').next().unwrap_or("");
                if first == "auriya" || first.ends_with("/auriya") {
                    return Some(pid);
                }
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compute_cpu_load_from_stat_active() {
        let stat = "18518 (auriya) S 1 18435 18435 0 -1 1077936384 6358 11015 1 0 567 2870 7 14 20 0 7 0 4949169 11185164288 1563";
        let uptime_secs = 49650.68;
        let load = compute_cpu_load_from_stat(stat, uptime_secs);
        assert_eq!(load, Some("21.6%".to_string()));
    }

    #[test]
    fn test_compute_cpu_load_from_stat_idle() {
        let stat = "1000 (auriya) S 1 1000 1000 0 -1 1077936384 100 200 0 0 1 1 0 0 20 0 7 0 10000 11185164288 1563";
        let uptime_secs = 200.0;
        let load = compute_cpu_load_from_stat(stat, uptime_secs);
        assert_eq!(load, Some("<0.1%".to_string()));
    }

    #[test]
    fn test_compute_cpu_load_from_stat_invalid() {
        assert_eq!(compute_cpu_load_from_stat("invalid stat", 100.0), None);
    }
}
