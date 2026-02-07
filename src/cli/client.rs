use crate::common::SOCKET_PATH;
use crate::{Context, Result};
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream;

pub struct IpcClient {
    socket_path: String,
}

impl IpcClient {
    pub fn new() -> Self {
        Self::with_path(SOCKET_PATH)
    }

    pub fn with_path(socket_path: impl Into<String>) -> Self {
        Self {
            socket_path: socket_path.into(),
        }
    }

    pub async fn send(&self, command: &str) -> Result<String> {
        let mut stream = UnixStream::connect(&self.socket_path)
            .await
            .context("Failed to connect to daemon. Is it running?")?;

        {
            let mut reader = tokio::io::BufReader::new(&mut stream);
            let mut greeting = String::new();
            let _ = reader.read_line(&mut greeting).await;
        }

        stream.write_all(command.as_bytes()).await?;
        stream.write_all(b"\n").await?;
        stream.shutdown().await?;

        let mut response = String::new();
        stream.read_to_string(&mut response).await?;

        Ok(response.trim().to_string())
    }

    pub async fn is_alive(&self) -> bool {
        UnixStream::connect(&self.socket_path).await.is_ok()
    }

    pub async fn ping(&self) -> Result<bool> {
        match self.send("PING").await {
            Ok(resp) => Ok(resp.contains("PONG")),
            Err(_) => Ok(false),
        }
    }
}

impl Default for IpcClient {
    fn default() -> Self {
        Self::new()
    }
}
