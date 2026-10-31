//! Handler for Native Android Binder CLI commands and diagnostics.

pub mod display;
pub mod hardware;
pub mod notify;
pub mod probe;
pub mod system;
pub mod test;
pub mod util;

use anyhow::Result;

use crate::cli::app::BinderCommands;

/// Dispatch incoming Binder CLI command to its respective domain handler.
pub async fn handle_binder(cmd: BinderCommands) -> Result<()> {
    match cmd {
        BinderCommands::Probe => probe::handle_probe(),
        BinderCommands::Test => test::handle_test(),
        BinderCommands::Dnd { mode } => display::handle_dnd(mode),
        BinderCommands::Rate { hz } => display::handle_rate(hz),
        BinderCommands::Wifi { action } => system::handle_wifi(action),
        BinderCommands::Touch { action } => system::handle_touch(action),
        BinderCommands::Statusbar { action } => system::handle_statusbar(action),
        BinderCommands::Power {
            action,
            name,
            value,
        } => hardware::handle_power(&action, name.as_deref(), value.as_deref()),
        BinderCommands::Vibrator {
            action,
            duration_ms,
        } => hardware::handle_vibrator(action.as_deref(), duration_ms),
        BinderCommands::Window { action, param } => {
            hardware::handle_window(&action, param.as_deref())
        }
        BinderCommands::PowerSave { action } => hardware::handle_power_save(action.as_deref()),
        BinderCommands::Toast { message, long } => notify::handle_toast(&message, long),
        BinderCommands::Broadcast { action, extra } => notify::handle_broadcast(&action, &extra),
    }
}
