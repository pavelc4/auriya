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
        - SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE|FAST>
        - SET_GOVERNOR <governor>
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

                let mut telemetry_lines = String::new();
                if let Ok(st) = h.current_state.read() {
                    if let Some(fps) = st.fps {
                        let source = match st.fps_source {
                            Some(crate::core::fps_meter::FpsSource::Ebpf) => "ebpf",
                            Some(crate::core::fps_meter::FpsSource::Sysfs) => "sysfs",
                            None => "?",
                        };
                        telemetry_lines.push_str(&format!("FPS={:.1} SOURCE={}\n", fps, source));
                    }
                    if let Some(ref cpu) = st.cpu_telemetry {
                        telemetry_lines.push_str(&format!(
                            "CPU_CORES={} CPU_LOAD={:.0}\n",
                            cpu.cores.len(),
                            cpu.load_pct,
                        ));
                        for core in &cpu.cores {
                            telemetry_lines.push_str(&format!(
                                "CORE_{}={} online={} freq={} governor={} cluster={:?}\n",
                                core.core_id,
                                core.core_id,
                                core.online,
                                core.cur_freq_khz,
                                core.governor,
                                core.cluster,
                            ));
                        }
                    }
                    if let Some(ref gpu) = st.gpu_telemetry {
                        telemetry_lines.push_str(&format!(
                            "GPU_FREQ={} GPU_LOAD={} GPU_VENDOR={:?}\n",
                            gpu.cur_freq_mhz.unwrap_or(0),
                            gpu.load_pct.unwrap_or(0),
                            gpu.vendor,
                        ));
                    }
                    if let Some(ref thermal) = st.thermal_telemetry {
                        let bat_c = crate::core::telemetry::battery::snapshot().temp_c;
                        telemetry_lines.push_str(&format!(
                            "TEMP_CPU={} TEMP_GPU={} TEMP_BAT={}\n",
                            thermal
                                .cpu_temp_c
                                .map(|v| format!("{:.1}", v))
                                .unwrap_or_else(|| "N/A".to_string()),
                            thermal
                                .gpu_temp_c
                                .map(|v| format!("{:.1}", v))
                                .unwrap_or_else(|| "N/A".to_string()),
                            bat_c
                                .map(|v| format!("{:.1}", v))
                                .unwrap_or_else(|| "N/A".to_string()),
                        ));
                    }
                }

                format!(
                    "ENABLED={} PACKAGES={} OVERRIDE={:?} LOG_LEVEL={}\n{}",
                    enabled, n, ov, log_level, telemetry_lines,
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
            Ok(Command::Reload) => {
                let gl_result = (h.reload_fn)();
                if let Ok(new_settings) =
                    crate::core::config::Settings::load(crate::core::config::settings_path())
                    && let Ok(mut g) = h.balance_governor.write()
                {
                    *g = new_settings.cpu.default_governor;
                }
                match gl_result {
                    Ok(n) => format!("OK RELOADED {}\n", n),
                    Err(e) => format!("ERR RELOAD {:?}\n", e),
                }
            }
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
                // Profile application writes several kernel controls; serialize
                // concurrent IPC requests so rapid switches cannot interleave.
                let _profile_guard = h
                    .profile_lock
                    .lock()
                    .map_err(|_| anyhow::anyhow!("profile lock poisoned"))?;
                let gov_guard = h.balance_governor.read();
                let balance_gov = gov_guard
                    .as_deref()
                    .map(|s| s.as_str())
                    .unwrap_or("schedutil");
                let r = match mode {
                    ProfileMode::Performance => profile::apply_performance(),
                    ProfileMode::Balance => profile::apply_balance(balance_gov),
                    ProfileMode::Powersave => profile::apply_powersave(),
                    ProfileMode::Fast => profile::apply_fast(),
                };
                match r {
                    Ok(_) => format!("OK SET_PROFILE {:?}\n", mode),
                    Err(e) => format!("ERR SET_PROFILE {:?}\n", e),
                }
            }
            Ok(Command::SetGovernor(gov)) => {
                crate::core::tweaks::paths::set_governor_cached(&gov);
                let resp = format!("OK SET_GOVERNOR {}\n", gov);
                if let Ok(mut g) = h.balance_governor.write() {
                    *g = gov;
                }
                resp
            }
            Ok(Command::AddGame(pkg)) => {
                use crate::core::config::gamelist::GameProfile;
                if let Ok(mut gl) = h.shared_config.write() {
                    let profile = GameProfile {
                        package: pkg.clone(),
                        cpu_governor: "performance".to_string(),
                        enable_dnd: h.dnd_default,
                        target_fps: None,
                        refresh_rate: None,
                        mode: Some("performance".to_string()),
                        ceiling: None,
                    };
                    // Copy-on-write: clone the shared snapshot only when we
                    // actually mutate it (rare, IPC-driven), keeping the
                    // per-tick read path a cheap Arc bump.
                    let g = std::sync::Arc::make_mut(&mut gl);
                    match g.add(profile) {
                        Ok(_) => {
                            if let Err(e) = g.save(crate::core::config::gamelist_path()) {
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
                    let g = std::sync::Arc::make_mut(&mut gl);
                    match g.remove(&pkg) {
                        Ok(_) => {
                            if let Err(e) = g.save(crate::core::config::gamelist_path()) {
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
            Ok(Command::UpdateGame(
                pkg,
                gov,
                dnd,
                target_fps,
                refresh_rate,
                mode,
                fps_array,
                ceiling,
            )) => {
                use crate::core::config::gamelist::GameProfileUpdate;
                if let Ok(mut gl) = h.shared_config.write() {
                    let upd = GameProfileUpdate {
                        governor: gov,
                        dnd,
                        target_fps,
                        refresh_rate,
                        mode,
                        fps_array,
                        ceiling,
                    };
                    let g = std::sync::Arc::make_mut(&mut gl);
                    match g.update(&pkg, upd) {
                        Ok(_) => {
                            if let Err(e) = g.save(crate::core::config::gamelist_path()) {
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
                (h.set_fps)(fps).await;
                format!("OK SET_FPS {}\n", fps)
            }
            Ok(Command::GetFps) => {
                let target = (h.get_fps)().await;
                let is_gaming = h
                    .current_state
                    .read()
                    .ok()
                    .map(|s| s.game_session)
                    .unwrap_or(false);
                let measured = if is_gaming {
                    h.current_state.read().ok().and_then(|s| s.fps)
                } else {
                    None
                };
                match measured {
                    Some(m) => format!("FPS={:.1} TARGET={}\n", m, target),
                    None => format!("FPS=0 TARGET={}\n", target),
                }
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
            Ok(Command::GetStats) => {
                use crate::core::stats::StatsSnapshot;
                use crate::core::telemetry::battery;

                let is_gaming = h
                    .current_state
                    .read()
                    .ok()
                    .map(|s| s.game_session)
                    .unwrap_or(false);
                // Windowed FPS stats from the FAS buffer. None when not in a game session;
                // also collapse an empty window (no frames) to None so
                // the UI can tell "inactive" from a real 0 fps.
                let fps = if is_gaming {
                    (h.get_fps_stats)().await.filter(|f| f.frames > 0)
                } else {
                    None
                };
                // Battery is read fresh from sysfs on request.
                let bat = battery::snapshot();

                // Everything else is the per-tick telemetry snapshot already in
                // CurrentState; clone the pieces out under the read lock.
                let snap = {
                    let st = h.current_state.read().ok();
                    let st = st.as_deref();
                    StatsSnapshot::build(
                        fps,
                        st.and_then(|s| s.cpu_telemetry.as_ref()),
                        st.and_then(|s| s.gpu_telemetry.as_ref()),
                        st.and_then(|s| s.thermal_telemetry.as_ref()),
                        &bat,
                        st.and_then(|s| s.pkg.as_deref()),
                        st.map(|s| s.profile).unwrap_or_default(),
                        st.map(|s| s.game_session).unwrap_or(false),
                    )
                };

                match serde_json::to_string(&snap) {
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
