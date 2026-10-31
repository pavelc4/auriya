//! Hardware Vibrator HAL actuator via AIDL `android.hardware.vibrator.IVibrator`.
//!
//! Controls device haptics directly via hardware HAL to silence vibrations during
//! intensive sessions or trigger native haptic pulses.

use anyhow::Result;

pub const VIBRATOR_SERVICE: &str = "android.hardware.vibrator.IVibrator/default";

/// Query hardware vibrator capabilities bitmask.
pub fn get_vibrator_capabilities() -> Result<u32> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(VIBRATOR_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("Vibrator HAL not found: {}", VIBRATOR_SERVICE),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("Vibrator HAL is not a proxy"),
        };
        // TRANSACTION_getCapabilities = 1
        let data = proxy.prepare_transact(true)?;
        let reply_opt = proxy.submit_transact(1, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("getCapabilities returned exception code: {}", ex);
            }
            let caps = rep.read_i32()?;
            return Ok(caps as u32);
        }
        anyhow::bail!("No reply received from Vibrator HAL");
    }

    #[cfg(not(target_os = "android"))]
    {
        Ok(0x27)
    }
}

/// Immediately turn off the hardware vibrator motor.
pub fn silence_vibrator() -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return Ok(());
        }
        let svc = match rsbinder::hub::check_service(VIBRATOR_SERVICE) {
            Some(s) => s,
            None => return Ok(()),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => return Ok(()),
        };
        // TRANSACTION_off = 2
        let data = proxy.prepare_transact(true)?;
        let reply_opt = proxy.submit_transact(2, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("Vibrator off returned exception code: {}", ex);
            }
        }
        tracing::debug!(target: "auriya::binder", "Hardware vibrator silenced");
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Hardware vibrator silenced");
        Ok(())
    }
}

/// Trigger a short haptic pulse on the hardware vibrator motor.
pub fn pulse_vibrator(duration_ms: u32) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(VIBRATOR_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("Vibrator HAL not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("Vibrator HAL is not a proxy"),
        };
        // TRANSACTION_on = 3
        let mut data = proxy.prepare_transact(true)?;
        data.write(&(duration_ms as i32))?;
        data.write(&Option::<rsbinder::SIBinder>::None)?;
        let reply_opt = proxy.submit_transact(3, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                anyhow::bail!("Vibrator on returned exception code: {}", ex);
            }
        }
        tracing::debug!(target: "auriya::binder", "Hardware vibrator pulse {}ms", duration_ms);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = duration_ms;
        tracing::debug!(target: "auriya::binder", "[Host Mock] Hardware vibrator pulse {}ms", duration_ms);
        Ok(())
    }
}
