use super::{app::*, client::IpcClient, output};
use crate::common::{SERVICE_SCRIPT, SOCKET_PATH};
use crate::{Context, Result};
use anyhow::bail;

pub async fn execute(cli: Cli) -> Result<()> {
    let socket = cli.socket.as_deref().unwrap_or(SOCKET_PATH);
    let client = IpcClient::with_path(socket);

    if !matches!(cli.command, Commands::Restart | Commands::Status) && !client.is_alive().await {
        bail!("Daemon is not running");
    }

    match cli.command {
        Commands::Status => {
            handle_status(&client).await?;
        }

        Commands::Enable => {
            let resp = client.send("ENABLE").await?;
            println!("{}", resp);
        }

        Commands::Disable => {
            let resp = client.send("DISABLE").await?;
            println!("{}", resp);
        }

        Commands::Reload => {
            let resp = client.send("RELOAD").await?;
            output::print_success(&format!("Configuration reloaded: {}", resp));
        }

        Commands::Restart => {
            handle_restart()?;
        }

        Commands::SetProfile { mode } => {
            let cmd = format!("SET_PROFILE {}", mode.to_upper_str());
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("Profile set: {}", resp));
        }

        Commands::SetFps { fps } => {
            let cmd = format!("SET_FPS {}", fps);
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("FPS set: {}", resp));
        }

        Commands::GetFps => {
            let resp = client.send("GET_FPS").await?;
            println!("Current FPS: {}", resp);
        }

        Commands::AddGame { package } => {
            let cmd = format!("ADD_GAME {}", package);
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("Game added: {}", resp));
        }

        Commands::RemoveGame { package } => {
            let cmd = format!("REMOVE_GAME {}", package);
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("Game removed: {}", resp));
        }

        Commands::ListGames => {
            let resp = client.send("GET_GAMELIST").await?;
            println!("Configured games:\n{}", resp);
        }

        Commands::ListPackages => {
            let resp = client.send("LIST_PACKAGES").await?;
            println!("Installed packages:\n{}", resp);
        }

        Commands::GetRates => {
            let resp = client.send("GET_SUPPORTED_RATES").await?;
            println!("Supported refresh rates:\n{}", resp);
        }

        Commands::SetLog { level } => {
            let cmd = format!("SET_LOG {}", level.to_upper_str());
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("Log level set: {}", resp));
        }

        Commands::GetPid => {
            let resp = client.send("GET_PID").await?;
            println!("Daemon PID: {}", resp);
        }

        Commands::Ping => {
            if client.ping().await? {
                output::print_success("Daemon is alive (PONG)");
            } else {
                output::print_error("Daemon not responding");
            }
        }

        Commands::Inject { package } => {
            let cmd = format!("INJECT {}", package);
            let resp = client.send(&cmd).await?;
            output::print_success(&format!("Injected: {}", resp));
        }

        Commands::ClearInject => {
            let resp = client.send("CLEAR_INJECT").await?;
            output::print_success(&format!("Inject cleared: {}", resp));
        }
    }

    Ok(())
}

async fn handle_status(client: &IpcClient) -> Result<()> {
    if !client.is_alive().await {
        output::print_daemon_stopped();
        return Ok(());
    }

    let response = client.send("STATUS").await?;
    output::print_status(&response);
    Ok(())
}

fn handle_restart() -> Result<()> {
    println!("Restarting daemon...");

    std::process::Command::new("pkill")
        .arg("-9")
        .arg("auriya")
        .output()
        .context("Failed to kill daemon")?;

    std::thread::sleep(std::time::Duration::from_secs(1));
    std::process::Command::new("sh")
        .arg(SERVICE_SCRIPT)
        .spawn()
        .context("Failed to start daemon")?;

    output::print_success("Restart initiated");
    Ok(())
}
