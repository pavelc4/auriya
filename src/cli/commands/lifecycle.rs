use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;

pub async fn handle_enable(client: &IpcClient) -> Result<()> {
    let resp = client.send("ENABLE").await?;
    println!("{resp}");
    Ok(())
}

pub async fn handle_disable(client: &IpcClient) -> Result<()> {
    let resp = client.send("DISABLE").await?;
    println!("{resp}");
    Ok(())
}

pub async fn handle_reload(client: &IpcClient) -> Result<()> {
    let resp = client.send("RELOAD").await?;
    output::print_success(&format!("Configuration reloaded: {resp}"));
    Ok(())
}

pub async fn handle_ping(client: &IpcClient) -> Result<()> {
    if client.ping().await? {
        output::print_success("Daemon is alive (PONG)");
    } else {
        output::print_error("Daemon not responding");
    }
    Ok(())
}

pub async fn handle_get_pid(client: &IpcClient) -> Result<()> {
    let resp = client.send("GET_PID").await?;
    println!("Daemon PID: {resp}");
    Ok(())
}
