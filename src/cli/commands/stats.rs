use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

pub async fn handle_stats(
    client: &IpcClient,
    json: bool,
    stream: bool,
    interval_ms: u64,
) -> Result<()> {
    if stream {
        let conn = client.connect().await?;
        let (r, mut w) = conn.into_split();
        let mut reader = BufReader::new(r);
        w.write_all(format!("STREAM_STATS {interval_ms}\n").as_bytes())
            .await?;

        let mut line = String::new();
        while reader.read_line(&mut line).await? > 0 {
            let s = line.trim();
            if s.starts_with('{') {
                if json {
                    println!("{s}");
                } else if let Ok(resp) = serde_json::from_str::<
                    crate::core::stats::ApiResponse<crate::core::stats::StatsSnapshot>,
                >(s)
                {
                    if let Some(snap) = resp.data {
                        output::print_monitor_frame(&snap, true);
                    }
                } else if let Ok(snap) =
                    serde_json::from_str::<crate::core::stats::StatsSnapshot>(s)
                {
                    output::print_monitor_frame(&snap, true);
                }
                let _ = std::io::Write::flush(&mut std::io::stdout());
            } else if s == "STREAM_STOPPED" {
                break;
            }
            line.clear();
        }
        return Ok(());
    }

    let resp = client.send("GET_STATS").await?;
    if json {
        print!("{resp}");
        return Ok(());
    }

    if let Ok(api_resp) = serde_json::from_str::<
        crate::core::stats::ApiResponse<crate::core::stats::StatsSnapshot>,
    >(&resp)
    {
        if let Some(snap) = api_resp.data {
            output::print_monitor_frame(&snap, false);
            return Ok(());
        }
    } else if let Ok(snap) = serde_json::from_str::<crate::core::stats::StatsSnapshot>(&resp) {
        output::print_monitor_frame(&snap, false);
        return Ok(());
    }

    print!("{resp}");
    Ok(())
}
