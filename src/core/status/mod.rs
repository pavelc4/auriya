pub mod cache;
pub mod snapshot;
pub mod watcher;

pub use cache::{COMPANION_STALE_TIMEOUT, SystemStatusCache};
pub use snapshot::SystemStatus;
pub use watcher::{await_status_file, start_status_watcher};

/// Default location of the status file written by the companion service.
pub const STATUS_FILE: &str = "/data/adb/.config/auriya/system_status";
