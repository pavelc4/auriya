use auriya::core::system_status::SystemStatus;

#[test]
fn parses_full_snapshot() {
    let raw = b"\
focused_app com.example.app 1234 10042
screen_awake 1
battery_saver 0
zen_mode 0
";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.focused_app.as_deref(), Some("com.example.app"));
    assert_eq!(s.focused_pid, Some(1234));
    assert_eq!(s.focused_uid, Some(10042));
    assert_eq!(s.screen_awake, Some(true));
    assert_eq!(s.battery_saver, Some(false));
    assert_eq!(s.zen_mode, Some(0));
    assert!(s.is_populated());
}

#[test]
fn parses_partial_snapshot() {
    let raw = b"screen_awake 0\n";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.screen_awake, Some(false));
    assert_eq!(s.focused_app, None);
    assert!(s.is_populated());
}

#[test]
fn ignores_unknown_keys_and_comments() {
    let raw = b"\
# this is a comment

future_field 42
focused_app com.test 99 10001
unknown_thing yes please
";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.focused_app.as_deref(), Some("com.test"));
    assert_eq!(s.focused_pid, Some(99));
}

#[test]
fn handles_crlf_and_extra_whitespace() {
    let raw = b"focused_app   com.crlf  555  10500\r\nscreen_awake   1\r\n";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.focused_app.as_deref(), Some("com.crlf"));
    assert_eq!(s.focused_pid, Some(555));
    assert_eq!(s.focused_uid, Some(10500));
    assert_eq!(s.screen_awake, Some(true));
}

#[test]
fn empty_input_is_unpopulated() {
    let s = SystemStatus::parse(b"");
    assert!(!s.is_populated());
}

#[test]
fn malformed_pid_is_dropped_but_pkg_keeps() {
    let raw = b"focused_app com.bad notanum 10042\n";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.focused_app.as_deref(), Some("com.bad"));
    assert_eq!(s.focused_pid, None);
    assert_eq!(s.focused_uid, Some(10042));
}

#[test]
fn bool_accepts_true_false_words() {
    let raw = b"screen_awake true\nbattery_saver false\n";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.screen_awake, Some(true));
    assert_eq!(s.battery_saver, Some(false));
}

#[test]
fn bool_rejects_garbage() {
    let raw = b"screen_awake maybe\n";
    let s = SystemStatus::parse(raw);
    assert_eq!(s.screen_awake, None);
}
