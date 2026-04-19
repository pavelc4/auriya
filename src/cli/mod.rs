mod app;
mod client;
mod executor;
mod output;

use crate::Result;
pub use app::Cli;

pub async fn run() -> Result<()> {
    use clap::Parser;

    let cli = Cli::parse();
    executor::execute(cli).await
}
