//! Native Toast actuator via Android `INotificationManager`.
//!
//! Directly requests `NotificationManagerService` to display standard text toasts
//! on the active display without spawning subshells or depending on an app process.

use anyhow::Result;

pub const NOTIFICATION_SERVICE: &str = "notification";

// Transaction code extracted from framework.jar INotificationManager$Stub
pub const TRANSACTION_ENQUEUE_TEXT_TOAST: u32 = 3;

#[cfg(target_os = "android")]
struct ToastToken;

#[cfg(target_os = "android")]
impl rsbinder::Remotable for ToastToken {
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

/// Display a standard text toast notification directly on the screen.
///
/// `duration`: 0 for short (~2 seconds), 1 for long (~3.5 seconds).
pub fn show_toast(message: &str) -> Result<bool> {
    show_toast_with_duration(message, 0)
}

/// Display a standard text toast notification with specified duration.
pub fn show_toast_with_duration(message: &str, duration: i32) -> Result<bool> {
    #[cfg(target_os = "android")]
    {
        use super::broadcast::write_string8;
        use rsbinder::Interface;

        if !crate::platform::binder::ensure_initialized() {
            anyhow::bail!("Binder ProcessState not initialized");
        }
        let svc = match rsbinder::hub::check_service(NOTIFICATION_SERVICE) {
            Some(s) => s,
            None => anyhow::bail!("notification service not found"),
        };
        let proxy = match svc.as_proxy() {
            Some(p) => p,
            None => anyhow::bail!("notification service is not a proxy"),
        };

        let token_binder = rsbinder::Binder::new(ToastToken);
        let token = token_binder.as_binder();

        // TRANSACTION_enqueueTextToast = 3
        let mut data = proxy.prepare_transact(true)?;
        data.write(&"root".to_string())?; // 1. pkg (String16) - Android allows UID 0 to use "root"
        data.write(&token)?; // 2. token (IBinder with stability)

        // 3. text: CharSequence via readTypedObject(CHAR_SEQUENCE_CREATOR)
        data.write_i32(1)?; // readTypedObject non-null indicator (1)
        data.write_i32(1)?; // CHAR_SEQUENCE_CREATOR plain string kind (1)
        write_string8(&mut data, Some(message))?; // String8 content

        // 4. duration (int): 0 = SHORT, 1 = LONG
        data.write_i32(duration)?;
        // 5. isUiContext (boolean)
        data.write_bool(false)?;
        // 6. displayId (int): 0 = DEFAULT_DISPLAY
        data.write_i32(0)?;
        // 7. callback: null strong binder with stability
        data.write(&Option::<rsbinder::SIBinder>::None)?;

        let reply_opt = proxy.submit_transact(TRANSACTION_ENQUEUE_TEXT_TOAST, &data, 0)?;
        if let Some(mut rep) = reply_opt {
            let ex = rep.read_i32()?;
            if ex != 0 {
                let err_msg: String = rep.read().unwrap_or_default();
                anyhow::bail!("enqueueTextToast returned exception {}: {}", ex, err_msg);
            }
            let success = rep.read_bool().unwrap_or(false);
            tracing::info!(target: "auriya::binder", "Displayed Native Toast: '{}' (success: {})", message, success);
            return Ok(success);
        }
        anyhow::bail!("enqueueTextToast received no reply");
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Native Toast: '{}' (duration: {})", message, duration);
        Ok(true)
    }
}
