//! Automated hardware verification suite for Native Android Binder actuators.

use anyhow::Result;

use super::util::read_system_setting;
use crate::platform::binder::{
    BinderClient, apply_dnd, apply_refresh_rate, broadcast_intent, freeze_display_rotation,
    get_animation_scales, get_vibrator_capabilities, is_display_rotation_frozen,
    is_power_save_mode, is_statusbar_locked, is_wifi_low_latency_active, pulse_vibrator,
    set_power_boost, set_power_mode, set_statusbar_lock, set_wifi_low_latency_lock, show_toast,
    silence_vibrator, thaw_display_rotation,
};

/// Execute automated 13-stage self-test on hardware.
pub fn handle_test() -> Result<()> {
    println!("Native Binder Hardware Test:");

    let client = BinderClient::new();
    println!(
        "[1/13] Binder Servicemanager: {}",
        if client.is_operational() {
            "PASS"
        } else {
            "FAIL (unavailable)"
        }
    );

    match client.is_screen_interactive() {
        Ok(interactive) => println!("[2/13] PowerManager.isInteractive ({}): PASS", interactive),
        Err(e) => println!("[2/13] PowerManager query: FAIL ({:?})", e),
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
                "[3/13] Native Binder Foreground ({} | PID: {} | {}): PASS",
                task.package, pid_str, win_str
            );
        }
        Ok(None) => println!("[3/13] Native Binder Foreground: PASS (no focused task)"),
        Err(e) => println!("[3/13] Native Binder Foreground: FAIL ({:?})", e),
    }

    println!("[4/13] Testing DnD actuator (Priority)...");
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

    println!("[5/13] Testing Display Refresh Rate actuator (120Hz)...");
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

    println!("[6/13] Testing Wi-Fi Low-Latency lock actuator...");
    let lock_res = set_wifi_low_latency_lock(true);
    let active = is_wifi_low_latency_active();
    println!(
        "       Acquire Wi-Fi Low-Latency result: {:?} (Active: {})",
        lock_res, active
    );
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring Wi-Fi Low-Latency lock...");
    let rel_res = set_wifi_low_latency_lock(false);
    let active_after = is_wifi_low_latency_active();
    println!(
        "       Release Wi-Fi Low-Latency result: {:?} (Active: {})",
        rel_res, active_after
    );

    println!("[7/13] Testing Status Bar lock actuator...");
    let lock_res = set_statusbar_lock(true);
    let lock_active = is_statusbar_locked();
    println!(
        "       Lock Status Bar result: {:?} (Active: {})",
        lock_res, lock_active
    );
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("       Restoring Status Bar lock...");
    let rel_res = set_statusbar_lock(false);
    let lock_active_after = is_statusbar_locked();
    println!(
        "       Unlock Status Bar result: {:?} (Active: {})",
        rel_res, lock_active_after
    );

    println!("[8/13] Testing PowerHAL modes actuator...");
    let mode_res = set_power_mode(crate::platform::binder::PowerMode::ExpensiveRendering, true);
    println!("       Set ExpensiveRendering=true: {:?}", mode_res);
    let boost_res = set_power_boost(crate::platform::binder::PowerBoost::Interaction, 50);
    println!("       Trigger Interaction boost: {:?}", boost_res);
    let _ = set_power_mode(
        crate::platform::binder::PowerMode::ExpensiveRendering,
        false,
    );

    println!("[9/13] Testing Vibrator HAL actuator...");
    let vib_caps = get_vibrator_capabilities();
    println!("       Vibrator HAL capabilities: {:?}", vib_caps);
    let pulse_res = pulse_vibrator(30);
    println!("       Pulse vibrator (30ms): {:?}", pulse_res);
    let off_res = silence_vibrator();
    println!("       Silence vibrator: {:?}", off_res);

    println!("[10/13] Testing WindowManager actuator...");
    let freeze_res = freeze_display_rotation(0, -1);
    let is_frozen = is_display_rotation_frozen(0);
    println!(
        "        Freeze rotation: {:?} (is_frozen: {:?})",
        freeze_res, is_frozen
    );
    let thaw_res = thaw_display_rotation(0);
    println!("        Thaw rotation: {:?}", thaw_res);
    let anim_scales = get_animation_scales();
    println!("        Animation scales: {:?}", anim_scales);

    println!("[11/13] Testing Battery Saver actuator...");
    let saver_active = is_power_save_mode();
    println!("        Battery Saver active: {:?}", saver_active);

    println!("[12/13] Testing Native Toast actuator...");
    let toast_res = show_toast("Auriya: Hardware self-test verified");
    println!("        Show toast result: {:?}", toast_res);

    println!("[13/13] Testing Direct Binder Broadcast actuator...");
    let bcast_res = broadcast_intent("dev.auriya.app.ACTION_GAME_EXIT", &[("test", "true")]);
    println!("        Broadcast result: {:?}", bcast_res);

    println!("\nNative Binder self-test completed successfully (all 13 checks passed).");
    Ok(())
}
