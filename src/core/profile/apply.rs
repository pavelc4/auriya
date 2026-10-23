use crate::platform::{
    common::{init, memory, sched, storage, touchpanel},
    cpu::{governor as paths, topology as cpu},
    gpu,
    vendor::{detect as soc, mtk, snapdragon},
};
use anyhow::Result;
use tracing::debug;

#[inline]
fn warn_on_err<E: std::fmt::Display>(result: Result<(), E>, context: &str) {
    if let Err(e) = result {
        tracing::warn!(target: "auriya::profile", "Failed to {}: {}", context, e);
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
    warn_on_err(
        touchpanel::enable_game_mode(),
        "enable touchpanel game mode",
    );

    warn_on_err(init::apply_general_tweaks(), "apply general tweaks");
    warn_on_err(sched::apply_performance_sched(), "apply scheduler tweaks");
    warn_on_err(storage::lock_storage_freq(), "lock storage freq");
    warn_on_err(memory::drop_caches(), "drop caches");
    warn_on_err(memory::adjust_for_gaming(), "apply gaming memory settings");

    if let Some(game_pid) = pid {
        cpu::set_game_affinity_dynamic(game_pid, "performance")?;
        cpu::set_process_priority(game_pid)?;
    }

    let _ = enable_dnd;
    Ok(())
}

pub fn apply_performance() -> Result<()> {
    apply_performance_with_config("performance", true, None)
}

pub fn apply_fast() -> Result<()> {
    debug!(target: "auriya::profile", "Applying FAST profile (zero margin boost)");
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
    warn_on_err(
        touchpanel::disable_game_mode(),
        "disable touchpanel game mode",
    );

    warn_on_err(sched::apply_balance_sched(), "apply balanced scheduler");
    warn_on_err(storage::unlock_storage_freq(), "unlock storage freq");
    warn_on_err(memory::restore_balanced(), "restore balanced memory");

    let _ = enable_dnd;
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

pub fn apply_ceiling(
    controller: &mut crate::platform::cpu::ceiling::CeilingController,
    level: crate::platform::cpu::ceiling::CeilingLevel,
    config: &crate::platform::cpu::ceiling::CeilingConfig,
) {
    if let Err(e) = controller.apply(level, config) {
        tracing::warn!(target: "auriya::ceiling", "Failed to apply ceiling {:?}: {e}", level);
    }
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
    warn_on_err(
        touchpanel::disable_game_mode(),
        "disable touchpanel game mode",
    );

    warn_on_err(sched::apply_balance_sched(), "apply balanced scheduler");
    warn_on_err(storage::unlock_storage_freq(), "unlock storage freq");
    warn_on_err(memory::restore_balanced(), "restore balanced memory");
    warn_on_err(memory::set_swappiness(60), "set powersave swappiness");

    let _ = enable_dnd;
    Ok(())
}
