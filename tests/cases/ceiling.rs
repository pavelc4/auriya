use auriya::core::tweaks::ceiling::{
    CeilingConfig, CeilingController, CeilingLevel, hw_max_freq_for_core,
};
use std::fs;
use std::os::unix::fs::PermissionsExt;

fn is_root() -> bool {
    (unsafe { libc::geteuid() == 0 })
        && std::env::var("AURIYA_TEST_ROOT")
            .map(|v| v == "1")
            .unwrap_or(false)
}

fn read_u64(path: &str) -> Option<u64> {
    fs::read_to_string(path)
        .ok()
        .and_then(|s| s.split_whitespace().next().and_then(|t| t.parse().ok()))
}

fn mode(path: &str) -> u32 {
    fs::metadata(path)
        .map(|m| m.permissions().mode() & 0o777)
        .unwrap_or(0)
}

fn online(core: usize) -> bool {
    fs::read_to_string(format!("/sys/devices/system/cpu/cpu{core}/online"))
        .map(|s| s.trim() == "1")
        .unwrap_or(true) // cpu0 often has no `online` node and is always up
}

fn snapshot(label: &str, cores: &[(&str, usize)]) {
    eprintln!("--- {label} ---");
    for (name, core) in cores {
        let d = format!("/sys/devices/system/cpu/cpu{core}/cpufreq");
        let max = read_u64(&format!("{d}/scaling_max_freq")).unwrap_or(0);
        let min = read_u64(&format!("{d}/scaling_min_freq")).unwrap_or(0);
        let mp = mode(&format!("{d}/scaling_max_freq"));
        let np = mode(&format!("{d}/scaling_min_freq"));
        eprintln!(
            "  {name:<6} cpu{core}: min={min:>8} max={max:>8} max_perm={mp:o} min_perm={np:o} online={}",
            online(*core)
        );
    }
}

/// Real-device test (root only): drive the ceiling through
/// balance → sleep(Low) → balance → in-game(High) → balance, capturing
/// each cluster's scaling_{min,max}_freq and permissions, and asserting
/// that every release fully restores the hardware limits with writable
/// permissions — the regression guard for the "cluster stuck low after
/// screen-off→on / min pinned at max after a game" bug.
///
/// Skipped automatically when not run as root (the default adb runner
/// runs as the shell user). To exercise it:
///   AURIYA_TEST_ROOT=1 cargo test ceiling_scenarios -- --nocapture
/// Stop the daemon first so it does not fight over the ceiling.
#[test]
fn ceiling_scenarios_capture_and_restore() {
    if !is_root() {
        eprintln!("ceiling_scenarios: SKIPPED (needs root; run with AURIYA_TEST_ROOT=1)");
        return;
    }

    let mut ctrl = CeilingController::new();
    let cfg = CeilingConfig::default();

    let mut cores: Vec<(&str, usize)> = Vec::new();
    if let Some(&c) = ctrl.layout.little_ids.first() {
        cores.push(("little", c));
    }
    // Use the LAST big core: apply_low freezes the upper half (the
    // first half is offlined), so this one exercises the freeze→restore
    // path rather than offline→online.
    if let Some(&c) = ctrl.layout.big_ids.last() {
        cores.push(("big", c));
    }
    if let Some(&c) = ctrl.layout.prime_ids.first() {
        cores.push(("prime", c));
    }
    assert!(!cores.is_empty(), "no CPU clusters detected");

    let hw_max = |core: usize| hw_max_freq_for_core(core);
    let hw_min = |core: usize| {
        read_u64(&format!(
            "/sys/devices/system/cpu/cpu{core}/cpufreq/cpuinfo_min_freq"
        ))
    };

    ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
    snapshot("BALANCE (baseline / free)", &cores);

    // SLEEP / screen-off → Low ceiling caps little + big max.
    ctrl.apply(CeilingLevel::Low, &cfg).unwrap();
    snapshot("LOW (sleep / screen-off)", &cores);

    // Release → every online core's max must be back at the hw limit
    // and writable (not the stuck-low 0o444 we used to leave behind).
    ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
    snapshot("BALANCE after LOW (restore)", &cores);
    for &(name, core) in &cores {
        if !online(core) {
            continue;
        }
        let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_max_freq");
        assert_eq!(
            read_u64(&p),
            hw_max(core),
            "{name} max not restored after LOW"
        );
        assert_ne!(
            mode(&p) & 0o222,
            0,
            "{name} scaling_max_freq still read-only after LOW"
        );
    }

    // IN-GAME → High ceiling locks min = max on every core.
    ctrl.apply(CeilingLevel::High, &cfg).unwrap();
    snapshot("HIGH (in-game)", &cores);
    for &(name, core) in &cores {
        let min = read_u64(&format!(
            "/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq"
        ));
        assert_eq!(min, hw_max(core), "{name} min not locked to max under HIGH");
    }

    // Release → min must drop back to the hw minimum, writable.
    ctrl.apply(CeilingLevel::Balance, &cfg).unwrap();
    snapshot("BALANCE after HIGH (restore)", &cores);
    for &(name, core) in &cores {
        let p = format!("/sys/devices/system/cpu/cpu{core}/cpufreq/scaling_min_freq");
        assert_eq!(
            read_u64(&p),
            hw_min(core),
            "{name} min not restored after HIGH"
        );
        assert_ne!(
            mode(&p) & 0o222,
            0,
            "{name} scaling_min_freq still read-only after HIGH"
        );
    }

    // Drop restores + onlines everything, leaving the device clean.
    drop(ctrl);
}
