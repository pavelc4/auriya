use anyhow::Result;
use std::process::Command;
use tracing::{debug, error};

pub fn is_low_power_mode_enabled() -> Result<bool> {
    let output = Command::new("/system/bin/cmd")
        .args(["settings", "get", "global", "low_power"])
        .output()?;

    if output.status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if stdout == "1" {
            debug!("System Battery Saver is ENABLED");
            return Ok(true);
        }
    } else {
        error!(
            "Failed to check low_power settings: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    Ok(false)
}
