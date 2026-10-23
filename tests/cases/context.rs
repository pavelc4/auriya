use auriya::core::config::gamelist::GameList;
use auriya::daemon::context::DaemonContext;
use auriya::daemon::ipc::types::LogLevelCmd;
use auriya::daemon::state::CurrentState;
use auriya::platform::display::DisplayMode;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, RwLock};

fn create_test_context() -> DaemonContext {
    DaemonContext {
        enabled: Arc::new(AtomicBool::new(true)),
        shared_config: Arc::new(RwLock::new(Arc::new(GameList::default()))),
        override_foreground: Arc::new(RwLock::new(None)),
        current_state: Arc::new(RwLock::new(CurrentState::default())),
        balance_governor: Arc::new(RwLock::new("schedutil".to_string())),
        dnd_default: false,
        current_log_level: Arc::new(RwLock::new(LogLevelCmd::Info)),
        supported_modes: Arc::new(vec![
            DisplayMode {
                id: 1,
                width: 1080,
                height: 2400,
                fps: 60.0,
            },
            DisplayMode {
                id: 2,
                width: 1080,
                height: 2400,
                fps: 120.0,
            },
            DisplayMode {
                id: 3,
                width: 1080,
                height: 2400,
                fps: 60.0,
            },
        ]),
        profile_lock: Arc::new(Mutex::new(())),
        fas_controller: None,
        reload_handle: None,
    }
}

#[test]
fn test_daemon_context_get_pid() {
    let ctx = create_test_context();
    assert_eq!(ctx.get_pid(), "PKG=None PID=None\n");

    {
        let mut st = ctx.current_state.write().unwrap();
        st.pkg = Some("com.example.game".to_string());
        st.pid = Some(4321);
    }
    assert_eq!(ctx.get_pid(), "PKG=com.example.game PID=4321\n");
}

#[test]
fn test_daemon_context_status() {
    let ctx = create_test_context();
    let status = ctx.status();
    assert!(status.contains("ENABLED=true"));
    assert!(status.contains("PACKAGES=0"));
    assert!(status.contains("OVERRIDE=None"));
    assert!(status.contains("LOG_LEVEL=Info"));
}

#[test]
fn test_daemon_context_supported_rates() {
    let ctx = create_test_context();
    let rates_json = ctx.get_supported_rates();
    assert_eq!(rates_json.trim(), "[60,120]");
}

#[tokio::test]
async fn test_daemon_context_fps_default() {
    let ctx = create_test_context();
    assert_eq!(ctx.get_fps().await, 60);

    let fps_resp = ctx.get_fps_response().await;
    assert_eq!(fps_resp, "FPS=0 TARGET=60\n");
}

#[tokio::test]
async fn test_daemon_context_build_stats_json() {
    let ctx = create_test_context();
    let json = ctx.build_stats_json().await;
    assert!(json.contains('{'));
    assert!(json.ends_with('\n'));
}
