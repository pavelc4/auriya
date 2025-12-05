use anyhow::Result;
use std::process::Command;

pub struct SurfaceFlinger;

impl SurfaceFlinger {
    pub fn find_layer(package: &str) -> Result<Option<String>> {
        let output = Command::new("dumpsys")
            .arg("SurfaceFlinger")
            .arg("--list")
            .output()?;

        let stdout = String::from_utf8_lossy(&output.stdout);

        let mut best_match = None;

        for line in stdout.lines() {
            let line = line.trim();
            if line.contains(package)
                && line.contains("SurfaceView")
                && !line.contains("Background for")
            {
                // Extract name from RequestedLayerState{NAME ...} if present
                let name = if let Some(start) = line.find("RequestedLayerState{") {
                    let rest = &line[start + 20..]; // len("RequestedLayerState{")
                    // Find end of name: usually before " parentId=" or " z=" or just "}"
                    let end = rest
                        .find(" parentId=")
                        .or_else(|| rest.find(" z="))
                        .or_else(|| rest.find("}"));

                    if let Some(end_idx) = end {
                        &rest[..end_idx]
                    } else {
                        rest
                    }
                } else {
                    line
                };

                return Ok(Some(name.trim().to_string()));
            }
        }

        Ok(best_match)
    }

    pub fn get_frame_time(layer: &str) -> Result<f32> {
        let output = Command::new("dumpsys")
            .arg("SurfaceFlinger")
            .arg("--latency")
            .arg(layer)
            .output()?;

        let stdout = String::from_utf8_lossy(&output.stdout);

        let mut lines: Vec<&str> = stdout.lines().collect();

        if lines.is_empty() {
            return Ok(0.0);
        }
        lines.remove(0);

        let valid_lines: Vec<&str> = lines
            .into_iter()
            .filter(|l| !l.trim().is_empty() && l.trim() != "0\t0\t0")
            .collect();

        if valid_lines.len() < 2 {
            return Ok(0.0);
        }

        // Get last two frames
        let last_frame = valid_lines[valid_lines.len() - 1];
        let prev_frame = valid_lines[valid_lines.len() - 2];

        let last_parts: Vec<&str> = last_frame.split_whitespace().collect();
        let prev_parts: Vec<&str> = prev_frame.split_whitespace().collect();

        if last_parts.len() < 2 || prev_parts.len() < 2 {
            return Ok(0.0);
        }

        let last_present: u64 = last_parts[1].parse().unwrap_or(0);
        let prev_present: u64 = prev_parts[1].parse().unwrap_or(0);

        if last_present > prev_present {
            let diff_ns = last_present - prev_present;
            let diff_ms = diff_ns as f32 / 1_000_000.0;

            if diff_ms > 200.0 {
                return Ok(0.0);
            }

            return Ok(diff_ms);
        }

        Ok(0.0)
    }
}
