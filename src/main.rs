use anyhow::Result;
use clap::Parser;

mod core;
mod daemon;

#[derive(Parser)]
struct Args {
    #[arg(long, default_value = "/data/adb/.config/auriya/auriya.toml")]
    packages: String,
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt::init();
    let args = Args::parse();
    let cfg = daemon::run::DaemonConfig {
        config_path: std::path::PathBuf::from(&args.packages),
        ..Default::default()
    };
    daemon::run::run_with_config(&cfg).await
}
