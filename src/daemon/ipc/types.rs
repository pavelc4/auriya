use crate::core::config::gamelist::GameProfileUpdate;
use std::sync::Arc;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevelCmd {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
}

pub use crate::core::profile::ProfileMode;
pub use crate::daemon::context::DaemonContext;

/// Shared context handle used across IPC handlers.
pub type IpcHandles = Arc<DaemonContext>;

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
