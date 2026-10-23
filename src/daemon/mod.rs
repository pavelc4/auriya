pub mod companion;
pub mod context;
pub mod engine;
pub mod ipc;
pub mod model;
pub mod pipeline;
pub mod watcher;

// Backward-compatible modules & re-exports
pub use companion::lock as companion_lock;
pub use companion::watchdog;
pub use context::DaemonContext;
pub use engine::{Daemon, run_with_config, run_with_config_and_logger};
pub use model::config::{self, DaemonConfig};
pub use model::event::{self, DaemonEvent, EventReceiver, EventSender};
pub use model::state::{self, CurrentState, LastState};
pub use pipeline::{PipelineDecision, PipelineInput, PowerSnapshot};
