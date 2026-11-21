use std::collections::HashMap;
use std::fs;
use std::path::Path;

use tracing::{debug, warn};

const VENDOR_PATHS: &[(&str, &str)] = &[
    ("/sys/module/mtk_fpsgo/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr_policy/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr_mtk/parameters/perfmgr_enable", "0"),
    ("/sys/module/migt/parameters/glk_fbreak_enable", "0"),
    ("/sys/module/migt/parameters/glk_disable", "1"),
    ("/proc/game_opt/disable_cpufreq_limit", "1"),
];

#[derive(Default)]
pub struct VendorLock {
    saved: HashMap<String, String>,
    locked: bool,
}

impl VendorLock {
    pub fn new() -> Self {
        Self {
            saved: HashMap::new(),
            locked: false,
        }
    }

    pub fn lock_all(&mut self) {
        if self.locked {
            return;
        }

        for (path, value) in VENDOR_PATHS {
            if !Path::new(path).exists() {
                continue;
            }

            if let Ok(prev) = fs::read_to_string(path) {
                let trimmed = prev.trim().to_string();
                if fs::write(path, value).is_ok() {
                    self.saved.insert(path.to_string(), trimmed);
                    debug!(target: "auriya::vendor_lock", "Locked {} -> {}", path, value);
                } else {
                    warn!(target: "auriya::vendor_lock", "Failed to write {} to {}", value, path);
                }
            }
        }

        self.locked = true;
    }

    pub fn unlock_all(&mut self) {
        if !self.locked {
            return;
        }

        for (path, _) in VENDOR_PATHS {
            if !Path::new(path).exists() {
                continue;
            }

            if let Some(saved_val) = self.saved.remove(*path) {
                if fs::write(path, &saved_val).is_ok() {
                    debug!(target: "auriya::vendor_lock", "Unlocked {}, restored {}", path, saved_val);
                } else {
                    warn!(target: "auriya::vendor_lock", "Failed to restore {} to {}", saved_val, path);
                }
            }
        }

        self.locked = false;
    }
}
