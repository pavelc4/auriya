use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::debug;

// (name, performance_value, balance_value)
const PATHS: &[(&str, &str, &str)] = &[
    ("game_switch_enable", "1", "0"),
    ("oplus_tp_limit_enable", "0", "1"),
    ("oppo_tp_limit_enable", "0", "1"),
    ("oplus_tp_direction", "1", "0"),
    ("oppo_tp_direction", "1", "0"),
];

fn write_mode(wanted: &str) -> u32 {
    let mut written = 0;
    for (name, perf_val, bal_val) in PATHS {
        let path = format!("/proc/touchpanel/{}", name);
        if Path::new(&path).exists() {
            let v = match wanted {
                "performance" => perf_val,
                _ => bal_val,
            };
            let _ = fs::write(&path, v);
            written += 1;
        }
    }
    written
}

pub fn enable_game_mode() -> Result<()> {
    let n = write_mode("performance");
    if n > 0 {
        debug!("Touchpanel game mode enabled ({} paths)", n);
    }
    Ok(())
}

pub fn disable_game_mode() -> Result<()> {
    let n = write_mode("balance");
    if n > 0 {
        debug!("Touchpanel game mode disabled ({} paths)", n);
    }
    Ok(())
}
