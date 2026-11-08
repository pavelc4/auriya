use auriya::core::policy::{
    PolicyContext, PolicyDecision, PolicyEngine, PowerSnapshot, SystemSnapshot,
};

#[test]
fn test_decide_powersave_when_screen_off() {
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: false,
            battery_saver: false,
        },
        foreground_pkg: Some("com.mobile.legends"),
        focused_pid: Some(1234),
        package_pids: &[1234],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: true,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::Powersave
    );
}

#[test]
fn test_decide_powersave_when_battery_saver_unwhitelisted() {
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: true,
        },
        foreground_pkg: Some("com.android.chrome"),
        focused_pid: Some(1234),
        package_pids: &[1234],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: false,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::Powersave
    );
}

#[test]
fn test_decide_battery_saver_does_not_lock_whitelisted_game() {
    // Regression test for Issue #152:
    // When battery saver is active, entering a whitelisted game must not be locked into Powersave.
    let pkg = "com.miHoYo.GenshinImpact";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: true,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(5678),
        package_pids: &[5678],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: true,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::EnterGame {
            pkg,
            pid: 5678,
            pids: &[5678],
        }
    );
}

#[test]
fn test_decide_no_foreground() {
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: None,
        focused_pid: None,
        package_pids: &[],
        is_pid_valid: false,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: false,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::NoForeground
    );
}

#[test]
fn test_decide_non_whitelisted() {
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some("com.android.chrome"),
        focused_pid: Some(999),
        package_pids: &[999],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: false,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::NonWhitelisted {
            pkg: "com.android.chrome",
            pid: Some(999),
            pids: &[999],
            reason: "not whitelisted",
        }
    );
}

#[test]
fn test_decide_enter_game() {
    let pkg = "com.miHoYo.GenshinImpact";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(5678),
        package_pids: &[5678],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: None,
        is_whitelisted: true,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::EnterGame {
            pkg,
            pid: 5678,
            pids: &[5678],
        }
    );
}

#[test]
fn test_decide_same_app_unwhitelisted_monitored() {
    let pkg = "com.android.chrome";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(999),
        package_pids: &[999],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: Some(pkg),
        is_whitelisted: false,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::SameAppMonitor { pkg }
    );
}

#[test]
fn test_decide_same_app_whitelisted_fas() {
    let pkg = "com.miHoYo.GenshinImpact";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(5678),
        package_pids: &[5678],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: Some(pkg),
        is_whitelisted: true,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::SameAppFas { pkg, pids: &[5678] }
    );
}

#[test]
fn test_decide_same_app_whitelisted_monitor_when_fas_disabled() {
    let pkg = "com.miHoYo.GenshinImpact";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(5678),
        package_pids: &[5678],
        is_pid_valid: true,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: Some(pkg),
        is_whitelisted: true,
        fas_enabled: false,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::SameAppMonitor { pkg }
    );
}

#[test]
fn test_decide_same_app_dead_pid() {
    let pkg = "com.android.chrome";
    let snapshot = SystemSnapshot {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg),
        focused_pid: Some(999),
        package_pids: &[999],
        is_pid_valid: false,
        is_floating: false,
    };
    let ctx = PolicyContext {
        last_pkg: Some(pkg),
        is_whitelisted: false,
        fas_enabled: true,
        fas_available: true,
    };

    assert_eq!(
        PolicyEngine::evaluate(&snapshot, &ctx),
        PolicyDecision::NonWhitelisted {
            pkg,
            pid: Some(999),
            pids: &[999],
            reason: "process dead",
        }
    );
}

#[test]
fn test_apply_powersave_governor_selection() {
    if let Ok(guard) = crate::common::verify_kernel_subsystem_write_access() {
        let res = auriya::core::profile::apply_powersave();
        assert!(res.is_ok());

        let available = auriya::platform::cpu::governor::available_governors();
        let expected_gov = if available.iter().any(|g| g == "powersave") {
            "powersave"
        } else {
            "schedutil"
        };

        let paths = crate::common::find_cpu_governor_paths().unwrap();
        let current_gov = std::fs::read_to_string(&paths[0]).unwrap();
        assert_eq!(current_gov.trim(), expected_gov);

        drop(guard);
    }
}
