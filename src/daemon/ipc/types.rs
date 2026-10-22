use crate::core::config::gamelist::{GameList, GameProfileUpdate};
use crate::daemon::state::CurrentState;
use std::pin::Pin;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, RwLock};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevelCmd {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
    Fast,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    Help,
    Status,
    Enable,
    Disable,
    Reload,
    Restart,
    SetLog(LogLevelCmd),
    Inject(String),
    ClearInject,
    GetPid,
    Ping,
    Quit,
    SetProfile(ProfileMode),
    SetGovernor(String),
    AddGame(String),
    RemoveGame(String),
    ListPackages,
    GetGameList,
    UpdateGame {
        pkg: String,
        update: Box<GameProfileUpdate>,
    },
    SetFps(u32),
    GetFps,
    GetSupportedRates,
    GetStats,
    StreamStats(u64),
}

pub type AsyncFpsCallback =
    Arc<dyn Fn(u32) -> Pin<Box<dyn std::future::Future<Output = ()> + Send + Sync>> + Send + Sync>;

pub type AsyncGetFpsCallback =
    Arc<dyn Fn() -> Pin<Box<dyn std::future::Future<Output = u32> + Send + Sync>> + Send + Sync>;

pub type AsyncGetStatsCallback = Arc<
    dyn Fn() -> Pin<
            Box<
                dyn std::future::Future<Output = Option<crate::core::stats::FpsStats>>
                    + Send
                    + Sync,
            >,
        > + Send
        + Sync,
>;

#[derive(Clone)]
pub struct IpcHandles {
    pub enabled: Arc<AtomicBool>,
    pub shared_config: Arc<RwLock<Arc<GameList>>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub reload_fn: Arc<dyn Fn() -> anyhow::Result<usize> + Send + Sync>,
    pub set_log_level: Arc<dyn Fn(LogLevelCmd) + Send + Sync>,
    pub set_fps: AsyncFpsCallback,
    pub get_fps: AsyncGetFpsCallback,
    pub get_fps_stats: AsyncGetStatsCallback,
    pub profile_lock: Arc<Mutex<()>>,

    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: Arc<RwLock<String>>,
    /// Default `enable_dnd` for a game created via `ADD_GAME`, from
    /// `settings.dnd.default_enable`. Snapshotted at IPC init.
    pub dnd_default: bool,
    pub current_log_level: Arc<RwLock<LogLevelCmd>>,
    pub supported_modes: Arc<Vec<crate::core::display::DisplayMode>>,
}
