use anyhow::{Context, Result};

use serde::{Deserialize, Serialize};

use tokio::process::Command;
use tracing::debug;

pub async fn set_refresh_rate(hz: u32) -> Result<()> {
    debug!(target: "auriya::display", "Setting refresh rate to {}Hz", hz);

    let _ = Command::new("settings")
        .args(["put", "system", "min_refresh_rate", &hz.to_string()])
        .status()
        .await?;

    let _ = Command::new("settings")
        .args(["put", "system", "peak_refresh_rate", &hz.to_string()])
        .status()
        .await?;
    Ok(())
}

async fn read_rate_setting(name: &str) -> Result<u32> {
    let output = Command::new("settings")
        .args(["get", "system", name])
        .output()
        .await?;

    // Android stores these as floats (e.g. "60.0"), so parse via f64 then round.
    Ok(String::from_utf8_lossy(&output.stdout)
        .trim()
        .parse::<f64>()
        .map(|f| f.round() as u32)
        .unwrap_or(0))
}

pub async fn get_display_rates() -> Result<(u32, u32)> {
    let min = read_rate_setting("min_refresh_rate").await?;
    let peak = read_rate_setting("peak_refresh_rate").await?;
    Ok((min, peak))
}

pub async fn restore_display_rates(min: u32, peak: u32) -> Result<()> {
    debug!(target: "auriya::display", "Restoring refresh rates min={}Hz peak={}Hz", min, peak);

    let _ = Command::new("settings")
        .args(["put", "system", "min_refresh_rate", &min.to_string()])
        .status()
        .await?;

    let _ = Command::new("settings")
        .args(["put", "system", "peak_refresh_rate", &peak.to_string()])
        .status()
        .await?;

    Ok(())
}

pub async fn reset_refresh_rate() -> Result<()> {
    debug!(target: "auriya::display", "Resetting refresh rate to auto");

    let _ = Command::new("settings")
        .args(["put", "system", "min_refresh_rate", "0"])
        .status()
        .await?;

    let _ = Command::new("settings")
        .args(["put", "system", "peak_refresh_rate", "0"])
        .status()
        .await?;

    Ok(())
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DisplayMode {
    pub id: u32,
    pub width: u32,
    pub height: u32,
    pub fps: f32,
}

pub async fn get_app_supported_modes() -> Result<Vec<DisplayMode>> {
    let output = Command::new("dumpsys").arg("display").output().await?;
    let stdout = String::from_utf8_lossy(&output.stdout);

    parse_app_supported_modes(&stdout)
}

fn parse_app_supported_modes(input: &str) -> Result<Vec<DisplayMode>> {
    // Look for appsSupportedModes first
    let start_marker = "appsSupportedModes [";
    let start_idx = input
        .find(start_marker)
        .context("Could not find appsSupportedModes in dumpsys output")?
        + start_marker.len();

    let rest = &input[start_idx..];

    // Find the matching closing bracket
    let mut depth = 1;
    let mut end_idx = 0;

    for (i, c) in rest.char_indices() {
        match c {
            '[' => depth += 1,
            ']' => depth -= 1,
            _ => {}
        }
        if depth == 0 {
            end_idx = i;
            break;
        }
    }

    if depth != 0 {
        anyhow::bail!("Could not find end of appsSupportedModes list");
    }

    let modes_str = &rest[..end_idx];
    let mut modes = Vec::new();

    let mut start = 0;
    let mut in_brace = false;
    for (i, c) in modes_str.char_indices() {
        if c == '{' && !in_brace {
            start = i + 1;
            in_brace = true;
        } else if c == '}' && in_brace {
            let chunk = &modes_str[start..i];
            in_brace = false;

            let mut id = 0;
            let mut width = 0;
            let mut height = 0;
            let mut fps = 0.0;

            for part in chunk.split(", ") {
                if let Some((key, val)) = part.split_once('=') {
                    match key {
                        "id" => id = val.parse().unwrap_or_default(),
                        "width" => width = val.parse().unwrap_or_default(),
                        "height" => height = val.parse().unwrap_or_default(),
                        "fps" => fps = val.parse().unwrap_or_default(),
                        _ => {}
                    }
                }
            }
            if id != 0 {
                modes.push(DisplayMode {
                    id,
                    width,
                    height,
                    fps,
                });
            }
        }
    }

    Ok(modes)
}
