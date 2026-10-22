use super::handlers::handle_client;
use super::types::IpcHandles;
use anyhow::Result;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use tokio::net::UnixListener;

pub async fn start<P: AsRef<Path>>(path: P, h: IpcHandles) -> Result<()> {
    let path_ref = path.as_ref();
    let _ = std::fs::remove_file(path_ref);
    let listener = UnixListener::bind(path_ref)?;
    let _ = std::fs::set_permissions(path_ref, std::fs::Permissions::from_mode(0o666));
    tracing::debug!(target: "auriya::daemon", "IPC listening at {:?}", path_ref);

    loop {
        let (stream, _) = listener.accept().await?;
        let hc = h.clone();
        tokio::spawn(async move {
            if let Err(e) = handle_client(stream, hc).await {
                if let Some(io_err) = e.downcast_ref::<std::io::Error>() {
                    match io_err.kind() {
                        std::io::ErrorKind::ConnectionReset | std::io::ErrorKind::BrokenPipe => {
                            tracing::debug!(target: "auriya::daemon", "client disconnected: {io_err}");
                            return;
                        }
                        _ => {}
                    }
                }
                tracing::warn!(target: "auriya::daemon", "client error: {e:#}");
            }
        });
    }
}
