use super::snapshot::SystemStatus;
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};
use tracing::error;

/// If no status file update arrives within this window the companion
/// is considered dead or disconnected.
pub const COMPANION_STALE_TIMEOUT: Duration = Duration::from_secs(15);

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
    /// respective field is unset.
    pub fn power_state(&self) -> (bool, bool) {
        let guard = self.inner.read().ok();
        match guard.as_deref().and_then(|opt| opt.as_ref()) {
            Some(s) => (
                s.screen_awake.unwrap_or(true),
                s.battery_saver.unwrap_or(false),
            ),
            None => (true, false),
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

    pub fn store(&self, status: SystemStatus) {
        match self.inner.write() {
            Ok(mut g) => *g = Some(status),
            Err(e) => error!(
                target: "auriya::status",
                "SystemStatusCache write lock poisoned: {e}"
            ),
        }
        if let Ok(mut e) = self.last_event.write() {
            *e = Instant::now();
        }
    }
}
