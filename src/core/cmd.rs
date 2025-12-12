use anyhow::{Context, Result};
use std::process::{Command, Output};
use std::time::Duration;
use tokio::process::Command as TokioCommand;

pub async fn run_cmd_timeout_async(
    program: &str,
    args: &[&str],
    timeout_ms: u64,
) -> Result<Output> {
    let timeout = Duration::from_millis(timeout_ms);

    tokio::time::timeout(
        timeout,
        TokioCommand::new(program).args(args).output(),
    )
    .await
    .context("Command timeout")?
    .with_context(|| format!("Failed to execute: {}", program))
}

pub fn run_cmd_timeout_sync(
    program: &str,
    args: &[&str],
    timeout_ms: u64,
) -> Result<Output> {
    use std::sync::mpsc;
    use std::thread;

    let program = program.to_string();
    let args: Vec<String> = args.iter().map(|s| s.to_string()).collect();
    let (tx, rx) = mpsc::channel();

    thread::spawn(move || {
        let result = Command::new(&program)
            .args(&args)
            .output()
            .with_context(|| format!("Failed to execute: {}", program));
        let _ = tx.send(result);
    });

    rx.recv_timeout(Duration::from_millis(timeout_ms))
        .context("Command timeout")?
}
