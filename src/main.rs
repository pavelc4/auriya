use anyhow::Result;
use clap::Parser;
use tracing_subscriber::{fmt, EnvFilter};
use tracing_subscriber::{reload, prelude::*};

mod core;
mod daemon;

#[derive(Parser)]
struct Args {
    #[arg(long, default_value = "/data/adb/.config/auriya/auriya.toml")]
    packages: String,
}

#[tokio::main]
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

    let args = Args::parse();
    let cfg = daemon::run::DaemonConfig {
        config_path: std::path::PathBuf::from(&args.packages),
        ..Default::default()
    };
    daemon::run::run_with_config_and_logger(&cfg, filter_handle).await
}
