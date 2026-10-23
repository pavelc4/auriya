use nix::unistd::getuid;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

/// RAII guard to ensure CPU scaling governors are restored to original state upon test completion.
pub struct GovernorRestorer {
    original_governors: Vec<(PathBuf, String)>,
}

impl Drop for GovernorRestorer {
    fn drop(&mut self) {
        for (path, gov) in &self.original_governors {
            let _ = fs::write(path, gov);
        }
    }
}

/// Verify process is running with root privileges (UID == 0).
pub fn verify_root_privileges() -> Result<(), String> {
    let uid = getuid();
    if !uid.is_root() {
        return Err(format!(
            "Test execution rejected: process UID is {}, but UID == 0 (root) is strictly required",
            uid.as_raw()
        ));
    }
    Ok(())
}

/// Discover all available CPU frequency scaling governor sysfs nodes on the device.
pub fn find_cpu_governor_paths() -> Result<Vec<PathBuf>, String> {
    let cpu_root = Path::new("/sys/devices/system/cpu");
    if !cpu_root.exists() {
        return Err(format!(
            "Kernel CPU subsystem path not found: {}",
            cpu_root.display()
        ));
    }

    let mut paths = Vec::new();
    let entries = fs::read_dir(cpu_root)
        .map_err(|e| format!("Failed to read {}: {}", cpu_root.display(), e))?;

    for entry in entries {
        let entry = entry.map_err(|e| format!("Failed to access directory entry: {}", e))?;
        let path = entry.path();
        if path.is_dir()
            && path
                .file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|name| {
                    name.starts_with("cpu") && name[3..].chars().all(|c| c.is_ascii_digit())
                })
        {
            let gov_file = path.join("cpufreq/scaling_governor");
            if gov_file.exists() {
                paths.push(gov_file);
            }
        }
    }

    if paths.is_empty() {
        return Err(
            "No active CPU governor nodes found under /sys/devices/system/cpu/cpu*/cpufreq/"
                .to_string(),
        );
    }

    paths.sort();
    Ok(paths)
}

/// Confirm write capability to kernel CPU governor sysfs nodes and capture original values for restoration.
pub fn verify_kernel_subsystem_write_access() -> Result<GovernorRestorer, String> {
    let paths = find_cpu_governor_paths()?;
    let mut original_governors = Vec::with_capacity(paths.len());

    for path in &paths {
        let current_gov = fs::read_to_string(path)
            .map_err(|e| format!("Failed reading governor from {}: {}", path.display(), e))?;
        let trimmed_gov = current_gov.trim().to_string();

        let mut file = OpenOptions::new()
            .write(true)
            .open(path)
            .map_err(|e| format!("Write permission denied for {}: {}", path.display(), e))?;

        file.write_all(trimmed_gov.as_bytes())
            .map_err(|e| format!("Failed writing to {}: {}", path.display(), e))?;

        original_governors.push((path.clone(), trimmed_gov));
    }

    Ok(GovernorRestorer { original_governors })
}

/// Primary entry point for tests requiring validated root and hardware control.
pub fn setup_verified_root_environment() -> Result<GovernorRestorer, String> {
    verify_root_privileges()?;
    verify_kernel_subsystem_write_access()
}
