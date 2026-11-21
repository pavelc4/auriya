use auriya::core::config::gamelist::GameList;
use auriya::daemon::context::DaemonContext;
use auriya::daemon::ipc::handle_client;
use auriya::daemon::ipc::types::LogLevelCmd;
use auriya::daemon::state::CurrentState;
use auriya::platform::display::DisplayMode;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, Instant};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{UnixListener, UnixStream};

fn create_fuzz_context() -> Arc<DaemonContext> {
    Arc::new(DaemonContext {
        enabled: Arc::new(AtomicBool::new(true)),
        shared_config: Arc::new(RwLock::new(Arc::new(GameList::default()))),
        override_foreground: Arc::new(RwLock::new(None)),
        current_state: Arc::new(RwLock::new(CurrentState::default())),
        balance_governor: Arc::new(RwLock::new("schedutil".to_string())),
        active_mode: Arc::new(RwLock::new(auriya::core::profile::ProfileMode::Balance)),
        dnd_default: false,
        current_log_level: Arc::new(RwLock::new(LogLevelCmd::Info)),
        supported_modes: Arc::new(vec![DisplayMode {
            id: 1,
            width: 1080,
            height: 2400,
            fps: 60.0,
        }]),
        profile_lock: Arc::new(Mutex::new(())),
        fas_controller: None,
        reload_handle: None,
        start_time: Instant::now(),
    })
}

#[tokio::test]
async fn test_ipc_protocol_fuzzing_and_dos_protection() {
    let ctx = create_fuzz_context();
    let temp_dir = std::env::temp_dir();
    let socket_path = temp_dir.join(format!("auriya_fuzz_sock_{}.sock", std::process::id()));
    let _ = std::fs::remove_file(&socket_path);

    let listener = UnixListener::bind(&socket_path).unwrap();
    let server_running = Arc::new(AtomicBool::new(true));

    let s_running = Arc::clone(&server_running);
    let s_ctx = Arc::clone(&ctx);
    let server_handle = tokio::spawn(async move {
        while s_running.load(std::sync::atomic::Ordering::Acquire) {
            tokio::select! {
                Ok((stream, _)) = listener.accept() => {
                    let c_ctx = Arc::clone(&s_ctx);
                    tokio::spawn(async move {
                        let _ = handle_client(stream, c_ctx).await;
                    });
                }
                _ = tokio::time::sleep(Duration::from_millis(5)) => {}
            }
        }
    });

    tokio::time::sleep(Duration::from_millis(20)).await;

    // Fuzz Case 1: Oversized payload (> 256 bytes)
    {
        let stream = UnixStream::connect(&socket_path).await.unwrap();
        let (r, mut w) = stream.into_split();
        let mut reader = BufReader::new(r);
        let mut line = String::new();
        reader.read_line(&mut line).await.unwrap(); // greeting

        // Send 1,000 'A' characters
        let huge_payload = format!("{}\n", "A".repeat(1000));
        w.write_all(huge_payload.as_bytes()).await.unwrap();

        line.clear();
        reader.read_line(&mut line).await.unwrap();
        assert_eq!(line.trim(), "ERR input too long");
    }

    // Fuzz Case 2: Unknown / Malformed commands
    let malformed_commands = [
        "MALFORMED_CMD_FOOBAR\n",
        "SET_PROFILE invalid_profile_name\n",
        "SET_FPS not_a_number\n",
        "STREAM_STATS foo extra_arg\n",
        "ADD_GAME\n",
        "REMOVE_GAME\n",
        "; rm -rf /; DROP TABLE;\n",
    ];

    for cmd in malformed_commands {
        let stream = UnixStream::connect(&socket_path).await.unwrap();
        let (r, mut w) = stream.into_split();
        let mut reader = BufReader::new(r);
        let mut line = String::new();
        reader.read_line(&mut line).await.unwrap(); // greeting

        w.write_all(cmd.as_bytes()).await.unwrap();
        line.clear();
        reader.read_line(&mut line).await.unwrap();
        assert!(
            line.starts_with("ERR"),
            "Malformed command '{cmd}' must return ERR response"
        );
    }

    // Fuzz Case 3: Empty lines and whitespace bursts
    {
        let stream = UnixStream::connect(&socket_path).await.unwrap();
        let (r, mut w) = stream.into_split();
        let mut reader = BufReader::new(r);
        let mut line = String::new();
        reader.read_line(&mut line).await.unwrap(); // greeting

        w.write_all(b"   \t  \n\n\n  \n").await.unwrap();
        w.write_all(b"PING\n").await.unwrap();

        line.clear();
        // Server should process empty lines as errors then successfully reply to PING
        while reader.read_line(&mut line).await.unwrap() > 0 {
            if line.trim() == "PONG" {
                break;
            }
            line.clear();
        }
        assert_eq!(line.trim(), "PONG");
    }

    server_running.store(false, std::sync::atomic::Ordering::Release);
    server_handle.abort();
    let _ = std::fs::remove_file(&socket_path);
}

#[test]
fn test_config_fuzz_validation() {
    // Malformed TOML input with invalid field types
    let fuzz_toml = r#"
[[game]]
package = 123456
target_fps = "not_an_array_or_number"
enable_dnd = "invalid_bool"
"#;
    let res = toml::from_str::<GameList>(fuzz_toml);
    assert!(res.is_err(), "Invalid types in gamelist must be rejected");

    // Extremely large number for target_fps
    let huge_num_toml = r#"
[[game]]
package = "com.test.app"
target_fps = 99999999999999999999999999999999999999999
"#;
    assert!(toml::from_str::<GameList>(huge_num_toml).is_err());
}
