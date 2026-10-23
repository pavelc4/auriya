use crate::core::config::{GameList, gamelist_path};
use notify::{EventKind, RecursiveMode, Watcher};
use std::sync::{Arc, RwLock};
use tokio::sync::mpsc;
use tracing::{debug, error, warn};

pub fn start_config_watcher(shared_gamelist: Arc<RwLock<Arc<GameList>>>) -> mpsc::Receiver<String> {
    let (watch_tx, watch_rx) = mpsc::channel::<String>(10);
    let gamelist_path = Arc::new(gamelist_path());

    std::thread::spawn(move || {
        let tx = watch_tx;
        let path = gamelist_path.clone();
        let shared_for_watcher = shared_gamelist;

        let mut watcher =
            match notify::recommended_watcher(move |res: Result<notify::Event, notify::Error>| {
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

                    if !path_str.contains("gamelist") || is_ignorable_file(&path_str) {
                        return;
                    }

                    debug!(target: "auriya::daemon", "Gamelist file changed, reloading...");
                    reload_gamelist_with_retry(&path, &shared_for_watcher);
                    let _ = tx.blocking_send("gamelist".to_string());
                }
            }) {
                Ok(w) => w,
                Err(e) => {
                    error!(target: "auriya::daemon", "Failed to create gamelist watcher: {}", e);
                    return;
                }
            };

        let config_dir = std::path::Path::new(crate::core::config::CONFIG_DIR);
        let _ = std::fs::create_dir_all(config_dir);

        if let Err(e) = watcher.watch(config_dir, RecursiveMode::NonRecursive) {
            error!(
                target: "auriya::daemon",
                "Failed to watch config directory {}: {}",
                config_dir.display(),
                e
            );
            return;
        }

        debug!(
            target: "auriya::daemon",
            "Config directory watcher started on {}",
            config_dir.display()
        );
        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    });

    watch_rx
}

pub fn is_ignorable_file(path_str: &str) -> bool {
    path_str.contains(".tmp") || path_str.ends_with('~') || path_str.contains(".swp")
}

pub fn reload_gamelist_with_retry(path: &std::path::Path, shared: &RwLock<Arc<GameList>>) -> bool {
    let max_retries = 3;
    let mut retry_count = 0;

    while retry_count < max_retries {
        match GameList::load(path) {
            Ok(new_cfg) => match shared.write() {
                Ok(mut g) => {
                    let count = new_cfg.game.len();
                    *g = Arc::new(new_cfg);
                    debug!(target: "auriya::daemon", "Gamelist reloaded: {} games", count);
                    return true;
                }
                Err(_) => {
                    error!(target: "auriya::daemon", "Failed to acquire gamelist lock");
                    return false;
                }
            },
            Err(e) => {
                retry_count += 1;
                if retry_count < max_retries {
                    warn!(
                        target: "auriya::daemon",
                        "Failed reloading gamelist (attempt {}/{}): {:?}, retrying in 2s...",
                        retry_count, max_retries, e
                    );
                    std::thread::sleep(std::time::Duration::from_secs(2));
                } else {
                    error!(
                        target: "auriya::daemon",
                        "Failed to reload gamelist after {} attempts: {:?}",
                        max_retries, e
                    );
                }
            }
        }
    }
    false
}
