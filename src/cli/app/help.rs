use clap::builder::styling::{AnsiColor, Effects, Styles};

pub fn cli_styles() -> Styles {
    Styles::styled()
        .header(AnsiColor::Cyan.on_default() | Effects::BOLD)
        .usage(AnsiColor::Cyan.on_default() | Effects::BOLD)
        .literal(AnsiColor::Yellow.on_default() | Effects::BOLD)
        .placeholder(AnsiColor::Green.on_default())
}

pub const EXAMPLES: &str = "\
EXAMPLES:
  auriyactl status                      Check daemon status and active game session
  auriyactl info                        Inspect hardware, SoC board, and eBPF support
  auriyactl monitor                     Live telemetry stream (matching info format)
  auriyactl monitor --oneline           Single-line compact telemetry
  auriyactl log -f                      Follow daemon logs in real-time
  auriyactl set-profile performance     Switch daemon power profile
  auriyactl binder probe                Probe Android Binder servicemanager
  auriyactl binder test                 Run automated Binder hardware verification
  auriyactl restart                     Gracefully restart daemon";
