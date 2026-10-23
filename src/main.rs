use anyhow::Result;
use tracing_subscriber::{EnvFilter, prelude::*, reload};

use auriya::{core, daemon};

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    // Load settings before initialising tracing so the user-configured
    // log_level actually takes effect.  Early errors go to stderr raw.
    let (settings, gamelist) = core::config::load_all()?;

    let level = &settings.daemon.log_level;
    let base_filter = EnvFilter::new(level);
    let (filter_layer, filter_handle) = reload::Layer::new(base_filter);

    let log_writer = std::sync::Arc::new(core::logger::LogWriter::default_daemon());
    let dmesg_layer = core::logger::DmesgLayer::new(log_writer);

    tracing_subscriber::registry()
        .with(filter_layer)
        .with(dmesg_layer)
        .init();

    tracing::info!(
        target: "status",
        "daemon init: pid={} version={} euid={}",
        std::process::id(),
        env!("CARGO_PKG_VERSION"),
        unsafe { libc::geteuid() }
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
    daemon::engine::run_with_config_and_logger(&cfg, filter_handle).await
}
