use std::path::Path;
use std::time::{Duration, Instant};
use tracing::debug;

/// Wait for the status file to be produced by the companion service.
///
/// The daemon refuses to start without a companion (the file IS the
/// contract). Returns `Ok(())` once the file exists, or an error once
/// `timeout` elapses.
pub fn await_status_file(path: &Path, timeout: Duration) -> anyhow::Result<()> {
    let start = Instant::now();
    let mut logged_wait = false;
    while !path.exists() {
        if start.elapsed() >= timeout {
            return Err(anyhow::anyhow!(
                "companion status file {} not present after {:?}; \
                 is the Android companion service running?",
                path.display(),
                timeout
            ));
        }
        if !logged_wait {
            debug!(
                target: "auriya::status",
                "Waiting for companion status file at {}",
                path.display()
            );
            logged_wait = true;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    Ok(())
}
