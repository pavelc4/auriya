//! Status Bar actuator via Android IStatusBarService.
//!
//! Provides status bar lock: locks the notification shade / quick settings expansion
//! and suppresses heads-up popups (TRANSACTION_disable = 4).

use anyhow::Result;
use std::sync::Mutex;

#[cfg(target_os = "android")]
struct StatusBarToken;

#[cfg(target_os = "android")]
impl rsbinder::Remotable for StatusBarToken {
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

// DISABLE_EXPAND (0x00010000) | DISABLE_NOTIFICATION_ALERTS (0x00040000)
pub const DISABLE_STATUSBAR_LOCK: i32 = 0x00050000;
pub const DISABLE_NONE: i32 = 0;

#[cfg(target_os = "android")]
static STATUSBAR_TOKEN: Mutex<Option<rsbinder::SIBinder>> = Mutex::new(None);

/// Immediately collapse any expanded notification panel or quick settings (code 2).
pub fn collapse_panels() -> Result<()> {
    #[cfg(target_os = "android")]
    {
        if !crate::platform::binder::ensure_initialized() {
            return Ok(());
        }
        let sb_svc = match rsbinder::hub::check_service("statusbar") {
            Some(s) => s,
            None => return Ok(()),
        };
        let proxy = match sb_svc.as_proxy() {
            Some(p) => p,
            None => return Ok(()),
        };
        // TRANSACTION_collapsePanels = 2
        let data = proxy.prepare_transact(true)?;
        let _ = proxy.submit_transact(2, &data, 0);
    }
    Ok(())
}

/// Enable or disable status bar lock via IStatusBarService.disable (code 4).
/// When enabled, notification panel pull-down and heads-up alerts are disabled.
pub fn set_statusbar_lock(enable: bool) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        use rsbinder::Interface;

        let mut lock_guard = STATUSBAR_TOKEN
            .lock()
            .map_err(|_| anyhow::anyhow!("StatusBarToken lock poisoned"))?;

        if enable {
            if lock_guard.is_some() {
                return Ok(true);
            }

            if !crate::platform::binder::ensure_initialized() {
                anyhow::bail!("Binder ProcessState not initialized");
            }

            let sb_svc = match rsbinder::hub::check_service("statusbar") {
                Some(s) => s,
                None => anyhow::bail!("statusbar service not found"),
            };
            let proxy = match sb_svc.as_proxy() {
                Some(p) => p,
                None => anyhow::bail!("statusbar service is not a proxy"),
            };

            // Collapse any active shade before locking
            let coll_data = proxy.prepare_transact(true)?;
            let _ = proxy.submit_transact(2, &coll_data, 0);

            let token_binder = rsbinder::Binder::new(StatusBarToken);
            let token = token_binder.as_binder();

            // TRANSACTION_disable = 4
            let mut data = proxy.prepare_transact(true)?;
            data.write(&DISABLE_STATUSBAR_LOCK)?;
            data.write(&token)?;
            data.write(&"dev.auriya".to_string())?;

            let reply_opt = proxy.submit_transact(4, &data, 0)?;
            if let Some(mut rep) = reply_opt {
                let ex = rep.read_i32()?;
                if ex != 0 {
                    anyhow::bail!("statusbar disable returned exception code: {}", ex);
                }
            }

            *lock_guard = Some(token);
            tracing::info!(target: "auriya::binder", "Enabled status bar lock (notification shade & heads-up locked)");
            Ok(true)
        } else {
            let Some(token) = lock_guard.take() else {
                return Ok(false);
            };

            if !crate::platform::binder::ensure_initialized() {
                return Ok(false);
            }

            let sb_svc = match rsbinder::hub::check_service("statusbar") {
                Some(s) => s,
                None => return Ok(false),
            };
            let proxy = match sb_svc.as_proxy() {
                Some(p) => p,
                None => return Ok(false),
            };

            // TRANSACTION_disable = 4 with DISABLE_NONE
            let mut data = proxy.prepare_transact(true)?;
            data.write(&DISABLE_NONE)?;
            data.write(&token)?;
            data.write(&"dev.auriya".to_string())?;

            let reply_opt = proxy.submit_transact(4, &data, 0)?;
            if let Some(mut rep) = reply_opt {
                let ex = rep.read_i32()?;
                if ex != 0 {
                    tracing::warn!(target: "auriya::binder", "statusbar restore returned exception code: {}", ex);
                }
            }

            tracing::info!(target: "auriya::binder", "Disabled status bar lock (notification shade restored)");
            Ok(false)
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = enable;
        Ok(false)
    }
}

/// Query whether status bar lock (notification shade lock) is currently active.
pub fn is_statusbar_locked() -> bool {
    #[cfg(target_os = "android")]
    {
        STATUSBAR_TOKEN.lock().map(|g| g.is_some()).unwrap_or(false)
    }

    #[cfg(not(target_os = "android"))]
    {
        false
    }
}
