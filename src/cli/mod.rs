mod app;
mod client;
mod executor;
mod output;

pub use app::Cli;
use crate::Result;

pub async fn run() -> Result<()> {
    use clap::Parser;

    let cli = Cli::parse();
    executor::execute(cli).await
}
