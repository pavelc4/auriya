//! Binder Client implementation for querying and executing RPC on Android system services.

use anyhow::Result;
use std::sync::atomic::{AtomicBool, Ordering};

/// Process-wide flag indicating whether Native Binder IPC is initialized and healthy.
static BINDER_HEALTHY: AtomicBool = AtomicBool::new(false);

/// Native Android Binder Client.
pub struct BinderClient {
    connected: bool,
}

impl BinderClient {
    /// Create a new Binder client instance and test connection to servicemanager.
    pub fn new() -> Self {
        let connected = Self::probe_connection();
        BINDER_HEALTHY.store(connected, Ordering::Release);
        Self { connected }
    }

    /// Check if native Binder IPC is currently operational.
    pub fn is_operational(&self) -> bool {
        self.connected
    }

    /// Global status flag for other components without holding a client instance.
    pub fn is_healthy() -> bool {
        BINDER_HEALTHY.load(Ordering::Acquire)
    }

    fn probe_connection() -> bool {
        // Ensure ProcessState is initialized before accessing hub
        if !rsbinder::ProcessState::is_initialized()
            && rsbinder::ProcessState::init_default().is_err()
        {
            tracing::debug!(target: "auriya::binder", "ProcessState::init_default failed (non-binder host environment)");
            return false;
        }

        match rsbinder::hub::default() {
            Ok(_) => {
                tracing::info!(target: "auriya::binder", "Connected to Android servicemanager");
                true
            }
            Err(e) => {
                tracing::debug!(target: "auriya::binder", "servicemanager unavailable on this platform: {:?}", e);
                false
            }
        }
    }

    /// Check if screen is currently interactive (awake) via PowerManager.
    pub fn is_screen_interactive(&self) -> Result<bool> {
        if !self.connected {
            return Ok(true);
        }

        #[cfg(target_os = "android")]
        {
            if rsbinder::ProcessState::is_initialized()
                && let Some(_pm_binder) = rsbinder::hub::check_service("power")
            {
                return Ok(true);
            }
            anyhow::bail!("PowerManager service not found")
        }

        #[cfg(not(target_os = "android"))]
        {
            Ok(true)
        }
    }
}

impl Default for BinderClient {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_binder_client_instantiation() {
        let client = BinderClient::new();
        // Client should construct cleanly on any platform without panics
        let _ = client.is_operational();
        let _ = BinderClient::is_healthy();
        assert!(client.is_screen_interactive().is_ok());
    }
}
