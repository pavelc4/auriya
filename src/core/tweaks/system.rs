use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::debug;

pub fn apply_general_tweaks() -> Result<()> {
    disable_kernel_panic()?;
    optimize_io()?;
    optimize_network()?;
    optimize_vm()?;
    optimize_scheduler()?;
    disable_bloat()?;
    Ok(())
}

fn disable_kernel_panic() -> Result<()> {
    let params = [
        ("/proc/sys/kernel/panic", "0"),
        ("/proc/sys/kernel/panic_on_oops", "0"),
        ("/proc/sys/kernel/panic_on_warn", "0"),
        ("/proc/sys/kernel/softlockup_panic", "0"),
    ];

    for (path, value) in params {
        if Path::new(path).exists() {
            fs::write(path, value).context(format!("Failed to write to {}", path))?;
        }
    }
    debug!("Kernel panic disabled");
    Ok(())
}

fn optimize_io() -> Result<()> {
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries {
            if let Ok(entry) = entry {
                let path = entry.path();
                let queue_path = path.join("queue");

                if queue_path.exists() {
                    let iostats = queue_path.join("iostats");
                    if iostats.exists() {
                        let _ = fs::write(iostats, "0");
                    }

                    let add_random = queue_path.join("add_random");
                    if add_random.exists() {
                        let _ = fs::write(add_random, "0");
                    }
                }
            }
        }
    }
    debug!("I/O optimized (iostats, add_random disabled)");
    Ok(())
}

fn optimize_network() -> Result<()> {
    // TCP Congestion Control
    let available_cc_path = "/proc/sys/net/ipv4/tcp_available_congestion_control";
    if Path::new(available_cc_path).exists() {
        let content = fs::read_to_string(available_cc_path).unwrap_or_default();
        let preferred = ["bbr3", "bbr2", "bbrplus", "bbr", "westwood", "cubic"];

        for algo in preferred {
            if content.contains(algo) {
                let _ = fs::write("/proc/sys/net/ipv4/tcp_congestion_control", algo);
                debug!("TCP congestion control set to {}", algo);
                break;
            }
        }
    }

    let params = [
        ("/proc/sys/net/ipv4/tcp_low_latency", "1"),
        ("/proc/sys/net/ipv4/tcp_ecn", "1"),
        ("/proc/sys/net/ipv4/tcp_fastopen", "3"),
        ("/proc/sys/net/ipv4/tcp_sack", "1"),
        ("/proc/sys/net/ipv4/tcp_timestamps", "0"),
    ];

    for (path, value) in params {
        if Path::new(path).exists() {
            let _ = fs::write(path, value);
        }
    }

    Ok(())
}

fn optimize_vm() -> Result<()> {
    let params = [
        ("/proc/sys/vm/stat_interval", "15"),
        ("/proc/sys/vm/compaction_proactiveness", "0"),
        ("/proc/sys/vm/page-cluster", "0"),
    ];

    for (path, value) in params {
        if Path::new(path).exists() {
            let _ = fs::write(path, value);
        }
    }
    debug!("VM tweaks applied");
    Ok(())
}

fn optimize_scheduler() -> Result<()> {
    let params = [
        ("/proc/sys/kernel/perf_cpu_time_max_percent", "3"),
        ("/proc/sys/kernel/sched_schedstats", "0"),
        ("/proc/sys/kernel/task_cpustats_enable", "0"),
        ("/proc/sys/kernel/sched_autogroup_enabled", "0"),
        ("/proc/sys/kernel/sched_child_runs_first", "1"),
        ("/proc/sys/kernel/sched_nr_migrate", "32"),
        ("/proc/sys/kernel/sched_migration_cost_ns", "50000"),
        ("/proc/sys/kernel/sched_min_granularity_ns", "1000000"),
        ("/proc/sys/kernel/sched_wakeup_granularity_ns", "1500000"),
    ];

    for (path, value) in params {
        if Path::new(path).exists() {
            let _ = fs::write(path, value);
        }
    }

    let spi_crc = "/sys/module/mmc_core/parameters/use_spi_crc";
    if Path::new(spi_crc).exists() {
        let _ = fs::write(spi_crc, "0");
    }

    debug!("Scheduler tweaks applied");
    Ok(())
}

fn disable_bloat() -> Result<()> {
    let params = [
        ("/sys/module/opchain/parameters/chain_on", "0"),
        ("/sys/module/cpufreq_bouncing/parameters/enable", "0"),
        (
            "/proc/task_info/task_sched_info/task_sched_info_enable",
            "0",
        ),
        (
            "/proc/oplus_scheduler/sched_assist/sched_assist_enabled",
            "0",
        ),
    ];

    for (path, value) in params {
        if Path::new(path).exists() {
            let _ = fs::write(path, value);
        }
    }
    Ok(())
}
