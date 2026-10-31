//! Hardware and OS actuators managed via Native Binder IPC and system fallback.

pub mod display;
pub mod dnd;
pub mod touch;
pub mod wifi;

pub use display::{apply_refresh_rate, get_user_preferred_display_mode};
pub use dnd::{DndMode, apply_dnd};
pub use touch::{is_high_touch_polling_rate_supported, set_high_touch_polling_rate};
pub use wifi::{is_wifi_low_latency_active, set_wifi_low_latency_lock};
