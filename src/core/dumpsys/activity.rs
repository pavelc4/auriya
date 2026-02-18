use crate::core::cmd::run_cmd_timeout_async;
use memchr::{memchr, memmem};
use std::fs;

#[inline]
pub fn is_pid_valid(pid: i32) -> bool {
    if pid <= 0 {
        return false;
    }
    std::path::Path::new(&format!("/proc/{}", pid)).exists()
}

pub async fn get_app_pid(package: &str) -> anyhow::Result<Option<i32>> {
    let out = match run_cmd_timeout_async(
        "/system/bin/dumpsys",
        &["activity", "activities"],
        1000,
    )
    .await
    {
        Ok(o) => o,
        Err(_) => return Ok(None),
    };

    find_pid_with_verification(&out.stdout, package)
}

pub fn find_pid_with_verification(data: &[u8], package: &str) -> anyhow::Result<Option<i32>> {
    let vis_finder = memmem::Finder::new(b"VisibleActivityProcess");
    let mut pos = 0;

    while let Some(offset) = vis_finder.find(&data[pos..]) {
        pos += offset;

        let line_start = data[..pos]
            .iter()
            .rposition(|&b| b == b'\n')
            .map_or(0, |p| p + 1);

        let line_end = memchr(b'\n', &data[pos..]).map_or(data.len(), |p| pos + p);

        let line = &data[line_start..line_end];

        if let Some(pid) = extract_pid_zerocopy(line) {
            tracing::debug!(
                target: "auriya:pid",
                "Found PID {} in VisibleActivityProcess, checking for '{}'",
                pid,
                package
            );

            if verify_pid_package(pid, package) {
                tracing::debug!(
                    target: "auriya:pid",
                    "✓ PID {} matches package '{}'",
                    pid,
                    package
                );
                return Ok(Some(pid));
            }
        }
        pos = line_end + 1;
    }
    Ok(None)
}

#[inline]
fn verify_pid_package(pid: i32, package: &str) -> bool {
    let cmdline_path = format!("/proc/{}/cmdline", pid);

    if let Ok(cmdline) = fs::read(&cmdline_path) {
        let end = cmdline
            .iter()
            .position(|&b| b == b'\0' || b == b':')
            .unwrap_or(cmdline.len());

        let cmdline_pkg = &cmdline[..end];
        let cmdline_str = String::from_utf8_lossy(cmdline_pkg);

        tracing::debug!(
            target: "auriya:pid",
            "  PID {} cmdline: '{}'",
            pid,
            cmdline_str
        );

        if cmdline_pkg == package.as_bytes() {
            return true;
        }

        if cmdline_str.contains(package) {
            return true;
        }
    }

    false
}

#[inline(always)]
pub fn extract_pid_zerocopy(line: &[u8]) -> Option<i32> {
    if let Some(brace_pos) = memchr(b'{', line) {
        if let Some(colon_offset) = memchr(b':', &line[brace_pos..]) {
            let pid_start = brace_pos + 1;
            let pid_end = brace_pos + colon_offset;
            let pid_bytes = &line[pid_start..pid_end];

            if let Ok(pid) = atoi_fast(pid_bytes) {
                return Some(pid);
            }
        }
    }

    let proc_finder = memmem::Finder::new(b"ProcessRecord{");
    if let Some(proc_pos) = proc_finder.find(line) {
        let after_brace = proc_pos + 14;


        if let Some(space_offset) = memchr(b' ', &line[after_brace..]) {
            let pid_start = after_brace + space_offset + 1;


            if let Some(colon_offset) = memchr(b':', &line[pid_start..]) {
                let pid_end = pid_start + colon_offset;
                let pid_bytes = &line[pid_start..pid_end];

                if let Ok(pid) = atoi_fast(pid_bytes) {
                    return Some(pid);
                }
            }
        }
    }

    None
}

#[inline(always)]
fn atoi_fast(bytes: &[u8]) -> Result<i32, ()> {
    if bytes.is_empty() {
        return Err(());
    }

    let mut result = 0i32;

    for &byte in bytes {
        match byte {
            b'0'..=b'9' => {
                result = result
                    .saturating_mul(10)
                    .saturating_add((byte - b'0') as i32);
            }
            b' ' | b'\t' => continue,
            _ => return Err(()),
        }
    }

    Ok(result)
}
