use std::process::Command;

pub fn get_foreground_package() -> anyhow::Result<Option<String>> {
    let out = Command::new("/system/bin/dumpsys")
        .args(["activity", "activities"])
        .output()?;
    let s = String::from_utf8_lossy(&out.stdout);

    for line in s.lines() {
        if let Some(pos) = line.find("ResumedActivity:") {
            if let Some(pkg) = parse_pkg_from_activity_line(&line[pos..]) {
                return Ok(Some(pkg));
            }
        }
    }

    for line in s.lines() {
        if let Some(pos) = line.find("mCurrentFocus=") {
            if let Some(pkg) = parse_pkg_from_window_line(&line[pos..]) {
                return Ok(Some(pkg));
            }
        }
    }
    Ok(None)
}

fn parse_pkg_from_activity_line(s: &str) -> Option<String> {
    if let Some(u0) = s.find(" u0 ") {
        let token = s[(u0 + 4)..].split_whitespace().next()?;
        let pkg = token.split('/').next()?;
        if pkg.contains('.') {
            return Some(pkg.to_string());
        }
    }
    None
}

fn parse_pkg_from_window_line(s: &str) -> Option<String> {
    let head = s.split('}').next().unwrap_or(s);
    let token = head.split_whitespace().find(|t| t.contains('/'))?;
    let pkg = token.split('/').next()?;
    if pkg.contains('.') {
        return Some(pkg.to_string());
    }
    None
}
