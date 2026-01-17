use super::commands::{Command, ProfileMode};
use super::server::IpcHandles;
use anyhow::Result;
use std::sync::atomic::Ordering;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::UnixStream;
use tracing::{debug, error, info};

const HELP: &str = "CMDS:
        - HELP | ?
        - STATUS
        - ENABLE | DISABLE
        - RELOAD
        - SETLOG <DEBUG|INFO|WARN|ERROR>
        - INJECT <pkg>
        - CLEAR_INJECT
        - GETPID
        - PING
        - QUIT
        - SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE>
        - ADD_GAME <pkg>
        - REMOVE_GAME <pkg>
 ";

/// Handle a single IPC client connection.
pub async fn handle_client(stream: UnixStream, h: IpcHandles) -> Result<()> {
    let (r, mut w) = stream.into_split();
    let mut reader = BufReader::new(r);
    let mut line = String::new();
    w.write_all(b"OK AURIYA IPC\n").await?;

    while reader.read_line(&mut line).await? > 0 {
        let s = line.trim();
        if s.len() > 256 {
            w.write_all(b"ERR input too long\n").await?;
            line.clear();
            continue;
        }
        let resp = match s.parse::<Command>() {
            Ok(Command::Help) => HELP.to_string(),
            Ok(Command::Ping) => "PONG\n".into(),
            Ok(Command::Quit) => {
                w.write_all(b"BYE\n").await?;
                break;
            }
            Ok(Command::GetPid) => {
                let st = h
                    .current_state
                    .read()
                    .ok()
                    .map(|g| g.clone())
                    .unwrap_or_default();
                match (st.pkg, st.pid) {
                    (Some(p), Some(id)) => format!("PKG={} PID={}\n", p, id),
                    (Some(p), None) => format!("PKG={} PID=None\n", p),
                    _ => "PKG=None PID=None\n".into(),
                }
            }
            Ok(Command::Status) => {
                let enabled = h.enabled.load(Ordering::Acquire);
                let n = h
                    .shared_config
                    .read()
                    .ok()
                    .map(|c| c.game.len())
                    .unwrap_or(0);
                let ov = h.override_foreground.read().ok().and_then(|o| o.clone());
                let log_level = match h.current_log_level.read() {
                    Ok(l) => format!("{:?}", *l),
                    Err(_) => "Unknown".to_string(),
                };
                format!(
                    "ENABLED={} PACKAGES={} OVERRIDE={:?} LOG_LEVEL={}\n",
                    enabled, n, ov, log_level
                )
            }
            Ok(Command::Enable) => {
                h.enabled.store(true, Ordering::Release);
                "OK ENABLED\n".into()
            }
            Ok(Command::Disable) => {
                h.enabled.store(false, Ordering::Release);
                "OK DISABLED\n".into()
            }
            Ok(Command::Reload) => match (h.reload_fn)() {
                Ok(n) => format!("OK RELOADED {}\n", n),
                Err(e) => format!("ERR RELOAD {:?}\n", e),
            },
            Ok(Command::Restart) => {
                info!(target: "auriya::ipc", "Restart requested via IPC - initiating self-restart");

                let log_path = "/data/adb/auriya/daemon.log";
                let _ = std::fs::write(log_path, "");

                use std::os::unix::process::CommandExt;
                let mut cmd = std::process::Command::new("sh");
                cmd.arg("-c")
                    .arg("sleep 2 && sh /data/adb/modules/auriya/service.sh")
                    .stdin(std::process::Stdio::null())
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null());

                unsafe {
                    cmd.pre_exec(|| {
                        libc::setsid();
                        Ok(())
                    });
                }

                if cmd.spawn().is_ok() {
                    debug!(target: "auriya::ipc", "Restart spawned, daemon exiting");
                    std::thread::spawn(|| {
                        std::thread::sleep(std::time::Duration::from_millis(500));
                        std::process::exit(0);
                    });
                    return Ok(());
                }
                "ERR RESTART_FAILED\n".into()
            }
            Ok(Command::SetLog(lvl)) => {
                (h.set_log_level)(lvl);
                "OK SET_LOG\n".into()
            }
            Ok(Command::Inject(pkg)) => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = Some(pkg);
                }
                "OK INJECT\n".into()
            }
            Ok(Command::ClearInject) => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = None;
                }
                "OK CLEAR_INJECT\n".into()
            }
            Ok(Command::SetProfile(mode)) => {
                use crate::core::profile;
                let r = match mode {
                    ProfileMode::Performance => profile::apply_performance(),
                    ProfileMode::Balance => profile::apply_balance(&h.balance_governor),
                    ProfileMode::Powersave => profile::apply_powersave(),
                };
                match r {
                    Ok(_) => format!("OK SET_PROFILE {:?}\n", mode),
                    Err(e) => format!("ERR SET_PROFILE {:?}\n", e),
                }
            }
            Ok(Command::AddGame(pkg)) => {
                use crate::core::config::gamelist::GameProfile;
                if let Ok(mut gl) = h.shared_config.write() {
                    let profile = GameProfile {
                        package: pkg.clone(),
                        cpu_governor: "performance".to_string(),
                        enable_dnd: true,
                        target_fps: None,
                        refresh_rate: None,
                        mode: Some("performance".to_string()),
                    };
                    match gl.add(profile) {
                        Ok(_) => {
                            if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                                format!("ERR SAVE_GAMELIST {:?}\n", e)
                            } else {
                                format!("OK ADD_GAME {}\n", pkg)
                            }
                        }
                        Err(e) => format!("ERR ADD_GAME {:?}\n", e),
                    }
                } else {
                    "ERR lock poisoned\n".to_string()
                }
            }
            Ok(Command::RemoveGame(pkg)) => {
                if let Ok(mut gl) = h.shared_config.write() {
                    match gl.remove(&pkg) {
                        Ok(_) => {
                            if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                                format!("ERR SAVE_GAMELIST {:?}\n", e)
                            } else {
                                format!("OK REMOVE_GAME {}\n", pkg)
                            }
                        }
                        Err(e) => format!("ERR REMOVE_GAME {:?}\n", e),
                    }
                } else {
                    "ERR lock poisoned\n".to_string()
                }
            }
            Ok(Command::ListPackages) => {
                use tokio::process::Command as TokioCommand;
                debug!(target: "auriya::ipc", "Executing ListPackages...");
                match TokioCommand::new("pm")
                    .arg("list")
                    .arg("packages")
                    .output()
                    .await
                {
                    Ok(output) => {
                        let stdout = String::from_utf8_lossy(&output.stdout);
                        debug!(target: "auriya::ipc", "ListPackages success, len: {}", stdout.len());
                        format!("{}\n", stdout)
                    }
                    Err(e) => {
                        error!(target: "auriya::ipc", "ListPackages failed: {:?}", e);
                        format!("ERR LIST_PACKAGES {:?}\n", e)
                    }
                }
            }

            Ok(Command::GetGameList) => {
                if let Ok(gl) = h.shared_config.read() {
                    match serde_json::to_string(&gl.game) {
                        Ok(json) => format!("{}\n", json),
                        Err(e) => format!("ERR GET_GAMELIST {:?}\n", e),
                    }
                } else {
                    "ERR lock poisoned\n".to_string()
                }
            }
            Ok(Command::UpdateGame(pkg, gov, dnd, target_fps, refresh_rate, mode, fps_array)) => {
                use crate::core::config::gamelist::GameProfileUpdate;
                if let Ok(mut gl) = h.shared_config.write() {
                    let upd = GameProfileUpdate {
                        governor: gov,
                        dnd,
                        target_fps,
                        refresh_rate,
                        mode,
                        fps_array,
                    };
                    match gl.update(&pkg, upd) {
                        Ok(_) => {
                            if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                                format!("ERR SAVE_GAMELIST {:?}\n", e)
                            } else {
                                format!("OK UPDATE_GAME {}\n", pkg)
                            }
                        }
                        Err(e) => format!("ERR UPDATE_GAME {:?}\n", e),
                    }
                } else {
                    "ERR lock poisoned\n".to_string()
                }
            }
            Ok(Command::SetFps(fps)) => {
                (h.set_fps)(fps);
                format!("OK SET_FPS {}\n", fps)
            }
            Ok(Command::GetFps) => {
                let fps = (h.get_fps)();
                format!("FPS={}\n", fps)
            }
            Ok(Command::GetSupportedRates) => {
                use std::collections::BTreeSet;
                let rates: Vec<u32> = h
                    .supported_modes
                    .iter()
                    .map(|m| m.fps.round() as u32)
                    .collect::<BTreeSet<_>>()
                    .into_iter()
                    .collect();

                match serde_json::to_string(&rates) {
                    Ok(json) => format!("{}\n", json),
                    Err(e) => format!("ERR JSON {:?}\n", e),
                }
            }
            Err(e) => format!("ERR {}\n", e),
        };
        if !resp.is_empty() {
            w.write_all(resp.as_bytes()).await?;
        }
        line.clear();
    }
    Ok(())
}
