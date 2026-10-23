use std::fs;
use std::time::Instant;

pub struct DaemonResourceTelemetry {
    pid: u32,
    page_size_bytes: u64,
    last_sample_time: Option<Instant>,
    last_total_ticks: u64,
}

impl Default for DaemonResourceTelemetry {
    fn default() -> Self {
        Self::new()
    }
}

impl DaemonResourceTelemetry {
    pub fn new() -> Self {
        // SAFETY: sysconf(_SC_PAGESIZE) is a POSIX constant query.
        let page_size = unsafe { libc::sysconf(libc::_SC_PAGESIZE) };
        let page_size_bytes = if page_size > 0 {
            page_size as u64
        } else {
            4096
        };

        Self {
            pid: std::process::id(),
            page_size_bytes,
            last_sample_time: None,
            last_total_ticks: 0,
        }
    }

    /// Sample current process resources via native Linux /proc interfaces (zero heap bloat).
    pub fn sample(&mut self) -> Option<DaemonResourceStats> {
        let now = Instant::now();

        // 1. Read Resident Set Size from /proc/self/statm
        let statm = fs::read_to_string("/proc/self/statm").ok()?;
        let mut parts = statm.split_ascii_whitespace();
        let _vmsize_pages = parts.next()?;
        let resident_pages: u64 = parts.next()?.parse().ok()?;
        let rss_bytes = resident_pages.saturating_mul(self.page_size_bytes);
        let rss_mb = (rss_bytes as f64) / (1024.0 * 1024.0);

        // 2. Read thread count from /proc/self/status
        let status = fs::read_to_string("/proc/self/status").ok()?;
        let mut thread_count = 1usize;
        for line in status.lines() {
            if let Some(count) = line
                .strip_prefix("Threads:\t")
                .and_then(|r| r.trim().parse::<usize>().ok())
            {
                thread_count = count;
                break;
            }
        }

        // 3. Compute CPU usage from /proc/self/stat (utime + stime)
        let mut cpu_pct = 0.0f32;
        if let Ok(stat) = fs::read_to_string("/proc/self/stat") {
            // Find closing paren of process name to avoid spaces in cmdline
            if let Some(idx) = stat.rfind(')') {
                let rest = &stat[idx + 1..];
                let stat_fields: Vec<&str> = rest.split_ascii_whitespace().collect();
                // In post-paren slice: field 11 is utime (index 11), field 12 is stime (index 12)
                if stat_fields.len() > 12 {
                    let utime: u64 = stat_fields[11].parse().unwrap_or(0);
                    let stime: u64 = stat_fields[12].parse().unwrap_or(0);
                    let current_ticks = utime + stime;

                    if let Some(prev_time) = self.last_sample_time {
                        let elapsed_secs = now.duration_since(prev_time).as_secs_f32();
                        if elapsed_secs > 0.0 && current_ticks >= self.last_total_ticks {
                            let tick_diff = (current_ticks - self.last_total_ticks) as f32;
                            // Clock ticks per second (usually 100 on Linux / CLK_TCK)
                            let ticks_per_sec = unsafe { libc::sysconf(libc::_SC_CLK_TCK) } as f32;
                            let clk_tck = if ticks_per_sec > 0.0 {
                                ticks_per_sec
                            } else {
                                100.0
                            };
                            cpu_pct = (tick_diff / clk_tck) / elapsed_secs * 100.0;
                        }
                    }

                    self.last_total_ticks = current_ticks;
                }
            }
        }

        self.last_sample_time = Some(now);

        Some(DaemonResourceStats {
            pid: self.pid,
            rss_mb,
            cpu_pct,
            threads: thread_count,
        })
    }
}

pub struct DaemonResourceStats {
    pub pid: u32,
    pub rss_mb: f64,
    pub cpu_pct: f32,
    pub threads: usize,
}
