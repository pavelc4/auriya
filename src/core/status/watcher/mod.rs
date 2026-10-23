pub mod await_file;
pub mod runner;

pub use await_file::await_status_file;
pub use runner::{event_touches, is_relevant, reload, run_watcher};

use crate::core::status::cache::SystemStatusCache;
use crate::core::status::snapshot::SystemStatus;
use std::path::PathBuf;
use tokio::sync::mpsc;
use tracing::{debug, warn};

pub fn start_status_watcher(
    path: PathBuf,
) -> anyhow::Result<(SystemStatusCache, mpsc::Receiver<()>)> {
    let cache = SystemStatusCache::new();
    let (tx, rx) = mpsc::channel::<()>(8);

    if let Ok(bytes) = std::fs::read(&path) {
        let parsed = SystemStatus::parse(&bytes);
        if parsed.is_populated() {
            cache.store(parsed);
            debug!(
                target: "auriya::status",
                "Pre-seeded SystemStatusCache from {}",
                path.display()
            );
        } else {
            warn!(
                target: "auriya::status",
                "Status file present but empty/unparsable at startup: {}",
                path.display()
            );
        }
    } else {
        return Err(anyhow::anyhow!(
            "failed to read status file {} during watcher startup",
            path.display()
        ));
    }

    let cache_for_thread = cache.clone();
    let path_for_thread = path.clone();

    std::thread::Builder::new()
        .name("auriya-status-watcher".into())
        .spawn(move || run_watcher(path_for_thread, cache_for_thread, tx))
        .map_err(|e| anyhow::anyhow!("spawn status watcher thread: {e}"))?;

    Ok((cache, rx))
}
