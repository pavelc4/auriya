//! Sensitive application blacklist.
//!
//! Protects applications configured in `blacklist.toml` from having eBPF
//! uprobes attached, preventing code tampering crashes on RASP-enabled apps.

use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::RwLock;

static BLACKLIST: RwLock<Vec<String>> = RwLock::new(Vec::new());

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct BlacklistConfig {
    #[serde(default, alias = "package")]
    pub packages: Vec<String>,
    #[serde(default)]
    pub blacklist: Option<BlacklistSection>,
}

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct BlacklistSection {
    #[serde(default, alias = "package")]
    pub packages: Vec<String>,
}

/// Load user-defined blacklist from `/data/adb/.config/auriya/blacklist.toml`.
pub fn load_custom_blacklist<P: AsRef<Path>>(path: P) {
    let path = path.as_ref();
    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return,
    };

    match toml::from_str::<BlacklistConfig>(&content) {
        Ok(cfg) => {
            let mut list = cfg.packages;
            if let Some(inner) = cfg.blacklist {
                list.extend(inner.packages);
            }
            let list: Vec<String> = list
                .into_iter()
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect();

            match BLACKLIST.write() {
                Ok(mut lock) => {
                    tracing::info!(
                        target: "auriya::daemon",
                        "Loaded {} blacklisted packages from {}",
                        list.len(),
                        path.display()
                    );
                    *lock = list;
                }
                Err(e) => {
                    tracing::error!(
                        target: "auriya::daemon",
                        "Failed to acquire blacklist lock: {}",
                        e
                    );
                }
            }
        }
        Err(e) => {
            tracing::warn!(
                target: "auriya::daemon",
                "Failed to parse blacklist {}: {}",
                path.display(),
                e
            );
        }
    }
}

/// Checks if a package is in the blacklist.
#[inline]
pub fn is_tamper_sensitive(pkg: &str) -> bool {
    let trimmed = pkg.trim();
    match trimmed.is_empty() {
        true => false,
        false => match BLACKLIST.read() {
            Ok(guard) => guard.iter().any(|p| trimmed.starts_with(p)),
            Err(_) => false,
        },
    }
}
