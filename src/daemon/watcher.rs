use crate::core::config::{GameList, gamelist_path, settings_path};
use notify::{EventKind, RecursiveMode, Watcher};
use std::sync::{Arc, RwLock};
use tokio::sync::mpsc;
use tracing::{error, info, warn};

pub fn start_config_watcher(shared_gamelist: Arc<RwLock<GameList>>) -> mpsc::Receiver<String> {
    let (watch_tx, watch_rx) = mpsc::channel::<String>(10);
    let gamelist_path = Arc::new(gamelist_path());
    let settings_path = Arc::new(settings_path());

    std::thread::spawn(move || {
        let tx = watch_tx;
        let path = gamelist_path.clone();
        let shared_for_watcher = shared_gamelist;

        let mut watcher = match notify::recommended_watcher(
            move |res: Result<notify::Event, notify::Error>| {
                if let Ok(event) = res
                    && matches!(event.kind, EventKind::Modify(_))
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

                    info!(target: "auriya::daemon", "Gamelist file changed, reloading...");
                    let max_retries = 3;
                    let mut retry_count = 0;
                    let mut success = false;

                    while retry_count < max_retries && !success {
                        match GameList::load(&*path) {
                            Ok(new_cfg) => match shared_for_watcher.write() {
                                Ok(mut g) => {
                                    let count = new_cfg.game.len();
                                    *g = new_cfg;
                                    info!(target: "auriya::daemon", "Gamelist reloaded: {} games", count);
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

        info!(target: "auriya::daemon", "Config file watchers started");
        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    });

    watch_rx
}
