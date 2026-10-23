mod app;
mod client;
pub mod commands;
mod executor;
pub mod output;

use crate::Result;
pub use app::{Cli, Commands, LogLevel, ProfileMode};

pub async fn run() -> Result<()> {
    setup_cli_panic_hook();
    use clap::Parser;

    let cli = Cli::parse();
    executor::execute(cli).await
}

fn setup_cli_panic_hook() {
    let prev_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let msg = info.to_string();
        let is_broken_pipe = msg.contains("Broken pipe")
            || msg.contains("os error 32")
            || info
                .payload()
                .downcast_ref::<&str>()
                .is_some_and(|s| s.contains("Broken pipe") || s.contains("os error 32"))
            || info
                .payload()
                .downcast_ref::<String>()
                .is_some_and(|s| s.contains("Broken pipe") || s.contains("os error 32"));

        if is_broken_pipe {
            std::process::exit(0);
        }
        prev_hook(info);
    }));
}
