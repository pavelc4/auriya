use clap::{Parser, Subcommand, ValueEnum};

#[derive(Parser)]
#[command(name = "auriyactl")]
#[command(version, about = "Auriya daemon control CLI")]
#[command(arg_required_else_help = true)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
    #[arg(short, long, global = true)]
    pub socket: Option<String>,
}

#[derive(Subcommand)]
pub enum Commands {
    Status,
    Enable,
    Disable,
    Reload,
    Restart,

    SetProfile {
        #[arg(value_enum)]
        mode: ProfileMode,
    },

    SetFps {
        fps: u32,
    },
    GetFps,

    AddGame {
        package: String,
    },

    RemoveGame {
        package: String,
    },

    ListGames,
    ListPackages,
    GetRates,

    SetLog {
        #[arg(value_enum)]
        level: LogLevel,
    },

    GetPid,
    Ping,
    Inject {
        package: String,
    },
    ClearInject,
}

#[derive(Clone, ValueEnum)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
    Fast,
}

impl ProfileMode {
    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Performance => "PERFORMANCE",
            Self::Balance => "BALANCE",
            Self::Powersave => "POWERSAVE",
            Self::Fast => "FAST",
        }
    }
}

#[derive(Clone, ValueEnum)]
pub enum LogLevel {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
}

impl LogLevel {
    pub fn to_upper_str(&self) -> &'static str {
        match self {
            Self::Trace => "TRACE",
            Self::Debug => "DEBUG",
            Self::Info => "INFO",
            Self::Warn => "WARN",
            Self::Error => "ERROR",
        }
    }
}
