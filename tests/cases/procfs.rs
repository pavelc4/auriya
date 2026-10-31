use auriya::platform::procfs::{is_pid_valid, verify_pid_package};

#[test]
fn test_is_pid_valid() {
    assert!(!is_pid_valid(-1));
    assert!(!is_pid_valid(0));
    assert!(!is_pid_valid(999_999_999));
    assert!(is_pid_valid(std::process::id() as i32));
}

#[test]
fn test_verify_pid_package_invalid_pid() {
    assert!(!verify_pid_package(-1, "com.mobile.legends"));
    assert!(!verify_pid_package(0, "com.mobile.legends"));
    assert!(!verify_pid_package(999_999_999, "com.mobile.legends"));
}

#[test]
fn test_parse_focused_package_from_dumpsys() {
    use auriya::platform::procfs::parse_focused_package_from_dumpsys;

    let sample1 = "  mCurrentFocus=Window{89d08fe u0 org.telegram.messenger/com.exteragram.messenger.MonetIconExtera}\n";
    assert_eq!(
        parse_focused_package_from_dumpsys(sample1),
        Some("org.telegram.messenger".to_string())
    );

    let sample2 = "  mCurrentFocus=Window{4454cf7 u0 com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity}\n";
    assert_eq!(
        parse_focused_package_from_dumpsys(sample2),
        Some("com.google.android.apps.nexuslauncher".to_string())
    );

    let sample3 = "  mCurrentFocus=null\n";
    assert_eq!(parse_focused_package_from_dumpsys(sample3), None);

    let sample4 = "random string without focus\n";
    assert_eq!(parse_focused_package_from_dumpsys(sample4), None);
}

#[test]
fn test_find_pid_by_package_nonexistent() {
    use auriya::platform::procfs::find_pid_by_package;

    assert_eq!(find_pid_by_package("nonexistent.fake.app.12345"), None);
}
