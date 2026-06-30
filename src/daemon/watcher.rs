use crate::core::config::{GameList, gamelist_path, settings_path};
use crate::daemon::event::{DaemonEvent, EventSender};
use notify::{EventKind, RecursiveMode, Watcher};
use std::path::PathBuf;
use std::sync::{Arc, RwLock};
use tokio::sync::mpsc;
use tracing::{debug, error, warn};
const MODULE_PATH: &str = "/data/adb/modules/auriya";

pub fn start_config_watcher(
    shared_gamelist: Arc<RwLock<Arc<GameList>>>,
) -> mpsc::Receiver<String> {
    let (watch_tx, watch_rx) = mpsc::channel::<String>(10);
    let gamelist_path = Arc::new(gamelist_path());
    let settings_path = Arc::new(settings_path());

    std::thread::spawn(move || {
        let tx = watch_tx;
        let path = gamelist_path.clone();
        let shared_for_watcher = shared_gamelist;

        let mut watcher = match notify::recommended_watcher(
            move |res: Result<notify::Event, notify::Error>| {
                // Catch both in-place edits (Modify) and atomic renames
                // (Create — write-tmp-then-rename produces a Create event
                // for the target path, not Modify).
                if let Ok(event) = res
                    && matches!(event.kind, EventKind::Modify(_) | EventKind::Create(_))
                {
                    let path_str = event
                        .paths
                        .first()
                        .map(|p| p.to_string_lossy().to_string())
                        .unwrap_or_default();

                    if path_str.contains("settings.toml") {
                        let _ = tx.blocking_send("settings".to_string());
                        return;
                    }

                    // Ignore events for temporary/swap files — only react
                    // when the actual gamelist file is touched.
                    if !path_str.contains("gamelist") {
                        return;
                    }
                    if path_str.contains(".tmp") || path_str.ends_with('~') || path_str.contains(".swp") {
                        return;
                    }

                    debug!(target: "auriya::daemon", "Gamelist file changed, reloading...");
                    let max_retries = 3;
                    let mut retry_count = 0;
                    let mut success = false;

                    while retry_count < max_retries && !success {
                        match GameList::load(&*path) {
                            Ok(new_cfg) => match shared_for_watcher.write() {
                                Ok(mut g) => {
                                    let count = new_cfg.game.len();
                                    *g = Arc::new(new_cfg);
                                    debug!(target: "auriya::daemon", "Gamelist reloaded: {} games", count);
                                    success = true;
                                }
                                Err(_) => {
                                    error!(target: "auriya::daemon", "Failed to acquire gamelist lock");
                                    break;
                                }
                            },
                            Err(e) => {
                                retry_count += 1;
                                if retry_count < max_retries {
                                    warn!(target: "auriya::daemon", "Failed reloading gamelist (attempt {}/{}): {:?}, retrying in 2s...", retry_count, max_retries, e);
                                    std::thread::sleep(std::time::Duration::from_secs(2));
                                } else {
                                    error!(target: "auriya::daemon", "Failed to reload gamelist after {} attempts: {:?}", max_retries, e);
                                }
                            }
                        }
                    }
                    let _ = tx.blocking_send("gamelist".to_string());
                }
            },
        ) {
            Ok(w) => w,
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to create gamelist watcher: {}", e);
                return;
            }
        };

        if let Err(e) = watcher.watch(&gamelist_path, RecursiveMode::NonRecursive) {
            error!(target: "auriya::daemon", "Failed to watch gamelist file: {}", e);
            return;
        }
        if let Err(e) = watcher.watch(&settings_path, RecursiveMode::NonRecursive) {
            error!(target: "auriya::daemon", "Failed to watch settings file: {}", e);
            return;
        }

        debug!(target: "auriya::daemon", "Config file watchers started");
        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    });

    watch_rx
}

/// Watch the module directory for a staged update.
///
/// Magisk/KernelSU drops an empty `update` file inside the module path
/// when a new version is flashed; the running daemon must stop gracefully
/// so the replacement binary takes over on the next boot. On a host where
/// the module path does not exist (development), the watch simply fails to
/// arm and the thread exits — the daemon keeps running normally.
pub fn start_module_update_watcher(event_tx: EventSender) {
    let modpath = PathBuf::from(MODULE_PATH);

    std::thread::spawn(move || {
        let tx = event_tx;
        let mut watcher = match notify::recommended_watcher(
            move |res: Result<notify::Event, notify::Error>| {
                let Ok(event) = res else {
                    return;
                };
                if !matches!(event.kind, EventKind::Create(_)) {
                    return;
                }
                let is_update = event
                    .paths
                    .iter()
                    .any(|p| p.file_name().is_some_and(|n| n == "update"));
                if is_update {
                    warn!(target: "auriya::daemon", "Module update staged, requesting graceful stop");
                    let _ = tx.blocking_send(DaemonEvent::ModuleUpdate);
                }
            },
        ) {
            Ok(w) => w,
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to create module update watcher: {e}");
                return;
            }
        };

        if let Err(e) = watcher.watch(&modpath, RecursiveMode::NonRecursive) {
            warn!(
                target: "auriya::daemon",
                "Module update watch unavailable ({}): {e}",
                modpath.display()
            );
            return;
        }

        debug!(target: "auriya::daemon", "Module update watcher started ({})", modpath.display());
        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    });
}
