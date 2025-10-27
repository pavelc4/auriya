use anyhow::Result;
use std::process::Command;

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
}

impl Default for ProfileMode {
    fn default() -> Self {
        ProfileMode::Balance
    }
}

pub fn apply_performance() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying PERFORMANCE profile");
    
    let _ = Command::new("sh")
        .args(["-c", "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > $cpu 2>/dev/null; done"])
        .output();
    
    let _ = Command::new("cmd")
        .args(["notification", "set_dnd", "on"])
        .output();
        Ok(())
}

pub fn apply_balance() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying BALANCE profile");
    
    let _ = Command::new("sh")
        .args(["-c", "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo schedutil > $cpu 2>/dev/null; done"])
        .output();
    
    Ok(())
}

pub fn apply_powersave() -> Result<()> {
    tracing::info!(target: "auriya::profile", "Applying POWERSAVE profile");
    
    let _ = Command::new("sh")
        .args(["-c", "for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo powersave > $cpu 2>/dev/null; done"])
        .output();
    
    let _ = Command::new("cmd")
        .args(["notification", "set_dnd", "off"])
        .output();
    
    Ok(())
}

pub fn apply_profile(mode: ProfileMode) -> Result<()> {
    match mode {
        ProfileMode::Performance => apply_performance(),
        ProfileMode::Balance => apply_balance(),
        ProfileMode::Powersave => apply_powersave(),
    }
}
