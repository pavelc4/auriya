use crate::core::cmd::run_cmd_timeout_async;
use memchr::{memchr, memmem};

pub async fn get_foreground_package() -> anyhow::Result<Option<String>> {
    let out = match run_cmd_timeout_async(
        "/system/bin/dumpsys",
        &["activity", "activities"],
        1000,
    )
    .await
    {
        Ok(o) => o,
        Err(e) => {
            tracing::debug!(target: "auriya:dumpsys", "dumpsys activity timeout: {:?}", e);
            return Ok(None);
        }
    };

    parse_foreground_zerocopy(&out.stdout)
}

fn parse_foreground_zerocopy(data: &[u8]) -> anyhow::Result<Option<String>> {
    let resumed_finder = memmem::Finder::new(b"ResumedActivity");

    let mut pos = 0;

    while let Some(offset) = resumed_finder.find(&data[pos..]) {
        pos += offset;

        let line_start = data[..pos]
            .iter()
            .rposition(|&b| b == b'\n')
            .map_or(0, |p| p + 1);

        let line_end = memchr(b'\n', &data[pos..]).map_or(data.len(), |p| pos + p);

        let line = &data[line_start..line_end];

        if let Some(pkg) = extract_package_zerocopy(line) {
            return Ok(Some(pkg));
        }
        pos = line_end + 1;
    }
    Ok(None)
}

fn extract_package_zerocopy(line: &[u8]) -> Option<String> {
    let u0_finder = memmem::Finder::new(b"u0 ");
    let u0_pos = u0_finder.find(line)? + 3;
    let rest = &line[u0_pos..];
    let slash_pos = memchr(b'/', rest)?;
    let pkg_bytes = &rest[..slash_pos];
    if memchr(b'.', pkg_bytes).is_some() {
        Some(String::from_utf8_lossy(pkg_bytes).trim().to_string())
    } else {
        None
    }
}
