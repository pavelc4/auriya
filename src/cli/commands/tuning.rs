use crate::Result;
use crate::cli::app::ProfileMode;
use crate::cli::client::IpcClient;
use crate::cli::output;

pub async fn handle_set_profile(client: &IpcClient, mode: ProfileMode) -> Result<()> {
    let resp = client
        .send(&format!("SET_PROFILE {}", mode.to_upper_str()))
        .await?;
    output::print_success(&format!("Profile set: {resp}"));
    Ok(())
}

pub async fn handle_set_fps(client: &IpcClient, fps: u32) -> Result<()> {
    let resp = client.send(&format!("SET_FPS {fps}")).await?;
    output::print_success(&format!("FPS set: {resp}"));
    Ok(())
}

pub async fn handle_get_fps(client: &IpcClient) -> Result<()> {
    let resp = client.send("GET_FPS").await?;
    println!("Current FPS: {resp}");
    Ok(())
}

pub async fn handle_add_game(client: &IpcClient, package: &str) -> Result<()> {
    let resp = client.send(&format!("ADD_GAME {package}")).await?;
    output::print_success(&format!("Game added: {resp}"));
    Ok(())
}

pub async fn handle_remove_game(client: &IpcClient, package: &str) -> Result<()> {
    let resp = client.send(&format!("REMOVE_GAME {package}")).await?;
    output::print_success(&format!("Game removed: {resp}"));
    Ok(())
}

pub async fn handle_list_games(client: &IpcClient) -> Result<()> {
    let resp = client.send("GET_GAMELIST").await?;
    println!("Configured games:\n{resp}");
    Ok(())
}
