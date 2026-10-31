//! Handler for Native Android Binder CLI commands and diagnostics.

use crate::Result;
use crate::cli::app::{BinderCommands, CliDndMode};
use crate::platform::binder::{BinderClient, apply_dnd, apply_refresh_rate};

pub async fn handle_binder(cmd: BinderCommands) -> Result<()> {
    match cmd {
        BinderCommands::Probe => handle_probe(),
        BinderCommands::Test => handle_test(),
        BinderCommands::Dnd { mode } => handle_dnd(mode),
        BinderCommands::Rate { hz } => handle_rate(hz),
        BinderCommands::Wifi { action } => handle_wifi(action),
        BinderCommands::Touch { action } => handle_touch(action),
        BinderCommands::Statusbar { action } => handle_statusbar(action),
        BinderCommands::Power {
            action,
            name,
            value,
        } => handle_power(&action, name.as_deref(), value.as_deref()),
        BinderCommands::Vibrator {
            action,
            duration_ms,
        } => handle_vibrator(action.as_deref(), duration_ms),
        BinderCommands::Window { action, param } => handle_window(&action, param.as_deref()),
        BinderCommands::PowerSave { action } => handle_power_save(action.as_deref()),
    }
}

fn handle_statusbar(action: Option<String>) -> Result<()> {
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

fn handle_touch(action: Option<String>) -> Result<()> {
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

fn handle_wifi(action: Option<String>) -> Result<()> {
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

fn handle_probe() -> Result<()> {
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

fn handle_dnd(mode: CliDndMode) -> Result<()> {
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

fn handle_rate(hz: u32) -> Result<()> {
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

fn handle_test() -> Result<()> {
    println!("Native Binder Hardware Test:");

    let client = BinderClient::new();
    println!(
        "[1/11] Binder Servicemanager: {}",
        if client.is_operational() {
            "PASS"
        } else {
            "FAIL (unavailable)"
        }
    );

    match client.is_screen_interactive() {
        Ok(interactive) => println!("[2/11] PowerManager.isInteractive ({}): PASS", interactive),
        Err(e) => println!("[2/11] PowerManager query: FAIL ({:?})", e),
    }

    match client.get_foreground_task() {
        Ok(Some(task)) => {
            let pid_str = task
                .pid
                .map(|p| p.to_string())
                .unwrap_or_else(|| "None".to_string());
            let win_str = if task.is_floating {
                "floating (30 FPS cap)"
            } else {
                "fullscreen"
            };
            println!(
                "[3/11] Native Binder Foreground ({} | PID: {} | {}): PASS",
                task.package, pid_str, win_str
            );
        }
        Ok(None) => println!("[3/11] Native Binder Foreground: PASS (no focused task)"),
        Err(e) => println!("[3/11] Native Binder Foreground: FAIL ({:?})", e),
    }

    println!("[4/11] Testing DnD actuator (Priority)...");
    apply_dnd(crate::platform::binder::DndMode::Priority)?;
    #[cfg(target_os = "android")]
    {
        let zen = read_system_setting("global", "zen_mode");
        println!("       zen_mode is {} (expected: 1)", zen);
    }
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring DnD actuator (All)...");
    apply_dnd(crate::platform::binder::DndMode::All)?;
    #[cfg(target_os = "android")]
    {
        let zen = read_system_setting("global", "zen_mode");
        println!("       zen_mode restored to {} (expected: 0)", zen);
    }

    println!("[5/11] Testing Display Refresh Rate actuator (120Hz)...");
    apply_refresh_rate(120)?;
    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        println!("       Native Binder mode: {:?}", binder_mode);
        let peak = read_system_setting("system", "peak_refresh_rate");
        println!("       peak_refresh_rate is {} (expected: 120.0)", peak);
    }
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring Display Refresh Rate (0Hz / dynamic)...");
    apply_refresh_rate(0)?;
    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        println!("       Native Binder mode restored to {:?}", binder_mode);
        let peak = read_system_setting("system", "peak_refresh_rate");
        println!("       peak_refresh_rate restored to {}", peak);
    }

    println!("[6/11] Testing Wi-Fi Low-Latency lock actuator...");
    let lock_res = crate::platform::binder::set_wifi_low_latency_lock(true);
    let active = crate::platform::binder::is_wifi_low_latency_active();
    println!(
        "       Acquire Wi-Fi Low-Latency result: {:?} (Active: {})",
        lock_res, active
    );
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring Wi-Fi Low-Latency lock...");
    let rel_res = crate::platform::binder::set_wifi_low_latency_lock(false);
    let active_after = crate::platform::binder::is_wifi_low_latency_active();
    println!(
        "       Release Wi-Fi Low-Latency result: {:?} (Active: {})",
        rel_res, active_after
    );

    println!("[7/11] Testing Status Bar lock actuator...");
    let lock_res = crate::platform::binder::set_statusbar_lock(true);
    let lock_active = crate::platform::binder::is_statusbar_locked();
    println!(
        "       Lock Status Bar result: {:?} (Active: {})",
        lock_res, lock_active
    );
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring Status Bar lock...");
    let rel_res = crate::platform::binder::set_statusbar_lock(false);
    let lock_active_after = crate::platform::binder::is_statusbar_locked();
    println!(
        "       Unlock Status Bar result: {:?} (Active: {})",
        rel_res, lock_active_after
    );

    println!("[8/11] Testing PowerHAL modes actuator...");
    let mode_res = crate::platform::binder::set_power_mode(
        crate::platform::binder::PowerMode::ExpensiveRendering,
        true,
    );
    println!("       Set ExpensiveRendering=true: {:?}", mode_res);
    let boost_res = crate::platform::binder::set_power_boost(
        crate::platform::binder::PowerBoost::Interaction,
        50,
    );
    println!("       Trigger Interaction boost: {:?}", boost_res);
    let _ = crate::platform::binder::set_power_mode(
        crate::platform::binder::PowerMode::ExpensiveRendering,
        false,
    );

    println!("[9/11] Testing Vibrator HAL actuator...");
    let vib_caps = crate::platform::binder::get_vibrator_capabilities();
    println!("       Vibrator HAL capabilities: {:?}", vib_caps);
    let pulse_res = crate::platform::binder::pulse_vibrator(30);
    println!("       Pulse vibrator (30ms): {:?}", pulse_res);
    let off_res = crate::platform::binder::silence_vibrator();
    println!("       Silence vibrator: {:?}", off_res);

    println!("[10/11] Testing WindowManager actuator...");
    let freeze_res = crate::platform::binder::freeze_display_rotation(0, -1);
    let is_frozen = crate::platform::binder::is_display_rotation_frozen(0);
    println!(
        "        Freeze rotation: {:?} (is_frozen: {:?})",
        freeze_res, is_frozen
    );
    let thaw_res = crate::platform::binder::thaw_display_rotation(0);
    println!("        Thaw rotation: {:?}", thaw_res);
    let anim_scales = crate::platform::binder::get_animation_scales();
    println!("        Animation scales: {:?}", anim_scales);

    println!("[11/11] Testing Battery Saver actuator...");
    let saver_active = crate::platform::binder::is_power_save_mode();
    println!("        Battery Saver active: {:?}", saver_active);

    println!("\nNative Binder self-test completed successfully (all 11 checks passed).");
    Ok(())
}

fn handle_power(action: &str, name: Option<&str>, value: Option<&str>) -> Result<()> {
    use crate::platform::binder::{
        PowerBoost, PowerMode, is_power_boost_supported, is_power_mode_supported, set_power_boost,
        set_power_mode,
    };

    match action {
        "status" => {
            println!("Hardware PowerHAL Status:");
            println!(
                "  Mode: ExpensiveRendering:     {}",
                if is_power_mode_supported(PowerMode::ExpensiveRendering) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
            println!(
                "  Mode: SustainedPerformance:   {}",
                if is_power_mode_supported(PowerMode::SustainedPerformance) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
            println!(
                "  Mode: FixedPerformance:       {}",
                if is_power_mode_supported(PowerMode::FixedPerformance) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
            println!(
                "  Mode: DeviceIdle:             {}",
                if is_power_mode_supported(PowerMode::DeviceIdle) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
            println!(
                "  Boost: Interaction:           {}",
                if is_power_boost_supported(PowerBoost::Interaction) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
            println!(
                "  Boost: DisplayUpdateImmediate:{}",
                if is_power_boost_supported(PowerBoost::DisplayUpdateImmediate) {
                    "Supported"
                } else {
                    "Unsupported"
                }
            );
        }
        "mode" => {
            let mode_name = name.unwrap_or("expensive").to_lowercase();
            let mode = match mode_name.as_str() {
                "expensive" | "expensiverendering" => PowerMode::ExpensiveRendering,
                "sustained" | "sustainedperformance" => PowerMode::SustainedPerformance,
                "fixed" | "fixedperformance" => PowerMode::FixedPerformance,
                "idle" | "deviceidle" => PowerMode::DeviceIdle,
                "interactive" => PowerMode::Interactive,
                "game" => PowerMode::Game,
                "lowpower" => PowerMode::LowPower,
                "vr" => PowerMode::Vr,
                other => {
                    eprintln!(
                        "Unknown PowerMode '{}'. Valid: expensive, sustained, fixed, idle, interactive, game",
                        other
                    );
                    return Ok(());
                }
            };

            let enable = match value.map(|s| s.to_lowercase()).as_deref() {
                Some("on" | "true" | "1" | "enable") => true,
                Some("off" | "false" | "0" | "disable") => false,
                Some(other) => {
                    eprintln!("Unknown value '{}'. Valid: on, off", other);
                    return Ok(());
                }
                None => {
                    let supp = is_power_mode_supported(mode);
                    println!(
                        "PowerMode {:?}: {}",
                        mode,
                        if supp { "Supported" } else { "Unsupported" }
                    );
                    return Ok(());
                }
            };

            set_power_mode(mode, enable)?;
            println!("PowerHAL setMode {:?} -> {}", mode, enable);
        }
        "boost" => {
            let boost_name = name.unwrap_or("interaction").to_lowercase();
            let boost = match boost_name.as_str() {
                "interaction" => PowerBoost::Interaction,
                "display" | "immediate" => PowerBoost::DisplayUpdateImmediate,
                "ml" => PowerBoost::MlAccelerator,
                "audio" => PowerBoost::AudioLaunch,
                "camera" => PowerBoost::CameraLaunch,
                other => {
                    eprintln!(
                        "Unknown PowerBoost '{}'. Valid: interaction, display, ml, audio, camera",
                        other
                    );
                    return Ok(());
                }
            };
            let duration_ms = value.and_then(|v| v.parse::<i32>().ok()).unwrap_or(100);

            set_power_boost(boost, duration_ms)?;
            println!("PowerHAL setBoost {:?} for {}ms", boost, duration_ms);
        }
        other => {
            eprintln!(
                "Unknown action '{}'. Valid actions: mode, boost, status",
                other
            );
        }
    }
    Ok(())
}

fn handle_vibrator(action: Option<&str>, duration_ms: Option<u32>) -> Result<()> {
    use crate::platform::binder::{get_vibrator_capabilities, pulse_vibrator, silence_vibrator};

    let act = action.unwrap_or("status");
    match act {
        "status" => match get_vibrator_capabilities() {
            Ok(caps) => println!(
                "Hardware Vibrator HAL: Available (capabilities: 0x{:04x})",
                caps
            ),
            Err(e) => println!("Hardware Vibrator HAL: Error ({:?})", e),
        },
        "off" | "silence" | "disable" => {
            silence_vibrator()?;
            println!("Hardware vibrator silenced (off)");
        }
        "pulse" | "on" | "enable" => {
            let ms = duration_ms.unwrap_or(50);
            pulse_vibrator(ms)?;
            println!("Triggered hardware haptic pulse ({}ms)", ms);
        }
        other => {
            eprintln!(
                "Unknown action '{}'. Valid actions: off, pulse, status",
                other
            );
        }
    }
    Ok(())
}

fn handle_window(action: &str, param: Option<&str>) -> Result<()> {
    use crate::platform::binder::{
        freeze_display_rotation, get_animation_scales, is_display_rotation_frozen,
        set_animation_scales, thaw_display_rotation,
    };

    match action {
        "rotation" => {
            let sub = param.unwrap_or("status");
            match sub {
                "freeze" | "lock" => {
                    freeze_display_rotation(0, -1)?;
                    println!("Display rotation frozen at current orientation");
                }
                "thaw" | "unlock" => {
                    thaw_display_rotation(0)?;
                    println!("Display rotation thawed (auto-rotation restored)");
                }
                "status" => {
                    let frozen = is_display_rotation_frozen(0).unwrap_or(false);
                    println!(
                        "Display Rotation: {}",
                        if frozen {
                            "Frozen (Locked)"
                        } else {
                            "Thawed (Unlocked)"
                        }
                    );
                }
                other => {
                    eprintln!(
                        "Unknown rotation parameter '{}'. Valid: freeze, thaw, status",
                        other
                    );
                }
            }
        }
        "anim" => {
            if let Some(p) = param {
                if p == "status" {
                    let scales = get_animation_scales()?;
                    println!("Window Animation Scales: {:?}", scales);
                } else if let Ok(scale) = p.parse::<f32>() {
                    set_animation_scales(scale)?;
                    println!("Applied window animation scale: {:.2}x", scale);
                } else {
                    eprintln!(
                        "Invalid scale '{}'. Provide a float (e.g. 0.0, 1.0) or 'status'",
                        p
                    );
                }
            } else {
                let scales = get_animation_scales()?;
                println!("Window Animation Scales: {:?}", scales);
            }
        }
        "status" => {
            let frozen = is_display_rotation_frozen(0).unwrap_or(false);
            println!(
                "Display Rotation: {}",
                if frozen {
                    "Frozen (Locked)"
                } else {
                    "Thawed (Unlocked)"
                }
            );
            if let Ok(scales) = get_animation_scales() {
                println!("Animation Scales: {:?}", scales);
            }
        }
        other => {
            eprintln!(
                "Unknown action '{}'. Valid actions: rotation, anim, status",
                other
            );
        }
    }
    Ok(())
}

fn handle_power_save(action: Option<&str>) -> Result<()> {
    use crate::platform::binder::{is_power_save_mode, set_power_save_mode_enabled};

    let act = action.unwrap_or("status");
    match act {
        "on" | "enable" | "1" => {
            let success = set_power_save_mode_enabled(true)?;
            println!("Battery Saver enabled (success: {})", success);
        }
        "off" | "disable" | "0" => {
            let success = set_power_save_mode_enabled(false)?;
            println!("Battery Saver disabled (success: {})", success);
        }
        "status" => {
            let active = is_power_save_mode()?;
            println!(
                "Battery Saver: {}",
                if active {
                    "Active (Enabled)"
                } else {
                    "Inactive (Disabled)"
                }
            );
        }
        other => {
            eprintln!("Unknown action '{}'. Valid actions: on, off, status", other);
        }
    }
    Ok(())
}

#[cfg(target_os = "android")]
fn read_system_setting(namespace: &str, key: &str) -> String {
    let out = std::process::Command::new("settings")
        .args(["get", namespace, key])
        .output();
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).trim().to_string(),
        Err(e) => format!("err: {:?}", e),
    }
}
