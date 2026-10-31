//! Hardware HAL CLI handlers (PowerHAL, Battery Saver, Vibrator, WindowManager).

use anyhow::Result;

/// Manage Android PowerHAL modes and performance boosts.
pub fn handle_power(action: &str, name: Option<&str>, value: Option<&str>) -> Result<()> {
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

/// Manage Hardware Vibrator HAL triggers.
pub fn handle_vibrator(action: Option<&str>, duration_ms: Option<u32>) -> Result<()> {
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

/// Manage WindowManager orientation freezing and animation scale factors.
pub fn handle_window(action: &str, param: Option<&str>) -> Result<()> {
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

/// Toggle or query Android Battery Saver state via PowerManagerService.
pub fn handle_power_save(action: Option<&str>) -> Result<()> {
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
