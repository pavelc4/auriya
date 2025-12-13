use crate::core::cmd::run_cmd_timeout_async;

pub async fn get_foreground_package() -> anyhow::Result<Option<String>> {
    let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["activity", "activities"], 2000)
        .await
    {
        Ok(o) => o,
        Err(e) => {
            tracing::debug!(target: "auriya:dumpsys", "dumpsys activity timeout/failed: {:?}", e);
            return Ok(None);
        }
    };

    let s = String::from_utf8_lossy(&out.stdout);
    let mut fallback_pkg: Option<String> = None;

    for line in s.lines() {
        if line.contains("ResumedActivity") {
            if let Some(pkg) = parse_pkg_from_activity_line(line) {
                return Ok(Some(pkg));
            }
        }

        if fallback_pkg.is_none() && line.contains("mCurrentFocus") {
            fallback_pkg = parse_pkg_from_window_line(line);
        }
    }

    Ok(fallback_pkg)
}

fn parse_pkg_from_activity_line(s: &str) -> Option<String> {
    if let Some(u0) = s.find("u0 ") {
        let token = s[u0 + 3..].split_whitespace().next()?;
        let pkg = token.split('/').next()?;
        if pkg.contains('.') {
            return Some(pkg.to_string());
        }
    }
    None
}

fn parse_pkg_from_window_line(s: &str) -> Option<String> {
    let head = s.split('{').next().unwrap_or(s);
    let token = head.split_whitespace().find(|t| t.contains('/'))?;
    let pkg = token.split('/').next()?;
    if pkg.contains('.') {
        return Some(pkg.to_string());
    }
    None
}
