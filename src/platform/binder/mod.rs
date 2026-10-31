//! Native Android Binder IPC adapter for Auriya.
//!
//! Provides zero-disk in-memory streaming communication with Android OS system services:
//! - NotificationManager (Do Not Disturb)
//! - DisplayManager / Settings (Display refresh rate)
//! - PowerManager (Screen interactive state)
//! - ActivityManager (Process & foreground activity lifecycle)

pub mod actuators;
pub mod client;
pub mod observer;

pub use actuators::{
    DndMode, apply_dnd, apply_refresh_rate, collapse_panels, get_user_preferred_display_mode,
    is_high_touch_polling_rate_supported, is_statusbar_locked, is_wifi_low_latency_active,
    query_binder_display_modes, set_high_touch_polling_rate, set_statusbar_lock,
    set_wifi_low_latency_lock,
};
pub use client::{BinderClient, ForegroundTask, is_window_floating};
pub use observer::{ProcessEvent, ProcessEventType, ProcessObserver};

/// Ensure Binder ProcessState is initialized on Android platforms.
pub fn ensure_initialized() -> bool {
    #[cfg(target_os = "android")]
    {
        if !rsbinder::ProcessState::is_initialized()
            && rsbinder::ProcessState::init_default().is_err()
        {
            return false;
        }
    }
    true
}
