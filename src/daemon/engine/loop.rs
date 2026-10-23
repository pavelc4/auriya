use super::status_file::STATUS_FILE;
use super::{Daemon, ReloadHandle};
use crate::daemon::companion::lock::start_companion_lock_watcher;
use crate::daemon::model::config::DaemonConfig;
use crate::daemon::model::event::{self, DaemonEvent};
use crate::daemon::watcher::{start_config_watcher, start_module_update_watcher};
use crate::platform::vendor::{detect, mtk};
use anyhow::Result;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use tokio::{signal, time};
use tracing::{debug, error, info};

pub const INGAME_INTERVAL_MS: u64 = 500;
pub const SCREEN_OFF_INTERVAL_MS: u64 = 2000;

pub async fn run_with_config_and_logger(cfg: &DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: &DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
    let supported_modes = match crate::platform::display::get_app_supported_modes().await {
        Ok(modes) => {
            debug!(target: "auriya::daemon", "Cached {} supported display modes", modes.len());
            Arc::new(modes)
        }
        Err(e) => {
            error!(target: "auriya::daemon", "Failed to cache supported modes: {}", e);
            Arc::new(Vec::new())
        }
    };

    let status_path = PathBuf::from(STATUS_FILE);
    let wait_timeout = Duration::from_secs(10);
    if let Err(e) =
        crate::core::system_status::watcher::await_status_file(&status_path, wait_timeout)
    {
        error!(target: "auriya::daemon", "Daemon | {e}");
        return Err(e);
    }

    let (status_cache, mut status_rx) =
        crate::core::system_status::watcher::start_status_watcher(status_path)?;

    let (event_tx, mut event_rx) = event::channel();

    let mut daemon = Daemon::new(cfg.clone(), supported_modes, status_cache, event_tx)?;

    if detect::is_mediatek() {
        debug!(target: "auriya::daemon", "MTK device detected, applying PPM fix...");
        tokio::task::spawn_blocking(mtk::fix_mediatek_ppm)
            .await
            .ok();
        debug!(target: "auriya::daemon", "MTK PPM fix applied");
    }

    daemon.init_ipc(filter_handle).await;

    tokio::time::sleep(time::Duration::from_millis(200)).await;
    debug!(target: "auriya::daemon", "IPC socket ready at /dev/socket/auriya.sock");

    let mut watch_rx = start_config_watcher(daemon.shared_gamelist.clone());

    start_module_update_watcher(daemon.event_tx.clone());
    start_companion_lock_watcher(daemon.event_tx.clone());

    debug!(target: "auriya::daemon", "Tick loop started (adaptive: {}ms idle, {}ms gaming)", daemon.normal_interval_ms, INGAME_INTERVAL_MS);

    daemon.tick().await;

    loop {
        let sleep_ms = if daemon.is_suspended() {
            SCREEN_OFF_INTERVAL_MS
        } else if daemon.is_in_game_session() {
            INGAME_INTERVAL_MS
        } else {
            daemon.normal_interval_ms
        };

        tokio::select! {
            _ = time::sleep(Duration::from_millis(sleep_ms)) => {
                daemon.tick().await;
            }
            Some(_) = status_rx.recv() => {
                debug!(target: "auriya::daemon", "Status update received, triggering instant tick");
                daemon.tick().await;
            }
            Some(msg) = watch_rx.recv() => {
                if msg == "settings" {
                    daemon.reload_settings();
                    daemon.tick().await;
                } else {
                    daemon.rebuild_whitelist();
                    debug!(target: "auriya::daemon", "Gamelist reload notification received, triggering instant tick");
                    daemon.tick().await;
                }
            }
            Some(ev) = event_rx.recv() => {
                match ev {
                    DaemonEvent::PidExited(pid) => {
                        debug!(target: "auriya::daemon", "Tracked PID {} exited, triggering instant tick", pid);
                        daemon.tick().await;
                    }
                    DaemonEvent::CompanionDied => {
                        daemon.watchdog.on_companion_died();
                    }
                    DaemonEvent::ModuleUpdate => {
                        info!(target: "auriya::daemon", "Daemon | Module update staged, stopping gracefully");
                        daemon.shutdown_cleanup();
                        break;
                    }
                }
            }
            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Daemon | Received Ctrl-C, shutting down");
                daemon.shutdown_cleanup();
                break;
            }
        }
    }
    info!(target: "auriya::daemon", "Daemon | Stopped");
    Ok(())
}
