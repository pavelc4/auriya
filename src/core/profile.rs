use crate::core::tweaks::{
    cpu, gpu, init, memory, paths, sched, storage,
    vendor::{detect as soc, mtk, snapdragon},
};
use anyhow::Result;
use std::process::Command;
#[inline]
fn warn_on_err<E: std::fmt::Display>(result: Result<(), E>, context: &str) {
    if let Err(e) = result {
        tracing::warn!(target: "auriya::profile", "Failed to {}: {}", context, e);
    }
}

#[inline]
fn set_notifications(enabled: bool) {
    let val = if enabled { "1" } else { "0" };
    let _ = Command::new("settings")
        .args(["put", "global", "heads_up_notifications_enabled", val])
        .output();
}

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
        _ => tracing::debug!(target: "auriya::profile", "No vendor tweaks for: {}", soc_type),
    }

    gpu::set_performance_mode()?;

    warn_on_err(init::apply_general_tweaks(), "apply general tweaks");
    warn_on_err(sched::apply_performance_sched(), "apply scheduler tweaks");
    warn_on_err(storage::lock_storage_freq(), "lock storage freq");
    warn_on_err(memory::drop_caches(), "drop caches");
    warn_on_err(memory::adjust_for_gaming(), "apply gaming memory settings");

    if let Some(game_pid) = pid {
        cpu::set_game_affinity_dynamic(game_pid, "performance")?;
        cpu::set_process_priority(game_pid)?;
    }

    if enable_dnd {
        set_notifications(false);
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

    warn_on_err(sched::apply_balance_sched(), "apply balanced scheduler");
    warn_on_err(storage::unlock_storage_freq(), "unlock storage freq");
    warn_on_err(memory::restore_balanced(), "restore balanced memory");

    set_notifications(true);
    tracing::info!(target: "auriya::profile", "Normal mode: notifications restored");

    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying POWERSAVE profile");

    paths::set_governor_cached("powersave");
    warn_on_err(memory::apply_powersave_lmk(), "apply LMK");
    set_notifications(true);

    Ok(())
}
