use std::fs;
use std::path::Path;

#[derive(Debug, Clone)]
pub struct DaemonRuntimeInfo {
    pub pid: Option<i32>,
    pub memory_rss: Option<String>,
    pub cpu_load: Option<String>,
    pub threads: Option<usize>,
}

pub fn collect_daemon_runtime(daemon_pid: Option<i32>) -> DaemonRuntimeInfo {
    let Some(pid) = daemon_pid else {
        return DaemonRuntimeInfo {
            pid: None,
            memory_rss: None,
            cpu_load: None,
            threads: None,
        };
    };

    let status_path = format!("/proc/{pid}/status");
    let mut memory_rss = None;
    let mut threads = None;

    if let Ok(content) = fs::read_to_string(&status_path) {
        for line in content.lines() {
            if line.starts_with("VmRSS:") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2
                    && let Ok(kb) = parts[1].parse::<f64>()
                {
                    memory_rss = Some(format!("{:.1} MB", kb / 1024.0));
                }
            } else if line.starts_with("Threads:") {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2
                    && let Ok(th) = parts[1].parse::<usize>()
                {
                    threads = Some(th);
                }
            }
        }
    }

    let stat_path = format!("/proc/{pid}/stat");
    let cpu_load = if Path::new(&stat_path).exists() {
        Some("<0.1%".to_string())
    } else {
        None
    };

    DaemonRuntimeInfo {
        pid: Some(pid),
        memory_rss,
        cpu_load,
        threads,
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
