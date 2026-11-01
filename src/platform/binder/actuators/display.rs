//! Display refresh rate actuator and user preference state snapshot.

use anyhow::Result;
use std::sync::Mutex;

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

/// Query display supported modes directly from `IDisplayManager.getDisplayInfo(display_id)`.
/// Bypasses `dumpsys display` subshell execution completely.
pub fn query_binder_display_modes(
    display_id: i32,
) -> Result<Option<Vec<crate::platform::display::DisplayMode>>> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
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
        data.write(&display_id)?;
        // TRANSACTION_getDisplayInfo = 1
        let reply_opt = proxy.submit_transact(1, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let size = rep.data_size();
            let mut modes = Vec::new();
            let mut pos = 0;
            let mut mode_id = 1u32;
            while pos + 16 <= size {
                rep.set_data_position(pos);
                if let (Ok(w), Ok(h), Ok(fps), Ok(vsync)) = (
                    rep.read_i32(),
                    rep.read_i32(),
                    rep.read_f32(),
                    rep.read_f32(),
                ) && (360..=4320).contains(&w)
                    && (360..=8192).contains(&h)
                    && (23.9..=360.1).contains(&fps)
                    && (fps - vsync).abs() < 0.5
                {
                    let fps_round = fps.round() as u32;
                    if !modes
                        .iter()
                        .any(|m: &crate::platform::display::DisplayMode| {
                            m.width == w as u32
                                && m.height == h as u32
                                && (m.fps.round() as u32) == fps_round
                        })
                    {
                        modes.push(crate::platform::display::DisplayMode {
                            id: mode_id,
                            width: w as u32,
                            height: h as u32,
                            fps,
                        });
                        mode_id += 1;
                    }
                }
                pos += 4;
            }
            if !modes.is_empty() {
                return Ok(Some(modes));
            }
        }
        Ok(None)
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = display_id;
        Ok(None)
    }
}

/// Query user preferred display refresh rate directly via Native Binder (code 47).
/// Returns `None` if device is in default dynamic/auto refresh rate mode.
pub fn get_user_preferred_display_mode() -> Result<Option<f32>> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
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

/// Query the current active display refresh rate in Hz from Native Binder.
/// Reads user preferred display mode (code 47).
/// If dynamic/auto (returns None), falls back to the max refresh rate from `IDisplayManager.getDisplayInfo(0)`.
pub fn get_active_display_refresh_rate() -> f64 {
    #[cfg(target_os = "android")]
    {
        if let Ok(Some(hz)) = get_user_preferred_display_mode()
            && hz > 0.0
        {
            return hz as f64;
        }
        if let Ok(Some(modes)) = query_binder_display_modes(0)
            && let Some(max_hz) = modes.iter().map(|m| m.fps).max_by(|a, b| a.total_cmp(b))
            && max_hz > 0.0
        {
            return max_hz as f64;
        }
        60.0
    }
    #[cfg(not(target_os = "android"))]
    {
        120.0
    }
}

/// Query discrete supported refresh rates from Native Binder (e.g. [60, 120]).
pub fn query_binder_supported_rates() -> Vec<u32> {
    #[cfg(target_os = "android")]
    {
        if let Ok(Some(modes)) = query_binder_display_modes(0) {
            let mut rates: Vec<u32> = modes.into_iter().map(|m| m.fps.round() as u32).collect();
            rates.sort_unstable();
            rates.dedup();
            if !rates.is_empty() {
                return rates;
            }
        }
        vec![60, 120]
    }
    #[cfg(not(target_os = "android"))]
    {
        vec![60, 120]
    }
}

/// Set or clear display mode via Native Binder IPC (code 45).
/// Passing `hz <= 0.0` clears the mode (`null`), restoring Dynamic Refresh Rate.
#[cfg(target_os = "android")]
fn set_binder_display_mode(hz: f32) -> Result<()> {
    if !crate::platform::binder::ensure_initialized() {
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
fn read_settings_float(key: &str) -> Option<f32> {
    let out = std::process::Command::new("cmd")
        .args(["settings", "get", "system", key])
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&out.stdout).trim().to_string();
    text.parse::<f32>().ok()
}
