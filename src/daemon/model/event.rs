use tokio::sync::mpsc;

/// An event that should wake the daemon's tick loop immediately.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DaemonEvent {
    /// The tracked foreground game process (this PID) exited.
    PidExited(i32),
    /// The Android companion service released its liveness lock — it was
    /// killed or crashed. The daemon should mark it dead and relaunch it.
    CompanionDied,
    /// A module update was staged (`<MODULE_PATH>/update` appeared). The
    /// daemon should stop gracefully so the new version takes over on the
    /// next boot.
    ModuleUpdate,
}

pub type EventSender = mpsc::Sender<DaemonEvent>;
pub type EventReceiver = mpsc::Receiver<DaemonEvent>;

/// Create the daemon event channel. The buffer is small — these events
/// are rare and the consumer drains them promptly.
pub fn channel() -> (EventSender, EventReceiver) {
    mpsc::channel(16)
}
