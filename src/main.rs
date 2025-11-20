use anyhow::Result;
use tracing_subscriber::{fmt, EnvFilter, reload, prelude::*};

mod core;
mod daemon;

const CONFIG_PATH: &str = "/data/adb/.config/auriya/auriya.toml";

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    let base_filter = EnvFilter::new("info");
    let (filter_layer, filter_handle) = reload::Layer::new(base_filter);

    let fmt_layer = fmt::layer()
        .with_ansi(false)
        .with_target(false)
        .with_writer(std::io::stderr);
    tracing_subscriber::registry()
        .with(filter_layer)
        .with(fmt_layer)
        .init();
    tracing::info!("Auriya daemon v{} starting", env!("CARGO_PKG_VERSION"));
    let cfg = daemon::run::DaemonConfig {
        config_path: CONFIG_PATH.into(),
        ..Default::default()
    };
    daemon::run::run_with_config_and_logger(&cfg, filter_handle).await
}
