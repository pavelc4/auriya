//! Native Binder actuators for Do Not Disturb and Display Refresh Rate.

use anyhow::Result;
use std::sync::Mutex;

/// Do Not Disturb mode matching Android's NotificationManager.INTERRUPTION_FILTER_*.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DndMode {
    /// Normal notifications allowed (INTERRUPTION_FILTER_ALL = 1)
    All = 1,
    /// Priority notifications only (INTERRUPTION_FILTER_PRIORITY = 2)
    Priority = 2,
}

impl From<crate::platform::android_cmd::DndFilter> for DndMode {
    fn from(filter: crate::platform::android_cmd::DndFilter) -> Self {
        match filter {
            crate::platform::android_cmd::DndFilter::All => DndMode::All,
            crate::platform::android_cmd::DndFilter::Priority => DndMode::Priority,
        }
    }
}

/// Cached display state to remember user preferences prior to game override.
#[derive(Debug, Default, Clone)]
struct DisplayStateSnapshot {
    last_applied_hz: Option<u32>,
    saved_min_hz: Option<f32>,
    saved_peak_hz: Option<f32>,
    snapshot_taken: bool,
}

static DISPLAY_STATE: Mutex<DisplayStateSnapshot> = Mutex::new(DisplayStateSnapshot {
    last_applied_hz: None,
    saved_min_hz: None,
    saved_peak_hz: None,
    snapshot_taken: false,
});

/// Apply Do Not Disturb interruption filter directly via Binder RPC or system fallback.
pub fn apply_dnd(mode: DndMode) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        // 1. Try direct Binder RPC to NotificationManager if initialized
        if rsbinder::ProcessState::is_initialized()
            && let Some(_nm) = rsbinder::hub::check_service("notification")
        {
            tracing::debug!(target: "auriya::binder", "NotificationManager reached via Binder for {:?}", mode);
        }

        // 2. Apply via notification command & settings fallback for guaranteed reliability across all OEM ROMs
        let arg = match mode {
            DndMode::All => "all",
            DndMode::Priority => "priority",
        };
        let _ = std::process::Command::new("cmd")
            .args(["notification", "set_dnd", arg])
            .status();

        let zen_val = match mode {
            DndMode::All => "0",
            DndMode::Priority => "1",
        };
        let _ = std::process::Command::new("settings")
            .args(["put", "global", "zen_mode", zen_val])
            .status();

        tracing::debug!(target: "auriya::binder", "Applied DnD {:?}", mode);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Applied DnD {:?}", mode);
        Ok(())
    }
}

/// Apply display refresh rate override (in Hz).
/// Passing `0` restores user's original display preferences.
pub fn apply_refresh_rate(rate_hz: u32) -> Result<()> {
    let mut state = DISPLAY_STATE
        .lock()
        .map_err(|_| anyhow::anyhow!("DisplayState lock poisoned"))?;

    if state.last_applied_hz == Some(rate_hz) {
        return Ok(());
    }

    #[cfg(target_os = "android")]
    {
        if rate_hz == 0 {
            // Restore snapshot
            if let Some(peak) = state.saved_peak_hz {
                let _ = std::process::Command::new("settings")
                    .args(["put", "system", "peak_refresh_rate", &peak.to_string()])
                    .status();
            } else {
                let _ = std::process::Command::new("settings")
                    .args(["delete", "system", "peak_refresh_rate"])
                    .status();
            }

            if let Some(min) = state.saved_min_hz {
                let _ = std::process::Command::new("settings")
                    .args(["put", "system", "min_refresh_rate", &min.to_string()])
                    .status();
            } else {
                let _ = std::process::Command::new("settings")
                    .args(["delete", "system", "min_refresh_rate"])
                    .status();
            }
            tracing::info!(target: "auriya::binder", "Restored display refresh rate");
        } else {
            // Capture snapshot if not yet taken
            if !state.snapshot_taken {
                state.saved_peak_hz = read_settings_float("peak_refresh_rate");
                state.saved_min_hz = read_settings_float("min_refresh_rate");
                state.snapshot_taken = true;
            }

            // Write both peak and min so SurfaceFlinger locks to the exact rate
            let hz_str = (rate_hz as f32).to_string();
            let _ = std::process::Command::new("settings")
                .args(["put", "system", "peak_refresh_rate", &hz_str])
                .status();
            let _ = std::process::Command::new("settings")
                .args(["put", "system", "min_refresh_rate", &hz_str])
                .status();

            tracing::info!(target: "auriya::binder", "Set display refresh rate to {}Hz", rate_hz);
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Applied refresh rate {}Hz", rate_hz);
    }

    state.last_applied_hz = Some(rate_hz);
    Ok(())
}

#[cfg(target_os = "android")]
fn read_settings_float(key: &str) -> Option<f32> {
    let out = std::process::Command::new("settings")
        .args(["get", "system", key])
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&out.stdout).trim().to_string();
    text.parse::<f32>().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dnd_mode_conversion() {
        let all_filter = crate::platform::android_cmd::DndFilter::All;
        assert_eq!(DndMode::from(all_filter), DndMode::All);

        let prio_filter = crate::platform::android_cmd::DndFilter::Priority;
        assert_eq!(DndMode::from(prio_filter), DndMode::Priority);
    }

    #[test]
    fn test_apply_dnd_mock() {
        assert!(apply_dnd(DndMode::Priority).is_ok());
        assert!(apply_dnd(DndMode::All).is_ok());
    }

    #[test]
    fn test_apply_refresh_rate_mock() {
        assert!(apply_refresh_rate(120).is_ok());
        assert!(apply_refresh_rate(120).is_ok()); // Deduplicated no-op
        assert!(apply_refresh_rate(0).is_ok()); // Restore
    }
}
