use super::types::{LogLevel, ProfileMode};
use clap::Subcommand;

#[derive(Subcommand)]
pub enum Commands {
    // === DAEMON LIFECYCLE ===
    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Display daemon status, active game, and power profile")]
    Status,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Enable Auriya daemon optimizations")]
    Enable,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Disable Auriya daemon optimizations")]
    Disable,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Reload settings and gamelist from disk")]
    Reload,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Gracefully restart daemon and Android companion")]
    Restart,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Ping daemon to check IPC socket responsiveness")]
    Ping,

    #[command(subcommand_help_heading = "Daemon Lifecycle")]
    #[command(about = "Get PID of the running Auriya daemon process")]
    GetPid,

    // === TUNING & PROFILES ===
    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "Set global performance profile")]
    SetProfile {
        #[arg(value_enum, help = "Profile mode")]
        mode: ProfileMode,
    },

    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "Set target FPS dynamically")]
    SetFps {
        #[arg(help = "Target frames per second")]
        fps: u32,
    },

    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "Get current measured FPS and target rate")]
    GetFps,

    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "Add game package to gamelist configuration")]
    AddGame {
        #[arg(help = "Android application package name")]
        package: String,
    },

    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "Remove game package from gamelist configuration")]
    RemoveGame {
        #[arg(help = "Android application package name")]
        package: String,
    },

    #[command(subcommand_help_heading = "Tuning & Profiles")]
    #[command(about = "List configured games in gamelist")]
    ListGames,

    // === DIAGNOSTICS & TELEMETRY ===
    #[command(subcommand_help_heading = "Diagnostics & Telemetry")]
    #[command(about = "Inspect device hardware, SoC board, and kernel eBPF capabilities")]
    Info,

    #[command(subcommand_help_heading = "Diagnostics & Telemetry")]
    #[command(about = "Stream live telemetry (FPS, CPU, GPU, temp, battery, app) in real-time")]
    Monitor {
        #[arg(
            short = '1',
            long,
            help = "Output compact 1-line stream instead of in-place dashboard"
        )]
        oneline: bool,
        #[arg(
            short,
            long,
            default_value = "1000",
            help = "Telemetry refresh interval in milliseconds"
        )]
        interval_ms: u64,
    },

    #[command(subcommand_help_heading = "Diagnostics & Telemetry")]
    #[command(about = "View or follow daemon logs")]
    Log {
        #[arg(
            short = 'f',
            long,
            help = "Follow log stream in real-time (like tail -f)"
        )]
        follow: bool,
        #[arg(
            short = 'n',
            long,
            default_value = "30",
            help = "Number of recent lines to display"
        )]
        lines: usize,
        #[arg(long, help = "Clear / truncate daemon log file")]
        clear: bool,
    },

    #[command(subcommand_help_heading = "Diagnostics & Telemetry")]
    #[command(about = "List supported display refresh rates")]
    GetRates,

    #[command(subcommand_help_heading = "Diagnostics & Telemetry")]
    #[command(about = "List installed packages on the system")]
    ListPackages,

    // === DEVELOPER & DEBUG ===
    #[command(subcommand_help_heading = "Developer & Debug")]
    #[command(about = "Set daemon log filtering level")]
    SetLog {
        #[arg(value_enum, help = "Tracing log level")]
        level: LogLevel,
    },

    #[command(subcommand_help_heading = "Developer & Debug")]
    #[command(about = "Inject mock foreground package for testing")]
    Inject {
        #[arg(help = "Package name to inject")]
        package: String,
    },

    #[command(subcommand_help_heading = "Developer & Debug")]
    #[command(about = "Clear mock foreground injection override")]
    ClearInject,
}
