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
            debug!("Low RAM device ({}MB), LMK: YES", total_ram_mb);
            true
        }
        3073..=8192 => {
            debug!("Mid RAM device ({}MB), LMK: YES", total_ram_mb);
            true
        }
        8193..=12288 => {
            info!("High RAM device ({}MB), LMK: BORDERLINE", total_ram_mb);
            false
        }
        _ => {
            info!(
                "Very high RAM device ({}MB), LMK: NO (LMKD sufficient)",
                total_ram_mb
            );
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
        "Calculated {} LMK for {}MB RAM (total_pages: {})",
        profile, total_ram_mb, total_pages
    );

    LmkConfig {
        minfree,
        adj: "0,1,2,4,9,15".to_string(),
    }
}

pub fn get_current_minfree() -> Result<Option<String>> {
    let path = "/sys/module/lowmemorykiller/parameters/minfree";

    if !Path::new(path).exists() {
        debug!("LMK minfree not available");
        return Ok(None);
    }

    match fs::read_to_string(path) {
        Ok(content) => Ok(Some(content.trim().to_string())),
        Err(e) => {
            debug!("Failed to read current minfree: {}", e);
            Ok(None)
        }
    }
}

pub fn apply_lmk(config: &LmkConfig) -> Result<()> {
    let minfree_path = "/sys/module/lowmemorykiller/parameters/minfree";
    let adj_path = "/sys/module/lowmemorykiller/parameters/adj";

    if !Path::new(minfree_path).exists() {
        debug!("LMK not available on this device");
        return Ok(());
    }

    let current_minfree = get_current_minfree()?;

    if current_minfree.as_deref() != Some(&config.minfree) {
        // chmod 666 -> write -> chmod 644 (Magisk module trick)
        let shell_result = std::process::Command::new("sh")
            .args([
                "-c",
                &format!(
                    "chmod 666 {} 2>/dev/null; echo '{}' > {}; chmod 644 {} 2>/dev/null",
                    minfree_path, config.minfree, minfree_path, minfree_path
                ),
            ])
            .output();

        let minfree_ok = if let Ok(output) = shell_result {
            output.status.success()
        } else {
            // Fallback to fs::write
            fs::write(minfree_path, &config.minfree).is_ok()
        };

        if minfree_ok {
            info!(
                "Set LMK minfree: {} -> {}",
                current_minfree.unwrap_or_else(|| "unknown".to_string()),
                config.minfree
            );
        } else {
            warn!("Failed to set LMK minfree to {}", config.minfree);
        }
    } else {
        debug!("LMK minfree already set");
    }

    // chmod 666 -> write -> chmod 644 for adj
    let adj_shell = std::process::Command::new("sh")
        .args([
            "-c",
            &format!(
                "chmod 666 {} 2>/dev/null; echo '{}' > {}; chmod 644 {} 2>/dev/null",
                adj_path, config.adj, adj_path, adj_path
            ),
        ])
        .output();

    let adj_ok = if let Ok(output) = adj_shell {
        output.status.success()
    } else {
        fs::write(adj_path, &config.adj).is_ok()
    };

    if !adj_ok {
        warn!("Failed to set LMK adj to {}", config.adj);
    }

    Ok(())
}

pub fn adjust_for_gaming() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        info!("Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!("Applying gaming LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "gaming");

    match apply_lmk(&config) {
        Ok(_) => info!("Gaming LMK applied successfully"),
        Err(e) => warn!("Failed to apply gaming LMK: {}", e),
    }

    if let Err(e) = set_swappiness(10) {
        warn!("Failed to set  swappiness: {}", e);
    }

    let vfs_path = "/proc/sys/vm/vfs_cache_pressure";
    if Path::new(vfs_path).exists() {
        let _ = fs::write(vfs_path, "80");
        debug!("vfs_cache_pressure set to 80 for gaming");
    }

    Ok(())
}

pub fn restore_balanced() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        debug!("Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!("Applying balanced LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "balanced");

    match apply_lmk(&config) {
        Ok(_) => info!("Balanced LMK applied successfully"),
        Err(e) => warn!("Failed to apply balanced LMK: {}", e),
    }

    if let Err(e) = set_swappiness(60) {
        warn!("Failed to set balanced swappiness: {}", e);
    }

    let vfs_path = "/proc/sys/vm/vfs_cache_pressure";
    if Path::new(vfs_path).exists() {
        let _ = fs::write(vfs_path, "100");
        debug!("vfs_cache_pressure set to 100 for balanced");
    }

    Ok(())
}
pub fn apply_powersave_lmk() -> Result<()> {
    let total_ram = get_total_ram_mb().unwrap_or(4096);

    if !should_apply_lmk(total_ram) {
        debug!("Skipping LMK for {}MB RAM", total_ram);
        return Ok(());
    }

    info!("Applying powersave LMK profile for {}MB RAM", total_ram);
    let config = calculate_lmk_for_ram(total_ram, "powersave");

    match apply_lmk(&config) {
        Ok(_) => info!("Powersave LMK applied successfully"),
        Err(e) => warn!("Failed to apply powersave LMK: {}", e),
    }

    if let Err(e) = set_swappiness(60) {
        warn!("Failed to set powersave swappiness: {}", e);
    }
    Ok(())
}

pub fn set_swappiness(value: u32) -> Result<()> {
    let path = "/proc/sys/vm/swappiness";

    // Method 1: chmod trick (Magisk module technique)
    // chmod 666 -> write -> chmod 644
    let chmod_write = std::process::Command::new("sh")
        .args([
            "-c",
            &format!(
                "chmod 666 {} 2>/dev/null; echo {} > {}; chmod 644 {} 2>/dev/null",
                path, value, path, path
            ),
        ])
        .output();

    if let Ok(output) = chmod_write {
        if output.status.success() {
            debug!("Swappiness set to {} via chmod trick", value);
            return Ok(());
        }
    }

    // Method 2: sysctl command
    let sysctl_result = std::process::Command::new("sysctl")
        .args(["-w", &format!("vm.swappiness={}", value)])
        .output();

    if let Ok(output) = sysctl_result {
        if output.status.success() {
            debug!("Swappiness set to {} via sysctl", value);
            return Ok(());
        }
    }

    // Method 3: Direct fs::write (fallback)
    fs::write(path, value.to_string()).context("Failed to set swappiness")?;
    debug!("Swappiness set to {} via fs::write", value);
    Ok(())
}

pub fn drop_caches() -> Result<()> {
    fs::write("/proc/sys/vm/drop_caches", "3").context("Failed to drop caches")?;
    info!("Kernel caches and buffers dropped");
    Ok(())
}
