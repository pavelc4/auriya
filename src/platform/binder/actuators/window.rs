//! Window Manager actuator via Android `IWindowManager`.
//!
//! Provides orientation lock (`freezeDisplayRotation` / `thawDisplayRotation`) and
//! instant animation speed control (`setAnimationScale`) without subshell execution.

use anyhow::Result;

pub const WINDOW_SERVICE: &str = "window";

// Transaction codes extracted from framework.jar on Android 14/15/17
pub const TRANSACTION_GET_ANIMATION_SCALE: u32 = 37;
pub const TRANSACTION_GET_ANIMATION_SCALES: u32 = 38;
pub const TRANSACTION_SET_ANIMATION_SCALE: u32 = 39;
pub const TRANSACTION_SET_ANIMATION_SCALES: u32 = 40;
pub const TRANSACTION_GET_DEFAULT_DISPLAY_ROTATION: u32 = 48;
pub const TRANSACTION_FREEZE_ROTATION: u32 = 55;
pub const TRANSACTION_THAW_ROTATION: u32 = 56;
pub const TRANSACTION_IS_ROTATION_FROZEN: u32 = 57;
pub const TRANSACTION_FREEZE_DISPLAY_ROTATION: u32 = 59;
pub const TRANSACTION_THAW_DISPLAY_ROTATION: u32 = 60;
pub const TRANSACTION_IS_DISPLAY_ROTATION_FROZEN: u32 = 61;

/// Freeze display orientation to prevent sudden auto-rotation.
/// Passing `rotation = -1` freezes at the current orientation.
pub fn freeze_display_rotation(display_id: i32, rotation: i32) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(WINDOW_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("window service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("window service is not a proxy"),
        };

        // TRANSACTION_freezeDisplayRotation = 59
        let mut data = proxy.prepare_transact(true)?;
        data.write(&display_id)?;
        data.write(&rotation)?;
        data.write(&"dev.auriya".to_string())?;

        let reply_opt = proxy.submit_transact(TRANSACTION_FREEZE_DISPLAY_ROTATION, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("freezeDisplayRotation returned exception code: {}", ex);
            }
        }
        tracing::info!(target: "auriya::binder", "Frozen display {} rotation to {}", display_id, rotation);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = (display_id, rotation);
        tracing::debug!(target: "auriya::binder", "[Host Mock] Frozen display rotation");
        Ok(())
    }
}

/// Thaw (unlock) display orientation, restoring sensor-based auto-rotation.
pub fn thaw_display_rotation(display_id: i32) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return Ok(());
        }
        let svc = match rsbinder::hub::check_service(WINDOW_SERVICE) {
            Some(s) => s,
            None => return Ok(()),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => return Ok(()),
        };

        // TRANSACTION_thawDisplayRotation = 60
        let mut data = proxy.prepare_transact(true)?;
        data.write(&display_id)?;
        data.write(&"dev.auriya".to_string())?;

        let reply_opt = proxy.submit_transact(TRANSACTION_THAW_DISPLAY_ROTATION, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("thawDisplayRotation returned exception code: {}", ex);
            }
        }
        tracing::info!(target: "auriya::binder", "Thawed display {} rotation", display_id);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = display_id;
        tracing::debug!(target: "auriya::binder", "[Host Mock] Thawed display rotation");
        Ok(())
    }
}

/// Query whether display rotation is currently frozen (locked).
pub fn is_display_rotation_frozen(display_id: i32) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return Ok(false);
        }
        let svc = match rsbinder::hub::check_service(WINDOW_SERVICE) {
            Some(s) => s,
            None => return Ok(false),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => return Ok(false),
        };

        // TRANSACTION_isDisplayRotationFrozen = 61
        let mut data = proxy.prepare_transact(true)?;
        data.write(&display_id)?;

        let reply_opt = proxy.submit_transact(TRANSACTION_IS_DISPLAY_ROTATION_FROZEN, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                return Ok(false);
            }
            return Ok(rep.read_bool().unwrap_or(false));
        }
        Ok(false)
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = display_id;
        Ok(false)
    }
}

/// Query current system animation scales `[window, transition, animator]`.
pub fn get_animation_scales() -> Result<Vec<f32>> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(WINDOW_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("window service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("window service is not a proxy"),
        };

        // TRANSACTION_getAnimationScales = 38
        let data = proxy.prepare_transact(true)?;
        let reply_opt = proxy.submit_transact(TRANSACTION_GET_ANIMATION_SCALES, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("getAnimationScales returned exception code: {}", ex);
            }
            let len = rep.read_i32()?;
            if len <= 0 {
                return Ok(vec![1.0, 1.0, 1.0]);
            }
            let mut scales = Vec::with_capacity(len as usize);
            for _ in 0..len {
                scales.push(rep.read_f32().unwrap_or(1.0));
            }
            return Ok(scales);
        }
        anyhow::bail!("getAnimationScales received no reply");
    }

    #[cfg(not(target_os = "android"))]
    {
        Ok(vec![1.0, 1.0, 1.0])
    }
}

/// Set global system animation scale multiplier (e.g. 0.0 for instant, 1.0 for default).
pub fn set_animation_scales(scale: f32) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(WINDOW_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("window service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("window service is not a proxy"),
        };

        // TRANSACTION_setAnimationScales = 40
        let mut data = proxy.prepare_transact(true)?;
        data.write(&3i32)?; // length = 3
        data.write(&scale)?;
        data.write(&scale)?;
        data.write(&scale)?;

        let reply_opt = proxy.submit_transact(TRANSACTION_SET_ANIMATION_SCALES, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("setAnimationScales returned exception code: {}", ex);
            }
        }
        tracing::info!(target: "auriya::binder", "Set global animation scale to {:.1}x", scale);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Set animation scales to {:.1}x", scale);
        Ok(())
    }
}
