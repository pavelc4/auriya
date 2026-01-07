use crate::core::tweaks::{
    cpu, gpu, init, memory, paths, sched, storage,
    vendor::{detect as soc, mtk, snapdragon},
};
use anyhow::Result;
use std::process::Command;
#[derive(Debug, PartialEq, Eq, Clone, Copy, Default)]
pub enum ProfileMode {
    Performance,
    #[default]
    Balance,
    Powersave,
}

impl std::fmt::Display for ProfileMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ProfileMode::Performance => write!(f, "Performance"),
            ProfileMode::Balance => write!(f, "Balance"),
            ProfileMode::Powersave => write!(f, "Powersave"),
        }
    }
}

impl std::str::FromStr for ProfileMode {
    type Err = ();
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "performance" => Ok(ProfileMode::Performance),
            "balance" => Ok(ProfileMode::Balance),
            "powersave" => Ok(ProfileMode::Powersave),
            _ => Err(()),
        }
    }
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

    paths::set_governor_cached(governor);

    cpu::enable_boost()?;
    paths::online_all_cores_cached();
    let soc_type = soc::detect_soc();
    tracing::info!(target: "auriya::profile", "Detected SoC: {}", soc_type);

    match soc_type {
        soc::SocType::MediaTek => {
            let _ = mtk::apply_performance();
        }
        soc::SocType::Snapdragon => {
            let _ = snapdragon::apply_performance();
        }
        _ => {
            tracing::debug!(target: "auriya::profile", "No specific performance tweaks for SoC: {}", soc_type);
        }
    }

    gpu::set_performance_mode()?;

    if let Err(e) = init::apply_general_tweaks() {
        tracing::warn!(target: "auriya::profile", "Failed to apply general system tweaks: {}", e);
    }

    if let Err(e) = sched::apply_performance_sched() {
        tracing::warn!(target: "auriya::profile", "Failed to apply scheduler tweaks: {}", e);
    }

    if let Err(e) = storage::lock_storage_freq() {
        tracing::warn!(target: "auriya::profile", "Failed to lock storage freq: {}", e);
    }

    if let Err(e) = memory::drop_caches() {
        tracing::warn!(target: "auriya::profile", "Failed to drop caches: {}", e);
    }
    if let Err(e) = memory::adjust_for_gaming() {
        tracing::warn!(target: "auriya::profile", "Failed to apply gaming memory settings: {}", e);
    }

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

    paths::set_governor_cached(governor);

    cpu::disable_boost()?;

    let soc_type = soc::detect_soc();
    match soc_type {
        soc::SocType::MediaTek => {
            let _ = mtk::apply_normal();
        }
        soc::SocType::Snapdragon => {
            let _ = snapdragon::apply_normal();
        }
        _ => {}
    }
    gpu::set_balanced_mode()?;

    // Restore balanced scheduler settings
    if let Err(e) = sched::apply_balance_sched() {
        tracing::warn!(target: "auriya::profile", "Failed to apply balanced scheduler tweaks: {}", e);
    }

    // Unlock storage frequency
    if let Err(e) = storage::unlock_storage_freq() {
        tracing::warn!(target: "auriya::profile", "Failed to unlock storage freq: {}", e);
    }

    if let Err(e) = memory::restore_balanced() {
        tracing::warn!(target: "auriya::profile", "Failed to restore balanced memory settings: {}", e);
    }

    let _ = Command::new("settings")
        .args(["put", "global", "heads_up_notifications_enabled", "1"])
        .output();

    tracing::info!(target: "auriya::profile", "Normal mode: notifications restored");

    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying POWERSAVE profile");

    paths::set_governor_cached("powersave");

    if let Err(e) = memory::apply_powersave_lmk() {
        tracing::warn!(target: "auriya::profile", "Failed to apply LMK: {}", e);
    }

    let _ = Command::new("settings")
        .args(["put", "global", "heads_up_notifications_enabled", "1"])
        .output();

    Ok(())
}
