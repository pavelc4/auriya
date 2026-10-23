use auriya::core::logger::{
    DaemonResourceTelemetry, LogFlag, LogWriter, StackWriter, StateChangeLogger, WindowThrottle,
    format_dmesg_entry, get_boottime_secs,
};
use core::str;
use std::fs;
use tempfile::tempdir;

#[test]
fn test_stack_writer_formatting() {
    let mut writer = StackWriter::<64>::new();
    assert_eq!(writer.as_bytes().len(), 0);

    core::fmt::write(&mut writer, format_args!("hello {}", 123)).unwrap();
    let result = str::from_utf8(writer.as_bytes()).unwrap();
    assert_eq!(result, "hello 123");

    writer.clear();
    assert_eq!(writer.as_bytes().len(), 0);
}

#[test]
fn test_boottime_relative_timestamp() {
    let uptime = get_boottime_secs();
    assert!(uptime >= 0.0);

    let mut writer = StackWriter::<128>::new();
    format_dmesg_entry(
        &mut writer,
        uptime,
        LogFlag::Status,
        format_args!("test event"),
    )
    .unwrap();

    let output = str::from_utf8(writer.as_bytes()).unwrap();
    assert!(output.starts_with('['));
    assert!(output.contains("] auriya: STATUS | test event\n"));
}

#[test]
fn test_dmesg_flags_alignment() {
    let flags = [
        LogFlag::Status,
        LogFlag::Code,
        LogFlag::Pass,
        LogFlag::Warn,
        LogFlag::Fail,
        LogFlag::Debug,
    ];

    let mut pipe_positions = Vec::new();

    for flag in flags {
        let mut writer = StackWriter::<128>::new();
        format_dmesg_entry(
            &mut writer,
            123.456789,
            flag,
            format_args!("alignment payload"),
        )
        .unwrap();

        let s = str::from_utf8(writer.as_bytes()).unwrap();
        let pipe_idx = s.find('|').expect("Missing vertical pipe");
        pipe_positions.push(pipe_idx);
    }

    // Assert that every single flag lines up the '|' character at the exact same column index!
    let first = pipe_positions[0];
    for pos in pipe_positions {
        assert_eq!(pos, first);
    }
}

#[test]
fn test_state_change_logger() {
    let mut logger = StateChangeLogger::<&str>::new();
    let mut logged = Vec::new();

    logger.log_if_changed("Powersave", |s| logged.push(*s));
    logger.log_if_changed("Powersave", |s| logged.push(*s)); // duplicate, should be skipped
    logger.log_if_changed("Powersave", |s| logged.push(*s)); // duplicate, should be skipped
    logger.log_if_changed("Performance", |s| logged.push(*s)); // change, should log
    logger.log_if_changed("Performance", |s| logged.push(*s)); // duplicate, should be skipped
    logger.log_if_changed("Balance", |s| logged.push(*s)); // change, should log

    assert_eq!(logged, vec!["Powersave", "Performance", "Balance"]);
}

#[test]
fn test_window_throttle() {
    let throttle = WindowThrottle::new(10);
    let mut window_triggers = Vec::new();

    for _ in 0..25 {
        if let Some(idx) = throttle.tick() {
            window_triggers.push(idx);
        }
    }

    assert_eq!(window_triggers, vec![1, 2]);
    assert_eq!(throttle.total_ticks(), 25);
}

#[test]
fn test_daemon_resource_telemetry() {
    let mut telemetry = DaemonResourceTelemetry::new();
    let stats = telemetry.sample();

    assert!(stats.is_some());
    let stats = stats.unwrap();
    assert_eq!(stats.pid, std::process::id());
    assert!(stats.rss_mb >= 0.0);
    assert!(stats.threads >= 1);
}

#[test]
fn test_log_writer_file_and_rotation() {
    let dir = tempdir().unwrap();
    let log_path = dir.path().join("daemon.log");

    let writer = LogWriter::new(&log_path);
    writer.write_bytes(b"[  100.000000] auriya: STATUS | write line 1\n");
    writer.write_bytes(b"[  100.000001] auriya: PASS   | write line 2\n");

    let contents = fs::read_to_string(&log_path).unwrap();
    assert!(contents.contains("auriya: STATUS | write line 1\n"));
    assert!(contents.contains("auriya: PASS   | write line 2\n"));
}

#[test]
fn test_module_prop_status_update() {
    let dir = tempdir().unwrap();
    let prop_path = dir.path().join("module.prop");
    fs::write(
        &prop_path,
        "id=auriya\nname=Auriya\ndescription=Ferris, let's goo!\nauthor=Pavelc4\n",
    )
    .unwrap();

    auriya::platform::module_prop::update_module_prop_at(prop_path.to_str().unwrap(), "active 🚀");

    let updated = fs::read_to_string(&prop_path).unwrap();
    assert!(updated.contains("description=Ferris, let's goo!\\nstatus: active 🚀\n"));

    auriya::platform::module_prop::update_module_prop_at(prop_path.to_str().unwrap(), "sleep 💤");
    let updated2 = fs::read_to_string(&prop_path).unwrap();
    assert!(updated2.contains("description=Ferris, let's goo!\\nstatus: sleep 💤\n"));
}
