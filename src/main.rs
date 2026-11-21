use anyhow::Result;
use tracing_subscriber::{EnvFilter, prelude::*, reload};

use auriya::{core, daemon};

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    // 1. Initialise tracing immediately so any early configuration or startup
    // errors are reliably captured in daemon.log instead of dropped into /dev/null.
    let log_writer = std::sync::Arc::new(core::logger::LogWriter::default_daemon());
    let dmesg_layer = core::logger::DmesgLayer::new(log_writer);
    let (filter_layer, filter_handle) = reload::Layer::new(EnvFilter::new("info"));

    tracing_subscriber::registry()
        .with(filter_layer)
        .with(dmesg_layer)
        .init();

    // 2. Load settings now that tracing is operational
    let (settings, gamelist) = match core::config::load_all() {
        Ok(cfg) => cfg,
        Err(e) => {
            tracing::error!("Fatal: failed to load configuration: {e:#}");
            return Err(e);
        }
    };

    // 3. Apply user-configured log level
    let _ = filter_handle.reload(EnvFilter::new(&settings.daemon.log_level));

    tracing::info!(
        target: "status",
        "daemon init: pid={} version={} euid={}",
        std::process::id(),
        env!("CARGO_PKG_VERSION"),
        unsafe { libc::geteuid() }
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
