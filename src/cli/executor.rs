use super::{app::*, client::IpcClient, output};
use crate::common::SOCKET_PATH;
use crate::{Context, Result};
use anyhow::bail;
use std::time::Duration;

const DAEMON_BIN: &str = "auriya";
const COMPANION_PROC: &str = "AuriyaSysMon";
const CONFIG_DIR: &str = "/data/adb/.config/auriya";
const LOG_DIR: &str = "/data/adb/auriya";
const SERVICE_SCRIPT: &str = "/data/adb/modules/auriya/service.sh";

pub async fn execute(cli: Cli) -> Result<()> {
    let socket = cli.socket.as_deref().unwrap_or(SOCKET_PATH);
    let client = IpcClient::with_path(socket);

    if !matches!(cli.command, Commands::Restart | Commands::Status) && !client.is_alive().await {
        bail!("Daemon is not running");
    }

    match cli.command {
        Commands::Status => handle_status(&client).await?,

        Commands::Enable => {
            let resp = client.send("ENABLE").await?;
            println!("{resp}");
        }

        Commands::Disable => {
            let resp = client.send("DISABLE").await?;
            println!("{resp}");
        }

        Commands::Reload => {
            let resp = client.send("RELOAD").await?;
            output::print_success(&format!("Configuration reloaded: {resp}"));
        }

        Commands::Restart => handle_restart()?,

        Commands::SetProfile { mode } => {
            let resp = client
                .send(&format!("SET_PROFILE {}", mode.to_upper_str()))
                .await?;
            output::print_success(&format!("Profile set: {resp}"));
        }

        Commands::SetFps { fps } => {
            let resp = client.send(&format!("SET_FPS {fps}")).await?;
            output::print_success(&format!("FPS set: {resp}"));
        }

        Commands::GetFps => {
            let resp = client.send("GET_FPS").await?;
            println!("Current FPS: {resp}");
        }

        Commands::AddGame { package } => {
            let resp = client.send(&format!("ADD_GAME {package}")).await?;
            output::print_success(&format!("Game added: {resp}"));
        }

        Commands::RemoveGame { package } => {
            let resp = client.send(&format!("REMOVE_GAME {package}")).await?;
            output::print_success(&format!("Game removed: {resp}"));
        }

        Commands::ListGames => {
            let resp = client.send("GET_GAMELIST").await?;
            println!("Configured games:\n{resp}");
        }

        Commands::ListPackages => {
            let resp = client.send("LIST_PACKAGES").await?;
            println!("Installed packages:\n{resp}");
        }

        Commands::GetRates => {
            let resp = client.send("GET_SUPPORTED_RATES").await?;
            println!("Supported refresh rates:\n{resp}");
        }

        Commands::SetLog { level } => {
            let resp = client
                .send(&format!("SETLOG {}", level.to_upper_str()))
                .await?;
            output::print_success(&format!("Log level set: {resp}"));
        }

        Commands::GetPid => {
            let resp = client.send("GET_PID").await?;
            println!("Daemon PID: {resp}");
        }

        Commands::Ping => {
            if client.ping().await? {
                output::print_success("Daemon is alive (PONG)");
            } else {
                output::print_error("Daemon not responding");
            }
        }

        Commands::Inject { package } => {
            let resp = client.send(&format!("INJECT {package}")).await?;
            output::print_success(&format!("Injected: {resp}"));
        }

        Commands::ClearInject => {
            let resp = client.send("CLEAR_INJECT").await?;
            output::print_success(&format!("Inject cleared: {resp}"));
        }
    }

    Ok(())
}

async fn handle_status(client: &IpcClient) -> Result<()> {
    if !client.is_alive().await {
        output::print_daemon_stopped();
        return Ok(());
    }
    let resp = client.send("STATUS").await?;
    output::print_status(&resp);
    Ok(())
}

fn handle_restart() -> Result<()> {
    println!("Restarting daemon + companion...");
    stop_processes();
    clear_runtime_state();
    launch_service()?;
    println!("Restart initiated — tail {LOG_DIR}/restart.log for progress");
    Ok(())
}

fn stop_processes() {
    use std::process::{Command, Stdio};
    for proc in [DAEMON_BIN, COMPANION_PROC] {
        let _ = Command::new("killall")
            .args(["-q", "-TERM", proc])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
    std::thread::sleep(Duration::from_secs(2));
    for proc in [DAEMON_BIN, COMPANION_PROC] {
        let _ = Command::new("killall")
            .args(["-q", "-KILL", proc])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
}

fn clear_runtime_state() {
    let _ = std::fs::remove_file(format!("{CONFIG_DIR}/system_status"));
    let _ = std::fs::remove_file(format!("{CONFIG_DIR}/companion.lock"));
    let _ = std::fs::write(format!("{LOG_DIR}/daemon.log"), "");
}

fn launch_service() -> Result<()> {
    use std::process::{Command, Stdio};
    Command::new("sh")
        .args([
            "-c",
            &format!("sh {SERVICE_SCRIPT} >> {LOG_DIR}/restart.log 2>&1 &"),
        ])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .context("failed to spawn service.sh")?;
    Ok(())
}
