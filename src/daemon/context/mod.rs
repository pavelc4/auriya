pub mod gamelist;
pub mod profile;
pub mod telemetry;

use crate::core::config::gamelist::GameList;
use crate::daemon::ipc::types::LogLevelCmd;
use crate::daemon::model::state::CurrentState;
use crate::platform::display::DisplayMode;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, RwLock};
use tracing::{debug, error};
use tracing_subscriber::EnvFilter;

pub use telemetry::format_telemetry;

pub type ReloadHandle =
    tracing_subscriber::reload::Handle<tracing_subscriber::EnvFilter, tracing_subscriber::Registry>;

/// Central daemon execution and state context.
///
/// Encapsulates shared state, hardware capabilities, and IPC command handlers
/// replacing fragmented handle bags and boxed callbacks.
pub struct DaemonContext {
    pub enabled: Arc<AtomicBool>,
    pub shared_config: Arc<RwLock<Arc<GameList>>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: Arc<RwLock<String>>,
    pub dnd_default: bool,
    pub current_log_level: Arc<RwLock<LogLevelCmd>>,
    pub supported_modes: Arc<Vec<DisplayMode>>,
    pub profile_lock: Arc<Mutex<()>>,
    pub fas_controller: Option<Arc<tokio::sync::Mutex<crate::core::fas::FasController>>>,
    pub reload_handle: Option<ReloadHandle>,
}

impl DaemonContext {
    /// Update runtime logging level filter.
    pub fn set_log_level(&self, lvl: LogLevelCmd) {
        let filter_str = match lvl {
            LogLevelCmd::Trace => "trace",
            LogLevelCmd::Debug => "debug",
            LogLevelCmd::Info => "info",
            LogLevelCmd::Warn => "warn",
            LogLevelCmd::Error => "error",
        };
        if let Ok(mut l) = self.current_log_level.write() {
            *l = lvl;
        }
        if let Some(ref handle) = self.reload_handle {
            match handle.reload(EnvFilter::new(filter_str)) {
                Ok(_) => debug!(target: "auriya::ipc", "Log level changed to {:?}", lvl),
                Err(e) => error!(target: "auriya::ipc", "Failed to change log level: {}", e),
            }
        }
    }
}
