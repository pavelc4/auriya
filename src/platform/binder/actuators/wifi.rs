//! Wi-Fi Low-Latency lock actuator via Android IWifiManager.

use anyhow::Result;
use std::sync::Mutex;

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

            if !crate::platform::binder::ensure_initialized() {
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

            if !crate::platform::binder::ensure_initialized() {
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
