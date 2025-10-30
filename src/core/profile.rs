use anyhow::{Context, Result};
use std::process::Command;

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
}

impl Default for ProfileMode {
    fn default() -> Self {
        ProfileMode::Balance
    }
}

pub fn apply_performance_with_config(governor: &str, enable_dnd: bool) -> Result<()> {
    tracing::info!(
        target: "auriya::profile",
        "Applying PERFORMANCE profile (governor: {}, dnd: {})",
        governor,
        enable_dnd
    );

    let cmd = format!(
        "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo {} > \"$cpu\" 2>/dev/null; done",
        governor
    );

    let output = Command::new("sh")
        .args(["-c", &cmd])
        .output()
        .context("Failed to execute CPU governor command")?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        tracing::warn!(
            target: "auriya::profile",
            "CPU governor command exited with error: {}",
            stderr
        );
    }

    if enable_dnd {
        let dnd_result = Command::new("cmd")
            .args(["notification", "set_dnd", "on"])
            .output();

        match dnd_result {
            Ok(out) if !out.status.success() => {
                tracing::warn!(
                    target: "auriya::profile",
                    "DND command failed: {}",
                    String::from_utf8_lossy(&out.stderr)
                );
            }
            Err(e) => {
                tracing::warn!(target: "auriya::profile", "Failed to set DND: {:#}", e);
            }
            _ => {}
        }
    }

    Ok(())
}

pub fn apply_performance() -> Result<()> {
    apply_performance_with_config("performance", true)
}

pub fn apply_balance(governor: &str) -> Result<()> {
    tracing::info!(
        target: "auriya::profile",
        "Applying BALANCE profile (governor: {})",
        governor
    );

    let cmd = format!(
        "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo {} > \"$cpu\" 2>/dev/null; done",
        governor
    );

    let output = Command::new("sh")
        .args(["-c", &cmd])
        .output()
        .context("Failed to execute CPU governor command")?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        tracing::warn!(
            target: "auriya::profile",
            "CPU governor command exited with error: {}",
            stderr
        );
    }

    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying POWERSAVE profile");

    let output = Command::new("sh")
        .args([
            "-c",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo powersave > \"$cpu\" 2>/dev/null; done"
        ])
        .output()
        .context("Failed to execute CPU governor command")?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        tracing::warn!(
            target: "auriya::profile",
            "CPU governor command exited with error: {}",
            stderr
        );
    }

    let _ = Command::new("cmd")
        .args(["notification", "set_dnd", "off"])
        .output();

    Ok(())
}
