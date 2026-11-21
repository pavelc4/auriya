use anyhow::Result;
use std::borrow::Cow;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::sync::Mutex;
use tracing::{debug, info, warn};

use crate::core::scaling::walt::{
    ClusterSpec, WaltCalculatedTuning, WaltCalculator, WaltCard, WaltProfileMode,
};

const WALT_SYSFS_DIR: &str = "/proc/sys/walt";

#[derive(Debug, Clone, Default)]
struct WaltStockBackup {
    upmigrate: Option<String>,
    downmigrate: Option<String>,
    group_upmigrate: Option<String>,
    group_downmigrate: Option<String>,
    window_stats_policy: Option<String>,
    ravg_window: Option<String>,
    coloc_busy_hysteresis: Option<String>,
}

#[derive(Debug)]
struct WaltState {
    enabled: bool,
    auto_tune: bool,
    active: bool,
    current_mode: WaltProfileMode,
    current_tuning: Option<WaltCalculatedTuning>,
    stock_backup: Option<WaltStockBackup>,
    clusters: Vec<ClusterSpec>,
}

impl WaltState {
    fn new() -> Self {
        let clusters = detect_cluster_specs();
        Self {
            enabled: true,
            auto_tune: true,
            active: false,
            current_mode: WaltProfileMode::Balance,
            current_tuning: None,
            stock_backup: None,
            clusters,
        }
    }
}

static WALT_STATE: Mutex<Option<WaltState>> = Mutex::new(None);

fn with_walt<F, R>(f: F) -> R
where
    F: FnOnce(&mut WaltState) -> R,
{
    let mut guard = WALT_STATE
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let state = guard.get_or_insert_with(WaltState::new);
    f(state)
}

/// Checks whether Qualcomm WALT scheduler sysfs nodes are available.
pub fn is_walt_supported() -> bool {
    Path::new(WALT_SYSFS_DIR).is_dir()
}

/// Detect cluster layout and capacities from sysfs.
pub fn detect_cluster_specs() -> Vec<ClusterSpec> {
    let mut capacity_to_cpus: BTreeMap<u32, Vec<usize>> = BTreeMap::new();

    for cpu_id in 0..32 {
        let cap_path = format!("/sys/devices/system/cpu/cpu{cpu_id}/cpu_capacity");
        let parsed_cap = fs::read_to_string(&cap_path)
            .ok()
            .and_then(|s| s.trim().parse::<u32>().ok());
        match parsed_cap {
            Some(cap_val) => {
                capacity_to_cpus.entry(cap_val).or_default().push(cpu_id);
            }
            None => continue,
        }
    }

    match capacity_to_cpus.is_empty() {
        true => vec![
            ClusterSpec::new(0, vec![0, 1, 2, 3], 271),
            ClusterSpec::new(1, vec![4, 5, 6], 827),
            ClusterSpec::new(2, vec![7], 1024),
        ],
        false => capacity_to_cpus
            .into_iter()
            .enumerate()
            .map(|(cluster_id, (capacity, cpu_ids))| {
                ClusterSpec::new(cluster_id, cpu_ids, capacity)
            })
            .collect(),
    }
}

fn backup_stock_if_needed(state: &mut WaltState) {
    match (state.stock_backup.is_some(), is_walt_supported()) {
        (true, _) | (_, false) => return,
        (false, true) => {}
    }

    let dir = Path::new(WALT_SYSFS_DIR);
    let backup = WaltStockBackup {
        upmigrate: fs::read_to_string(dir.join("sched_upmigrate"))
            .ok()
            .map(|s| s.trim().to_string()),
        downmigrate: fs::read_to_string(dir.join("sched_downmigrate"))
            .ok()
            .map(|s| s.trim().to_string()),
        group_upmigrate: fs::read_to_string(dir.join("sched_group_upmigrate"))
            .ok()
            .map(|s| s.trim().to_string()),
        group_downmigrate: fs::read_to_string(dir.join("sched_group_downmigrate"))
            .ok()
            .map(|s| s.trim().to_string()),
        window_stats_policy: fs::read_to_string(dir.join("sched_window_stats_policy"))
            .ok()
            .map(|s| s.trim().to_string()),
        ravg_window: fs::read_to_string(dir.join("sched_ravg_window"))
            .ok()
            .map(|s| s.trim().to_string()),
        coloc_busy_hysteresis: fs::read_to_string(
            dir.join("sched_coloc_busy_hysteresis_enable_cpus"),
        )
        .ok()
        .map(|s| s.trim().to_string()),
    };

    debug!("Captured WALT stock settings: {:?}", backup);
    state.stock_backup = Some(backup);
}

fn write_walt_file(filename: &str, value: &str) -> Result<()> {
    let path = Path::new(WALT_SYSFS_DIR).join(filename);
    if path.exists() {
        fs::write(&path, value)?;
        debug!("WALT wrote {} -> {}", filename, value);
    }
    Ok(())
}

fn write_migration_pair(up_file: &str, up_val: &str, down_file: &str, down_val: &str) {
    // Qualcomm WALT enforces upmigrate >= downmigrate at all times.
    // When lowering thresholds, downmigrate must be written first.
    // When raising thresholds, upmigrate must be written first.
    if write_walt_file(down_file, down_val).is_ok() {
        if let Err(e) = write_walt_file(up_file, up_val) {
            warn!("Failed to write {}: {}", up_file, e);
        }
    } else {
        if let Err(e) = write_walt_file(up_file, up_val) {
            warn!("Failed to write {}: {}", up_file, e);
        }
        if let Err(e) = write_walt_file(down_file, down_val) {
            warn!("Failed to write {}: {}", down_file, e);
        }
    }
}

fn apply_mode_internal(state: &mut WaltState, mode: WaltProfileMode) -> Result<()> {
    match state.enabled {
        false => {
            debug!("WALT tuning disabled by user configuration");
            Ok(())
        }
        true => {
            backup_stock_if_needed(state);

            let tuning = match (state.auto_tune, state.current_tuning.as_ref()) {
                (true, _) => WaltCalculator::calculate_tuning(&state.clusters, mode),
                (false, Some(t)) => t.clone(),
                (false, None) => WaltCalculator::calculate_tuning(&state.clusters, mode),
            };

            write_migration_pair(
                "sched_upmigrate",
                &tuning.upmigrate_str(),
                "sched_downmigrate",
                &tuning.downmigrate_str(),
            );
            write_migration_pair(
                "sched_group_upmigrate",
                &tuning.group_upmigrate.to_string(),
                "sched_group_downmigrate",
                &tuning.group_downmigrate.to_string(),
            );

            if let Err(e) = write_walt_file(
                "sched_window_stats_policy",
                &tuning.window_stats_policy.to_string(),
            ) {
                warn!("Failed to write sched_window_stats_policy: {}", e);
            }
            if let Err(e) = write_walt_file("sched_ravg_window", &tuning.ravg_window.to_string()) {
                warn!("Failed to write sched_ravg_window: {}", e);
            }
            if let Err(e) = write_walt_file(
                "sched_coloc_busy_hysteresis_enable_cpus",
                &tuning.coloc_busy_hysteresis_enable_cpus.to_string(),
            ) {
                warn!(
                    "Failed to write sched_coloc_busy_hysteresis_enable_cpus: {}",
                    e
                );
            }

            info!(
                "Applied WALT tuning (mode={}, upmigrate={}, downmigrate={}, window_policy={})",
                mode.as_str(),
                tuning.upmigrate_str(),
                tuning.downmigrate_str(),
                tuning.window_stats_policy
            );

            state.current_mode = mode;
            state.current_tuning = Some(tuning);
            state.active = true;

            Ok(())
        }
    }
}

fn restore_stock_internal(state: &mut WaltState) -> Result<()> {
    match state.stock_backup.as_ref() {
        None => {
            debug!("No WALT stock backup available to restore");
            Ok(())
        }
        Some(backup) => {
            if let (Some(u), Some(d)) = (&backup.upmigrate, &backup.downmigrate) {
                write_migration_pair("sched_upmigrate", u, "sched_downmigrate", d);
            }
            if let (Some(gu), Some(gd)) = (&backup.group_upmigrate, &backup.group_downmigrate) {
                write_migration_pair("sched_group_upmigrate", gu, "sched_group_downmigrate", gd);
            }
            if let Some(ref v) = backup.window_stats_policy {
                let _ = write_walt_file("sched_window_stats_policy", v);
            }
            if let Some(ref v) = backup.ravg_window {
                let _ = write_walt_file("sched_ravg_window", v);
            }
            if let Some(ref v) = backup.coloc_busy_hysteresis {
                let _ = write_walt_file("sched_coloc_busy_hysteresis_enable_cpus", v);
            }

            debug!("Restored WALT stock settings");
            state.active = false;
            state.current_mode = WaltProfileMode::Balance;

            Ok(())
        }
    }
}

/// Apply calculated WALT tunables for the requested profile mode.
pub fn apply_mode(mode: WaltProfileMode) -> Result<()> {
    match is_walt_supported() {
        false => Ok(()),
        true => with_walt(|state| apply_mode_internal(state, mode)),
    }
}

/// Restore original stock WALT tunables.
pub fn restore_stock() -> Result<()> {
    match is_walt_supported() {
        false => Ok(()),
        true => with_walt(restore_stock_internal),
    }
}

/// Convenience hook for gaming performance profile.
pub fn apply_performance() -> Result<()> {
    apply_mode(WaltProfileMode::Performance)
}

/// Convenience hook for normal/balanced mode.
pub fn apply_normal() -> Result<()> {
    apply_mode(WaltProfileMode::Balance)
}

/// Enable or disable WALT tuning at runtime.
pub fn set_enabled(enabled: bool) -> String {
    with_walt(|state| {
        state.enabled = enabled;
        match enabled {
            false => {
                let _ = restore_stock_internal(state);
                "OK WALT DISABLED (STOCK RESTORED)".to_string()
            }
            true => {
                let _ = apply_mode_internal(state, state.current_mode);
                "OK WALT ENABLED".to_string()
            }
        }
    })
}

/// Set auto-tuning mode.
pub fn set_auto_tune(auto_tune: bool) -> String {
    with_walt(|state| {
        state.auto_tune = auto_tune;
        match auto_tune {
            true => {
                let _ = apply_mode_internal(state, state.current_mode);
                "OK WALT AUTO_TUNE ENABLED".to_string()
            }
            false => "OK WALT AUTO_TUNE DISABLED".to_string(),
        }
    })
}

/// Get telemetry snapshot card for WALT.
pub fn get_status_card() -> WaltCard {
    match is_walt_supported() {
        false => WaltCard {
            supported: false,
            active: false,
            auto_tune: false,
            mode: Cow::Borrowed("unsupported"),
            upmigrate: Vec::new(),
            downmigrate: Vec::new(),
            group_upmigrate: None,
            group_downmigrate: None,
            window_policy: None,
            ravg_window_ms: None,
        },
        true => with_walt(|state| {
            let (up, down, grp_up, grp_down, policy, window_ms) =
                match state.current_tuning.as_ref() {
                    Some(t) => (
                        t.upmigrate.clone(),
                        t.downmigrate.clone(),
                        Some(t.group_upmigrate),
                        Some(t.group_downmigrate),
                        Some(t.window_stats_policy),
                        Some((t.ravg_window / 1_000_000) as u32),
                    ),
                    None => {
                        let dir = Path::new(WALT_SYSFS_DIR);
                        let up = match fs::read_to_string(dir.join("sched_upmigrate")) {
                            Ok(s) => s
                                .split_whitespace()
                                .filter_map(|x| x.parse().ok())
                                .collect(),
                            Err(_) => Vec::new(),
                        };
                        let down = match fs::read_to_string(dir.join("sched_downmigrate")) {
                            Ok(s) => s
                                .split_whitespace()
                                .filter_map(|x| x.parse().ok())
                                .collect(),
                            Err(_) => Vec::new(),
                        };
                        let grp_up = fs::read_to_string(dir.join("sched_group_upmigrate"))
                            .ok()
                            .and_then(|s| s.trim().parse().ok());
                        let grp_down = fs::read_to_string(dir.join("sched_group_downmigrate"))
                            .ok()
                            .and_then(|s| s.trim().parse().ok());
                        let policy = fs::read_to_string(dir.join("sched_window_stats_policy"))
                            .ok()
                            .and_then(|s| s.trim().parse().ok());
                        let window_ms = fs::read_to_string(dir.join("sched_ravg_window"))
                            .ok()
                            .and_then(|s| s.trim().parse::<u64>().ok())
                            .map(|ns| (ns / 1_000_000) as u32);
                        (up, down, grp_up, grp_down, policy, window_ms)
                    }
                };

            WaltCard {
                supported: true,
                active: state.active,
                auto_tune: state.auto_tune,
                mode: Cow::Borrowed(state.current_mode.as_str()),
                upmigrate: up,
                downmigrate: down,
                group_upmigrate: grp_up,
                group_downmigrate: grp_down,
                window_policy: policy,
                ravg_window_ms: window_ms,
            }
        }),
    }
}

/// Format human-readable status for CLI.
pub fn get_status_summary() -> String {
    let card = get_status_card();
    match card.supported {
        false => "WALT: Unsupported (not a Qualcomm WALT kernel)".to_string(),
        true => {
            let up = card
                .upmigrate
                .iter()
                .map(|v| v.to_string())
                .collect::<Vec<_>>()
                .join(" ");
            let down = card
                .downmigrate
                .iter()
                .map(|v| v.to_string())
                .collect::<Vec<_>>()
                .join(" ");

            format!(
                "WALT Status:\n  Supported: {}\n  Active: {}\n  Auto-tune: {}\n  Mode: {}\n  Upmigrate: [{}]\n  Downmigrate: [{}]\n  Group Upmigrate: {:?}\n  Group Downmigrate: {:?}\n  Window Policy: {:?}\n  RAVG Window: {:?} ms",
                card.supported,
                card.active,
                card.auto_tune,
                card.mode,
                up,
                down,
                card.group_upmigrate,
                card.group_downmigrate,
                card.window_policy,
                card.ravg_window_ms
            )
        }
    }
}
