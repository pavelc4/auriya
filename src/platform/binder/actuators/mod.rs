//! Hardware and OS actuators managed via Native Binder IPC and system fallback.

pub mod broadcast;
pub mod display;
pub mod dnd;
pub mod power;
pub mod power_hal;
pub mod statusbar;
pub mod toast;
pub mod touch;
pub mod vibrator;
pub mod wifi;
pub mod window;

pub use broadcast::broadcast_intent;
pub use display::{
    apply_refresh_rate, get_user_preferred_display_mode, query_binder_display_modes,
};
pub use dnd::{DndMode, apply_dnd};
pub use power::{is_power_save_mode, set_power_save_mode_enabled};
pub use power_hal::{
    PowerBoost, PowerMode, is_power_boost_supported, is_power_mode_supported, set_power_boost,
    set_power_mode,
};
pub use statusbar::{collapse_panels, is_statusbar_locked, set_statusbar_lock};
pub use toast::{show_toast, show_toast_with_duration};
pub use touch::{is_high_touch_polling_rate_supported, set_high_touch_polling_rate};
pub use vibrator::{get_vibrator_capabilities, pulse_vibrator, silence_vibrator};
pub use wifi::{is_wifi_low_latency_active, set_wifi_low_latency_lock};
pub use window::{
    freeze_display_rotation, get_animation_scales, is_display_rotation_frozen,
    set_animation_scales, thaw_display_rotation,
};
