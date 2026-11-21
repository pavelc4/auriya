use anyhow::Result;
use tracing_subscriber::{EnvFilter, prelude::*, reload};

use auriya::{core, daemon};

fn setup_daemon_panic_hook() {
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let uptime = core::logger::get_boottime_secs();
        let backtrace = std::backtrace::Backtrace::capture();
        let location = info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_else(|| "unknown location".to_string());

        let payload = match (
            info.payload().downcast_ref::<&str>(),
            info.payload().downcast_ref::<String>(),
        ) {
            (Some(s), _) => *s,
            (_, Some(s)) => s.as_str(),
            _ => "unknown payload",
        };

        let report = format!(
            "[{:>12.6}] auriya: FAIL   | CRASH TOMBSTONE: panic at [{location}]: {payload}\nStack backtrace:\n{backtrace}\n",
            uptime
        );

        // 1. Write crash tombstone to /data/adb/auriya/crash.log
        let _ = std::fs::create_dir_all(auriya::common::LOG_DIR);
        let _ = std::fs::write(auriya::common::CRASH_LOG_FILE, &report);

        // 2. Append directly to daemon.log
        if let Ok(mut f) = std::fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(auriya::common::LOG_FILE)
        {
            use std::io::Write;
            let _ = f.write_all(report.as_bytes());
        }

        // 3. Mark module status
        auriya::platform::module_prop::set_module_status("crashed");

        // Forward to default hook for stderr/logcat
        default_hook(info);
    }));
}

fn record_boot_failure(reason: &str) {
    let uptime = core::logger::get_boottime_secs();
    let entry = format!(
        "[{:>12.6}] auriya: FAIL   | BOOT FAILURE: {reason}\n",
        uptime
    );
    let _ = std::fs::create_dir_all(auriya::common::LOG_DIR);
    let _ = std::fs::write(auriya::common::BOOT_ERROR_FILE, entry);
    auriya::platform::module_prop::set_module_status("boot_failed");
}

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> Result<()> {
    // 0. Install global panic hook early so any unhandled panic is persisted to crash.log
    setup_daemon_panic_hook();

    // 1. Initialise tracing immediately so any early configuration or startup
    // errors are reliably captured in daemon.log instead of dropped into /dev/null.
    let log_writer = std::sync::Arc::new(core::logger::LogWriter::default_daemon());
    let dmesg_layer = core::logger::DmesgLayer::new(log_writer);
    let (filter_layer, filter_handle) = reload::Layer::new(EnvFilter::new("info"));

    tracing_subscriber::registry()
        .with(filter_layer)
        .with(dmesg_layer)
        .init();

    // 2. Preflight root check
    let euid = unsafe { libc::geteuid() };
    if euid != 0 {
        let msg = format!("Daemon must run as root (EUID=0, found EUID={euid})");
        tracing::error!(target: "status", "{msg}");
        record_boot_failure(&msg);
        anyhow::bail!("{msg}");
    }

    // 3. Protect daemon from Android Low Memory Killer (LMK) during heavy gaming sessions
    if let Err(e) = std::fs::write("/proc/self/oom_score_adj", "-1000") {
        tracing::debug!(target: "status", "Failed to set oom_score_adj: {e}");
    } else {
        tracing::debug!(target: "status", "oom_score_adj set to -1000 (LMK protected)");
    }

    // 4. Load settings now that tracing is operational
    let (settings, gamelist) = match core::config::load_all() {
        Ok(cfg) => cfg,
        Err(e) => {
            let msg = format!("Fatal: failed to load configuration: {e:#}");
            tracing::error!(target: "status", "{msg}");
            record_boot_failure(&msg);
            return Err(e);
        }
    };

    // Clear stale boot error log once config is validated
    let _ = std::fs::remove_file(auriya::common::BOOT_ERROR_FILE);

    // Clean up stale socket file if left over from an unclean shutdown
    if std::path::Path::new(auriya::common::SOCKET_PATH).exists() {
        tracing::debug!(target: "status", "Cleaning up stale IPC socket: {}", auriya::common::SOCKET_PATH);
        let _ = std::fs::remove_file(auriya::common::SOCKET_PATH);
    }

    // 5. Apply user-configured log level
    let _ = filter_handle.reload(EnvFilter::new(&settings.daemon.log_level));

    tracing::info!(
        target: "status",
        "daemon init: pid={} version={} euid={}",
        std::process::id(),
        env!("CARGO_PKG_VERSION"),
        euid
    );
    tracing::debug!(
        target: "status",
        "environment: ksu_env={} apatch_env={} magisk_env={} bpf_fs={}",
        std::path::Path::new("/data/adb/ksu").exists(),
        std::path::Path::new("/data/adb/ap").exists(),
        std::path::Path::new("/data/adb/magisk").exists(),
        std::path::Path::new("/sys/fs/bpf").exists(),
    );
    tracing::info!(
        target: "code",
        "config loaded: governor={} fas={} games={}",
        settings.cpu.default_governor,
        if settings.fas.enabled {
            "enabled"
        } else {
            "disabled"
        },
        gamelist.game.len()
    );

    let cfg = daemon::engine::DaemonConfig {
        log_debounce_ms: 5000,
        settings,
        gamelist,
    };
    auriya::platform::module_prop::set_module_status("active");
    let _binder = auriya::platform::binder::BinderClient::new();
    let res = daemon::engine::run_with_config_and_logger(cfg, filter_handle).await;
    auriya::platform::module_prop::set_module_status("sleep");
    res
}
