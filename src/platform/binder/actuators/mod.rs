//! Hardware and OS actuators managed via Native Binder IPC and system fallback.

pub mod display;
pub mod dnd;
pub mod statusbar;
pub mod touch;
pub mod wifi;

pub use display::{
    apply_refresh_rate, get_user_preferred_display_mode, query_binder_display_modes,
};
pub use dnd::{DndMode, apply_dnd};
pub use statusbar::{collapse_panels, is_statusbar_locked, set_statusbar_lock};
pub use touch::{is_high_touch_polling_rate_supported, set_high_touch_polling_rate};
pub use wifi::{is_wifi_low_latency_active, set_wifi_low_latency_lock};
