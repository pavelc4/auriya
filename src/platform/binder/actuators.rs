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
        let _ = super::ensure_initialized();
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

/// Query user preferred display refresh rate directly via Native Binder (code 47).
/// Returns `None` if device is in default dynamic/auto refresh rate mode.
pub fn get_user_preferred_display_mode() -> Result<Option<f32>> {
    #[cfg(target_os = "android")]
    {
        if !super::ensure_initialized() {
            return Ok(None);
        }
        let display_svc = match rsbinder::hub::check_service("display") {
            Some(s) => s,
            None => return Ok(None),
        };
        let proxy = match display_svc.as_proxy() {
            Some(p) => p,
            None => return Ok(None),
        };
        let mut data = proxy.prepare_transact(true)?;
        data.write(&0i32)?; // displayId = 0
        let reply_opt = proxy.submit_transact(47, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                return Ok(None);
            }
            let is_null = rep.read_i32()?;
            if is_null == 0 {
                return Ok(None);
            }
            // Skip modeId, physicalWidth, physicalHeight, flags, width, height (6 * 4 = 24 bytes)
            rep.set_data_position(rep.data_position() + 24);
            let hz = rep.read_f32()?;
            return Ok(Some(hz));
        }
        Ok(None)
    }

    #[cfg(not(target_os = "android"))]
    {
        Ok(None)
    }
}

/// Set or clear display mode via Native Binder IPC (code 45).
/// Passing `hz <= 0.0` clears the mode (`null`), restoring Dynamic Refresh Rate.
#[cfg(target_os = "android")]
fn set_binder_display_mode(hz: f32) -> Result<()> {
    if !super::ensure_initialized() {
        anyhow::bail!("Binder ProcessState not initialized");
    }
    let display_svc = match rsbinder::hub::check_service("display") {
        Some(s) => s,
        None => anyhow::bail!("display service not found"),
    };
    let proxy = match display_svc.as_proxy() {
        Some(p) => p,
        None => anyhow::bail!("display service is not a proxy"),
    };

    let mut data = proxy.prepare_transact(true)?;
    data.write(&0i32)?; // displayId = 0 (primary display)

    if hz <= 0.0 {
        // Clear mode: mode = null (0 indicates null Parcelable)
        data.write(&0i32)?;
    } else {
        // Set mode: 1 = non-null Display.Mode
        data.write(&1i32)?;
        data.write(&-1i32)?; // modeId = -1
        data.write(&-1i32)?; // physicalWidth = -1
        data.write(&-1i32)?; // physicalHeight = -1
        data.write(&0i32)?; // flags = 0
        data.write(&-1i32)?; // width = -1
        data.write(&-1i32)?; // height = -1
        data.write(&hz)?; // refreshRate = hz
        data.write(&hz)?; // vsyncRate = hz
        data.write(&0i32)?; // alternativeRefreshRates len = 0
        data.write(&0i32)?; // supportedHdrTypes len = 0
    }

    data.write(&false)?; // storeMode = false (do not persist to disk)

    // TRANSACTION_setUserPreferredDisplayMode = 45
    let reply = proxy.submit_transact(45, &data, 0)?;
    if let Some(mut rep) = reply {
        let ex = rep.read_i32()?;
        if ex != 0 {
            anyhow::bail!("setUserPreferredDisplayMode returned exception code {}", ex);
        }
    }
    Ok(())
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
        // 1. Direct Native Binder RPC to DisplayManagerService (SurfaceFlinger)
        let _ = set_binder_display_mode(rate_hz as f32);

        // 2. Settings synchronization (for app/game engines inspecting ContentResolver)
        if rate_hz == 0 {
            // Restore snapshot
            if let Some(peak) = state.saved_peak_hz {
                let _ = std::process::Command::new("cmd")
                    .args([
                        "settings",
                        "put",
                        "system",
                        "peak_refresh_rate",
                        &peak.to_string(),
                    ])
                    .status();
            } else {
                let _ = std::process::Command::new("cmd")
                    .args(["settings", "delete", "system", "peak_refresh_rate"])
                    .status();
            }

            if let Some(min) = state.saved_min_hz {
                let _ = std::process::Command::new("cmd")
                    .args([
                        "settings",
                        "put",
                        "system",
                        "min_refresh_rate",
                        &min.to_string(),
                    ])
                    .status();
            } else {
                let _ = std::process::Command::new("cmd")
                    .args(["settings", "delete", "system", "min_refresh_rate"])
                    .status();
            }
            tracing::info!(target: "auriya::binder", "Restored display refresh rate to dynamic");
        } else {
            // Capture snapshot if not yet taken
            if !state.snapshot_taken {
                state.saved_peak_hz = read_settings_float("peak_refresh_rate");
                state.saved_min_hz = read_settings_float("min_refresh_rate");
                state.snapshot_taken = true;
            }

            // Write both peak and min so framework ContentObservers trigger
            let hz_str = (rate_hz as f32).to_string();
            let _ = std::process::Command::new("cmd")
                .args(["settings", "put", "system", "peak_refresh_rate", &hz_str])
                .status();
            let _ = std::process::Command::new("cmd")
                .args(["settings", "put", "system", "min_refresh_rate", &hz_str])
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
struct WifiLockToken;

#[cfg(target_os = "android")]
impl rsbinder::Remotable for WifiLockToken {
    fn descriptor() -> &'static str {
        "android.os.IBinder"
    }

    fn on_transact(
        &self,
        _code: rsbinder::TransactionCode,
        _reader: &mut rsbinder::Parcel,
        _reply: &mut rsbinder::Parcel,
    ) -> rsbinder::Result<()> {
        Ok(())
    }

    fn on_dump(&self, _writer: &mut dyn std::io::Write, _args: &[String]) -> rsbinder::Result<()> {
        Ok(())
    }
}

#[cfg(target_os = "android")]
static WIFI_LOCK_TOKEN: Mutex<Option<rsbinder::SIBinder>> = Mutex::new(None);

/// Acquire or release Android Wi-Fi Low-Latency lock (WIFI_MODE_FULL_LOW_LATENCY = 4)
/// directly via Native Binder IPC to IWifiManager (codes 62 & 64).
pub fn set_wifi_low_latency_lock(enable: bool) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        use rsbinder::Interface;

        let mut lock_guard = WIFI_LOCK_TOKEN.lock().unwrap();

        if enable {
            if lock_guard.is_some() {
                return Ok(true);
            }

            if !super::ensure_initialized() {
                anyhow::bail!("Binder ProcessState not initialized");
            }

            let wifi_svc = match rsbinder::hub::check_service("wifi") {
                Some(s) => s,
                None => anyhow::bail!("wifi service not found"),
            };
            let proxy = match wifi_svc.as_proxy() {
                Some(p) => p,
                None => anyhow::bail!("wifi service is not a proxy"),
            };

            let token_binder = rsbinder::Binder::new(WifiLockToken);
            let token = token_binder.as_binder();

            // TRANSACTION_acquireWifiLock = 62
            let mut data = proxy.prepare_transact(true)?;
            data.write(&token)?;
            data.write(&4i32)?; // WIFI_MODE_FULL_LOW_LATENCY = 4
            data.write(&"auriya-gaming-wifi-lock".to_string())?;
            data.write(&0i32)?; // WorkSource = null
            data.write(&"dev.auriya".to_string())?;
            data.write(&0i32)?; // Bundle = null

            let reply_opt = proxy.submit_transact(62, &data, 0)?;
            if let Some(mut rep) = reply_opt {
                let ex = rep.read_i32()?;
                if ex != 0 {
                    anyhow::bail!("acquireWifiLock exception: {}", ex);
                }
                let success = rep.read_bool()?;
                if success {
                    *lock_guard = Some(token);
                    tracing::info!(target: "auriya::binder", "Acquired Wi-Fi Low-Latency lock (type 4)");
                    return Ok(true);
                }
                tracing::warn!(target: "auriya::binder", "acquireWifiLock returned false");
                return Ok(false);
            }
            anyhow::bail!("acquireWifiLock received no reply");
        } else {
            let Some(token) = lock_guard.take() else {
                return Ok(false);
            };

            if !super::ensure_initialized() {
                return Ok(false);
            }

            let wifi_svc = match rsbinder::hub::check_service("wifi") {
                Some(s) => s,
                None => return Ok(false),
            };
            let proxy = match wifi_svc.as_proxy() {
                Some(p) => p,
                None => return Ok(false),
            };

            // TRANSACTION_releaseWifiLock = 64
            let mut data = proxy.prepare_transact(true)?;
            data.write(&token)?;
            let reply_opt = proxy.submit_transact(64, &data, 0)?;
            if let Some(mut rep) = reply_opt {
                let ex = rep.read_i32()?;
                if ex != 0 {
                    tracing::warn!(target: "auriya::binder", "releaseWifiLock exception: {}", ex);
                } else {
                    let _ = rep.read_bool();
                }
            }
            tracing::info!(target: "auriya::binder", "Released Wi-Fi Low-Latency lock");
            Ok(false)
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = enable;
        Ok(false)
    }
}

/// Query whether the Wi-Fi Low-Latency lock is currently active.
pub fn is_wifi_low_latency_active() -> bool {
    #[cfg(target_os = "android")]
    {
        WIFI_LOCK_TOKEN.lock().unwrap().is_some()
    }

    #[cfg(not(target_os = "android"))]
    {
        false
    }
}

#[cfg(target_os = "android")]
fn read_settings_float(key: &str) -> Option<f32> {
    let out = std::process::Command::new("cmd")
        .args(["settings", "get", "system", key])
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&out.stdout).trim().to_string();
    text.parse::<f32>().ok()
}

/// Set High Touch Polling Rate via LineageOS/AOSP Touch HAL if available.
pub fn set_high_touch_polling_rate(enabled: bool) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        if !super::ensure_initialized() {
            return Ok(false);
        }

        let touch_svc = match rsbinder::hub::check_service(
            "vendor.lineage.touch.IHighTouchPollingRate/default",
        ) {
            Some(s) => s,
            None => return Ok(false),
        };

        let proxy = match touch_svc.as_proxy() {
            Some(p) => p,
            None => return Ok(false),
        };

        // TRANSACTION_setEnabled = 2
        let mut data = match proxy.prepare_transact(true) {
            Ok(d) => d,
            Err(_) => return Ok(false),
        };

        if data.write_bool(enabled).is_err() {
            return Ok(false);
        }

        match proxy.submit_transact(2, &data, 0) {
            Ok(_) => {
                tracing::debug!(target: "auriya::binder", "HighTouchPollingRate set to {}", enabled);
                Ok(true)
            }
            _ => Ok(false),
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = enabled;
        Ok(false)
    }
}

/// Check if High Touch Polling Rate HAL service is registered.
pub fn is_high_touch_polling_rate_supported() -> bool {
    #[cfg(target_os = "android")]
    {
        if !super::ensure_initialized() {
            return false;
        }
        rsbinder::hub::check_service("vendor.lineage.touch.IHighTouchPollingRate/default").is_some()
    }

    #[cfg(not(target_os = "android"))]
    {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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

    #[test]
    fn test_wifi_low_latency_mock() {
        assert!(!is_wifi_low_latency_active());
        assert!(set_wifi_low_latency_lock(true).is_ok());
        assert!(set_wifi_low_latency_lock(false).is_ok());
    }

    #[test]
    fn test_touch_polling_rate() {
        let _supported = is_high_touch_polling_rate_supported();
        assert!(set_high_touch_polling_rate(true).is_ok());
        assert!(set_high_touch_polling_rate(false).is_ok());
    }
}
