use super::commands::LogLevelCmd;
use super::handlers::handle_client;
use crate::core::config::gamelist::GameList;
use crate::daemon::state::CurrentState;
use anyhow::Result;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, RwLock};
use tokio::net::UnixListener;

pub struct IpcHandles {
    pub enabled: Arc<AtomicBool>,
    pub shared_config: Arc<RwLock<GameList>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub reload_fn: Arc<dyn Fn() -> anyhow::Result<usize> + Send + Sync>,
    pub set_log_level: Arc<dyn Fn(LogLevelCmd) + Send + Sync>,
    pub set_fps: Arc<dyn Fn(u32) + Send + Sync>,
    pub get_fps: Arc<dyn Fn() -> u32 + Send + Sync>,
    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: String,
    pub current_log_level: Arc<RwLock<LogLevelCmd>>,
    pub supported_modes: Arc<Vec<crate::core::display::DisplayMode>>,
}

pub async fn start<P: AsRef<Path>>(path: P, h: IpcHandles) -> Result<()> {
    let path_ref = path.as_ref();
    let _ = std::fs::remove_file(path_ref);
    let listener = UnixListener::bind(path_ref)?;
    let _ = std::fs::set_permissions(path_ref, std::fs::Permissions::from_mode(0o666));
    tracing::debug!(target: "auriya::daemon", "IPC listening at {:?}", path_ref);

    loop {
        let (stream, _) = listener.accept().await?;
        let hc = IpcHandles {
            enabled: h.enabled.clone(),
            shared_config: h.shared_config.clone(),
            override_foreground: h.override_foreground.clone(),
            reload_fn: h.reload_fn.clone(),
            set_log_level: h.set_log_level.clone(),
            set_fps: h.set_fps.clone(),
            get_fps: h.get_fps.clone(),
            current_state: h.current_state.clone(),
            balance_governor: h.balance_governor.clone(),
            current_log_level: h.current_log_level.clone(),
            supported_modes: h.supported_modes.clone(),
        };
        tokio::spawn(async move {
            if let Err(e) = handle_client(stream, hc).await {
                tracing::warn!(target: "auriya::daemon", "client error: {:?}", e);
            }
        });
    }
}
