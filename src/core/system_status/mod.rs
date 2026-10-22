// System status snapshot — the Android side (companion service) writes
// this file whenever the foreground app, screen state, battery saver, or
// DnD/zen mode changes. The daemon reads it instead of polling
// `dumpsys activity` / `dumpsys power` directly.
//
// Format (line-based, key-value, one entry per line):
//
//   focused_app <package> <pid> <uid>
//   screen_awake <0|1>
//   battery_saver <0|1>
//   zen_mode <0|1|2|3>
//
// Lines are unordered and any subset may be present. Unknown lines are
// ignored so the format can grow without breaking older readers.

use memchr::memchr;

pub mod watcher;

pub use watcher::SystemStatusCache;

/// Default location of the status file written by the companion service.
pub const STATUS_FILE: &str = "/data/adb/.config/auriya/system_status";

/// A snapshot of system-wide state the daemon needs to make profile
/// decisions. All fields are optional because the writer may emit a
/// partial update (e.g. only `focused_app` changed).
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct SystemStatus {
    pub focused_app: Option<String>,
    pub focused_pid: Option<i32>,
    pub focused_uid: Option<i32>,
    pub screen_awake: Option<bool>,
    pub battery_saver: Option<bool>,
    pub zen_mode: Option<u8>,
}

impl SystemStatus {
    /// Parse the status file contents. Unknown / malformed lines are
    /// skipped silently so the file format can be extended without
    /// breaking older daemon versions.
    pub fn parse(data: &[u8]) -> Self {
        let mut out = Self::default();
        for line in split_lines(data) {
            parse_line(line, &mut out);
        }
        out
    }

    /// True when at least one field has been populated. Used to detect
    /// "the writer produced a valid snapshot" vs "the file is empty or
    /// pure garbage".
    pub fn is_populated(&self) -> bool {
        self.focused_app.is_some()
            || self.screen_awake.is_some()
            || self.battery_saver.is_some()
            || self.zen_mode.is_some()
    }
}

fn split_lines(data: &[u8]) -> impl Iterator<Item = &[u8]> {
    let mut rest = data;
    std::iter::from_fn(move || {
        if rest.is_empty() {
            return None;
        }
        match memchr(b'\n', rest) {
            Some(pos) => {
                let line = &rest[..pos];
                rest = &rest[pos + 1..];
                Some(strip_cr(line))
            }
            None => {
                let line = rest;
                rest = &[];
                Some(strip_cr(line))
            }
        }
    })
}

#[inline]
fn strip_cr(line: &[u8]) -> &[u8] {
    if let Some((&b'\r', rest)) = line.split_last() {
        rest
    } else {
        line
    }
}

fn parse_line(line: &[u8], out: &mut SystemStatus) {
    let line = trim_ascii(line);
    if line.is_empty() || line.starts_with(b"#") {
        return;
    }

    let (key, value) = match split_once(line, b' ') {
        Some(parts) => parts,
        None => return,
    };
    let value = trim_ascii(value);

    match key {
        b"focused_app" => parse_focused_app(value, out),
        b"screen_awake" => out.screen_awake = parse_bool(value),
        b"battery_saver" => out.battery_saver = parse_bool(value),
        b"zen_mode" => out.zen_mode = parse_u8(value),
        _ => {} // forward-compatible: ignore unknown keys
    }
}

fn parse_focused_app(value: &[u8], out: &mut SystemStatus) {
    // <package> <pid> <uid> — pid and uid are optional
    let mut it = split_whitespace(value);
    let Some(pkg) = it.next() else {
        return;
    };
    let pkg_str = match std::str::from_utf8(pkg) {
        Ok(s) => s.to_string(),
        Err(_) => return,
    };
    out.focused_app = Some(pkg_str);

    if let Some(pid_bytes) = it.next() {
        out.focused_pid = parse_i32(pid_bytes);
    }
    if let Some(uid_bytes) = it.next() {
        out.focused_uid = parse_i32(uid_bytes);
    }
}

fn parse_bool(value: &[u8]) -> Option<bool> {
    match value {
        b"1" | b"true" => Some(true),
        b"0" | b"false" => Some(false),
        _ => None,
    }
}

fn parse_u8(value: &[u8]) -> Option<u8> {
    std::str::from_utf8(value).ok()?.trim().parse().ok()
}

fn parse_i32(value: &[u8]) -> Option<i32> {
    std::str::from_utf8(value).ok()?.trim().parse().ok()
}

fn split_once(data: &[u8], sep: u8) -> Option<(&[u8], &[u8])> {
    let pos = memchr(sep, data)?;
    Some((&data[..pos], &data[pos + 1..]))
}

fn split_whitespace(data: &[u8]) -> impl Iterator<Item = &[u8]> {
    let mut rest = data;
    std::iter::from_fn(move || {
        rest = trim_start_ascii(rest);
        if rest.is_empty() {
            return None;
        }
        let end = rest
            .iter()
            .position(|b| matches!(b, b' ' | b'\t'))
            .unwrap_or(rest.len());
        let tok = &rest[..end];
        rest = &rest[end..];
        Some(tok)
    })
}

fn trim_ascii(data: &[u8]) -> &[u8] {
    trim_end_ascii(trim_start_ascii(data))
}

fn trim_start_ascii(data: &[u8]) -> &[u8] {
    let mut i = 0;
    while i < data.len() && matches!(data[i], b' ' | b'\t') {
        i += 1;
    }
    &data[i..]
}

fn trim_end_ascii(data: &[u8]) -> &[u8] {
    let mut end = data.len();
    while end > 0 && matches!(data[end - 1], b' ' | b'\t') {
        end -= 1;
    }
    &data[..end]
}
