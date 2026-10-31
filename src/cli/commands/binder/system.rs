//! System locks CLI handlers (Wi-Fi low latency, status bar lock, high touch rate).

use anyhow::Result;

/// Toggle or query Wi-Fi low-latency lock.
pub fn handle_wifi(action: Option<String>) -> Result<()> {
    let act = action.as_deref().unwrap_or("status");
    match act {
        "on" | "enable" | "acquire" => {
            crate::platform::binder::set_wifi_low_latency_lock(true)?;
            println!("Acquired Wi-Fi Low-Latency lock (WIFI_MODE_FULL_LOW_LATENCY = 4)");
        }
        "off" | "disable" | "release" => {
            crate::platform::binder::set_wifi_low_latency_lock(false)?;
            println!("Released Wi-Fi Low-Latency lock");
        }
        "status" => {
            let active = crate::platform::binder::is_wifi_low_latency_active();
            println!(
                "Wi-Fi Low-Latency Lock: {}",
                if active { "Active (Held)" } else { "Inactive" }
            );
        }
        other => {
            eprintln!("Unknown action '{}'. Valid actions: on, off, status", other);
        }
    }
    Ok(())
}

/// Toggle or query Status Bar lock (prevent notification shade expansion).
pub fn handle_statusbar(action: Option<String>) -> Result<()> {
    let act = action.as_deref().unwrap_or("status");
    match act {
        "lock" | "on" | "enable" => {
            crate::platform::binder::collapse_panels()?;
            crate::platform::binder::set_statusbar_lock(true)?;
            println!("Status Bar Lock: active (notification shade & alerts disabled)");
        }
        "unlock" | "off" | "disable" => {
            crate::platform::binder::set_statusbar_lock(false)?;
            println!("Status Bar Lock: restored (notification shade & alerts restored)");
        }
        "status" => {
            let active = crate::platform::binder::is_statusbar_locked();
            println!(
                "Status Bar Lock: {}",
                if active {
                    "Active (Locked)"
                } else {
                    "Inactive (Unlocked)"
                }
            );
        }
        other => {
            eprintln!(
                "Unknown action '{}'. Valid actions: lock, unlock, status",
                other
            );
        }
    }
    Ok(())
}

/// Toggle or query High Touch Polling Rate game mode.
pub fn handle_touch(action: Option<String>) -> Result<()> {
    let act = action.as_deref().unwrap_or("status");
    match act {
        "on" | "enable" => {
            crate::platform::common::touchpanel::enable_game_mode()?;
            println!("High touch polling rate enabled");
        }
        "off" | "disable" => {
            crate::platform::common::touchpanel::disable_game_mode()?;
            println!("High touch polling rate disabled");
        }
        "status" => {
            let supp = crate::platform::common::touchpanel::is_supported();
            println!(
                "High Touch Polling Rate: {}",
                if supp {
                    "Supported (Ready)"
                } else {
                    "Not supported on this device"
                }
            );
        }
        other => {
            eprintln!("Unknown action '{}'. Valid actions: on, off, status", other);
        }
    }
    Ok(())
}
