use auriya::core::config::Settings;
use auriya::core::config::gamelist::{GameList, GameProfile};
use auriya::core::system_status::SystemStatusCache;
use auriya::daemon::config::DaemonConfig;
use auriya::daemon::engine::Daemon;
use auriya::daemon::event;
use auriya::daemon::pipeline::{PipelineDecision, PipelineInput, PowerSnapshot};
use std::sync::Arc;

fn create_test_daemon(gamelist: GameList) -> Daemon {
    let cfg = DaemonConfig {
        settings: Settings::default(),
        gamelist,
        log_debounce_ms: 2000,
    };
    let (tx, _rx) = event::channel();
    Daemon::new(cfg, Arc::new(Vec::new()), SystemStatusCache::new(), tx).unwrap()
}

#[test]
fn test_decide_powersave_when_screen_off() {
    let daemon = create_test_daemon(GameList::default());
    let gamelist = GameList::default();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: false,
            battery_saver: false,
        },
        foreground_pkg: Some("com.mobile.legends".to_string()),
        focused_pid: Some(1234),
        package_pids: vec![1234],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::Powersave
    );
}

#[test]
fn test_decide_powersave_when_battery_saver() {
    let daemon = create_test_daemon(GameList::default());
    let gamelist = GameList::default();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: true,
        },
        foreground_pkg: Some("com.mobile.legends".to_string()),
        focused_pid: Some(1234),
        package_pids: vec![1234],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::Powersave
    );
}

#[test]
fn test_decide_no_foreground() {
    let daemon = create_test_daemon(GameList::default());
    let gamelist = GameList::default();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: None,
        focused_pid: None,
        package_pids: Vec::new(),
        is_pid_valid: false,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::NoForeground
    );
}

#[test]
fn test_decide_non_whitelisted() {
    let daemon = create_test_daemon(GameList::default());
    let gamelist = GameList::default();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some("com.android.chrome".to_string()),
        focused_pid: Some(999),
        package_pids: vec![999],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::NonWhitelisted {
            pkg: "com.android.chrome",
            pid: Some(999),
            pids: &[999],
            reason: "not whitelisted",
        }
    );
}

fn make_genshin_gamelist() -> GameList {
    let pkg = "com.miHoYo.GenshinImpact";
    GameList {
        game: vec![GameProfile {
            package: pkg.to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: true,
            target_fps: None,
            refresh_rate: None,
            mode: Some("performance".to_string()),
            ceiling: None,
            thermal_preset: None,
            wifi_lock: true,
            statusbar_lock: true,
            touch_boost: true,
            power_hal: true,
            silence_vibrator: false,
            freeze_rotation: false,
        }],
    }
}

#[test]
fn test_decide_enter_game() {
    let pkg = "com.miHoYo.GenshinImpact";
    let daemon = create_test_daemon(make_genshin_gamelist());
    let gamelist = make_genshin_gamelist();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg.to_string()),
        focused_pid: Some(5678),
        package_pids: vec![5678],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::EnterGame {
            pkg,
            pid: 5678,
            pids: &[5678],
        }
    );
}

#[tokio::test]
async fn test_decide_same_app_unwhitelisted_monitored() {
    let mut daemon = create_test_daemon(GameList::default());
    let gamelist = GameList::default();

    // First transition: entering an unwhitelisted app
    let enter_decision = PipelineDecision::NonWhitelisted {
        pkg: "com.android.chrome",
        pid: Some(999),
        pids: &[999],
        reason: "not whitelisted",
    };
    daemon.apply(enter_decision, &gamelist).await.unwrap();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some("com.android.chrome".to_string()),
        focused_pid: Some(999),
        package_pids: vec![999],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::SameAppMonitor {
            pkg: "com.android.chrome",
        }
    );
}

#[tokio::test]
async fn test_decide_same_app_whitelisted_fas() {
    let mut daemon = create_test_daemon(make_genshin_gamelist());
    let gamelist = make_genshin_gamelist();
    let pkg = "com.miHoYo.GenshinImpact";

    let enter_decision = PipelineDecision::EnterGame {
        pkg,
        pid: 5678,
        pids: &[5678],
    };
    daemon.apply(enter_decision, &gamelist).await.unwrap();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg.to_string()),
        focused_pid: Some(5678),
        package_pids: vec![5678],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::SameAppFas { pkg, pids: &[5678] }
    );
}

#[tokio::test]
async fn test_decide_same_app_unwhitelisted_monitor_when_fas_disabled() {
    let gamelist = GameList::default();
    let mut cfg = DaemonConfig {
        settings: Settings::default(),
        gamelist: GameList::default(),
        log_debounce_ms: 2000,
    };
    cfg.settings.fas.enabled = false;
    let (tx, _rx) = event::channel();
    let mut daemon = Daemon::new(cfg, Arc::new(Vec::new()), SystemStatusCache::new(), tx).unwrap();

    let enter_decision = PipelineDecision::NonWhitelisted {
        pkg: "com.android.chrome",
        pid: Some(999),
        pids: &[999],
        reason: "not whitelisted",
    };
    daemon.apply(enter_decision, &gamelist).await.unwrap();

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some("com.android.chrome".to_string()),
        focused_pid: Some(999),
        package_pids: vec![999],
        is_pid_valid: true,
        is_floating: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::SameAppMonitor {
            pkg: "com.android.chrome",
        }
    );
}
