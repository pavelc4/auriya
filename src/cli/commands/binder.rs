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
    }
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

    match client.get_foreground_app() {
        Ok(Some((pkg, pid))) => {
            let pid_str = pid
                .map(|p| p.to_string())
                .unwrap_or_else(|| "None".to_string());
            println!("  Foreground App:             {} (PID: {})", pkg, pid_str);
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

    #[cfg(target_os = "android")]
    {
        if rsbinder::ProcessState::is_initialized() {
            for service in [
                "power",
                "notification",
                "activity",
                "activity_task",
                "display",
            ] {
                let avail = rsbinder::hub::check_service(service).is_some();
                println!(
                    "  Service '{:<14}':   {}",
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
        "[1/6] Binder Servicemanager: {}",
        if client.is_operational() {
            "PASS"
        } else {
            "FAIL (unavailable)"
        }
    );

    match client.is_screen_interactive() {
        Ok(interactive) => println!("[2/6] PowerManager.isInteractive ({}): PASS", interactive),
        Err(e) => println!("[2/6] PowerManager query: FAIL ({:?})", e),
    }

    match client.get_foreground_app() {
        Ok(Some((pkg, pid))) => {
            let pid_str = pid
                .map(|p| p.to_string())
                .unwrap_or_else(|| "None".to_string());
            println!(
                "[3/6] Native Binder Foreground ({} | PID: {}): PASS",
                pkg, pid_str
            );
        }
        Ok(None) => println!("[3/6] Native Binder Foreground: PASS (no focused task)"),
        Err(e) => println!("[3/6] Native Binder Foreground: FAIL ({:?})", e),
    }

    println!("[4/6] Testing DnD actuator (Priority)...");
    apply_dnd(crate::platform::binder::DndMode::Priority)?;
    #[cfg(target_os = "android")]
    {
        let zen = read_system_setting("global", "zen_mode");
        println!("      zen_mode is {} (expected: 1)", zen);
    }
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("      Restoring DnD actuator (All)...");
    apply_dnd(crate::platform::binder::DndMode::All)?;
    #[cfg(target_os = "android")]
    {
        let zen = read_system_setting("global", "zen_mode");
        println!("      zen_mode restored to {} (expected: 0)", zen);
    }

    println!("[5/6] Testing Display Refresh Rate actuator (120Hz)...");
    apply_refresh_rate(120)?;
    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        println!("      Native Binder mode: {:?}", binder_mode);
        let peak = read_system_setting("system", "peak_refresh_rate");
        println!("      peak_refresh_rate is {} (expected: 120.0)", peak);
    }
    std::thread::sleep(std::time::Duration::from_millis(250));
    println!("      Restoring Display Refresh Rate (0Hz / dynamic)...");
    apply_refresh_rate(0)?;
    #[cfg(target_os = "android")]
    {
        let binder_mode = crate::platform::binder::get_user_preferred_display_mode()
            .ok()
            .flatten();
        println!("      Native Binder mode restored to {:?}", binder_mode);
        let peak = read_system_setting("system", "peak_refresh_rate");
        println!("      peak_refresh_rate restored to {}", peak);
    }

    println!("\n[6/6] Binder self-test completed successfully.");
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
