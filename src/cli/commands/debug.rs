use crate::Result;
use crate::cli::app::LogLevel;
use crate::cli::client::IpcClient;
use crate::cli::output;

pub async fn handle_inject(client: &IpcClient, package: &str) -> Result<()> {
    let resp = client.send(&format!("INJECT {package}")).await?;
    output::print_success(&format!("Injected: {resp}"));
    Ok(())
}

pub async fn handle_clear_inject(client: &IpcClient) -> Result<()> {
    let resp = client.send("CLEAR_INJECT").await?;
    output::print_success(&format!("Inject cleared: {resp}"));
    Ok(())
}

pub async fn handle_set_log(client: &IpcClient, level: LogLevel) -> Result<()> {
    let resp = client
        .send(&format!("SETLOG {}", level.to_upper_str()))
        .await?;
    output::print_success(&format!("Log level set: {resp}"));
    Ok(())
}

pub async fn handle_get_rates(client: &IpcClient) -> Result<()> {
    let resp = client.send("GET_SUPPORTED_RATES").await?;
    println!("Supported refresh rates:\n{resp}");
    Ok(())
}

pub async fn handle_list_packages(client: &IpcClient) -> Result<()> {
    let resp = client.send("LIST_PACKAGES").await?;
    println!("Installed packages:\n{resp}");
    Ok(())
}
