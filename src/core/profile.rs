use crate::core::tweaks::{cpu, gpu, memory};
use anyhow::{Context, Result};
use std::process::Command;

#[derive(Debug, PartialEq, Eq, Clone, Copy, Default)]
pub enum ProfileMode {
    Performance,
    #[default]
    Balance,
    Powersave,
}

pub fn apply_performance_with_config(
    governor: &str,
    enable_dnd: bool,
    pid: Option<i32>,
) -> Result<()> {
    tracing::info!(
        target: "auriya::profile",
        "Applying PERFORMANCE profile (governor: {}, suppress_notifs: {}, pid: {:?})",
        governor,
        enable_dnd,
        pid
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

    cpu::enable_boost()?;
    cpu::online_all_cores()?;
    gpu::set_performance_mode()?;

    memory::drop_caches()?;
    memory::adjust_for_gaming()?;

    if let Some(game_pid) = pid {
        cpu::set_game_affinity_dynamic(game_pid, "performance")?;
        cpu::set_process_priority(game_pid)?;
    }

    if enable_dnd {
        let _ = Command::new("settings")
            .args(["put", "global", "heads_up_notifications_enabled", "0"])
            .output();

        tracing::info!(target: "auriya::profile", "Gaming mode: notifications silenced");
    }

    Ok(())
}

pub fn apply_performance() -> Result<()> {
    apply_performance_with_config("performance", true, None)
}

pub fn apply_balance(governor: &str) -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying BALANCE profile (governor: {})", governor);

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
        tracing::warn!(target: "auriya::profile", "CPU governor command exited with error: {}", stderr);
    }

    cpu::disable_boost()?;
    gpu::set_balanced_mode()?;
    memory::restore_balanced()?;

    let _ = Command::new("settings")
        .args(["put", "global", "heads_up_notifications_enabled", "1"])
        .output();

    tracing::info!(target: "auriya::profile", "Normal mode: notifications restored");

    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying POWERSAVE profile");

    let output = Command::new("sh")
        .args([
            "-c",
            "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo powersave > \"$cpu\" 2>/dev/null; done",
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
    if let Err(e) = memory::apply_powersave_lmk() {
        tracing::warn!(target: "auriya::profile", "Failed to apply LMK: {}", e);
    }

    let _ = Command::new("settings")
        .args(["put", "global", "heads_up_notifications_enabled", "1"])
        .output();

    Ok(())
}
