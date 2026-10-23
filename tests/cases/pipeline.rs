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
    let gamelist = GameList::default();
    let daemon = create_test_daemon(gamelist.clone());

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: false,
            battery_saver: false,
        },
        foreground_pkg: Some("com.example.game".to_string()),
        focused_pid: Some(1234),
        is_pid_valid: true,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::Powersave
    );
}

#[test]
fn test_decide_powersave_when_battery_saver() {
    let gamelist = GameList::default();
    let daemon = create_test_daemon(gamelist.clone());

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: true,
        },
        foreground_pkg: Some("com.example.game".to_string()),
        focused_pid: Some(1234),
        is_pid_valid: true,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::Powersave
    );
}

#[test]
fn test_decide_no_foreground() {
    let gamelist = GameList::default();
    let daemon = create_test_daemon(gamelist.clone());

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: None,
        focused_pid: None,
        is_pid_valid: false,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::NoForeground
    );
}

#[test]
fn test_decide_non_whitelisted() {
    let gamelist = GameList::default();
    let daemon = create_test_daemon(gamelist.clone());

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some("com.android.chrome".to_string()),
        focused_pid: Some(999),
        is_pid_valid: true,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::NonWhitelisted {
            pkg: "com.android.chrome".to_string(),
            reason: "not whitelisted",
        }
    );
}

#[test]
fn test_decide_enter_game() {
    let pkg = "com.miHoYo.GenshinImpact";
    let gamelist = GameList {
        game: vec![GameProfile {
            package: pkg.to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: true,
            target_fps: None,
            refresh_rate: None,
            mode: Some("performance".to_string()),
            ceiling: None,
            thermal_preset: None,
        }],
    };
    let daemon = create_test_daemon(gamelist.clone());

    let input = PipelineInput {
        power: PowerSnapshot {
            screen_awake: true,
            battery_saver: false,
        },
        foreground_pkg: Some(pkg.to_string()),
        focused_pid: Some(5678),
        is_pid_valid: true,
    };

    assert_eq!(
        daemon.decide(&input, &gamelist),
        PipelineDecision::EnterGame {
            pkg: pkg.to_string(),
            pid: 5678,
        }
    );
}
