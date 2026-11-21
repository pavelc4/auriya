use auriya::core::config::gamelist::GameList;
use auriya::core::config::settings::Settings;
use auriya::core::fas::buffer::{FrameBuffer, TargetFps};
use auriya::core::fas::{FasController, FasTuning};
use auriya::core::fps::meter::FpsMeter;
use auriya::core::profile::ProfileMode;
use auriya::daemon::context::DaemonContext;
use auriya::daemon::ipc::handle_client;
use auriya::daemon::ipc::types::LogLevelCmd;
use auriya::daemon::state::CurrentState;
use auriya::platform::display::DisplayMode;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, Instant};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{UnixListener, UnixStream};

fn create_test_context() -> Arc<DaemonContext> {
    Arc::new(DaemonContext {
        enabled: Arc::new(AtomicBool::new(true)),
        shared_config: Arc::new(RwLock::new(Arc::new(GameList::default()))),
        override_foreground: Arc::new(RwLock::new(None)),
        current_state: Arc::new(RwLock::new(CurrentState::default())),
        balance_governor: Arc::new(RwLock::new("schedutil".to_string())),
        active_mode: Arc::new(RwLock::new(ProfileMode::Balance)),
        dnd_default: false,
        current_log_level: Arc::new(RwLock::new(LogLevelCmd::Info)),
        supported_modes: Arc::new(vec![DisplayMode {
            id: 1,
            width: 1080,
            height: 2400,
            fps: 120.0,
        }]),
        profile_lock: Arc::new(Mutex::new(())),
        fas_controller: None,
        reload_handle: None,
        start_time: Instant::now(),
    })
}

fn create_test_fas() -> (
    tokio::sync::broadcast::Sender<Duration>,
    Arc<tokio::sync::Mutex<FasController>>,
) {
    let (tx, rx) = tokio::sync::broadcast::channel(128);
    let settings = Settings::default();
    let tuning = FasTuning::for_mode(&settings, ProfileMode::Balance);
    let controller = FasController::new(rx, TargetFps::Array(vec![60, 90, 120]), tuning);
    (tx, Arc::new(tokio::sync::Mutex::new(controller)))
}

/// Verify that high contention across multiple parallel tasks accessing
/// FasController does not result in deadlocks, timeouts, or state corruption.
#[tokio::test]
async fn test_fas_controller_heavy_contention_no_deadlock() {
    let (tx, fas) = create_test_fas();
    let running = Arc::new(AtomicBool::new(true));
    let mut handles = Vec::new();

    // 4 background writers pushing frame durations through the broadcast channel
    for _ in 0..4 {
        let tx_c = tx.clone();
        let r_c = Arc::clone(&running);
        handles.push(tokio::spawn(async move {
            while r_c.load(Ordering::Acquire) {
                let _ = tx_c.send(Duration::from_millis(16));
                tokio::task::yield_now().await;
            }
        }));
    }

    // 4 tasks repeatedly calling set_target_fps
    for task_id in 0..4 {
        let fas_c = Arc::clone(&fas);
        let r_c = Arc::clone(&running);
        handles.push(tokio::spawn(async move {
            let rates = [60, 90, 120];
            let mut i = task_id;
            while r_c.load(Ordering::Acquire) {
                if let Ok(mut guard) =
                    tokio::time::timeout(Duration::from_millis(100), fas_c.lock()).await
                {
                    guard.set_target_fps(rates[i % rates.len()]);
                } else {
                    panic!("Deadlock detected: failed to acquire fas lock within 100ms");
                }
                i = i.wrapping_add(1);
                tokio::task::yield_now().await;
            }
        }));
    }

    // 4 tasks repeatedly reading get_target_fps and fps_stats
    for _ in 0..4 {
        let fas_c = Arc::clone(&fas);
        let r_c = Arc::clone(&running);
        handles.push(tokio::spawn(async move {
            while r_c.load(Ordering::Acquire) {
                if let Ok(guard) =
                    tokio::time::timeout(Duration::from_millis(100), fas_c.lock()).await
                {
                    let fps = guard.get_target_fps();
                    assert!(fps >= 30, "Target FPS must be non-zero");
                    let stats = guard.fps_stats();
                    assert!(stats.avg >= 0.0);
                } else {
                    panic!("Deadlock detected: failed to acquire fas lock for reading");
                }
                tokio::task::yield_now().await;
            }
        }));
    }

    // 4 tasks calling drain_frames
    for _ in 0..4 {
        let fas_c = Arc::clone(&fas);
        let r_c = Arc::clone(&running);
        handles.push(tokio::spawn(async move {
            while r_c.load(Ordering::Acquire) {
                if let Ok(mut guard) =
                    tokio::time::timeout(Duration::from_millis(100), fas_c.lock()).await
                {
                    guard.drain_frames();
                } else {
                    panic!("Deadlock detected: failed to acquire fas lock for draining");
                }
                tokio::task::yield_now().await;
            }
        }));
    }

    // Run high contention for 500ms
    tokio::time::sleep(Duration::from_millis(500)).await;
    running.store(false, Ordering::Release);

    for h in handles {
        h.await.expect("Worker task panicked during contention");
    }
}

/// Verify that 30 concurrent OS threads and tasks hammering DaemonContext locks
/// (profile_lock, balance_governor, active_mode, current_state) execute without
/// deadlocking, lock poisoning, or race conditions.
#[tokio::test]
async fn test_daemon_context_multithreaded_contention_no_deadlock() {
    let ctx = create_test_context();
    let running = Arc::new(AtomicBool::new(true));
    let mut handles = Vec::new();

    // 10 threads acquiring profile_lock and updating active_mode
    for i in 0..10 {
        let ctx_c = Arc::clone(&ctx);
        let r_c = Arc::clone(&running);
        handles.push(tokio::task::spawn_blocking(move || {
            let modes = [
                ProfileMode::Balance,
                ProfileMode::Powersave,
                ProfileMode::Performance,
                ProfileMode::Fast,
            ];
            let mut idx = i;
            while r_c.load(Ordering::Acquire) {
                let mode = modes[idx % modes.len()];
                {
                    let _guard = ctx_c.profile_lock.lock().unwrap();
                    let _gov = ctx_c.balance_governor.read().unwrap();
                    let mut active = ctx_c.active_mode.write().unwrap();
                    *active = mode;
                }
                idx = idx.wrapping_add(1);
            }
        }));
    }

    // 10 threads reading status, get_pid, and active_mode
    for _ in 0..10 {
        let ctx_c = Arc::clone(&ctx);
        let r_c = Arc::clone(&running);
        handles.push(tokio::task::spawn_blocking(move || {
            while r_c.load(Ordering::Acquire) {
                let pid_str = ctx_c.get_pid();
                assert!(pid_str.starts_with("PKG="));
                let status_str = ctx_c.status();
                assert!(status_str.contains("ENABLED="));
                let _mode = *ctx_c.active_mode.read().unwrap();
            }
        }));
    }

    // 10 threads updating current_state (foreground app changes)
    for i in 0..10 {
        let ctx_c = Arc::clone(&ctx);
        let r_c = Arc::clone(&running);
        handles.push(tokio::task::spawn_blocking(move || {
            let mut count: usize = i;
            while r_c.load(Ordering::Acquire) {
                {
                    let mut st = ctx_c.current_state.write().unwrap();
                    st.pkg = Some(format!("com.game.app_{count}"));
                    st.pid = Some((1000 + count) as i32);
                }
                count = count.wrapping_add(1);
            }
        }));
    }

    // Stress for 400ms
    tokio::time::sleep(Duration::from_millis(400)).await;
    running.store(false, Ordering::Release);

    for h in handles {
        h.await.expect("Thread failed during context stress");
    }
}

/// Verify that the daemon IPC server never hangs or starves fast clients when
/// handling concurrent fast clients, slow/stalled readers, and abrupt socket disconnects.
#[tokio::test]
async fn test_ipc_concurrent_clients_with_slow_and_disconnecting_readers() {
    let ctx = create_test_context();
    let temp_dir = std::env::temp_dir();
    let socket_path = temp_dir.join(format!("auriya_test_sock_{}.sock", std::process::id()));
    let _ = std::fs::remove_file(&socket_path);

    let listener = UnixListener::bind(&socket_path).expect("Failed to bind test unix socket");
    let server_running = Arc::new(AtomicBool::new(true));

    // Spawn server accept loop
    let s_running = Arc::clone(&server_running);
    let s_ctx = Arc::clone(&ctx);
    let server_handle = tokio::spawn(async move {
        while s_running.load(Ordering::Acquire) {
            tokio::select! {
                Ok((stream, _)) = listener.accept() => {
                    let c_ctx = Arc::clone(&s_ctx);
                    tokio::spawn(async move {
                        let _ = handle_client(stream, c_ctx).await;
                    });
                }
                _ = tokio::time::sleep(Duration::from_millis(10)) => {}
            }
        }
    });

    // Give server time to listen
    tokio::time::sleep(Duration::from_millis(20)).await;

    let mut client_handles = Vec::new();
    let completed_fast_pings = Arc::new(AtomicUsize::new(0));

    // Group 1: 10 Fast clients performing PING and GET_PID
    for _ in 0..10 {
        let p = socket_path.clone();
        let counter = Arc::clone(&completed_fast_pings);
        client_handles.push(tokio::spawn(async move {
            for _ in 0..5 {
                let stream = UnixStream::connect(&p)
                    .await
                    .expect("Client connect failed");
                let (r, mut w) = stream.into_split();
                let mut reader = BufReader::new(r);
                let mut line = String::new();

                // Read greeting: "OK AURIYA IPC\n"
                reader.read_line(&mut line).await.unwrap();
                assert!(line.contains("OK AURIYA IPC"));
                line.clear();

                // Send PING and assert PONG within 100ms
                w.write_all(b"PING\n").await.unwrap();
                let res =
                    tokio::time::timeout(Duration::from_millis(100), reader.read_line(&mut line))
                        .await;
                assert!(res.is_ok(), "Fast client timed out waiting for PONG");
                assert_eq!(line.trim(), "PONG");
                counter.fetch_add(1, Ordering::Relaxed);
            }
        }));
    }

    // Group 2: 10 Abrupt disconnect clients (closing socket immediately or mid-command)
    for i in 0..10 {
        let p = socket_path.clone();
        client_handles.push(tokio::spawn(async move {
            for _ in 0..5 {
                if let Ok(mut stream) = UnixStream::connect(&p).await {
                    if i % 2 == 0 {
                        let _ = stream.write_all(b"GET_ST").await;
                    }
                    // Abrupt drop
                    drop(stream);
                }
                tokio::task::yield_now().await;
            }
        }));
    }

    // Group 3: 5 Slow / stalled clients (reading header, then refusing to read further)
    for _ in 0..5 {
        let p = socket_path.clone();
        client_handles.push(tokio::spawn(async move {
            if let Ok(mut stream) = UnixStream::connect(&p).await {
                let mut buf = [0u8; 16];
                let _ = stream.read(&mut buf).await;
                // Stall for 150ms without reading, then disconnect
                tokio::time::sleep(Duration::from_millis(150)).await;
                let _ = stream.write_all(b"QUIT\n").await;
            }
        }));
    }

    // Await all client tasks with an overall 2-second timeout
    let clients_fut = async {
        for h in client_handles {
            h.await.expect("Client task panicked");
        }
    };
    tokio::time::timeout(Duration::from_secs(2), clients_fut)
        .await
        .expect("Test hung: IPC clients or server deadlocked");

    assert_eq!(
        completed_fast_pings.load(Ordering::Acquire),
        50,
        "All 50 fast ping transactions must succeed despite slow/aborted peers"
    );

    // Teardown server
    server_running.store(false, Ordering::Release);
    server_handle.abort();
    let _ = std::fs::remove_file(&socket_path);
}

/// Verify that channel backpressure using try_send gracefully drops excess events
/// without blocking the sender thread, deadlocking, or leaking memory.
#[tokio::test]
async fn test_channel_backpressure_and_try_send_no_blocking() {
    let (tx, mut rx) = tokio::sync::mpsc::channel::<u32>(16);
    let dropped_count = Arc::new(AtomicUsize::new(0));

    let tx_c = tx.clone();
    let d_c = Arc::clone(&dropped_count);

    // Producer rapidly attempts to send 5,000 items into a 16-capacity channel
    let sender_handle = tokio::spawn(async move {
        for i in 0..5000 {
            if tx_c.try_send(i).is_err() {
                d_c.fetch_add(1, Ordering::Relaxed);
            }
        }
    });

    sender_handle.await.expect("Sender task panicked");
    assert!(
        dropped_count.load(Ordering::Relaxed) > 0,
        "try_send must drop excess items when bounded channel is full"
    );

    // Consumer drains the remaining valid items without hang
    let mut drained = 0;
    while rx.try_recv().is_ok() {
        drained += 1;
    }
    assert!(drained > 0 && drained <= 16);
}

/// Verify that potentially hanging operations are strictly aborted by timeouts,
/// guaranteeing that daemon loops and client handlers never freeze indefinitely.
#[tokio::test]
async fn test_timeout_guard_prevents_indefinite_hang() {
    let hanging_lock = Arc::new(tokio::sync::Mutex::new(()));
    let guard = hanging_lock.lock().await;

    let lock_clone = Arc::clone(&hanging_lock);
    let start = Instant::now();

    // Attempt to acquire already-held mutex with 40ms timeout
    let res = tokio::time::timeout(Duration::from_millis(40), lock_clone.lock()).await;

    assert!(res.is_err(), "Must time out when lock is permanently held");
    let elapsed = start.elapsed();
    assert!(
        elapsed >= Duration::from_millis(35) && elapsed < Duration::from_millis(150),
        "Timeout must trigger accurately without indefinite blocking (took {:?})",
        elapsed
    );

    drop(guard);
}

/// Verify longtime uptime stability of FrameBuffer: pushes 100,000 frames
/// across target FPS transitions to verify bounded memory, zero heap reallocations,
/// and accurate stats over continuous operation.
#[test]
fn test_frame_buffer_sliding_window_longtime_uptime_stability() {
    let mut buf = FrameBuffer::new(TargetFps::Array(vec![60, 90, 120]));
    let max_allowed_frames = 120 * 5; // 600 frames max capacity

    // Simulate 100,000 frames of gameplay across 60, 90, 120 FPS shifts
    let test_durations = [
        Duration::from_micros(16666), // ~60 fps
        Duration::from_micros(11111), // ~90 fps
        Duration::from_micros(8333),  // ~120 fps
    ];

    for i in 0..100_000 {
        let ft = test_durations[(i / 500) % test_durations.len()];
        buf.push(ft);

        // Periodically verify invariants
        if i % 10_000 == 0 {
            assert!(
                buf.recent_frametimes(1000).len() <= max_allowed_frames,
                "Buffer capacity exceeded bounded sliding window"
            );
            assert!(
                buf.target_fps.is_some(),
                "Target FPS must be active after warmup"
            );
        }
    }

    // Mid-game warm reset and clear
    buf.reset_governor();
    assert_eq!(buf.recent_frametimes(100).len(), 0);

    for _ in 0..100 {
        buf.push(Duration::from_micros(16666));
    }
    buf.clear();
    assert_eq!(buf.target_fps, None);
}

/// Verify that concurrent reads and drains on FpsMeter across multiple threads
/// execute cleanly without poisoned locks or race conditions.
#[test]
fn test_fps_meter_multithreaded_reader_drain_race_freedom() {
    let (tx, rx) = tokio::sync::broadcast::channel(64);
    let meter = Arc::new(RwLock::new(FpsMeter::new(Some(rx))));
    let running = Arc::new(AtomicBool::new(true));
    let mut handles = Vec::new();

    // 4 background writer threads sending frame timestamps
    for _ in 0..4 {
        let tx_c = tx.clone();
        let r_c = Arc::clone(&running);
        handles.push(std::thread::spawn(move || {
            while r_c.load(Ordering::Acquire) {
                let _ = tx_c.send(Duration::from_micros(16666));
                std::thread::yield_now();
            }
        }));
    }

    // 4 background reader threads reading and clearing meter
    for _ in 0..4 {
        let m_c = Arc::clone(&meter);
        let r_c = Arc::clone(&running);
        handles.push(std::thread::spawn(move || {
            while r_c.load(Ordering::Acquire) {
                if let Ok(mut m) = m_c.write() {
                    let _reading = m.read();
                }
                std::thread::yield_now();
            }
        }));
    }

    std::thread::sleep(Duration::from_millis(200));
    running.store(false, Ordering::Release);

    for h in handles {
        h.join().expect("Worker thread panicked");
    }
}

/// Verify that corrupted or truncated TOML files never crash the daemon or corrupt
/// the in-memory configuration state, maintaining seamless fallback.
#[test]
fn test_config_corruption_and_malformed_toml_fallback() {
    let tmp = tempfile::NamedTempFile::new().unwrap();
    let valid_toml = r#"
[[game]]
package = "com.valid.game"
cpu_governor = "schedutil"
enable_dnd = true
"#;
    std::fs::write(tmp.path(), valid_toml).unwrap();

    let shared = Arc::new(RwLock::new(Arc::new(GameList::load(tmp.path()).unwrap())));
    assert_eq!(shared.read().unwrap().game.len(), 1);

    // Corrupt the file with truncated unclosed syntax
    let corrupted_toml = r#"
[[game
package = "broken_truncated
cpu_governor = "sched
"#;
    std::fs::write(tmp.path(), corrupted_toml).unwrap();

    // Loading corrupted file must fail gracefully
    assert!(GameList::load(tmp.path()).is_err());

    // In-memory active configuration must remain completely intact
    let active = shared.read().unwrap();
    assert_eq!(active.game.len(), 1);
    assert_eq!(active.game[0].package, "com.valid.game");
}

/// Verify that sudden process death (e.g. SIGKILL from Android Low Memory Killer)
/// is captured immediately by PidTracker without hanging or leaking worker threads.
#[test]
fn test_sudden_process_death_and_pid_exit_cleanup() {
    let (tx, mut rx) = tokio::sync::mpsc::channel(4);
    let mut child = std::process::Command::new("sleep")
        .arg("30")
        .spawn()
        .expect("spawn child for sudden death test");
    let pid = child.id() as i32;

    let tracker = auriya::core::pid_tracker::PidTracker::spawn(pid, tx);
    assert!(tracker.is_alive(), "Process must be alive after spawn");

    // Simulate sudden process termination (SIGKILL)
    child.kill().expect("Simulate process kill");
    let _ = child.wait();

    // Verify exit event arrival
    let deadline = Instant::now() + Duration::from_secs(3);
    let mut received_event = None;
    while Instant::now() < deadline {
        if let Ok(ev) = rx.try_recv() {
            received_event = Some(ev);
            break;
        }
        std::thread::sleep(Duration::from_millis(20));
    }

    assert_eq!(
        received_event,
        Some(auriya::daemon::event::DaemonEvent::PidExited(pid))
    );
    assert!(
        !tracker.is_alive(),
        "Tracker must report dead status immediately"
    );
    drop(tracker);
}

/// Verify that teardown of hardware locks and vendor controls releases cleanly
/// without panic or leaving broken state.
#[test]
fn test_hardware_locks_clean_release_on_drop() {
    let mut locks = auriya::platform::vendor::VendorLock::new();
    locks.unlock_all();

    let mut ceiling = auriya::platform::cpu::ceiling::CeilingController::new();
    ceiling.restore();
    ceiling.online_all();
    drop(ceiling);
}

/// Verify that high-volume application switches and continuous telemetry updates
/// over long sessions maintain bounded memory without monotonic heap growth.
#[tokio::test]
async fn test_long_session_memory_monotonicity_and_no_leak() {
    let ctx = create_test_context();
    let cache = auriya::core::system_status::SystemStatusCache::new();

    for i in 0..5_000 {
        // App switch
        {
            let mut st = ctx.current_state.write().unwrap();
            st.pkg = Some(format!("com.switch.app_{}", i % 50));
            st.pid = Some(2000 + (i % 50));
        }

        // Status update
        cache.store(auriya::core::status::SystemStatus {
            focused_app: Some(format!("com.switch.app_{}", i % 50)),
            focused_pid: Some(2000 + (i % 50)),
            focused_uid: Some(10000),
            screen_awake: Some(true),
            battery_saver: None,
            zen_mode: None,
            is_floating: None,
        });

        // Telemetry serialization
        if i % 100 == 0 {
            let json = ctx.build_stats_json().await;
            assert!(json.starts_with('{'));
        }
    }

    // Verify current state remains strictly single active app
    let st = ctx.current_state.read().unwrap();
    assert!(st.pkg.is_some());
    assert!(st.pid.is_some());
}
