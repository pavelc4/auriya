use auriya::core::procfs::{is_pid_valid, verify_pid_package};

#[test]
fn test_is_pid_valid() {
    assert!(!is_pid_valid(-1));
    assert!(!is_pid_valid(0));
    assert!(!is_pid_valid(999_999_999));
    assert!(is_pid_valid(std::process::id() as i32));
}

#[test]
fn test_verify_pid_package_invalid_pid() {
    assert!(!verify_pid_package(-1, "com.example"));
    assert!(!verify_pid_package(0, "com.example"));
    assert!(!verify_pid_package(999_999_999, "com.example"));
}
