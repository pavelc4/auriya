pub mod apply;
pub mod decide;
pub mod fas;
pub mod ingest;

use crate::core::config::gamelist::GameList;
use crate::daemon::engine::Daemon;
use anyhow::Result;

/// Subset of power state consumed by the daemon tick.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PowerSnapshot {
    pub screen_awake: bool,
    pub battery_saver: bool,
}

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

/// Decision determined during the Decide phase. Zero heap allocation via borrowed slices.
#[derive(Debug, PartialEq, Eq)]
pub enum PipelineDecision<'a> {
    Powersave,
    NoForeground,
    NonWhitelisted {
        pkg: &'a str,
        pid: Option<i32>,
        pids: &'a [i32],
        reason: &'static str,
    },
    SameAppFas {
        pkg: &'a str,
        pids: &'a [i32],
    },
    SameAppMonitor {
        pkg: &'a str,
    },
    EnterGame {
        pkg: &'a str,
        pid: i32,
        pids: &'a [i32],
    },
}

impl Daemon {
    /// Execute the full ingest -> decide -> apply pipeline.
    pub async fn execute_pipeline(&mut self, gamelist: &GameList) -> Result<()> {
        let input = self.ingest();
        let decision = self.decide(&input, gamelist);
        self.apply(decision, gamelist).await
    }
}
