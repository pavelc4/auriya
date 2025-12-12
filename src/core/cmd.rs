use anyhow::{Context, Result};
use std::process::Output;
use std::time::Duration;
use tokio::process::Command as TokioCommand;

pub async fn run_cmd_timeout_async(
    program: &str,
    args: &[&str],
    timeout_ms: u64,
) -> Result<Output> {
    let timeout = Duration::from_millis(timeout_ms);

    tokio::time::timeout(timeout, TokioCommand::new(program).args(args).output())
        .await
        .context("Command timeout")?
        .with_context(|| format!("Failed to execute: {}", program))
}
