//! Real-time companion liveness detection via advisory file locks.
//!
//! The Android companion holds an exclusive `fcntl` write lock on
//! [`COMPANION_LOCK_FILE`] for its entire lifetime. The kernel releases
//! that lock automatically the instant the process exits or is killed, so
//! the daemon can detect a dead companion *immediately* — far better than
//! the coarse "no status update for N seconds" staleness heuristic, which
//! stays as a backstop.
//!
//! A dedicated thread:
//!   1. waits (via `F_GETLK`) until the companion is holding the lock,
//!   2. blocks in `F_SETLKW` until it can take the lock — which only
//!      happens once the companion releases it (i.e. died),
//!   3. immediately releases again and emits [`DaemonEvent::CompanionDied`],
//!   4. loops, re-arming once the relaunched companion re-acquires.
//!
//! Releasing in step 3 before the daemon relaunches the companion avoids
//! a race where the new companion's own `tryAcquire` would fail.

use crate::core::config::CONFIG_DIR;
use crate::daemon::event::{DaemonEvent, EventSender};
use std::ffi::{CStr, CString};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::time::Duration;
use tracing::{debug, error, warn};

const RETRY_INTERVAL: Duration = Duration::from_secs(1);

/// Spawn the companion liveness watcher thread.
pub fn start_companion_lock_watcher(event_tx: EventSender) {
    // Same path the companion locks (`<CONFIG_DIR>/companion.lock`).
    let path = match CString::new(format!("{CONFIG_DIR}/companion.lock")) {
        Ok(p) => p,
        Err(e) => {
            error!(target: "auriya::companion", "Invalid lock path: {e}");
            return;
        }
    };

    let spawned = std::thread::Builder::new()
        .name("auriya-complock".into())
        .spawn(move || {
            debug!(target: "auriya::companion", "Companion lock watcher started");
            loop {
                // Companion should already hold the lock at this point.
                wait_until_locked(&path);

                if block_until_free(&path) {
                    warn!(
                        target: "auriya::companion",
                        "Companion liveness lock released"
                    );
                    // Dedicated thread (never joined from async) → a
                    // blocking send is safe and we want guaranteed delivery.
                    if event_tx.blocking_send(DaemonEvent::CompanionDied).is_err() {
                        // Daemon is gone; nothing left to wake.
                        return;
                    }
                } else {
                    std::thread::sleep(RETRY_INTERVAL);
                }
            }
        });

    if let Err(e) = spawned {
        error!(target: "auriya::companion", "Failed to spawn lock watcher: {e}");
    }
}

fn open_lock(path: &CStr) -> Option<OwnedFd> {
    let fd = unsafe {
        libc::open(
            path.as_ptr(),
            libc::O_RDWR | libc::O_CREAT | libc::O_CLOEXEC,
            0o600,
        )
    };
    if fd < 0 {
        return None;
    }
    Some(unsafe { OwnedFd::from_raw_fd(fd) })
}

fn make_flock(l_type: libc::c_int) -> libc::flock {
    let mut fl: libc::flock = unsafe { std::mem::zeroed() };
    fl.l_type = l_type as libc::c_short;
    fl.l_whence = libc::SEEK_SET as libc::c_short;
    fl.l_start = 0;
    fl.l_len = 0; // whole file
    fl
}

/// Poll `F_GETLK` until another process holds a write lock on the file —
/// i.e. the companion is alive and holding its lock. Retries on transient
/// open failures (e.g. the config dir not yet present).
fn wait_until_locked(path: &CStr) {
    loop {
        if let Some(fd) = open_lock(path) {
            let mut fl = make_flock(libc::F_WRLCK);
            let r = unsafe { libc::fcntl(fd.as_raw_fd(), libc::F_GETLK, &raw mut fl) };
            if r == 0 && fl.l_type != libc::F_UNLCK as libc::c_short {
                return; // someone holds it → companion alive
            }
        }
        std::thread::sleep(RETRY_INTERVAL);
    }
}

/// Block in `F_SETLKW` until we can take a write lock, which only succeeds
/// once the current holder releases — meaning the companion exited.
/// Releases immediately so a relaunched companion can re-acquire.
/// Returns `true` when the lock became free, `false` on failure.
fn block_until_free(path: &CStr) -> bool {
    let Some(fd) = open_lock(path) else {
        return false;
    };
    loop {
        let mut fl = make_flock(libc::F_WRLCK);
        let r = unsafe { libc::fcntl(fd.as_raw_fd(), libc::F_SETLKW, &raw mut fl) };
        if r == 0 {
            // Acquired → the holder had released. Drop it again at once.
            let mut un = make_flock(libc::F_UNLCK);
            unsafe {
                libc::fcntl(fd.as_raw_fd(), libc::F_SETLK, &raw mut un);
            }
            return true;
        }
        if std::io::Error::last_os_error().raw_os_error() == Some(libc::EINTR) {
            continue;
        }
        return false;
    }
}
