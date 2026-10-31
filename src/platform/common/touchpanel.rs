use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::debug;

// Table of known OEM touch game mode nodes: (path, performance_value, balance_value)
const TOUCH_PATHS: &[(&str, &str, &str)] = &[
    // Xiaomi / POCO (bump_sample_rate)
    (
        "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
        "1",
        "0",
    ),
    ("/sys/class/touch/touch_dev/bump_sample_rate", "1", "0"),
    // OnePlus / OPPO / Realme (OPlus touchpanel)
    ("/proc/touchpanel/game_switch_enable", "1", "0"),
    ("/proc/touchpanel/oplus_tp_limit_enable", "0", "1"),
    ("/proc/touchpanel/oppo_tp_limit_enable", "0", "1"),
    ("/proc/touchpanel/oplus_tp_direction", "1", "0"),
    ("/proc/touchpanel/oppo_tp_direction", "1", "0"),
    // Black Shark
    ("/sys/devices/virtual/touch/touch_dev/game_mode", "1", "0"),
    // ASUS ROG Phone
    ("/sys/class/sensors/g_sensor/game_mode", "1", "0"),
];

fn write_sysfs_mode(wanted: &str) -> u32 {
    let mut written = 0;
    for (path, perf_val, bal_val) in TOUCH_PATHS {
        if Path::new(path).exists() {
            let v = match wanted {
                "performance" => perf_val,
                _ => bal_val,
            };
            if fs::write(path, v).is_ok() {
                written += 1;
            }
        }
    }
    written
}

/// Set touchpanel game mode (high sampling / polling rate boost).
pub fn set_game_mode(enabled: bool) -> Result<()> {
    // 1. Try Native Binder Touch HAL if available
    #[cfg(target_os = "android")]
    let binder_ok = crate::platform::binder::set_high_touch_polling_rate(enabled).unwrap_or(false);
    #[cfg(not(target_os = "android"))]
    let binder_ok = false;

    // 2. Write to vendor sysfs nodes if available
    let wanted = if enabled { "performance" } else { "balance" };
    let n = write_sysfs_mode(wanted);

    if binder_ok || n > 0 {
        debug!(
            target: "auriya::profile",
            "Touchpanel game mode set to {} (binder: {}, sysfs nodes: {})",
            enabled, binder_ok, n
        );
    } else {
        debug!(
            target: "auriya::profile",
            "Touchpanel high polling rate not supported on this device, ignoring"
        );
    }

    Ok(())
}

/// Enable touchpanel game mode (high polling rate).
pub fn enable_game_mode() -> Result<()> {
    set_game_mode(true)
}

/// Disable touchpanel game mode (normal polling rate).
pub fn disable_game_mode() -> Result<()> {
    set_game_mode(false)
}

/// Check if high touch polling rate is supported on this device via Binder or sysfs.
pub fn is_supported() -> bool {
    #[cfg(target_os = "android")]
    if crate::platform::binder::is_high_touch_polling_rate_supported() {
        return true;
    }

    TOUCH_PATHS
        .iter()
        .any(|(path, _, _)| Path::new(path).exists())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_touchpanel_mode_toggle() {
        assert!(enable_game_mode().is_ok());
        assert!(disable_game_mode().is_ok());
    }
}
