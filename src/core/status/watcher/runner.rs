use crate::core::status::cache::SystemStatusCache;
use crate::core::status::snapshot::SystemStatus;
use notify::{EventKind, RecursiveMode, Watcher, event::ModifyKind};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::sync::mpsc;
use tracing::{debug, error, warn};

pub fn run_watcher(path: PathBuf, cache: SystemStatusCache, tx: mpsc::Sender<()>) {
    let parent = match path.parent() {
        Some(p) => p.to_path_buf(),
        None => {
            error!(
                target: "auriya::status",
                "Status file path {} has no parent directory",
                path.display()
            );
            return;
        }
    };
    let filename = path.file_name().map(|n| n.to_os_string());

    let mut watcher = match notify::recommended_watcher({
        let cache = cache.clone();
        let tx = tx.clone();
        let path = path.clone();
        let filename = filename.clone();
        move |res: Result<notify::Event, notify::Error>| {
            let Ok(event) = res else {
                return;
            };
            if !is_relevant(&event) {
                return;
            }
            if !event_touches(&event, filename.as_deref()) {
                return;
            }
            reload(&path, &cache, &tx);
        }
    }) {
        Ok(w) => w,
        Err(e) => {
            error!(
                target: "auriya::status",
                "Failed to create status watcher: {e}"
            );
            return;
        }
    };

    if let Err(e) = watcher.watch(&parent, RecursiveMode::NonRecursive) {
        error!(
            target: "auriya::status",
            "Failed to watch {}: {e}",
            parent.display()
        );
        return;
    }

    debug!(
        target: "auriya::status",
        "Status watcher started (watching {})",
        parent.display()
    );

    loop {
        std::thread::park();
    }
}

pub fn is_relevant(event: &notify::Event) -> bool {
    matches!(
        event.kind,
        EventKind::Create(_)
            | EventKind::Modify(ModifyKind::Data(_))
            | EventKind::Modify(ModifyKind::Name(_))
            | EventKind::Modify(ModifyKind::Any)
            | EventKind::Modify(ModifyKind::Other)
    )
}

pub fn event_touches(event: &notify::Event, filename: Option<&std::ffi::OsStr>) -> bool {
    let Some(name) = filename else {
        return true;
    };
    event.paths.iter().any(|p| p.file_name() == Some(name))
}

pub fn reload(path: &Path, cache: &SystemStatusCache, tx: &mpsc::Sender<()>) {
    let bytes = match std::fs::read(path) {
        Ok(b) => b,
        Err(e) => {
            std::thread::sleep(Duration::from_millis(5));
            match std::fs::read(path) {
                Ok(b) => b,
                Err(_) => {
                    warn!(
                        target: "auriya::status",
                        "Failed to read status file on event: {e}"
                    );
                    return;
                }
            }
        }
    };

    let parsed = SystemStatus::parse(&bytes);
    if !parsed.is_populated() {
        warn!(
            target: "auriya::status",
            "Parsed status update was empty; ignoring"
        );
        return;
    }

    cache.store(parsed);
    let _ = tx.try_send(());
}
