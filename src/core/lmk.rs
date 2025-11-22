use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::{debug, info, warn};

#[derive(Debug, Clone)]
pub struct LmkConfig {
    pub minfree: String,
    pub adj: String,
}

pub fn get_total_ram_mb() -> Result<u64> {
    let content = fs::read_to_string("/proc/meminfo").context("Failed to read /proc/meminfo")?;

    for line in content.lines() {
        if line.starts_with("MemTotal:") {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 {
                let kb: u64 = parts[1].parse()?;
                return Ok(kb / 1024);
            }
        }
    }

    anyhow::bail!("Could not find MemTotal in /proc/meminfo")
}

pub fn should_apply_lmk(total_ram_mb: u64) -> bool {
    match total_ram_mb {
        0..=3072 => {
            debug!(target: "auriya::lmk", "Low RAM device ({}MB), LMK: YES", total_ram_mb);
            true
        }
        3073..=8192 => {
            debug!(target: "auriya::lmk", "Mid RAM device ({}MB), LMK: YES", total_ram_mb);
            true
        }
        8193..=12288 => {
            info!(target: "auriya::lmk", "High RAM device ({}MB), LMK: BORDERLINE", total_ram_mb);
            false
        }
        _ => {
            info!(target: "auriya::lmk", "Very high RAM device ({}MB), LMK: NO (LMKD sufficient)", total_ram_mb);
            false
        }
    }
}

pub fn calculate_lmk_for_ram(total_ram_mb: u64, profile: &str) -> LmkConfig {
    let total_pages = (total_ram_mb * 1024) / 4;

    let (fg_pct, vis_pct, sec_pct, hid_pct, con_pct, emp_pct) = match profile {
        "gaming" => (5, 8, 12, 45, 70, 95),
        "balanced" => (10, 15, 20, 50, 75, 95),
        "powersave" => (15, 25, 35, 60, 80, 95),
        _ => (5, 8, 12, 45, 70, 95),
    };

    let foreground = (total_pages * fg_pct) / 100;
    let visible = (total_pages * vis_pct) / 100;
    let secondary = (total_pages * sec_pct) / 100;
    let hidden = (total_pages * hid_pct) / 100;
    let content = (total_pages * con_pct) / 100;
    let empty = (total_pages * emp_pct) / 100;

    let minfree = format!(
        "{},{},{},{},{},{}",
        foreground, visible, secondary, hidden, content, empty
    );

    info!(
        target: "auriya::lmk",
        "Calculated {} LMK for {}MB RAM (total_pages: {})",
        profile,
        total_ram_mb,
        total_pages
    );

    LmkConfig {
        minfree,
        adj: "0,1,2,4,9,15".to_string(),
    }
}

pub fn get_current_minfree() -> Result<Option<String>> {
    let path = "/sys/module/lowmemorykiller/parameters/minfree";

    if !Path::new(path).exists() {
        debug!(target: "auriya::lmk", "LMK minfree not available");
        return Ok(None);
    }

    match fs::read_to_string(path) {
        Ok(content) => Ok(Some(content.trim().to_string())),
        Err(e) => {
            debug!(target: "auriya::lmk", "Failed to read current minfree: {}", e);
            Ok(None)
        }
    }
}

pub fn apply_lmk(config: &LmkConfig) -> Result<()> {
    let minfree_path = "/sys/module/lowmemorykiller/parameters/minfree";
    let adj_path = "/sys/module/lowmemorykiller/parameters/adj";

    if !Path::new(minfree_path).exists() {
        debug!(target: "auriya::lmk", "LMK not available on this device");
        return Ok(());
    }

    let current_minfree = get_current_minfree()?;

    if current_minfree.as_deref() != Some(&config.minfree) {
        fs::write(minfree_path, &config.minfree)
            .with_context(|| format!("Failed to set LMK minfree to {}", config.minfree))?;

        info!(
            target: "auriya::lmk",
            "Set LMK minfree: {} -> {}",
            current_minfree.unwrap_or_else(|| "unknown".to_string()),
            config.minfree
        );
    } else {
        debug!(target: "auriya::lmk", "LMK minfree already set");
    }

    fs::write(adj_path, &config.adj)
        .with_context(|| format!("Failed to set LMK adj to {}", config.adj))?;

    Ok(())
}

pub fn apply_gaming_lmk() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        info!(target: "auriya::lmk", "Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!(target: "auriya::lmk", "Applying gaming LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "gaming");

    match apply_lmk(&config) {
        Ok(_) => {
            info!(target: "auriya::lmk", "Gaming LMK applied successfully");
            Ok(())
        }
        Err(e) => {
            warn!(target: "auriya::lmk", "Failed to apply gaming LMK: {}", e);
            Ok(())
        }
    }
}

pub fn apply_balanced_lmk() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        debug!(target: "auriya::lmk", "Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!(target: "auriya::lmk", "Applying balanced LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "balanced");

    match apply_lmk(&config) {
        Ok(_) => {
            info!(target: "auriya::lmk", "Balanced LMK applied successfully");
            Ok(())
        }
        Err(e) => {
            warn!(target: "auriya::lmk", "Failed to apply balanced LMK: {}", e);
            Ok(())
        }
    }
}

pub fn apply_powersave_lmk() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        debug!(target: "auriya::lmk", "Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!(target: "auriya::lmk", "Applying powersave LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "powersave");

    match apply_lmk(&config) {
        Ok(_) => {
            info!(target: "auriya::lmk", "Powersave LMK applied successfully");
            Ok(())
        }
        Err(e) => {
            warn!(target: "auriya::lmk", "Failed to apply powersave LMK: {}", e);
            Ok(())
        }
    }
}
