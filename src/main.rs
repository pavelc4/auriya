use anyhow::Result;
use tracing_subscriber::{EnvFilter, fmt, prelude::*, reload};

mod core;
mod daemon;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    let base_filter = EnvFilter::new("info");
    let (filter_layer, filter_handle) = reload::Layer::new(base_filter);
    let timer = tracing_subscriber::fmt::time::UtcTime::new(
        time::format_description::parse("[hour]:[minute]:[second]").unwrap(),
    );

    tracing_subscriber::registry()
        .with(filter_layer)
        .with(
            fmt::layer()
                .with_ansi(false)
                .with_target(false)
                .with_level(false)
                .with_timer(timer)
                .with_writer(std::io::stderr),
        )
        .init();

    let (settings, gamelist) = core::config::load_all()?;

    tracing::info!(
        "Auriya v{} started (CPU={}, FAS={}, games={})",
        env!("CARGO_PKG_VERSION"),
        settings.cpu.default_governor,
        if settings.fas.enabled { "on" } else { "off" },
        gamelist.game.len()
    );

    let cfg = daemon::run::DaemonConfig {
        log_debounce_ms: 5000,
        settings,
        gamelist,
    };
    daemon::run::run_with_config_and_logger(&cfg, filter_handle).await
}
