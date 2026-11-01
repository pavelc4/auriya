use super::status_file::STATUS_FILE;
use super::{Daemon, ReloadHandle};
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

pub async fn run_with_config_and_logger(cfg: DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
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
    let (status_cache, mut status_rx) = if status_path.exists() {
        crate::core::system_status::watcher::start_status_watcher(status_path)?
    } else {
        (
            crate::core::system_status::SystemStatusCache::new(),
            tokio::sync::mpsc::channel(1).1,
        )
    };

    let (proc_tx, mut proc_rx) = tokio::sync::mpsc::channel(32);
    let observer = crate::platform::binder::ProcessObserver::new(proc_tx);
    if let Err(e) = observer.register() {
        tracing::debug!(target: "auriya::binder", "ProcessObserver registration deferred: {:?}", e);
    }

    let (event_tx, mut event_rx) = event::channel();

    let mut daemon = Daemon::new(cfg, supported_modes, status_cache, event_tx)?;

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

    let mut watch_rx = start_config_watcher(Arc::clone(&daemon.shared_gamelist));

    start_module_update_watcher(daemon.event_tx.clone());

    debug!(target: "auriya::daemon", "Tick loop started (adaptive: {}ms idle, {}ms gaming)", daemon.normal_interval_ms, INGAME_INTERVAL_MS);

    daemon.tick().await;

    loop {
        let sleep_ms = if daemon.is_suspended() {
            SCREEN_OFF_INTERVAL_MS
        } else if daemon.has_active_monitored_app() {
            INGAME_INTERVAL_MS
        } else {
            daemon.normal_interval_ms
        };

        tokio::select! {
            biased;
            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Daemon | Received Ctrl-C, shutting down");
                daemon.shutdown_cleanup();
                break;
            }
            Some(ev) = event_rx.recv() => {
                match ev {
                    DaemonEvent::PidExited(pid) => {
                        debug!(target: "auriya::daemon", "Tracked PID {} exited, triggering instant tick", pid);
                        daemon.tick().await;
                    }
                    DaemonEvent::ForegroundChanged { pid, uid, foreground } => {
                        if foreground
                            && let Some(pkg) = crate::platform::common::procfs::get_process_package(pid)
                        {
                            daemon.status_cache.store(crate::core::status::SystemStatus {
                                focused_app: Some(pkg),
                                focused_pid: Some(pid),
                                focused_uid: Some(uid),
                                screen_awake: Some(true),
                                battery_saver: None,
                                zen_mode: None,
                                is_floating: None,
                            });
                        }
                        daemon.tick().await;
                    }
                    DaemonEvent::ModuleUpdate => {
                        info!(target: "auriya::daemon", "Daemon | Module update staged, stopping gracefully");
                        daemon.shutdown_cleanup();
                        break;
                    }
                }
            }
            Some(p_ev) = proc_rx.recv() => {
                match p_ev.event_type {
                    crate::platform::binder::ProcessEventType::ForegroundActivitiesChanged => {
                        if p_ev.foreground {
                            let pkg = p_ev.pkg.or_else(|| {
                                if p_ev.pid > 0 {
                                    crate::platform::common::procfs::get_process_package(p_ev.pid)
                                } else {
                                    None
                                }
                            })
                            .or_else(|| {
                                crate::platform::common::procfs::query_current_foreground()
                                    .map(|(p, _)| p)
                            });

                            if let Some(pkg) = pkg {
                                let pid = if p_ev.pid > 0 {
                                    Some(p_ev.pid)
                                } else {
                                    crate::platform::common::procfs::find_pid_by_package(&pkg)
                                };

                                daemon.status_cache.store(crate::core::status::SystemStatus {
                                    focused_app: Some(pkg),
                                    focused_pid: pid,
                                    focused_uid: Some(p_ev.uid),
                                    screen_awake: Some(true),
                                    battery_saver: None,
                                    zen_mode: None,
                                    is_floating: None,
                                });
                            }
                        } else if daemon.last.pid == Some(p_ev.pid)
                            && let Some((pkg, pid)) =
                                crate::platform::common::procfs::query_current_foreground()
                        {
                            daemon.status_cache.store(crate::core::status::SystemStatus {
                                focused_app: Some(pkg),
                                focused_pid: pid,
                                focused_uid: None,
                                screen_awake: Some(true),
                                battery_saver: None,
                                zen_mode: None,
                                is_floating: None,
                            });
                        }
                        daemon.tick().await;
                    }
                    crate::platform::binder::ProcessEventType::ProcessDied
                        if daemon.last.pid == Some(p_ev.pid) =>
                    {
                        debug!(target: "auriya::daemon", "Observed dead PID {}, triggering instant tick", p_ev.pid);
                        daemon.tick().await;
                    }
                    _ => {}
                }
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
            Some(_) = status_rx.recv() => {
                debug!(target: "auriya::daemon", "Status update received, triggering instant tick");
                daemon.tick().await;
            }
            _ = time::sleep(Duration::from_millis(sleep_ms)) => {
                daemon.tick().await;
            }
        }
    }
    info!(target: "auriya::daemon", "Daemon | Stopped");
    Ok(())
}
