pub mod commands;
pub mod help;
pub mod types;

pub use commands::Commands;
pub use help::{EXAMPLES, cli_styles};
pub use types::{LogLevel, ProfileMode};

use clap::Parser;

#[derive(Parser)]
#[command(name = "auriyactl")]
#[command(version, about = "Auriya daemon control & hardware diagnostics CLI")]
#[command(styles = cli_styles())]
#[command(after_help = EXAMPLES)]
#[command(arg_required_else_help = true)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
    #[arg(
        short,
        long,
        global = true,
        help = "Path to custom IPC unix domain socket"
    )]
    pub socket: Option<String>,
}
