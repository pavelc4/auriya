//! Vendor Hardware Power HAL actuator via AIDL `android.hardware.power.IPower`.
//!
//! Directly drives Qualcomm QTI PowerHAL / MediaTek PowerHAL without manual
//! kernel sysfs tuning or subshell execution.

use anyhow::Result;
use serde::{Deserialize, Serialize};

use std::sync::atomic::{AtomicU32, Ordering};

pub const POWER_HAL_SERVICE: &str = "android.hardware.power.IPower/default";

static ACTIVE_POWER_MODES: AtomicU32 = AtomicU32::new(0);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[repr(i32)]
pub enum PowerMode {
    DoubleTapToWake = 0,
    LowPower = 1,
    SustainedPerformance = 2,
    FixedPerformance = 3,
    Vr = 4,
    DeviceIdle = 5,
    MultiWindow = 6,
    ExpensiveRendering = 7,
    Interactive = 8,
    Game = 9,
    AudioStreamingLowLatency = 10,
    CameraStreamingSecure = 11,
    GameLoading = 12,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[repr(i32)]
pub enum PowerBoost {
    Interaction = 0,
    DisplayUpdateImmediate = 1,
    MlAccelerator = 2,
    AudioLaunch = 3,
    CameraLaunch = 4,
    CameraShot = 5,
}

/// Query whether a specific PowerMode is supported by the hardware PowerHAL.
pub fn is_power_mode_supported(mode: PowerMode) -> bool {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return false;
        }
        let svc = match rsbinder::hub::check_service(POWER_HAL_SERVICE) {
            Some(s) => s,
            None => return false,
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => return false,
        };
        // TRANSACTION_isModeSupported = 2
        let Ok(mut data) = proxy.prepare_transact(true) else {
            return false;
        };
        if data.write(&(mode as i32)).is_err() {
            return false;
        }
        let Ok(Some(mut reply)) = proxy.submit_transact(2, &data, 0) else {
            return false;
        };
        if let Ok(ex) = reply.read_i32()
            && ex != 0
        {
            return false;
        }
        reply.read_bool().unwrap_or(false)
    }

    #[cfg(not(target_os = "android"))]
    {
        matches!(
            mode,
            PowerMode::SustainedPerformance
                | PowerMode::FixedPerformance
                | PowerMode::ExpensiveRendering
                | PowerMode::DeviceIdle
        )
    }
}

/// Set a hardware PowerMode on the vendor PowerHAL.
pub fn set_power_mode(mode: PowerMode, enable: bool) -> Result<()> {
    let bit = 1u32 << (mode as u32);
    let prev = ACTIVE_POWER_MODES.load(Ordering::Relaxed);
    let is_active = (prev & bit) != 0;
    if is_active == enable {
        return Ok(());
    }

    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(POWER_HAL_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("PowerHAL service not found: {}", POWER_HAL_SERVICE),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("PowerHAL service is not a proxy"),
        };
        // TRANSACTION_setMode = 1 (oneway void setMode(in Mode type, in boolean enabled))
        let mut data = proxy.prepare_transact(true)?;
        data.write(&(mode as i32))?;
        data.write(&enable)?;
        proxy.submit_transact(1, &data, rsbinder::FLAG_ONEWAY)?;
        if enable {
            ACTIVE_POWER_MODES.fetch_or(bit, Ordering::Relaxed);
        } else {
            ACTIVE_POWER_MODES.fetch_and(!bit, Ordering::Relaxed);
        }
        tracing::debug!(target: "auriya::binder", "PowerHAL setMode {:?} -> {}", mode, enable);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        if enable {
            ACTIVE_POWER_MODES.fetch_or(bit, Ordering::Relaxed);
        } else {
            ACTIVE_POWER_MODES.fetch_and(!bit, Ordering::Relaxed);
        }
        tracing::debug!(target: "auriya::binder", "[Host Mock] PowerHAL setMode {:?} -> {}", mode, enable);
        Ok(())
    }
}

/// Query whether a specific PowerBoost is supported by the hardware PowerHAL.
pub fn is_power_boost_supported(boost: PowerBoost) -> bool {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return false;
        }
        let svc = match rsbinder::hub::check_service(POWER_HAL_SERVICE) {
            Some(s) => s,
            None => return false,
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => return false,
        };
        // TRANSACTION_isBoostSupported = 4
        let Ok(mut data) = proxy.prepare_transact(true) else {
            return false;
        };
        if data.write(&(boost as i32)).is_err() {
            return false;
        }
        let Ok(Some(mut reply)) = proxy.submit_transact(4, &data, 0) else {
            return false;
        };
        if let Ok(ex) = reply.read_i32()
            && ex != 0
        {
            return false;
        }
        reply.read_bool().unwrap_or(false)
    }

    #[cfg(not(target_os = "android"))]
    {
        matches!(boost, PowerBoost::Interaction)
    }
}

/// Trigger a hardware PowerBoost on the vendor PowerHAL with specified duration.
pub fn set_power_boost(boost: PowerBoost, duration_ms: i32) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(POWER_HAL_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("PowerHAL service not found: {}", POWER_HAL_SERVICE),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("PowerHAL service is not a proxy"),
        };
        // TRANSACTION_setBoost = 3 (oneway void setBoost(in Boost type, in int durationMs))
        let mut data = proxy.prepare_transact(true)?;
        data.write(&(boost as i32))?;
        data.write(&duration_ms)?;
        proxy.submit_transact(3, &data, rsbinder::FLAG_ONEWAY)?;
        tracing::debug!(target: "auriya::binder", "PowerHAL setBoost {:?} for {}ms", boost, duration_ms);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = duration_ms;
        tracing::debug!(target: "auriya::binder", "[Host Mock] PowerHAL setBoost {:?} for {}ms", boost, duration_ms);
        Ok(())
    }
}
