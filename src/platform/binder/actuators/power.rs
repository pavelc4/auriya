//! Power Manager actuator via Android `IPowerManager`.
//!
//! Provides direct Native Binder control of Android Battery Saver state
//! without executing subshell commands (`cmd power set-mode`).

use anyhow::Result;

pub const POWER_SERVICE: &str = "power";

// Transaction codes for IPowerManager$Stub across Android versions
pub const TRANSACTION_IS_POWER_SAVE_MODE: u32 = 15;
pub const TRANSACTION_SET_POWER_SAVE_MODE_ENABLED: u32 = 17;
pub const TRANSACTION_IS_BATTERY_SAVER_SUPPORTED: u32 = 18;

#[cfg(target_os = "android")]
fn get_is_power_save_mode_code() -> u32 {
    let api = crate::platform::common::info::props::get_system_property("ro.build.version.sdk")
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(34);
    match api {
        0..=30 => 13,
        31..=33 => 14,
        _ => 15,
    }
}

/// Query whether Android OS Battery Saver is currently active via Native Binder.
pub fn is_power_save_mode() -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return Ok(false);
        }
        let svc = match rsbinder::hub::check_service(POWER_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("power service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("power service is not a proxy"),
        };

        let tx_code = get_is_power_save_mode_code();
        let data = proxy.prepare_transact(true)?;
        let reply_opt = proxy.submit_transact(tx_code, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("isPowerSaveMode returned exception code: {}", ex);
            }
            return Ok(rep.read_bool().unwrap_or(false));
        }
        anyhow::bail!("isPowerSaveMode received no reply");
    }

    #[cfg(not(target_os = "android"))]
    {
        Ok(false)
    }
}

/// Enable or disable Android OS Battery Saver mode via Native Binder.
pub fn set_power_save_mode_enabled(enable: bool) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(POWER_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("power service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("power service is not a proxy"),
        };

        // TRANSACTION_setPowerSaveModeEnabled = 26
        let mut data = proxy.prepare_transact(true)?;
        data.write(&enable)?;

        let reply_opt = proxy.submit_transact(TRANSACTION_SET_POWER_SAVE_MODE_ENABLED, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("setPowerSaveModeEnabled returned exception code: {}", ex);
            }
            let success = rep.read_bool().unwrap_or(false);
            tracing::info!(target: "auriya::binder", "Set Battery Saver enabled: {} (success: {})", enable, success);
            return Ok(success);
        }
        anyhow::bail!("setPowerSaveModeEnabled received no reply");
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Set Battery Saver enabled: {}", enable);
        Ok(enable)
    }
}
