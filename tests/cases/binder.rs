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
}
