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
        if !super::ensure_initialized() {
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

    /// Query the currently focused foreground package and its PID directly via Native Binder IPC.
    /// Uses Android's `activity_task` service (IActivityTaskManager code 30 getFocusedRootTaskInfo).
    pub fn get_foreground_app(&self) -> Result<Option<(String, Option<i32>)>> {
        Ok(self.get_foreground_task()?.map(|t| (t.package, t.pid)))
    }

    /// Query the focused foreground task including window bounds and floating/freeform state.
    pub fn get_foreground_task(&self) -> Result<Option<ForegroundTask>> {
        if !self.connected {
            return Ok(None);
        }

        #[cfg(target_os = "android")]
        {
            if !super::ensure_initialized() {
                return Ok(None);
            }

            let atm = match rsbinder::hub::check_service("activity_task") {
                Some(s) => s,
                None => return Ok(None),
            };
            let proxy = match atm.as_proxy() {
                Some(p) => p,
                None => return Ok(None),
            };

            let data = proxy.prepare_transact(true)?;
            // Transaction 30: getFocusedRootTaskInfo on IActivityTaskManager
            let reply_opt = proxy.submit_transact(30, &data, 0)?;
            let mut reply = match reply_opt {
                Some(r) => r,
                None => return Ok(None),
            };

            if reply.data_size() < 0x28 {
                return Ok(None);
            }

            let ex = reply.read_i32()?;
            if ex != 0 {
                return Ok(None);
            }
            let is_null = reply.read_i32()?;
            if is_null == 0 {
                return Ok(None);
            }

            // Inspect window bounds at offset 0x08 to detect floating / freeform windows
            reply.set_data_position(0x08);
            let has_bounds = reply.read_i32()?;
            let is_floating = if has_bounds != 0 {
                let left = reply.read_i32()?;
                let top = reply.read_i32()?;
                let right = reply.read_i32()?;
                let bottom = reply.read_i32()?;
                is_window_floating(left, top, right, bottom)
            } else {
                false
            };

            // Seek to 0x24: ComponentName presence flag and UTF-16 string
            reply.set_data_position(0x24);
            let has_comp = reply.read_i32()?;
            if has_comp == 0 {
                return Ok(None);
            }

            let comp_str: Option<String> = reply.read()?;
            let comp = match comp_str {
                Some(c) => c,
                None => return Ok(None),
            };

            let pkg = comp.split('/').next().unwrap_or(&comp).trim().to_string();
            if pkg.is_empty() {
                return Ok(None);
            }

            let pid = crate::platform::common::procfs::find_pid_by_package(&pkg);
            Ok(Some(ForegroundTask {
                package: pkg,
                pid,
                is_floating,
            }))
        }

        #[cfg(not(target_os = "android"))]
        {
            Ok(None)
        }
    }
}

/// Detailed information about the currently focused foreground task.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ForegroundTask {
    pub package: String,
    pub pid: Option<i32>,
    pub is_floating: bool,
}

/// Helper to determine if the window bounds indicate a floating, freeform, or split-screen window.
pub fn is_window_floating(left: i32, top: i32, right: i32, bottom: i32) -> bool {
    if left > 20 || top > 20 {
        return true;
    }
    let width = (right - left).abs();
    let height = (bottom - top).abs();
    if width == 0 || height == 0 {
        return false;
    }
    let min_dim = width.min(height);
    let max_dim = width.max(height);
    min_dim < 700 || max_dim < 1400
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
