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
    DndMode, PowerBoost, PowerMode, apply_dnd, apply_refresh_rate, collapse_panels,
    freeze_display_rotation, get_animation_scales, get_user_preferred_display_mode,
    get_vibrator_capabilities, is_display_rotation_frozen, is_high_touch_polling_rate_supported,
    is_power_boost_supported, is_power_mode_supported, is_power_save_mode, is_statusbar_locked,
    is_wifi_low_latency_active, pulse_vibrator, query_binder_display_modes, set_animation_scales,
    set_high_touch_polling_rate, set_power_boost, set_power_mode, set_power_save_mode_enabled,
    set_statusbar_lock, set_wifi_low_latency_lock, silence_vibrator, thaw_display_rotation,
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
