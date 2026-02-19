use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::debug;

pub fn fix_mediatek_ppm() {
    let ppm_path = "/proc/ppm/enabled";
    if Path::new(ppm_path).exists() {
        let _ = fs::write(ppm_path, "0");
        std::thread::sleep(std::time::Duration::from_secs(1));
        let _ = fs::write(ppm_path, "1");
        debug!("Applied MediaTek PPM fix");
    }
}

fn set_ppm_policies(enabled: bool) {
    let policy_path = "/proc/ppm/policy_status";
    if !Path::new(policy_path).exists() {
        return;
    }
    let content = match fs::read_to_string(policy_path) {
        Ok(c) => c,
        Err(_) => return,
    };
    let val = if enabled { 1u8 } else { 0u8 };
    for line in content.lines() {
        if let Some(idx) = line
            .trim()
            .strip_prefix('[')
            .and_then(|s| s.split(']').next())
            .and_then(|s| s.trim().parse::<u8>().ok())
        {
            let _ = fs::write(policy_path, format!("{} {}", idx, val));
        }
    }
}

pub fn apply_performance() -> Result<()> {
    set_ppm_policies(false);

    let fpsgo_path = "/sys/kernel/fpsgo/common/force_onoff";
    if Path::new(fpsgo_path).exists() {
        let _ = fs::write(fpsgo_path, "0");
    }

    let _ = fs::write("/proc/cpufreq/cpufreq_cci_mode", "1");
    let _ = fs::write("/proc/cpufreq/cpufreq_power_mode", "3");

    let _ = fs::write(
        "/sys/devices/platform/boot_dramboost/dramboost/dramboost",
        "1",
    );

    let _ = fs::write("/sys/devices/system/cpu/eas/enable", "0");

    let _ = fs::write(
        "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled",
        "0",
    );

    let _ = fs::write(
        "/proc/mtk_batoc_throttling/battery_oc_protect_stop",
        "stop 1",
    );

    let _ = fs::write("/sys/kernel/eara_thermal/enable", "0");

    debug!("Applied MediaTek performance tweaks");
    Ok(())
}

pub fn apply_normal() -> Result<()> {
    set_ppm_policies(true);

    let fpsgo_path = "/sys/kernel/fpsgo/common/force_onoff";
    if Path::new(fpsgo_path).exists() {
        let _ = fs::write(fpsgo_path, "2");
    }

    let _ = fs::write("/proc/cpufreq/cpufreq_cci_mode", "0");
    let _ = fs::write("/proc/cpufreq/cpufreq_power_mode", "0");

    let _ = fs::write(
        "/sys/devices/platform/boot_dramboost/dramboost/dramboost",
        "0",
    );

    let _ = fs::write("/sys/devices/system/cpu/eas/enable", "2");

    let _ = fs::write(
        "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled",
        "1",
    );

    let _ = fs::write(
        "/proc/mtk_batoc_throttling/battery_oc_protect_stop",
        "stop 0",
    );

    let _ = fs::write("/sys/kernel/eara_thermal/enable", "1");

    debug!("Restored MediaTek normal tweaks");
    Ok(())
}
