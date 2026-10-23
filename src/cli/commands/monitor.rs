use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;
use std::time::Duration;

pub async fn handle_monitor(client: &IpcClient, oneline: bool, interval_ms: u64) -> Result<()> {
    let interval = Duration::from_millis(interval_ms.max(200));
    let mut ticker = tokio::time::interval(interval);

    loop {
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {
                println!();
                break;
            }
            _ = ticker.tick() => {
                match client.send("GET_STATS").await {
                    Ok(json_str) => {
                        match serde_json::from_str::<crate::core::stats::StatsSnapshot>(&json_str) {
                            Ok(snap) => output::print_monitor_frame(&snap, oneline),
                            Err(e) => eprintln!("Error parsing telemetry: {e}"),
                        }
                    }
                    Err(e) => {
                        eprintln!("Error querying daemon stats: {e}");
                        break;
                    }
                }
            }
        }
    }

    Ok(())
}
