use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::debug;

fn get_devfreq_max_freq(path: &Path) -> Option<String> {
    let avail_path = path.join("available_frequencies");
    if let Ok(content) = fs::read_to_string(&avail_path) {
        content
            .split_whitespace()
            .filter_map(|s| s.parse::<u64>().ok().map(|v| (v, s.to_string())))
            .max_by_key(|(v, _)| *v)
            .map(|(_, s)| s)
    } else {
        None
    }
}

fn get_devfreq_min_freq(path: &Path) -> Option<String> {
    let avail_path = path.join("available_frequencies");
    if let Ok(content) = fs::read_to_string(&avail_path) {
        content
            .split_whitespace()
            .filter_map(|s| s.parse::<u64>().ok().map(|v| (v, s.to_string())))
            .min_by_key(|(v, _)| *v)
            .map(|(_, s)| s)
    } else {
        None
    }
}

pub fn lock_storage_freq() -> Result<()> {
    let mut locked = 0;

    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            let path = entry.path();

            if (name.contains("ufshc") || name.starts_with("mmc"))
                && let Some(max) = get_devfreq_max_freq(&path)
            {
                let _ = fs::write(path.join("min_freq"), &max);
                let _ = fs::write(path.join("max_freq"), &max);
                debug!("Storage {} locked to {} Hz", name, max);
                locked += 1;
            }
        }
    }

    if locked > 0 {
        debug!("Storage frequency locked ({} devices)", locked);
    }
    Ok(())
}

pub fn unlock_storage_freq() -> Result<()> {
    let mut unlocked = 0;

    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            let path = entry.path();

            if (name.contains("ufshc") || name.starts_with("mmc"))
                && let (Some(min), Some(max)) =
                    (get_devfreq_min_freq(&path), get_devfreq_max_freq(&path))
            {
                let _ = fs::write(path.join("min_freq"), &min);
                let _ = fs::write(path.join("max_freq"), &max);
                debug!("Storage {} unlocked (min={}, max={})", name, min, max);

                unlocked += 1;
            }
        }
    }

    if unlocked > 0 {
        debug!("Storage frequency unlocked ({} devices)", unlocked);
    }
    Ok(())
}
