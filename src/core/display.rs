// The daemon used to drive `min_refresh_rate` / `peak_refresh_rate`
// directly via `settings put`, but those writes are now delegated to
// the Android companion through `auriya_cmd` (see `core::cmd_writer`).
// Only the dumpsys-based mode enumeration is left in this module — the
// daemon still needs it to validate user-requested rates against
// `appsSupportedModes` before forwarding them.
use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use tokio::process::Command;

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
