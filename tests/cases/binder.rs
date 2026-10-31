//! Integration tests for Native Binder IPC module.

use auriya::platform::binder::{
    BinderClient, DndMode, ProcessEventType, ProcessObserver, apply_dnd, apply_refresh_rate,
};
use tokio::sync::mpsc;

#[test]
fn test_dnd_modes() {
    assert_eq!(DndMode::All as i32, 1);
    assert_eq!(DndMode::Priority as i32, 2);
}

#[test]
fn test_binder_client_lifecycle() {
    let client = BinderClient::new();
    let _ = client.is_operational();
    assert!(client.is_screen_interactive().is_ok());
    assert!(client.get_foreground_app().is_ok());
}

#[test]
fn test_actuators_execution() {
    assert!(apply_dnd(DndMode::Priority).is_ok());
    assert!(apply_dnd(DndMode::All).is_ok());

    assert!(apply_refresh_rate(90).is_ok());
    assert!(apply_refresh_rate(120).is_ok());
    assert!(apply_refresh_rate(0).is_ok());
}

#[test]
fn test_core_profile_dnd_integration() {
    auriya::core::profile::request_dnd(auriya::core::profile::DndFilter::Priority);
    auriya::core::profile::request_dnd(auriya::core::profile::DndFilter::All);
}

#[tokio::test]
async fn test_observer_event_delivery_and_filtering() {
    let (tx, mut rx) = mpsc::channel(32);
    let observer = ProcessObserver::new(tx);

    observer.on_foreground_activities_changed(5432, 10120, true);
    let ev1 = rx.recv().await.expect("Expected event");
    assert_eq!(ev1.pid, 5432);
    assert_eq!(ev1.uid, 10120);
    assert!(ev1.foreground);
    assert_eq!(
        ev1.event_type,
        ProcessEventType::ForegroundActivitiesChanged
    );

    observer.on_foreground_activities_changed(5432, 10120, false);
    let ev2 = rx.recv().await.expect("Expected event");
    assert_eq!(ev2.pid, 5432);
    assert!(!ev2.foreground);

    observer.on_process_died(5432, 10120);
    let ev3 = rx.recv().await.expect("Expected event");
    assert_eq!(ev3.pid, 5432);
    assert_eq!(ev3.event_type, ProcessEventType::ProcessDied);
}

#[tokio::test]
async fn test_observer_saturation_resilience() {
    let (tx, mut rx) = mpsc::channel(2);
    let observer = ProcessObserver::new(tx);

    observer.on_foreground_activities_changed(1, 1001, true);
    observer.on_foreground_activities_changed(2, 1002, true);
    observer.on_foreground_activities_changed(3, 1003, true);

    let first = rx.recv().await.unwrap();
    let second = rx.recv().await.unwrap();
    assert_eq!(first.pid, 1);
    assert_eq!(second.pid, 2);
}

#[test]
fn test_get_process_package() {
    let my_pid = std::process::id() as i32;
    let pkg = auriya::platform::procfs::get_process_package(my_pid);
    assert!(pkg.is_some());
    assert!(!pkg.unwrap().is_empty());
    assert_eq!(auriya::platform::procfs::get_process_package(-1), None);
}

#[tokio::test]
async fn test_cli_binder_commands() {
    use auriya::cli::{BinderCommands, CliDndMode};

    assert_eq!(
        auriya::platform::binder::DndMode::from(CliDndMode::Priority),
        auriya::platform::binder::DndMode::Priority
    );
    assert_eq!(
        auriya::platform::binder::DndMode::from(CliDndMode::All),
        auriya::platform::binder::DndMode::All
    );

    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Probe)
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Dnd {
            mode: CliDndMode::Priority,
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Rate { hz: 90 })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Rate { hz: 0 })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Test)
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Wifi { action: None })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Touch { action: None })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Statusbar { action: None })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Power {
            action: "status".to_string(),
            name: None,
            value: None,
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Power {
            action: "mode".to_string(),
            name: Some("expensive".to_string()),
            value: Some("on".to_string()),
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Power {
            action: "boost".to_string(),
            name: Some("interaction".to_string()),
            value: Some("50".to_string()),
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Vibrator {
            action: Some("status".to_string()),
            duration_ms: None,
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Vibrator {
            action: Some("pulse".to_string()),
            duration_ms: Some(30),
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Window {
            action: "rotation".to_string(),
            param: Some("status".to_string()),
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Window {
            action: "anim".to_string(),
            param: Some("1.0".to_string()),
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::PowerSave { action: None })
            .await
            .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Toast {
            message: "Test Toast".to_string(),
            long: false,
        })
        .await
        .is_ok()
    );
    assert!(
        auriya::cli::commands::binder::handle_binder(BinderCommands::Broadcast {
            action: "dev.auriya.app.TEST".to_string(),
            extra: vec!["key=val".to_string()],
        })
        .await
        .is_ok()
    );
}

#[test]
fn test_power_hal_actuator() {
    use auriya::platform::binder::{
        PowerBoost, PowerMode, is_power_boost_supported, is_power_mode_supported, set_power_boost,
        set_power_mode,
    };

    assert!(is_power_mode_supported(PowerMode::ExpensiveRendering));
    assert!(is_power_mode_supported(PowerMode::SustainedPerformance));
    assert!(set_power_mode(PowerMode::ExpensiveRendering, true).is_ok());
    assert!(set_power_mode(PowerMode::ExpensiveRendering, false).is_ok());

    assert!(is_power_boost_supported(PowerBoost::Interaction));
    assert!(set_power_boost(PowerBoost::Interaction, 50).is_ok());
}

#[test]
fn test_vibrator_actuator() {
    use auriya::platform::binder::{get_vibrator_capabilities, pulse_vibrator, silence_vibrator};

    let caps = get_vibrator_capabilities();
    assert!(caps.is_ok());
    assert!(silence_vibrator().is_ok());
    assert!(pulse_vibrator(30).is_ok());
}

#[test]
fn test_window_actuator() {
    use auriya::platform::binder::{
        freeze_display_rotation, get_animation_scales, is_display_rotation_frozen,
        set_animation_scales, thaw_display_rotation,
    };

    assert!(freeze_display_rotation(0, -1).is_ok());
    assert!(thaw_display_rotation(0).is_ok());
    let frozen = is_display_rotation_frozen(0);
    assert!(frozen.is_ok());

    let scales = get_animation_scales();
    assert!(scales.is_ok());
    assert_eq!(scales.unwrap().len(), 3);
    assert!(set_animation_scales(0.0).is_ok());
    assert!(set_animation_scales(1.0).is_ok());
}

#[test]
fn test_power_save_actuator() {
    use auriya::platform::binder::{is_power_save_mode, set_power_save_mode_enabled};

    let saver = is_power_save_mode();
    assert!(saver.is_ok());
    assert!(set_power_save_mode_enabled(true).is_ok());
    assert!(set_power_save_mode_enabled(false).is_ok());
}

#[test]
fn test_statusbar_lock_actuator() {
    use auriya::platform::binder::{collapse_panels, is_statusbar_locked, set_statusbar_lock};

    assert!(collapse_panels().is_ok());
    assert!(!is_statusbar_locked());
    assert!(set_statusbar_lock(true).is_ok());
    assert!(set_statusbar_lock(false).is_ok());
}

#[tokio::test]
async fn test_binder_display_modes_query() {
    use auriya::platform::binder::query_binder_display_modes;
    use auriya::platform::display::get_app_supported_modes;

    let res = query_binder_display_modes(0);
    assert!(res.is_ok());

    let _modes = get_app_supported_modes().await;
}

#[test]
fn test_wifi_low_latency_actuator() {
    use auriya::platform::binder::{is_wifi_low_latency_active, set_wifi_low_latency_lock};

    assert!(!is_wifi_low_latency_active());
    assert!(set_wifi_low_latency_lock(true).is_ok());
    assert!(set_wifi_low_latency_lock(false).is_ok());
}

#[test]
fn test_touch_polling_rate_actuator() {
    use auriya::platform::binder::{
        is_high_touch_polling_rate_supported, set_high_touch_polling_rate,
    };

    let _supported = is_high_touch_polling_rate_supported();
    assert!(set_high_touch_polling_rate(true).is_ok());
    assert!(set_high_touch_polling_rate(false).is_ok());
}

#[test]
fn test_window_floating_detection() {
    use auriya::platform::binder::is_window_floating;

    // Fullscreen landscape window (0, 0, 2400, 1080)
    assert!(!is_window_floating(0, 0, 2400, 1080));
    // Fullscreen portrait window (0, 0, 1080, 2400)
    assert!(!is_window_floating(0, 0, 1080, 2400));
    // Floating/Freeform offset window
    assert!(is_window_floating(100, 100, 800, 600));
    // Small dimension window
    assert!(is_window_floating(0, 0, 500, 500));
}

#[tokio::test]
async fn test_observer_queue_overflow_nonblocking() {
    let (tx, mut rx) = mpsc::channel(1);
    let observer = ProcessObserver::new(tx);

    observer.on_foreground_activities_changed(100, 1000, true);
    // Overflow should not block or panic
    observer.on_foreground_activities_changed(101, 1001, true);

    let first = rx.recv().await.unwrap();
    assert_eq!(first.pid, 100);
}

#[test]
fn test_toast_actuator() {
    use auriya::platform::binder::{show_toast, show_toast_with_duration};

    let res = show_toast("Test message");
    eprintln!("show_toast result: {:?}", res);
    assert!(res.is_ok());
    assert!(show_toast_with_duration("Test long message", 1).is_ok());
}

#[test]
fn test_broadcast_actuator() {
    use auriya::platform::binder::broadcast_intent;

    let res = broadcast_intent(
        "dev.auriya.app.TEST_ACTION",
        &[("key", "val"), ("foo", "bar")],
    );
    eprintln!("broadcast_intent result: {:?}", res);
    assert!(res.is_ok());
    assert!(broadcast_intent("dev.auriya.app.TEST_ACTION_EMPTY", &[]).is_ok());
}
