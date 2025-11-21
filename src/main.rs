use anyhow::Result;
use tracing_subscriber::{fmt, EnvFilter, reload, prelude::*};

mod core;
mod daemon;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    let base_filter = EnvFilter::new("info");
    let (filter_layer, filter_handle) = reload::Layer::new(base_filter);

    tracing_subscriber::registry()
        .with(filter_layer)
        .with(fmt::layer()
            .with_ansi(false)
            .with_target(false)
            .with_writer(std::io::stderr))
        .init();

    tracing::info!("Auriya daemon v{} starting", env!("CARGO_PKG_VERSION"));
    let (settings, gamelist) = core::config::load_all()?;

    tracing::info!(
        "Config loaded: CPU={}, FAS={}, Games={}",
        settings.cpu.default_governor,
        settings.fas.enabled,
        gamelist.game.len()
    );

    let cfg = daemon::run::DaemonConfig {
        poll_interval: std::time::Duration::from_secs(2),
        log_debounce_ms: 2000,
        settings,
        gamelist,
    };
    daemon::run::run_with_config_and_logger(&cfg, filter_handle).await
}
