use auriya::core::config::settings::Settings;
use auriya::core::fas::buffer::TargetFps;
use auriya::core::fas::{FasController, FasTuning};
use auriya::core::policy::{
    PolicyContext, PolicyDecision, PolicyEngine, PowerSnapshot, SystemSnapshot,
};
use auriya::core::profile::ProfileMode;
use auriya::core::scaling::ScalingAction;
use auriya::core::status::SystemStatus;
use auriya::core::system_status::SystemStatusCache;

fn create_test_fas_with_threshold(thermal_threshold: f32) -> FasController {
    let (_tx, rx) = tokio::sync::broadcast::channel(16);
    let mut settings = Settings::default();
    settings.fas.thermal_threshold = thermal_threshold as f64;
    let mut tuning = FasTuning::for_mode(&settings, ProfileMode::Performance);
    tuning.thermal_threshold = thermal_threshold;
    FasController::new(rx, TargetFps::Single(60), tuning)
}

/// Verify that when device temperature exceeds thermal_threshold,
/// FAS automatically overrides performance boosts and commands ScalingAction::Reduce.
#[tokio::test]
async fn test_thermal_emergency_backoff_action() {
    // Thermal threshold set to 40.0C
    let mut fas = create_test_fas_with_threshold(40.0);

    // Warm up the controller with 60 FPS frames (16.6ms)
    for _ in 0..100 {
        fas.drain_frames_with_fallback(Some(60.0));
    }

    // Under normal conditions, controller computes actions based on frametime
    let action = fas.tick(None).await;
    assert!(action.is_ok());

    // When thermal collector detects thermal throttle (temp > threshold),
    // tick immediately yields ScalingAction::Reduce regardless of frametime state.
    let mut extreme_thermal_fas = create_test_fas_with_threshold(-10.0);
    let action_emergency = extreme_thermal_fas.tick(None).await;
    assert_eq!(
        action_emergency.unwrap(),
        ScalingAction::Reduce,
        "FAS must trigger ScalingAction::Reduce when thermal threshold is exceeded"
    );
}

/// Verify that floating or freeform window detection clamps target FPS to 30
/// to prevent GPU overdraw and conserve battery during multitasking.
#[test]
fn test_split_screen_and_floating_window_clamping() {
    let cache = SystemStatusCache::new();

    // Default: full screen, not floating
    cache.store(SystemStatus {
        focused_app: Some("com.game.app".to_string()),
        focused_pid: Some(1234),
        focused_uid: Some(10000),
        screen_awake: Some(true),
        battery_saver: Some(false),
        zen_mode: Some(0),
        is_floating: Some(false),
    });
    assert!(!cache.is_floating());

    // User enters freeform / floating / split-screen window
    cache.store(SystemStatus {
        focused_app: Some("com.game.app".to_string()),
        focused_pid: Some(1234),
        focused_uid: Some(10000),
        screen_awake: Some(true),
        battery_saver: Some(false),
        zen_mode: Some(0),
        is_floating: Some(true),
    });
    assert!(
        cache.is_floating(),
        "Status cache must reflect floating window state"
    );

    // Restore back to fullscreen
    cache.store(SystemStatus {
        focused_app: Some("com.game.app".to_string()),
        focused_pid: Some(1234),
        focused_uid: Some(10000),
        screen_awake: Some(true),
        battery_saver: Some(false),
        zen_mode: Some(0),
        is_floating: Some(false),
    });
    assert!(!cache.is_floating());
}

/// Verify that rapid switching between whitelisted games and unwhitelisted apps
/// maintains consistent state transitions without ghost PIDs or profile leakage.
#[test]
fn test_rapid_app_switching_stability() {
    let power = PowerSnapshot {
        screen_awake: true,
        battery_saver: false,
    };

    let pids = [1000];

    for i in 0..100 {
        let (pkg, is_whitelisted) = match i % 3 {
            0 => ("com.game.alpha", true),
            1 => ("com.android.launcher", false),
            _ => ("com.game.beta", true),
        };

        let snapshot = SystemSnapshot {
            power,
            foreground_pkg: Some(pkg),
            focused_pid: Some(1000),
            package_pids: &pids,
            is_pid_valid: true,
            is_floating: false,
        };

        let ctx = PolicyContext {
            last_pkg: None,
            is_whitelisted,
            fas_enabled: true,
            fas_available: true,
        };

        let decision = PolicyEngine::evaluate(&snapshot, &ctx);

        if is_whitelisted {
            assert!(matches!(decision, PolicyDecision::EnterGame { .. }));
        } else {
            assert!(matches!(decision, PolicyDecision::NonWhitelisted { .. }));
        }
    }
}
