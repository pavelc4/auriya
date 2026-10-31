//! Shared helper utilities for Binder CLI commands.

/// Read an Android system setting value using `settings get`.
#[cfg(target_os = "android")]
pub fn read_system_setting(namespace: &str, key: &str) -> String {
    let out = std::process::Command::new("settings")
        .args(["get", namespace, key])
        .output();
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).trim().to_string(),
        Err(e) => format!("err: {:?}", e),
    }
}

/// Fallback for host builds where `settings` binary does not exist.
#[cfg(not(target_os = "android"))]
pub fn read_system_setting(_namespace: &str, _key: &str) -> String {
    "0".to_string()
}
