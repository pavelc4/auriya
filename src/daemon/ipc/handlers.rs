use super::commands::HELP;
use super::types::{Command, DaemonContext};
use anyhow::Result;
use std::sync::Arc;
use std::sync::atomic::Ordering;
use tokio::io::{AsyncBufReadExt, AsyncRead, AsyncWrite, AsyncWriteExt, BufReader};
use tokio::net::UnixStream;
use tracing::{debug, error, info};

/// Handle a single IPC client connection.
pub async fn handle_client(stream: UnixStream, ctx: Arc<DaemonContext>) -> Result<()> {
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
            Command::GetPid => w.write_all(ctx.get_pid().as_bytes()).await?,
            Command::Status => w.write_all(ctx.status().as_bytes()).await?,
            Command::Enable => {
                ctx.enabled.store(true, Ordering::Release);
                w.write_all(b"OK ENABLED\n").await?;
            }
            Command::Disable => {
                ctx.enabled.store(false, Ordering::Release);
                w.write_all(b"OK DISABLED\n").await?;
            }
            Command::Reload => w.write_all(ctx.reload().as_bytes()).await?,
            Command::Restart => {
                if handle_restart()? {
                    return Ok(());
                }
                w.write_all(b"ERR RESTART_FAILED\n").await?;
            }
            Command::SetLog(lvl) => {
                ctx.set_log_level(lvl);
                w.write_all(b"OK SET_LOG\n").await?;
            }
            Command::Inject(pkg) => {
                if let Ok(mut g) = ctx.override_foreground.write() {
                    *g = Some(pkg);
                }
                w.write_all(b"OK INJECT\n").await?;
            }
            Command::ClearInject => {
                if let Ok(mut g) = ctx.override_foreground.write() {
                    *g = None;
                }
                w.write_all(b"OK CLEAR_INJECT\n").await?;
            }
            Command::SetProfile(mode) => {
                let resp = ctx.set_profile(mode)?;
                w.write_all(resp.as_bytes()).await?;
            }
            Command::SetGovernor(gov) => {
                crate::platform::cpu::governor::set_governor_cached(&gov);
                if let Ok(mut g) = ctx.balance_governor.write() {
                    *g = gov.clone();
                }
                w.write_all(format!("OK SET_GOVERNOR {gov}\n").as_bytes())
                    .await?;
            }
            Command::AddGame(pkg) => w.write_all(ctx.add_game(&pkg).as_bytes()).await?,
            Command::RemoveGame(pkg) => w.write_all(ctx.remove_game(&pkg).as_bytes()).await?,
            Command::UpdateGame { pkg, update } => {
                w.write_all(ctx.update_game(&pkg, *update).as_bytes())
                    .await?;
            }
            Command::ListPackages => w.write_all(handle_list_packages().await.as_bytes()).await?,
            Command::GetGameList => w.write_all(ctx.get_gamelist().as_bytes()).await?,
            Command::SetFps(fps) => {
                ctx.set_fps(fps).await;
                w.write_all(format!("OK SET_FPS {fps}\n").as_bytes())
                    .await?;
            }
            Command::GetFps => w.write_all(ctx.get_fps_response().await.as_bytes()).await?,
            Command::GetSupportedRates => {
                w.write_all(ctx.get_supported_rates().as_bytes()).await?;
            }
            Command::GetStats => {
                let json = ctx.build_stats_json().await;
                w.write_all(json.as_bytes()).await?;
            }
            Command::StreamStats(interval_ms) => {
                handle_stream_stats(&mut reader, &mut w, &ctx, interval_ms).await?;
            }
        }
        line.clear();
    }
    Ok(())
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

async fn handle_stream_stats<R, W>(
    reader: &mut BufReader<R>,
    w: &mut W,
    ctx: &DaemonContext,
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
                let json = ctx.build_stats_json().await;
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
