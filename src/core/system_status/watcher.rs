// Inotify watcher + cache for the system_status file written by the
// Android companion service.
//
// Design:
//   - `SystemStatusCache` is a cheap-to-clone snapshot holder backed by
//     `Arc<RwLock<Option<SystemStatus>>>`. The daemon tick loop reads
//     from it lock-free in the common case.
//   - `start_status_watcher` spawns a dedicated thread that watches the
//     file via the `notify` crate (inotify under the hood on Linux),
//     re-parses on every `IN_CLOSE_WRITE`, and updates the cache.
//   - Atomic writes from the companion side (`tmp → rename`) appear as
//     a single coherent event, so the daemon never sees a half-written
//     file.

use super::SystemStatus;
use notify::{EventKind, RecursiveMode, Watcher, event::ModifyKind};
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};
use tokio::sync::mpsc;
use tracing::{debug, error, warn};

/// Thread-safe holder of the latest parsed system status.
///
/// Cheap to clone — internally just an `Arc`. Read paths take a read
/// lock that returns immediately in the absence of a writer, so the
/// tick loop pays almost nothing per tick.
#[derive(Debug, Clone)]
pub struct SystemStatusCache {
    inner: Arc<RwLock<Option<SystemStatus>>>,
    /// Timestamp of the last successful file parse. Updated by the
    /// watcher thread every time it reads a new snapshot. The daemon
    /// uses this to detect a crashed companion (if too stale).
    last_event: Arc<RwLock<Instant>>,
}

impl Default for SystemStatusCache {
    fn default() -> Self {
        Self {
            inner: Arc::default(),
            last_event: Arc::new(RwLock::new(Instant::now())),
        }
    }
}

impl SystemStatusCache {
    pub fn new() -> Self {
        Self::default()
    }

    /// Convenience: return the focused package name from the latest
    /// snapshot, if any.
    pub fn focused_package(&self) -> Option<String> {
        self.inner
            .read()
            .ok()
            .and_then(|g| g.as_ref().and_then(|s| s.focused_app.clone()))
    }

    /// Convenience: return the focused PID from the latest snapshot.
    pub fn focused_pid(&self) -> Option<i32> {
        self.inner
            .read()
            .ok()
            .and_then(|g| g.as_ref().and_then(|s| s.focused_pid))
    }

    /// Convenience: derive the (`screen_awake`, `battery_saver`) pair
    /// the tick loop needs. Defaults to `(true, false)` when the
    /// respective field is unset — the daemon falls back to "best case"
    /// so it does not aggressively powersave when the snapshot is
    /// incomplete.
    pub fn power_state(&self) -> (bool, bool) {
        // Read the two bools out under the guard instead of deep-cloning
        // the whole SystemStatus (which carries a String) every tick.
        match self.inner.read() {
            Ok(g) => match g.as_ref() {
                Some(s) => (
                    s.screen_awake.unwrap_or(true),
                    s.battery_saver.unwrap_or(false),
                ),
                None => (true, false),
            },
            Err(_) => (true, false),
        }
    }

    /// Time since the last successful status file parse. The daemon
    /// uses this to detect a crashed companion: if more than
    /// [`COMPANION_STALE_TIMEOUT`] has elapsed the companion is
    /// presumed dead.
    pub fn elapsed_since_last_event(&self) -> Duration {
        self.last_event
            .read()
            .map(|t| t.elapsed())
            .unwrap_or(Duration::MAX)
    }

    fn store(&self, status: SystemStatus) {
        match self.inner.write() {
            Ok(mut g) => *g = Some(status),
            Err(e) => error!(
                target: "auriya::status",
                "SystemStatusCache write lock poisoned: {e}"
            ),
        }
        // Bump the event timestamp on every successful store so the
        // daemon can detect staleness.
        if let Ok(mut e) = self.last_event.write() {
            *e = Instant::now();
        }
    }
}

/// If no status file update arrives within this window the companion
/// is considered dead or disconnected.
pub const COMPANION_STALE_TIMEOUT: Duration = Duration::from_secs(15);

/// Wait for the status file to be produced by the companion service.
///
/// The daemon refuses to start without a companion (the file IS the
/// contract). Returns `Ok(())` once the file exists, or an error once
/// `timeout` elapses.
pub fn await_status_file(path: &Path, timeout: Duration) -> anyhow::Result<()> {
    let start = Instant::now();
    let mut logged_wait = false;
    while !path.exists() {
        if start.elapsed() >= timeout {
            return Err(anyhow::anyhow!(
                "companion status file {} not present after {:?}; \
                 is the Android companion service running?",
                path.display(),
                timeout
            ));
        }
        if !logged_wait {
            debug!(
                target: "auriya::status",
                "Waiting for companion status file at {}",
                path.display()
            );
            logged_wait = true;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    Ok(())
}

/// Start the background watcher. Returns the populated cache plus a
/// receiver that fires whenever a new snapshot has been parsed (the
/// daemon uses this to break out of its sleep in `tokio::select!`).
///
/// The watcher pre-seeds the cache from the on-disk file before
/// returning, so the first daemon tick already sees real data.
pub fn start_status_watcher(
    path: PathBuf,
) -> anyhow::Result<(SystemStatusCache, mpsc::Receiver<()>)> {
    let cache = SystemStatusCache::new();
    let (tx, rx) = mpsc::channel::<()>(8);

    // Pre-seed from disk so the first tick is not blind.
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

fn run_watcher(path: PathBuf, cache: SystemStatusCache, tx: mpsc::Sender<()>) {
    // Watch the parent directory rather than the file itself, because
    // the companion writes via `tmp → rename` and the original inode
    // gets replaced on every update. Watching the directory lets us
    // see the rename target reliably.
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

    // Park the thread forever — the `notify` crate keeps its own
    // internal thread for the inotify fd; this outer thread just owns
    // the watcher so it does not get dropped.
    loop {
        std::thread::park();
    }
}

fn is_relevant(event: &notify::Event) -> bool {
    matches!(
        event.kind,
        EventKind::Create(_)
            | EventKind::Modify(ModifyKind::Data(_))
            | EventKind::Modify(ModifyKind::Name(_))
            | EventKind::Modify(ModifyKind::Any)
            | EventKind::Modify(ModifyKind::Other)
    )
}

fn event_touches(event: &notify::Event, filename: Option<&std::ffi::OsStr>) -> bool {
    let Some(name) = filename else {
        return true;
    };
    event.paths.iter().any(|p| p.file_name() == Some(name))
}

fn reload(path: &Path, cache: &SystemStatusCache, tx: &mpsc::Sender<()>) {
    let bytes = match std::fs::read(path) {
        Ok(b) => b,
        Err(e) => {
            // The companion may be mid-rename; retry once after a tiny
            // sleep before warning.
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
    // Non-blocking send — the daemon may be busy and skip a wake-up;
    // that's fine, the next tick will read the latest snapshot anyway.
    let _ = tx.try_send(());
}
