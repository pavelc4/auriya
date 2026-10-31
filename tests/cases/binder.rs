//! Integration tests for Native Binder IPC module.

use auriya::platform::android_cmd::DndFilter;
use auriya::platform::binder::{
    BinderClient, DndMode, ProcessEventType, ProcessObserver, apply_dnd, apply_refresh_rate,
};
use tokio::sync::mpsc;

#[test]
fn test_dnd_mode_conversions() {
    assert_eq!(DndMode::from(DndFilter::All), DndMode::All);
    assert_eq!(DndMode::from(DndFilter::Priority), DndMode::Priority);
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
