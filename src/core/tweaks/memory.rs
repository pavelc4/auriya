use anyhow::{Context, Result};
use std::fs;
use std::path::Path;
use tracing::{debug, info};

pub fn adjust_for_gaming() -> Result<()> {
    let lmk_path = "/sys/module/lowmemorykiller/parameters/minfree";
    if Path::new(lmk_path).exists() {
        let minfree = "18432,23040,27648,32256,36864,46080";
        fs::write(lmk_path, minfree).context("Failed to set LMK for gaming")?;
        info!("LMK set for gaming mode");
    } else {
        debug!("LMK sysfs not found, skipping LMK adjustment");
    }

    set_swappiness(10)?;

    Ok(())
}

pub fn restore_balanced() -> Result<()> {
    let lmk_path = "/sys/module/lowmemorykiller/parameters/minfree";
    if Path::new(lmk_path).exists() {
        let minfree = "18432,23040,27648,55296,82944,110592";
        fs::write(lmk_path, minfree).ok();
    }

    set_swappiness(60)?;

    Ok(())
}

pub fn set_swappiness(value: u32) -> Result<()> {
    let path = "/proc/sys/vm/swappiness";
    fs::write(path, value.to_string()).context("Failed to set swappiness")?;
    debug!("Swappiness set to {}", value);
    Ok(())
}

pub fn drop_caches() -> Result<()> {
    fs::write("/proc/sys/vm/drop_caches", "3").context("Failed to drop caches")?;
    info!("Kernel caches and buffers dropped");
    Ok(())
}
