use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::info;

pub fn fix_mediatek_ppm() {
    let ppm_path = "/proc/ppm/enabled";
    if Path::new(ppm_path).exists() {
        let _ = fs::write(ppm_path, "0");
        std::thread::sleep(std::time::Duration::from_secs(1));
        let _ = fs::write(ppm_path, "1");
        info!("Applied MediaTek PPM fix");
    }
}

pub fn apply_performance() -> Result<()> {
    // PPM policies
    let _ = Path::new("/proc/ppm/policy_status").exists();
    {}

    let fpsgo_path = "/sys/kernel/fpsgo/common/force_onoff";
    if Path::new(fpsgo_path).exists() {
        let _ = fs::write(fpsgo_path, "0");
    }

    // MTK Power and CCI mode
    let _ = fs::write("/proc/cpufreq/cpufreq_cci_mode", "1");
    let _ = fs::write("/proc/cpufreq/cpufreq_power_mode", "3");

    // DDR Boost mode
    let _ = fs::write(
        "/sys/devices/platform/boot_dramboost/dramboost/dramboost",
        "1",
    );

    // EAS/HMP Switch
    let _ = fs::write("/sys/devices/system/cpu/eas/enable", "0");

    // Disable GED KPI
    let _ = fs::write(
        "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled",
        "0",
    );

    // Disable battery current limiter
    let _ = fs::write(
        "/proc/mtk_batoc_throttling/battery_oc_protect_stop",
        "stop 1",
    );

    // Eara Thermal
    let _ = fs::write("/sys/kernel/eara_thermal/enable", "0");

    info!("Applied MediaTek performance tweaks");
    Ok(())
}

pub fn apply_normal() -> Result<()> {
    // Free FPSGO
    let fpsgo_path = "/sys/kernel/fpsgo/common/force_onoff";
    if Path::new(fpsgo_path).exists() {
        let _ = fs::write(fpsgo_path, "2");
    }

    // MTK Power and CCI mode
    let _ = fs::write("/proc/cpufreq/cpufreq_cci_mode", "0");
    let _ = fs::write("/proc/cpufreq/cpufreq_power_mode", "0");

    // DDR Boost mode
    let _ = fs::write(
        "/sys/devices/platform/boot_dramboost/dramboost/dramboost",
        "0",
    );

    // EAS/HMP Switch
    let _ = fs::write("/sys/devices/system/cpu/eas/enable", "2");

    // Enable GED KPI
    let _ = fs::write(
        "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled",
        "1",
    );

    // Enable battery current limiter
    let _ = fs::write(
        "/proc/mtk_batoc_throttling/battery_oc_protect_stop",
        "stop 0",
    );

    // Eara Thermal
    let _ = fs::write("/sys/kernel/eara_thermal/enable", "1");

    info!("Restored MediaTek normal tweaks");
    Ok(())
}
