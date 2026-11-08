pub mod apply;
pub mod decide;
pub mod fas;
pub mod ingest;

use crate::core::config::gamelist::GameList;
use crate::daemon::engine::Daemon;
use anyhow::Result;

pub use crate::core::policy::{PolicyDecision as PipelineDecision, PowerSnapshot};

/// Pipeline input collected during the Ingest phase.
#[derive(Debug, Clone)]
pub struct PipelineInput {
    pub power: PowerSnapshot,
    pub foreground_pkg: Option<String>,
    pub focused_pid: Option<i32>,
    pub package_pids: Vec<i32>,
    pub is_pid_valid: bool,
    pub is_floating: bool,
}

impl Daemon {
    /// Execute the full ingest -> decide -> apply pipeline.
    pub async fn execute_pipeline(&mut self, gamelist: &GameList) -> Result<()> {
        let input = self.ingest();
        let decision = self.decide(&input, gamelist);
        self.apply(decision, gamelist).await
    }
}
