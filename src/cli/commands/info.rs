use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;

pub async fn handle_info(client: &IpcClient) -> Result<()> {
    let is_alive = client.is_alive().await;
    let (pid, fps_source) = if is_alive {
        let pid = client
            .send("GET_PID")
            .await
            .ok()
            .and_then(|s| s.trim().parse::<i32>().ok());

        let status = client.send("STATUS").await.unwrap_or_default();
        let fps_source = if status.contains("SOURCE=ebpf") {
            Some("ebpf")
        } else if status.contains("SOURCE=sysfs") {
            Some("sysfs")
        } else {
            None
        };

        (pid, fps_source)
    } else {
        (None, None)
    };

    let mut display_rates =
        if let Ok(modes) = crate::platform::display::get_app_supported_modes().await {
            if !modes.is_empty() {
                let mut uniq: Vec<u32> = modes.iter().map(|m| m.fps.round() as u32).collect();
                uniq.sort_unstable();
                uniq.dedup();
                let current_rate = uniq.iter().max().copied().unwrap_or(60);
                let formatted = uniq
                    .iter()
                    .map(|r| format!("{r}Hz"))
                    .collect::<Vec<_>>()
                    .join(", ");
                let reso = modes
                    .iter()
                    .max_by_key(|m| m.width * m.height)
                    .map(|m| format!("({}x{}) ", m.width, m.height))
                    .unwrap_or_default();
                Some(format!("{current_rate}Hz {reso}[Supported: {formatted}]"))
            } else {
                None
            }
        } else {
            None
        };

    if display_rates.is_none() && is_alive {
        display_rates = client.send("GET_SUPPORTED_RATES").await.ok().and_then(|s| {
            let rates: Vec<u32> = serde_json::from_str(&s).ok()?;
            if rates.is_empty() {
                None
            } else {
                let formatted = rates
                    .iter()
                    .map(|r| format!("{r}Hz"))
                    .collect::<Vec<_>>()
                    .join(", ");
                let current_rate = rates.iter().max().copied().unwrap_or(60);
                Some(format!("{current_rate}Hz [Supported: {formatted}]"))
            }
        });
    }

    let report = crate::platform::info::collect_report(pid, display_rates, fps_source);
    output::print_info(&report);
    Ok(())
}
