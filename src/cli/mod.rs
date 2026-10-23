mod app;
mod client;
pub mod commands;
mod executor;
pub mod output;

use crate::Result;
pub use app::{Cli, Commands, LogLevel, ProfileMode};

pub async fn run() -> Result<()> {
    use clap::Parser;

    let cli = Cli::parse();
    executor::execute(cli).await
}
