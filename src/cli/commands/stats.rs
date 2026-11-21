use crate::Result;
use crate::cli::client::IpcClient;
use crate::cli::output;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

pub(crate) fn parse_stats_snapshot(json_str: &str) -> Option<crate::core::stats::StatsSnapshot> {
    serde_json::from_str::<crate::core::stats::ApiResponse<crate::core::stats::StatsSnapshot>>(
        json_str,
    )
    .ok()
    .and_then(|r| r.data)
    .or_else(|| serde_json::from_str::<crate::core::stats::StatsSnapshot>(json_str).ok())
}

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
            match s {
                "STREAM_STOPPED" => break,
                payload if payload.starts_with('{') => {
                    if json {
                        println!("{payload}");
                    } else if let Some(snap) = parse_stats_snapshot(payload) {
                        output::print_monitor_frame(&snap, true);
                    }
                    let _ = std::io::Write::flush(&mut std::io::stdout());
                }
                _ => {}
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

    match parse_stats_snapshot(&resp) {
        Some(snap) => output::print_monitor_frame(&snap, false),
        None => print!("{resp}"),
    }

    Ok(())
}
