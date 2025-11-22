use std::process::Command;

pub fn get_app_pid(package: &str) -> anyhow::Result<Option<i32>> {
    if let Some(pid) = try_visible_activity_process()? {
        return Ok(Some(pid));
    }
    if let Some(pid) = try_activity_processes(package)? {
        return Ok(Some(pid));
    }
    if let Some(pid) = try_activity_top(package)? {
        return Ok(Some(pid));
    }
    ps_fallback(package)
}

fn try_visible_activity_process() -> anyhow::Result<Option<i32>> {
    let out = Command::new("/system/bin/dumpsys")
        .args(["activity", "activities"])
        .output()?;
    let s = String::from_utf8_lossy(&out.stdout);
    if let Some(line) = s.lines().find(|l| l.contains("VisibleActivityProcess:["))
        && let Some(open) = line.find('{') {
            let inner = &line[(open + 1)..];
            let inner = inner.split('}').next().unwrap_or(inner);
            for tok in inner.split_whitespace() {
                if let Some((pid_str, _rest)) = tok.split_once(':')
                    && let Ok(pid) = pid_str.parse::<i32>()
                        && pid > 0 {
                            return Ok(Some(pid));
                        }
            }
        }
    Ok(None)
}

fn try_activity_processes(package: &str) -> anyhow::Result<Option<i32>> {
    let out = Command::new("/system/bin/dumpsys")
        .args(["activity", "processes", package])
        .output()?;
    let s = String::from_utf8_lossy(&out.stdout);
    Ok(parse_pid_from_str(&s))
}

fn try_activity_top(package: &str) -> anyhow::Result<Option<i32>> {
    let out = Command::new("/system/bin/dumpsys")
        .args(["activity", "top"])
        .output()?;
    let s = String::from_utf8_lossy(&out.stdout);
    for line in s.lines().filter(|l| l.contains(package)) {
        if let Some(pid) = parse_pid_from_str(line) {
            return Ok(Some(pid));
        }
    }
    Ok(None)
}

fn ps_fallback(package: &str) -> anyhow::Result<Option<i32>> {
    let out = Command::new("/system/bin/sh")
        .args(["-c", &format!("ps -A | grep -F {}", package)])
        .output()?;
    let s = String::from_utf8_lossy(&out.stdout);
    for line in s.lines().filter(|l| l.contains(package)) {
        for tok in line.split_whitespace() {
            if let Ok(n) = tok.parse::<i32>()
                && n > 0 {
                    return Ok(Some(n));
                }
        }
    }
    Ok(None)
}

fn parse_pid_from_str(s: &str) -> Option<i32> {
    if let Some(i) = s.find("pid=") {
        let rest = &s[i + 4..];
        let end = rest
            .find(|c: char| !c.is_ascii_digit())
            .unwrap_or(rest.len());
        if end > 0
            && let Ok(pid) = rest[..end].parse::<i32>()
                && pid > 0 {
                    return Some(pid);
                }
    }
    None
}
