use super::app::{Cli, Commands};
use super::client::IpcClient;
use super::commands;
use super::output;
use crate::Result;
use crate::common::SOCKET_PATH;
use anyhow::bail;
use std::time::Instant;

pub async fn execute(cli: Cli) -> Result<()> {
    let start_time = Instant::now();
    let socket = cli.socket.as_deref().unwrap_or(SOCKET_PATH);
    let client = IpcClient::with_path(socket);

    if requires_daemon(&cli.command) && !client.is_alive().await {
        bail!("Daemon is not running");
    }

    let is_streaming = is_streaming_command(&cli.command);

    dispatch(cli.command, &client).await?;

    if !is_streaming {
        output::print_execution_time(start_time.elapsed());
    }

    Ok(())
}

fn requires_daemon(cmd: &Commands) -> bool {
    !matches!(
        cmd,
        Commands::Restart | Commands::Status | Commands::Info | Commands::Log { .. }
    )
}

fn is_streaming_command(cmd: &Commands) -> bool {
    matches!(
        cmd,
        Commands::Monitor { .. } | Commands::Log { follow: true, .. }
    )
}

async fn dispatch(command: Commands, client: &IpcClient) -> Result<()> {
    match command {
        // Lifecycle
        Commands::Status => commands::handle_status(client).await?,
        Commands::Enable => commands::handle_enable(client).await?,
        Commands::Disable => commands::handle_disable(client).await?,
        Commands::Reload => commands::handle_reload(client).await?,
        Commands::Restart => commands::handle_restart()?,
        Commands::Ping => commands::handle_ping(client).await?,
        Commands::GetPid => commands::handle_get_pid(client).await?,

        // Diagnostics & Telemetry
        Commands::Info => commands::handle_info(client).await?,
        Commands::Monitor {
            oneline,
            interval_ms,
        } => {
            commands::handle_monitor(client, oneline, interval_ms).await?;
        }
        Commands::Log {
            follow,
            lines,
            clear,
        } => {
            commands::handle_log(follow, lines, clear)?;
        }
        Commands::GetRates => commands::handle_get_rates(client).await?,
        Commands::ListPackages => commands::handle_list_packages(client).await?,

        // Tuning & Profiles
        Commands::SetProfile { mode } => commands::handle_set_profile(client, mode).await?,
        Commands::SetFps { fps } => commands::handle_set_fps(client, fps).await?,
        Commands::GetFps => commands::handle_get_fps(client).await?,
        Commands::AddGame { package } => commands::handle_add_game(client, &package).await?,
        Commands::RemoveGame { package } => commands::handle_remove_game(client, &package).await?,
        Commands::ListGames => commands::handle_list_games(client).await?,

        // Developer & Debug
        Commands::SetLog { level } => commands::handle_set_log(client, level).await?,
        Commands::Inject { package } => commands::handle_inject(client, &package).await?,
        Commands::ClearInject => commands::handle_clear_inject(client).await?,
    }
    Ok(())
}
