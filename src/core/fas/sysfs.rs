use anyhow::{Context, Result};
use std::path::Path;

const FPS_SYSFS_PATHS: &[&str] = &[
    "/sys/class/drm/card0/sde-crtc-0/measured_fps", // Snapdragon (common)
    "/sys/class/drm/card0/sde_crtc_fps",            // Snapdragon (variant)
    "/sys/class/graphics/fb0/measured_fps",         // Some Exynos/MTK
    "/sys/class/graphics/fb0/fps",                  // Older devices
];

pub fn detect_fps_path() -> Option<String> {
    for path in FPS_SYSFS_PATHS {
        let path_obj = Path::new(path);

        if path_obj.exists() {
            match std::fs::read_to_string(path) {
                Ok(content) if !content.trim().is_empty() => {
                    tracing::info!(
                        target: "auriya::fas","Detected sysfs FPS path: {} (content: {})",
                        path,
                        content.trim().chars().take(50).collect::<String>()
                    );
                    return Some(path.to_string());
                }
                Ok(_) => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "Path exists but empty: {}",
                        path
                    );
                }
                Err(e) => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "Path exists but not readable: {} ({})",
                        path,
                        e
                    );
                }
            }
        }
    }

    tracing::warn!(
        target: "auriya::fas",
        "No sysfs FPS path detected, will use dumpsys fallback"
    );
    None
}

pub fn read_sysfs_fps(path: &str) -> Result<f32> {
    let content = std::fs::read_to_string(path)
        .with_context(|| format!("Failed to read sysfs FPS from {}", path))?;

    parse_sysfs_fps(&content)
        .with_context(|| format!("Failed to parse sysfs FPS: {}", content.trim()))
}

fn parse_sysfs_fps(content: &str) -> Result<f32> {
    let trimmed = content.trim();
    if trimmed.starts_with("fps:")
        && let Some(fps_str) = trimmed.split_whitespace().nth(1) {
            return fps_str.parse::<f32>().with_context(|| format!("Invalid FPS value: {}", fps_str));
    }
    trimmed.parse::<f32>().with_context(|| format!("Invalid FPS format: {}", trimmed))
}
