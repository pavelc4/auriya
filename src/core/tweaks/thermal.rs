use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::str::FromStr;

use anyhow::{Context, Result};
use tracing::{debug, info, warn};

/// Xiaomi `sconfig` node: a single integer selecting the vendor thermal
/// preset. Values follow the reference mapping used by Xtra-Kernel-Manager.
const SCONFIG: &str = "/sys/class/thermal/thermal_message/sconfig";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ThermalPreset {
    /// 11 — stock/default class
    Class0,
    /// 2 — max performance
    Extreme,
    /// 10 — adaptive/dynamic
    Dynamic,
    /// 8 — keeps thermals relaxed during calls
    Incalls,
    /// 20 — loose limit (some ROMs ship this as the stock value)
    Thermal20,
}

impl ThermalPreset {
    pub fn index(self) -> u32 {
        match self {
            Self::Class0 => 11,
            Self::Extreme => 2,
            Self::Dynamic => 10,
            Self::Incalls => 8,
            Self::Thermal20 => 20,
        }
    }
}

impl FromStr for ThermalPreset {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let s = s.trim().to_lowercase();
        match s.as_str() {
            "class0" | "class 0" | "default" => Ok(Self::Class0),
            "extreme" => Ok(Self::Extreme),
            "dynamic" | "dyn" => Ok(Self::Dynamic),
            "incalls" | "in calls" => Ok(Self::Incalls),
            "thermal20" | "thermal 20" => Ok(Self::Thermal20),
            // Raw sconfig values (mirrors the reference kernel mapping).
            "11" | "0" => Ok(Self::Class0),
            "2" => Ok(Self::Extreme),
            "10" => Ok(Self::Dynamic),
            "8" => Ok(Self::Incalls),
            "20" => Ok(Self::Thermal20),
            _ => Err(format!("unknown thermal preset: {s}")),
        }
    }
}

impl std::fmt::Display for ThermalPreset {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            Self::Class0 => "class0",
            Self::Extreme => "extreme",
            Self::Dynamic => "dynamic",
            Self::Incalls => "incalls",
            Self::Thermal20 => "thermal20",
        })
    }
}

pub fn sconfig_exists() -> bool {
    Path::new(SCONFIG).exists()
}

fn read_value(path: &Path) -> Option<i32> {
    fs::read_to_string(path)
        .ok()
        .and_then(|v| v.trim().parse::<i32>().ok())
}

/// Write a raw `sconfig` value. No-op (but logs) when the node is missing,
/// so non-Xiaomi devices keep running untouched.
pub fn set_value(value: i32) -> Result<()> {
    if !sconfig_exists() {
        warn!(target: "auriya::thermal", "sconfig missing, thermal value {value} not applied");
        return Ok(());
    }

    let _ = fs::set_permissions(SCONFIG, fs::Permissions::from_mode(0o666));
    fs::write(SCONFIG, value.to_string())
        .with_context(|| format!("Cannot write thermal value {value}"))?;
    let _ = fs::set_permissions(SCONFIG, fs::Permissions::from_mode(0o444));
    debug!(target: "auriya::thermal", "Applied thermal sconfig={value}");
    Ok(())
}

/// Write a preset to `sconfig`.
pub fn set_preset(preset: ThermalPreset) -> Result<()> {
    set_value(preset.index() as i32)
}

/// Load the persisted stock thermal value, or — when absent — scan the
/// current `sconfig` and persist it. The stock value must be captured
/// before Auriya ever writes the node; persisting it makes the default
/// survive daemon restarts even mid-game.
pub fn load_or_capture_rom_default(default_file: &Path) -> Option<i32> {
    capture_from(default_file, Path::new(SCONFIG))
}

pub fn capture_from(default_file: &Path, sysfs: &Path) -> Option<i32> {
    if let Some(value) = read_value(default_file) {
        return Some(value);
    }

    let stock = read_value(sysfs)?;
    if let Err(e) = fs::write(default_file, stock.to_string()) {
        warn!(target: "auriya::thermal", "Failed to persist stock thermal default: {e}");
        return None;
    }
    info!(target: "auriya::thermal", "Captured stock thermal default: sconfig={stock}");
    Some(stock)
}
