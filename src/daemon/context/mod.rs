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
    pub active_mode: Arc<RwLock<crate::core::profile::ProfileMode>>,
    pub dnd_default: bool,
    pub current_log_level: Arc<RwLock<LogLevelCmd>>,
    pub supported_modes: Arc<Vec<DisplayMode>>,
    pub profile_lock: Arc<Mutex<()>>,
    pub fas_controller: Option<Arc<tokio::sync::Mutex<crate::core::fas::FasController>>>,
    pub reload_handle: Option<ReloadHandle>,
    pub start_time: std::time::Instant,
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
                Ok(_) => {
                    debug!(target: "auriya::ipc", "Log level changed to {:?}", lvl);
                    if matches!(lvl, LogLevelCmd::Debug | LogLevelCmd::Trace) {
                        self.log_debug_baseline();
                    }
                }
                Err(e) => error!(target: "auriya::ipc", "Failed to change log level: {}", e),
            }
        }
    }

    fn log_debug_baseline(&self) {
        let soc = crate::platform::vendor::detect_soc();
        let gpu_vendor = crate::platform::gpu::detect_vendor();
        let caps = crate::core::scaling::PlatformCapabilities::detect();
        let sysfs_fps = crate::core::fps::SysfsFpsReader::detect_sysfs();
        let state_summary = self.current_state.read().ok().map(|s| {
            (
                s.pkg.clone(),
                s.pid,
                s.profile,
                s.game_session,
                s.fps,
                s.fps_source,
                s.gpu_telemetry.clone(),
            )
        });
        debug!(
            target: "auriya::diag",
            "Debug baseline | soc={} gpu_vendor={:?} caps=(gpu={}, cpu={}) sysfs_fps={:?} fas_enabled={}",
            soc,
            gpu_vendor,
            caps.gpu_controllable,
            caps.cpu_controllable,
            sysfs_fps,
            self.fas_controller.is_some(),
        );
        if let Some((pkg, pid, profile, game_session, fps, fps_src, gpu)) = state_summary {
            debug!(
                target: "auriya::diag",
                "Debug state    | pkg={:?} pid={:?} profile={:?} game_session={} fps={:?} src={:?} gpu={:?}",
                pkg, pid, profile, game_session, fps, fps_src, gpu
            );
        }
    }
}
