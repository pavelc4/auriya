use crate::core::cmd_writer::{self, DndFilter};
use crate::core::tweaks::{
    cpu, gpu, init, memory, paths, sched, storage,
    vendor::{detect as soc, mtk, snapdragon},
};
use anyhow::Result;
use std::sync::atomic::{AtomicBool, Ordering};
use tracing::debug;

/// Set by the daemon each tick to indicate whether the companion service
/// is believed to be alive. When `false`, `request_dnd` falls back to
/// `settings put global zen_mode` directly instead of going through the
/// companion's cmd_writer path.
static COMPANION_ALIVE: AtomicBool = AtomicBool::new(true);

/// Update the global companion-alive flag. Called by the daemon tick
/// after its health check.
pub fn set_companion_alive(alive: bool) {
    COMPANION_ALIVE.store(alive, Ordering::Release);
}

#[inline]
fn warn_on_err<E: std::fmt::Display>(result: Result<(), E>, context: &str) {
    if let Err(e) = result {
        tracing::warn!(target: "auriya::profile", "Failed to {}: {}", context, e);
    }
}

/// Request the companion service to apply the given DnD filter. The
/// daemon cannot call NotificationManager itself — it would need to be
/// an app rather than a root binary — so we hand the work off through
/// the cmd file. When the companion is known dead we fall back to a
/// `settings put global zen_mode` call directly (less polished — no status
/// bar icon — but still functional).
#[inline]
fn request_dnd(filter: DndFilter) {
    if COMPANION_ALIVE.load(Ordering::Acquire) {
        if let Err(e) = cmd_writer::shared().write_dnd(filter) {
            tracing::warn!(
                target: "auriya::profile",
                "Failed to write DnD {:?} to cmd file: {}",
                filter,
                e
            );
        } else {
            debug!(target: "auriya::profile", "Requested DnD filter {:?}", filter);
        }
    } else {
        let val = match filter {
            DndFilter::All => "0",
            DndFilter::Priority => "1",
        };
        debug!(
            target: "auriya::profile",
            "DnD fallback (companion dead): zen_mode={val}"
        );
        let _ = std::process::Command::new("settings")
            .args(["put", "global", "zen_mode", val])
            .status();
    }
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
    debug!(
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
    debug!(target: "auriya::profile", "Detected SoC: {}", soc_type);

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
        request_dnd(DndFilter::Priority);
    }

    Ok(())
}

pub fn apply_performance() -> Result<()> {
    apply_performance_with_config("performance", true, None)
}

pub fn apply_balance(governor: &str) -> Result<()> {
    apply_balance_with_dnd(governor, false)
}

pub fn apply_balance_with_dnd(governor: &str, enable_dnd: bool) -> Result<()> {
    debug!(
        target: "auriya::profile",
        "Applying BALANCE profile (governor: {}, dnd: {})",
        governor,
        enable_dnd
    );

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

    // Only clear DnD if no game session asked us to keep it on. The
    // FAS Reduce path still calls into balance during a live session
    // (thermal chill / load drop) and we must not yank the user's
    // notification filter while they are still in the game.
    if !enable_dnd {
        request_dnd(DndFilter::All);
    }

    Ok(())
}

pub fn apply_gpu_boost() -> Result<()> {
    debug!(target: "auriya::profile", "Applying GPU BOOST (GPU only, CPU untouched)");
    gpu::set_performance_mode()?;
    Ok(())
}

pub fn apply_cpu_boost(governor: &str, pid: Option<i32>) -> Result<()> {
    debug!(
        target: "auriya::profile",
        "Applying CPU BOOST (governor: {}, pid: {:?})",
        governor,
        pid
    );

    paths::set_governor_cached(governor);
    cpu::enable_boost()?;
    paths::online_all_cores_cached();
    warn_on_err(sched::apply_performance_sched(), "apply scheduler tweaks");
    gpu::set_balanced_mode()?;

    if let Some(game_pid) = pid {
        cpu::set_game_affinity_dynamic(game_pid, "performance")?;
        cpu::set_process_priority(game_pid)?;
    }

    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    apply_powersave_with_dnd(false)
}

pub fn apply_powersave_with_dnd(enable_dnd: bool) -> Result<()> {
    debug!(
        target: "auriya::profile",
        "Applying POWERSAVE profile (dnd: {})",
        enable_dnd
    );

    paths::set_governor_cached("powersave");
    warn_on_err(memory::apply_powersave_lmk(), "apply LMK");

    if !enable_dnd {
        request_dnd(DndFilter::All);
    }

    Ok(())
}
