use anyhow::{Context, Result};
use regex::Regex;
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

pub async fn get_refresh_rate() -> Result<u32> {
    let output = Command::new("settings")
        .args(["get", "system", "min_refresh_rate"])
        .output()
        .await?;

    let out_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if let Ok(val) = out_str.parse::<u32>() {
        Ok(val)
    } else {
        Ok(0)
    }
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

    let re = Regex::new(r"id=(\d+), width=(\d+), height=(\d+), fps=([\d\.]+)")?;
    let mut modes = Vec::new();

    for caps in re.captures_iter(modes_str) {
        let id = caps[1].parse()?;
        let width = caps[2].parse()?;
        let height = caps[3].parse()?;
        let fps = caps[4].parse()?;

        modes.push(DisplayMode {
            id,
            width,
            height,
            fps,
        });
    }

    Ok(modes)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_app_supported_modes() {
        let input = r#"
    mBaseDisplayInfo=DisplayInfo{"Layar Built-In", displayId 0, ... appsSupportedModes [{id=1, width=1080, height=2400, fps=60.000004, vsync=60.000004, synthetic=false, alternativeRefreshRates=[90.0, 120.00001], supportedHdrTypes=[2, 3, 4]}, {id=2, width=1080, height=2400, fps=120.00001, vsync=120.00001, synthetic=false, alternativeRefreshRates=[60.000004, 90.0], supportedHdrTypes=[2, 3, 4]}, {id=3, width=1080, height=2400, fps=90.0, vsync=90.0, synthetic=false, alternativeRefreshRates=[60.000004, 120.00001], supportedHdrTypes=[2, 3, 4]}], hdrCapabilities ...}
        "#;

        let modes = parse_app_supported_modes(input).unwrap();
        assert_eq!(modes.len(), 3);

        assert_eq!(modes[0].id, 1);
        assert_eq!(modes[0].fps, 60.000004);

        assert_eq!(modes[1].id, 2);
        assert_eq!(modes[1].fps, 120.00001);

        assert_eq!(modes[2].id, 3);
        assert_eq!(modes[2].fps, 90.0);
    }
}
