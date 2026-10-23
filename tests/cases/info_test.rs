use auriya::platform::info::{collect_report, find_daemon_pid};

#[test]
fn test_collect_report_sanity() {
    let report = collect_report(
        None,
        Some("120Hz (Supported: 60Hz, 120Hz)".to_string()),
        Some("ebpf"),
    );

    // Verify non-empty basic hardware & platform fields
    assert!(!report.hw.brand.is_empty());
    assert!(!report.hw.codename.is_empty());
    assert!(!report.hw.kernel.is_empty());
    assert!(!report.hw.selinux.is_empty());
    assert!(!report.hw.root_manager.is_empty());

    // Verify eBPF fields are populated
    assert!(!report.ebpf.bpf_mount.is_empty());
    assert!(!report.ebpf.bpf_syscall.is_empty());
    assert!(!report.ebpf.jit_compiler.is_empty());
    assert!(!report.ebpf.kala_probe.is_empty());
    assert_eq!(
        report.ebpf.active_engine,
        "eBPF Broadcast (Low-overhead hook)"
    );

    // Display rates preserved
    assert_eq!(
        report.hw.display_rates.as_deref(),
        Some("120Hz (Supported: 60Hz, 120Hz)")
    );
}

#[test]
fn test_find_daemon_pid_or_none() {
    // Should not panic, either returns Some(pid) or None
    let _ = find_daemon_pid();
}
