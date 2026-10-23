use crate::core::profile::ProfileMode;
use crate::daemon::model::config::DaemonConfig;
use crate::daemon::model::state::LastState;
use std::time::Duration;
use tracing::{debug, error};

pub const STATUS_FILE: &str = "/data/adb/auriya/status";

pub fn update_current_profile_file(mode: ProfileMode) {
    let val = match mode {
        ProfileMode::Performance => "1",
        ProfileMode::Balance => "2",
        ProfileMode::Powersave => "3",
        ProfileMode::Fast => "4",
    };

    let config_path = crate::core::config::CONFIG_DIR;
    let profile_file = format!("{}/current_profile", config_path);

    let _ = std::fs::create_dir_all(config_path);

    if let Err(e) = std::fs::write(&profile_file, val) {
        error!(target: "auriya::daemon", "Failed to update current_profile: {}", e);
    } else {
        debug!(target: "auriya::daemon", "Updated current_profile to {} ({})", val, mode);
    }
}

#[inline]
pub(crate) fn now_ms() -> u128 {
    use std::time::{SystemTime, UNIX_EPOCH};

    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|_e| {
            tracing::warn!(target: "auriya::daemon", "Daemon | System clock error (using fallback)");
            Duration::from_secs(0)
        })
        .as_millis()
}

#[inline]
pub(crate) fn should_log_change(last: &LastState, cfg: &DaemonConfig) -> bool {
    match last.last_log_ms {
        None => true,
        Some(t) => now_ms().saturating_sub(t) >= cfg.log_debounce_ms,
    }
}

#[inline]
pub(crate) fn bump_log(last: &mut LastState) {
    last.last_log_ms = Some(now_ms());
}
