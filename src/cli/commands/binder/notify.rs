//! Notifications and broadcast intent CLI handlers.

use anyhow::Result;

/// Display a standard text toast notification directly on the screen.
pub fn handle_toast(message: &str, long: bool) -> Result<()> {
    let duration = if long { 1 } else { 0 };
    let success = crate::platform::binder::show_toast_with_duration(message, duration)?;
    println!("Native Toast: '{}' (success: {})", message, success);
    Ok(())
}

/// Send an explicit or broadcast Intent via Android ActivityManagerService.
pub fn handle_broadcast(action: &str, extra: &[String]) -> Result<()> {
    let mut parsed_extras = Vec::new();
    for item in extra {
        if let Some((k, v)) = item.split_once('=') {
            parsed_extras.push((k.trim(), v.trim()));
        } else {
            parsed_extras.push((item.as_str(), ""));
        }
    }
    crate::platform::binder::broadcast_intent(action, &parsed_extras)?;
    println!(
        "Dispatched Intent broadcast: '{}' ({} extras)",
        action,
        parsed_extras.len()
    );
    Ok(())
}
