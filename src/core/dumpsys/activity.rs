use crate::core::cmd::run_cmd_timeout_async;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

// Rate-limit helper
static LAST_PS_WARN_MS: AtomicU64 = AtomicU64::new(0);
const PS_WARN_DEBOUNCE_MS: u64 = 30000; // 30s

#[inline]
pub fn is_pid_valid(pid: i32) -> bool {
    if pid <= 0 {
        return false;
    }
    std::path::Path::new(&format!("/proc/{}", pid)).exists()
}

pub async fn get_app_pid(package: &str) -> anyhow::Result<Option<i32>> {
    if let Some(pid) = try_visible_activity_process().await? {
        return Ok(Some(pid));
    }
    if let Some(pid) = try_activity_processes(package).await? {
        return Ok(Some(pid));
    }
    if let Some(pid) = try_activity_top(package).await? {
        return Ok(Some(pid));
    }
    ps_fallback(package).await
}

async fn try_visible_activity_process() -> anyhow::Result<Option<i32>> {
    let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["activity", "activities"], 2000)
        .await
    {
        Ok(o) => o,
        Err(_) => return Ok(None),
    };

    let s = String::from_utf8_lossy(&out.stdout);
    if let Some(line) = s.lines().find(|l| l.contains("VisibleActivityProcess"))
        && let Some(open) = line.find('{')
    {
        let inner = &line[open + 1..];
        let inner = inner.split('}').next().unwrap_or(inner);
        for tok in inner.split_whitespace() {
            if let Some((pid_str, _rest)) = tok.split_once(':')
                && let Ok(pid) = pid_str.parse::<i32>()
                && pid > 0
            {
                return Ok(Some(pid));
            }
        }
    }
    Ok(None)
}

async fn try_activity_processes(package: &str) -> anyhow::Result<Option<i32>> {
    let out = match run_cmd_timeout_async(
        "/system/bin/dumpsys",
        &["activity", "processes", package],
        2000,
    )
    .await
    {
        Ok(o) => o,
        Err(_) => return Ok(None),
    };

    let s = String::from_utf8_lossy(&out.stdout);
    Ok(parse_pid_from_str(&s))
}

async fn try_activity_top(package: &str) -> anyhow::Result<Option<i32>> {
    let out = match run_cmd_timeout_async("/system/bin/dumpsys", &["activity", "top"], 2000).await {
        Ok(o) => o,
        Err(_) => return Ok(None),
    };

    let s = String::from_utf8_lossy(&out.stdout);
    for line in s.lines().filter(|l| l.contains(package)) {
        if let Some(pid) = parse_pid_from_str(line) {
            return Ok(Some(pid));
        }
    }
    Ok(None)
}

async fn ps_fallback(package: &str) -> anyhow::Result<Option<i32>> {
    let cmd = format!("ps -A | grep -F '{}'", package);
    let out = match run_cmd_timeout_async("/system/bin/sh", &["-c", &cmd], 1500).await {
        Ok(o) => o,
        Err(e) => {
            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64;
            let last = LAST_PS_WARN_MS.load(Ordering::Relaxed);
            if now.saturating_sub(last) > PS_WARN_DEBOUNCE_MS {
                tracing::warn!(target: "auriya:pid", "ps fallback failed: {:?}", e);
                LAST_PS_WARN_MS.store(now, Ordering::Relaxed);
            }
            return Ok(None);
        }
    };

    let s = String::from_utf8_lossy(&out.stdout);
    for line in s.lines().filter(|l| l.contains(package)) {
        for tok in line.split_whitespace() {
            if let Ok(n) = tok.parse::<i32>()
                && n > 0
            {
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
            && pid > 0
        {
            return Some(pid);
        }
    }
    None
}
