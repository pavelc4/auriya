// Command writer for the Android companion service.
//
// Some daemon decisions (DnD toggle, target refresh rate) need to be
// executed via Android framework APIs — the daemon itself runs as
// root and cannot call them. We instead serialise the request to a
// small file (`auriya_cmd`) that the companion service watches via
// inotify and replays through the proper APIs.
//
// Wire format (mirrors `android/shared/.../CmdFormat.kt`):
//
//     seq 42
//     dnd 1            # 0=off, 1=priority, 2=total, 3=alarms
//     refresh_rate 90  # Hz; 0 means "restore previous"
//
// Each write replaces the file atomically (tmp → rename) so the
// companion never reads a half-written payload. `seq` is a process-
// monotonic counter so the companion can deduplicate.

use std::fmt::Write as _;
use std::io::Write as _;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicU64, Ordering};

pub const CMD_FILE: &str = "/data/adb/.config/auriya/auriya_cmd";

/// Process-wide writer pointing at [`CMD_FILE`]. Every caller MUST go
/// through this — multiple writers would each start their own `seq`
/// counter and the companion's dedup logic would mis-fire.
pub fn shared() -> &'static CmdWriter {
    static WRITER: OnceLock<CmdWriter> = OnceLock::new();
    WRITER.get_or_init(CmdWriter::default_path)
}

/// Maps to NotificationManager.INTERRUPTION_FILTER_*.
///
/// The full enum is part of the wire contract with the companion
/// service even when the daemon does not currently emit every
/// variant — keeping the dead variants in place avoids breaking the
/// numeric mapping if a future profile picks them up.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[allow(dead_code)]
pub enum DndFilter {
    /// All notifications pass through.
    All = 0,
    /// Priority-only (the most common gaming setting).
    Priority = 1,
    /// Total silence.
    None = 2,
    /// Alarms only.
    Alarms = 3,
}

/// Single command snapshot delivered to the companion.
#[derive(Debug, Default, Clone)]
pub struct Cmd {
    pub dnd: Option<DndFilter>,
    pub refresh_rate: Option<u32>,
}

/// Writer for `auriya_cmd`. Cheap to construct (just a path + atomic
/// counter); clone-friendly via the `Arc` the caller wraps it in.
pub struct CmdWriter {
    target: PathBuf,
    seq: AtomicU64,
}

impl CmdWriter {
    pub fn new<P: AsRef<Path>>(target: P) -> Self {
        Self {
            target: target.as_ref().to_path_buf(),
            seq: AtomicU64::new(0),
        }
    }

    /// Default writer targeting the canonical path. Use in production.
    pub fn default_path() -> Self {
        Self::new(CMD_FILE)
    }

    /// Convenience: write a single-field DnD command. Returns the
    /// seq assigned to the write so callers can correlate logs.
    pub fn write_dnd(&self, filter: DndFilter) -> anyhow::Result<u64> {
        self.write(&Cmd {
            dnd: Some(filter),
            ..Cmd::default()
        })
    }

    /// Convenience: write a single-field refresh-rate command. `0`
    /// signals the companion to restore whatever it captured before the
    /// daemon first set a custom rate.
    pub fn write_refresh_rate(&self, hz: u32) -> anyhow::Result<u64> {
        self.write(&Cmd {
            refresh_rate: Some(hz),
            ..Cmd::default()
        })
    }

    /// Render `cmd` and atomically replace the file. Returns the seq
    /// used so the caller can correlate logs if needed.
    pub fn write(&self, cmd: &Cmd) -> anyhow::Result<u64> {
        let seq = self.seq.fetch_add(1, Ordering::Relaxed) + 1;

        let mut payload = String::with_capacity(64);
        let _ = writeln!(payload, "seq {seq}");
        if let Some(dnd) = cmd.dnd {
            let _ = writeln!(payload, "dnd {}", dnd as u8);
        }
        if let Some(rr) = cmd.refresh_rate {
            let _ = writeln!(payload, "refresh_rate {rr}");
        }

        let parent = self.target.parent().ok_or_else(|| {
            anyhow::anyhow!("cmd file {} has no parent directory", self.target.display())
        })?;
        std::fs::create_dir_all(parent).ok();

        // Sibling tempfile, then atomic rename — the companion's
        // inotify watcher sees a single coherent CLOSE_WRITE/MOVED_TO
        // event per update.
        let mut tmp = self.target.clone();
        let stem = self
            .target
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("auriya_cmd");
        tmp.set_file_name(format!(".{stem}.tmp"));

        {
            let mut f = std::fs::File::create(&tmp)?;
            f.write_all(payload.as_bytes())?;
            f.sync_all()?;
        }
        std::fs::rename(&tmp, &self.target)?;
        Ok(seq)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::PathBuf;

    fn temp_target() -> PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!(
            "auriya-cmd-{}-{}.tmp",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_nanos())
                .unwrap_or(0)
        ));
        p
    }

    #[test]
    fn encodes_dnd_filter() {
        let target = temp_target();
        let writer = CmdWriter::new(&target);
        writer.write_dnd(DndFilter::Priority).unwrap();
        let text = fs::read_to_string(&target).unwrap();
        assert!(text.contains("seq 1"));
        assert!(text.contains("dnd 1"));
        fs::remove_file(&target).ok();
    }

    #[test]
    fn seq_increments_per_write() {
        let target = temp_target();
        let writer = CmdWriter::new(&target);
        let s1 = writer.write_dnd(DndFilter::All).unwrap();
        let s2 = writer.write_dnd(DndFilter::Priority).unwrap();
        let s3 = writer.write_dnd(DndFilter::None).unwrap();
        assert_eq!(s1, 1);
        assert_eq!(s2, 2);
        assert_eq!(s3, 3);
        let text = fs::read_to_string(&target).unwrap();
        assert!(text.contains("seq 3"));
        assert!(text.contains("dnd 2"));
        fs::remove_file(&target).ok();
    }

    #[test]
    fn encodes_combined_payload() {
        let target = temp_target();
        let writer = CmdWriter::new(&target);
        writer
            .write(&Cmd {
                dnd: Some(DndFilter::Alarms),
                refresh_rate: Some(120),
            })
            .unwrap();
        let text = fs::read_to_string(&target).unwrap();
        assert!(text.contains("seq 1"));
        assert!(text.contains("dnd 3"));
        assert!(text.contains("refresh_rate 120"));
        fs::remove_file(&target).ok();
    }

    #[test]
    fn refresh_rate_zero_means_restore() {
        let target = temp_target();
        let writer = CmdWriter::new(&target);
        writer
            .write(&Cmd {
                dnd: None,
                refresh_rate: Some(0),
            })
            .unwrap();
        let text = fs::read_to_string(&target).unwrap();
        assert!(text.contains("refresh_rate 0"));
        assert!(!text.contains("dnd"));
        fs::remove_file(&target).ok();
    }
}
