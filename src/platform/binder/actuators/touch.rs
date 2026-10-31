//! High Touch Polling Rate actuator via LineageOS/AOSP Touch HAL AIDL.

use anyhow::Result;

/// Set High Touch Polling Rate via LineageOS/AOSP Touch HAL if available.
pub fn set_high_touch_polling_rate(enabled: bool) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
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
        if !crate::platform::binder::ensure_initialized() {
            return false;
        }
        rsbinder::hub::check_service("vendor.lineage.touch.IHighTouchPollingRate/default").is_some()
    }

    #[cfg(not(target_os = "android"))]
    {
        false
    }
}
