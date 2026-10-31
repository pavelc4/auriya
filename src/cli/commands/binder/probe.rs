//! Native Android Binder diagnostic and service availability probe.

use anyhow::Result;

use crate::platform::binder::BinderClient;

/// Execute comprehensive Native Android Binder hardware diagnostic probe.
pub fn handle_probe() -> Result<()> {
    println!("Native Android Binder Probe:");

    let client = BinderClient::new();
    let status_str = if client.is_operational() {
        "Operational"
    } else {
        "Unavailable"
    };
    println!("  Binder IPC Status:          {}", status_str);

    match client.is_screen_interactive() {
        Ok(interactive) => println!("  PowerManager.isInteractive: {}", interactive),
        Err(e) => println!("  PowerManager query:         Error ({:?})", e),
    }

    match client.get_foreground_task() {
        Ok(Some(task)) => {
            let pid_str = task
                .pid
                .map(|p| p.to_string())
                .unwrap_or_else(|| "None".to_string());
            let win_str = if task.is_floating {
                "Floating/Freeform (30 FPS cap)"
            } else {
                "Fullscreen"
            };
            println!(
                "  Foreground App:             {} (PID: {}) [{}]",
                task.package, pid_str, win_str
            );
        }
        Ok(None) => println!("  Foreground App:             None"),
        Err(e) => println!("  Foreground App query:       Error ({:?})", e),
    }

    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        let binder_str = binder_mode
            .map(|m| format!("{:.0}Hz", m))
            .unwrap_or_else(|| "null (dynamic)".to_string());
        println!("  Display Mode (Binder):      {}", binder_str);
    }

    println!(
        "  Wi-Fi Low-Latency Lock:     {}",
        if crate::platform::binder::is_wifi_low_latency_active() {
            "Active (Held)"
        } else {
            "Inactive"
        }
    );

    println!(
        "  Status Bar Lock:            {}",
        if crate::platform::binder::is_statusbar_locked() {
            "Active (Locked)"
        } else {
            "Inactive (Unlocked)"
        }
    );

    println!(
        "  High Touch Polling Rate:    {}",
        if crate::platform::common::touchpanel::is_supported() {
            "Supported (Ready)"
        } else {
            "Not supported on this device"
        }
    );

    if let Ok(battery_saver) = crate::platform::binder::is_power_save_mode() {
        println!(
            "  Battery Saver (Native):     {}",
            if battery_saver { "Enabled" } else { "Disabled" }
        );
    }

    if let Ok(frozen) = crate::platform::binder::is_display_rotation_frozen(0) {
        println!(
            "  Display Rotation:           {}",
            if frozen {
                "Frozen (Locked)"
            } else {
                "Thawed (Unlocked)"
            }
        );
    }

    if let Ok(scales) = crate::platform::binder::get_animation_scales() {
        println!("  Animation Scales:           {:?}", scales);
    }

    if let Ok(caps) = crate::platform::binder::get_vibrator_capabilities() {
        println!("  Vibrator HAL Caps:          0x{:04x}", caps);
    }

    println!(
        "  PowerHAL Sustained:         {}",
        if crate::platform::binder::is_power_mode_supported(
            crate::platform::binder::PowerMode::SustainedPerformance
        ) {
            "Supported"
        } else {
            "Unsupported"
        }
    );
    println!(
        "  PowerHAL Expensive:         {}",
        if crate::platform::binder::is_power_mode_supported(
            crate::platform::binder::PowerMode::ExpensiveRendering
        ) {
            "Supported"
        } else {
            "Unsupported"
        }
    );

    #[cfg(target_os = "android")]
    {
        if rsbinder::ProcessState::is_initialized() {
            for service in [
                "power",
                "notification",
                "activity",
                "activity_task",
                "display",
                "statusbar",
                "window",
                "wifi",
                "thermalservice",
                "android.hardware.power.IPower/default",
                "android.hardware.vibrator.IVibrator/default",
                "vendor.lineage.touch.IHighTouchPollingRate/default",
            ] {
                let avail = rsbinder::hub::check_service(service).is_some();
                println!(
                    "  Service '{:<30}': {}",
                    service,
                    if avail { "Available" } else { "Not found" }
                );
            }
        }
    }

    #[cfg(not(target_os = "android"))]
    {
        println!("  (Running on host OS — Android Binder services mocked)");
    }

    Ok(())
}
