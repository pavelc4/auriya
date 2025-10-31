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
    daemon::run_pid_only_with_path(&args.packages).await
}
