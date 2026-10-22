use super::commands::HELP;
use super::types::{Command, IpcHandles, ProfileMode};
use crate::core::config::gamelist::GameProfileUpdate;
use crate::daemon::state::CurrentState;
use anyhow::Result;
use std::sync::atomic::Ordering;
use tokio::io::{AsyncBufReadExt, AsyncRead, AsyncWrite, AsyncWriteExt, BufReader};
use tokio::net::UnixStream;
use tracing::{debug, error, info};

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

        let cmd = match s.parse::<Command>() {
            Ok(c) => c,
            Err(e) => {
                w.write_all(format!("ERR {e}\n").as_bytes()).await?;
                line.clear();
                continue;
            }
        };

        match cmd {
            Command::Help => w.write_all(HELP.as_bytes()).await?,
            Command::Ping => w.write_all(b"PONG\n").await?,
            Command::Quit => {
                w.write_all(b"BYE\n").await?;
                break;
            }
            Command::GetPid => w.write_all(handle_get_pid(&h).as_bytes()).await?,
            Command::Status => w.write_all(handle_status(&h).as_bytes()).await?,
            Command::Enable => {
                h.enabled.store(true, Ordering::Release);
                w.write_all(b"OK ENABLED\n").await?;
            }
            Command::Disable => {
                h.enabled.store(false, Ordering::Release);
                w.write_all(b"OK DISABLED\n").await?;
            }
            Command::Reload => w.write_all(handle_reload(&h).as_bytes()).await?,
            Command::Restart => {
                if handle_restart()? {
                    return Ok(());
                }
                w.write_all(b"ERR RESTART_FAILED\n").await?;
            }
            Command::SetLog(lvl) => {
                (h.set_log_level)(lvl);
                w.write_all(b"OK SET_LOG\n").await?;
            }
            Command::Inject(pkg) => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = Some(pkg);
                }
                w.write_all(b"OK INJECT\n").await?;
            }
            Command::ClearInject => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = None;
                }
                w.write_all(b"OK CLEAR_INJECT\n").await?;
            }
            Command::SetProfile(mode) => {
                let resp = handle_set_profile(&h, mode)?;
                w.write_all(resp.as_bytes()).await?;
            }
            Command::SetGovernor(gov) => {
                crate::core::tweaks::paths::set_governor_cached(&gov);
                if let Ok(mut g) = h.balance_governor.write() {
                    *g = gov.clone();
                }
                w.write_all(format!("OK SET_GOVERNOR {gov}\n").as_bytes())
                    .await?;
            }
            Command::AddGame(pkg) => w.write_all(handle_add_game(&h, &pkg).as_bytes()).await?,
            Command::RemoveGame(pkg) => {
                w.write_all(handle_remove_game(&h, &pkg).as_bytes()).await?
            }
            Command::UpdateGame { pkg, update } => {
                w.write_all(handle_update_game(&h, &pkg, *update).as_bytes())
                    .await?;
            }
            Command::ListPackages => w.write_all(handle_list_packages().await.as_bytes()).await?,
            Command::GetGameList => w.write_all(handle_get_gamelist(&h).as_bytes()).await?,
            Command::SetFps(fps) => {
                (h.set_fps)(fps).await;
                w.write_all(format!("OK SET_FPS {fps}\n").as_bytes())
                    .await?;
            }
            Command::GetFps => w.write_all(handle_get_fps(&h).await.as_bytes()).await?,
            Command::GetSupportedRates => {
                w.write_all(handle_get_supported_rates(&h).as_bytes())
                    .await?;
            }
            Command::GetStats => {
                let json = build_stats_json(&h).await;
                w.write_all(json.as_bytes()).await?;
            }
            Command::StreamStats(interval_ms) => {
                handle_stream_stats(&mut reader, &mut w, &h, interval_ms).await?;
            }
        }
        line.clear();
    }
    Ok(())
}

fn handle_get_pid(h: &IpcHandles) -> String {
    let st = h
        .current_state
        .read()
        .ok()
        .map(|g| g.clone())
        .unwrap_or_default();
    match (st.pkg, st.pid) {
        (Some(p), Some(id)) => format!("PKG={p} PID={id}\n"),
        (Some(p), None) => format!("PKG={p} PID=None\n"),
        _ => "PKG=None PID=None\n".into(),
    }
}

fn handle_status(h: &IpcHandles) -> String {
    let enabled = h.enabled.load(Ordering::Acquire);
    let n = h
        .shared_config
        .read()
        .ok()
        .map(|c| c.game.len())
        .unwrap_or(0);
    let ov = h.override_foreground.read().ok().and_then(|o| o.clone());
    let log_level = match h.current_log_level.read() {
        Ok(l) => format!("{l:?}"),
        Err(_) => "Unknown".to_string(),
    };

    let telemetry = h
        .current_state
        .read()
        .map(|st| format_telemetry(&st))
        .unwrap_or_default();

    format!("ENABLED={enabled} PACKAGES={n} OVERRIDE={ov:?} LOG_LEVEL={log_level}\n{telemetry}")
}

fn format_telemetry(st: &CurrentState) -> String {
    let mut out = String::new();
    if let Some(fps) = st.fps {
        let source = match st.fps_source {
            Some(crate::core::fps_meter::FpsSource::Ebpf) => "ebpf",
            Some(crate::core::fps_meter::FpsSource::Sysfs) => "sysfs",
            None => "?",
        };
        out.push_str(&format!("FPS={fps:.1} SOURCE={source}\n"));
    }
    if let Some(ref cpu) = st.cpu_telemetry {
        out.push_str(&format!(
            "CPU_CORES={} CPU_LOAD={:.0}\n",
            cpu.cores.len(),
            cpu.load_pct
        ));
        for core in &cpu.cores {
            out.push_str(&format!(
                "CORE_{id}={id} online={online} freq={freq} governor={gov} cluster={cluster:?}\n",
                id = core.core_id,
                online = core.online,
                freq = core.cur_freq_khz,
                gov = core.governor,
                cluster = core.cluster,
            ));
        }
    }
    if let Some(ref gpu) = st.gpu_telemetry {
        out.push_str(&format!(
            "GPU_FREQ={} GPU_LOAD={} GPU_VENDOR={:?}\n",
            gpu.cur_freq_mhz.unwrap_or(0),
            gpu.load_pct.unwrap_or(0),
            gpu.vendor,
        ));
    }
    if let Some(ref thermal) = st.thermal_telemetry {
        let bat_c = crate::core::telemetry::battery::snapshot().temp_c;
        out.push_str(&format!(
            "TEMP_CPU={} TEMP_GPU={} TEMP_BAT={}\n",
            thermal
                .cpu_temp_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
            thermal
                .gpu_temp_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
            bat_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
        ));
    }
    out
}

fn handle_reload(h: &IpcHandles) -> String {
    let gl_result = (h.reload_fn)();
    if let (Ok(new_settings), Ok(mut g)) = (
        crate::core::config::Settings::load(crate::core::config::settings_path()),
        h.balance_governor.write(),
    ) {
        *g = new_settings.cpu.default_governor;
    }
    match gl_result {
        Ok(n) => format!("OK RELOADED {n}\n"),
        Err(e) => format!("ERR RELOAD {e:?}\n"),
    }
}

fn handle_restart() -> Result<bool> {
    info!(target: "auriya::ipc", "Restart requested via IPC - initiating self-restart");
    let _ = std::fs::write("/data/adb/auriya/daemon.log", "");

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
        Ok(true)
    } else {
        Ok(false)
    }
}

fn handle_set_profile(h: &IpcHandles, mode: ProfileMode) -> Result<String> {
    use crate::core::profile;
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
        Ok(_) => Ok(format!("OK SET_PROFILE {mode:?}\n")),
        Err(e) => Ok(format!("ERR SET_PROFILE {e:?}\n")),
    }
}

fn handle_add_game(h: &IpcHandles, pkg: &str) -> String {
    use crate::core::config::gamelist::GameProfile;
    let Ok(mut gl) = h.shared_config.write() else {
        return "ERR lock poisoned\n".into();
    };

    let profile = GameProfile {
        package: pkg.to_string(),
        cpu_governor: "performance".to_string(),
        enable_dnd: h.dnd_default,
        target_fps: None,
        refresh_rate: None,
        mode: Some("performance".to_string()),
        ceiling: None,
        thermal_preset: None,
    };

    let g = std::sync::Arc::make_mut(&mut gl);
    if let Err(e) = g.add(profile) {
        return format!("ERR ADD_GAME {e:?}\n");
    }
    match g.save(crate::core::config::gamelist_path()) {
        Ok(_) => format!("OK ADD_GAME {pkg}\n"),
        Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
    }
}

fn handle_remove_game(h: &IpcHandles, pkg: &str) -> String {
    let Ok(mut gl) = h.shared_config.write() else {
        return "ERR lock poisoned\n".into();
    };

    let g = std::sync::Arc::make_mut(&mut gl);
    if let Err(e) = g.remove(pkg) {
        return format!("ERR REMOVE_GAME {e:?}\n");
    }
    match g.save(crate::core::config::gamelist_path()) {
        Ok(_) => format!("OK REMOVE_GAME {pkg}\n"),
        Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
    }
}

fn handle_update_game(h: &IpcHandles, pkg: &str, upd: GameProfileUpdate) -> String {
    let Ok(mut gl) = h.shared_config.write() else {
        return "ERR lock poisoned\n".into();
    };

    let g = std::sync::Arc::make_mut(&mut gl);
    if let Err(e) = g.update(pkg, upd) {
        return format!("ERR UPDATE_GAME {e:?}\n");
    }
    match g.save(crate::core::config::gamelist_path()) {
        Ok(_) => format!("OK UPDATE_GAME {pkg}\n"),
        Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
    }
}

async fn handle_list_packages() -> String {
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
            format!("{stdout}\n")
        }
        Err(e) => {
            error!(target: "auriya::ipc", "ListPackages failed: {e:?}");
            format!("ERR LIST_PACKAGES {e:?}\n")
        }
    }
}

fn handle_get_gamelist(h: &IpcHandles) -> String {
    let Ok(gl) = h.shared_config.read() else {
        return "ERR lock poisoned\n".into();
    };
    serde_json::to_string(&gl.game)
        .map(|json| format!("{json}\n"))
        .unwrap_or_else(|e| format!("ERR GET_GAMELIST {e:?}\n"))
}

async fn handle_get_fps(h: &IpcHandles) -> String {
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
        Some(m) => format!("FPS={m:.1} TARGET={target}\n"),
        None => format!("FPS=0 TARGET={target}\n"),
    }
}

fn handle_get_supported_rates(h: &IpcHandles) -> String {
    use std::collections::BTreeSet;
    let rates: Vec<u32> = h
        .supported_modes
        .iter()
        .map(|m| m.fps.round() as u32)
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect();

    serde_json::to_string(&rates)
        .map(|json| format!("{json}\n"))
        .unwrap_or_else(|e| format!("ERR JSON {e:?}\n"))
}

async fn handle_stream_stats<R, W>(
    reader: &mut BufReader<R>,
    w: &mut W,
    h: &IpcHandles,
    interval_ms: u64,
) -> Result<()>
where
    R: AsyncRead + Unpin,
    W: AsyncWrite + Unpin,
{
    w.write_all(b"STREAM_START\n").await?;
    let mut ticker = tokio::time::interval(std::time::Duration::from_millis(interval_ms));
    ticker.tick().await;
    let mut line = String::new();

    loop {
        tokio::select! {
            _ = ticker.tick() => {
                let json = build_stats_json(h).await;
                if w.write_all(json.as_bytes()).await.is_err() {
                    break;
                }
            }
            res = reader.read_line(&mut line) => {
                match res {
                    Ok(n) if n > 0 => {
                        let cmd = line.trim();
                        if cmd == "STOP" || cmd == "QUIT" {
                            let _ = w.write_all(b"STREAM_STOPPED\n").await;
                            break;
                        }
                        line.clear();
                    }
                    _ => break,
                }
            }
        }
    }
    Ok(())
}

async fn build_stats_json(h: &IpcHandles) -> String {
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
        Ok(json) => format!("{json}\n"),
        Err(e) => format!("ERR JSON {e:?}\n"),
    }
}
