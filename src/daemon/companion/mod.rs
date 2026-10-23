pub mod lock;
pub mod watchdog;

pub use lock::start_companion_lock_watcher;
pub use watchdog::CompanionWatchdog;
