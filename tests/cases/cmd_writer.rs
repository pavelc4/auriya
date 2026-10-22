use auriya::core::cmd_writer::{Cmd, CmdWriter, DndFilter};
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
    assert_eq!(s1, 1);
    assert_eq!(s2, 2);
    let text = fs::read_to_string(&target).unwrap();
    assert!(text.contains("seq 2"));
    assert!(text.contains("dnd 1"));
    fs::remove_file(&target).ok();
}

#[test]
fn encodes_combined_payload() {
    let target = temp_target();
    let writer = CmdWriter::new(&target);
    writer
        .write(&Cmd {
            dnd: Some(DndFilter::All),
            refresh_rate: Some(120),
        })
        .unwrap();
    let text = fs::read_to_string(&target).unwrap();
    assert!(text.contains("seq 1"));
    assert!(text.contains("dnd 0"));
    assert!(text.contains("refresh_rate 120"));
    fs::remove_file(&target).ok();
}

#[test]
fn refresh_rate_zero_means_restore() {
    let target = temp_target();
    let writer = CmdWriter::new(&target);
    writer
        .write(&Cmd {
            refresh_rate: Some(0),
            ..Cmd::default()
        })
        .unwrap();
    let text = fs::read_to_string(&target).unwrap();
    assert!(text.contains("refresh_rate 0"));
    assert!(!text.contains("dnd"));
    fs::remove_file(&target).ok();
}
