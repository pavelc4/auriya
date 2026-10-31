//! Display refresh rate and Do Not Disturb CLI handlers.

use anyhow::Result;

use super::util::read_system_setting;
use crate::cli::app::CliDndMode;
use crate::platform::binder::{apply_dnd, apply_refresh_rate};

/// Manage Do Not Disturb filter mode via NotificationManagerService.
pub fn handle_dnd(mode: CliDndMode) -> Result<()> {
    let dnd_mode = mode.into();
    apply_dnd(dnd_mode)?;
    println!("Applied Do Not Disturb mode: {:?}", dnd_mode);

    #[cfg(target_os = "android")]
    {
        let zen = read_system_setting("global", "zen_mode");
        println!("Verified setting (global/zen_mode): {}", zen);
    }

    Ok(())
}

/// Apply display refresh rate override or restore dynamic behavior via DisplayManagerService.
pub fn handle_rate(hz: u32) -> Result<()> {
    apply_refresh_rate(hz)?;
    if hz == 0 {
        println!("Restored original display refresh rate preferences (dynamic)");
    } else {
        println!("Applied display refresh rate: {}Hz", hz);
    }

    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        let binder_str = binder_mode
            .map(|m| format!("{:.0}Hz", m))
            .unwrap_or_else(|| "null (dynamic)".to_string());
        println!("Verified Native Binder mode: {}", binder_str);

        let peak = read_system_setting("system", "peak_refresh_rate");
        let min = read_system_setting("system", "min_refresh_rate");
        println!("Verified settings (peak: {}, min: {})", peak, min);
    }

    Ok(())
}
