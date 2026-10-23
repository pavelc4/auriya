use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;

pub async fn handle_status(client: &IpcClient) -> Result<()> {
    if !client.is_alive().await {
        output::print_daemon_stopped();
        return Ok(());
    }
    let resp = client.send("STATUS").await?;
    output::print_status(&resp);
    Ok(())
}
