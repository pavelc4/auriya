use crate::daemon::CurrentState;
use anyhow::Result;
use std::sync::atomic::{AtomicBool, Ordering};
use std::{
    path::Path,
    sync::{Arc, RwLock},
};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{UnixListener, UnixStream};
#[derive(Debug, Clone)]
pub enum LogLevelCmd {
    Debug,
    Info,
    Warn,
    Error,
}

pub struct IpcHandles {
    pub enabled: Arc<AtomicBool>,
    pub shared_packages: Arc<RwLock<Vec<String>>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub reload_fn: Arc<dyn Fn() -> Result<usize> + Send + Sync>,
    pub set_log_level: Arc<dyn Fn(LogLevelCmd) + Send + Sync>,
    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: String,
}

fn help_text() -> &'static str {
    "CMDS:\n\
     - HELP | ?\n\
     - STATUS\n\
     - ENABLE | DISABLE\n\
     - RELOAD\n\
     - SET_LOG <debug|info|warn|error>\n\
     - INJECT <package>\n\
     - CLEAR_INJECT\n\
     - GETPID\n\
     - PING\n\
     - QUIT\n
     - SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE>\n
     "
}

pub async fn start_ipc_socket<P: AsRef<Path>>(path: P, h: IpcHandles) -> Result<()> {
    let _ = std::fs::remove_file(&path);
    let listener = UnixListener::bind(&path)?;
    tracing::info!(target: "auriya::daemon", "IPC listening at {:?}", path.as_ref());
    
    loop {
        let (stream, _) = listener.accept().await?;
        let h_clone = IpcHandles {
            enabled: h.enabled.clone(),
            shared_packages: h.shared_packages.clone(),
            override_foreground: h.override_foreground.clone(),
            reload_fn: h.reload_fn.clone(),
            set_log_level: h.set_log_level.clone(),
            current_state: h.current_state.clone(),
            balance_governor: h.balance_governor.clone(), 
        };
        tokio::spawn(async move {
            if let Err(e) = handle_client(stream, h_clone).await {
                tracing::warn!(target: "auriya::daemon", "IPC client error: {:?}", e);
            }
        });
    }
}

async fn handle_client(stream: UnixStream, h: IpcHandles) -> Result<()> {
    let (r, mut w) = stream.into_split();
    let mut reader = BufReader::new(r);
    let mut line = String::new();
    w.write_all(b"OK AURIYA IPC\n").await?;

    while reader.read_line(&mut line).await? > 0 {
        let msg = line.trim();
        let mut parts = msg.split_whitespace();
        let cmd = parts.next().unwrap_or("").to_uppercase();

        match cmd.as_str() {
            "HELP" | "?" => w.write_all(help_text().as_bytes()).await?,
            "PING" => w.write_all(b"PONG\n").await?,
            "QUIT" => {
                w.write_all(b"BYE\n").await?;
                break; // keluar dari loop, tutup koneksi
            }
            "GETPID" => {
                let st = h.current_state.read().unwrap().clone();
                match (st.pkg, st.pid) {
                    (Some(p), Some(id)) => {
                        w.write_all(format!("PKG={} PID={}\n", p, id).as_bytes()).await?;
                    }
                    (Some(p), None) => {
                        w.write_all(format!("PKG={} PID=None\n", p).as_bytes()).await?;
                    }
                    _ => w.write_all(b"PKG=None PID=None\n").await?,
                }
            }
            "STATUS" => {
                let enabled = h.enabled.load(Ordering::Relaxed);
                let n = h.shared_packages.read().unwrap().len();
                let ov = h.override_foreground.read().unwrap().clone();
                w.write_all(
                    format!("ENABLED={enabled} PACKAGES={n} OVERRIDE={:?}\n", ov).as_bytes(),
                )
                .await?;
            }
            "ENABLE" => {
                h.enabled.store(true, Ordering::Relaxed);
                w.write_all(b"OK ENABLED\n").await?;
            }
            "DISABLE" => {
                h.enabled.store(false, Ordering::Relaxed);
                w.write_all(b"OK DISABLED\n").await?;
            }
            "RELOAD" => match (h.reload_fn)() {
                Ok(n) => w.write_all(format!("OK RELOADED {n}\n").as_bytes()).await?,
                Err(e) => w.write_all(format!("ERR RELOAD {e:?}\n").as_bytes()).await?,
            },
            "SET_LOG" => match parts.next().map(|s| s.to_lowercase()) {
                Some(lvl) if ["debug", "info", "warn", "error"].contains(&lvl.as_str()) => {
                    let m = match lvl.as_str() {
                        "debug" => LogLevelCmd::Debug,
                        "info" => LogLevelCmd::Info,
                        "warn" => LogLevelCmd::Warn,
                        _ => LogLevelCmd::Error,
                    };
                    (h.set_log_level)(m);
                    w.write_all(b"OK SET_LOG\n").await?;
                }
                _ => {
                    w.write_all(b"ERR SET_LOG usage: SET_LOG <debug|info|warn|error>\n").await?;
                }
            },
            "INJECT" => {
                if let Some(pkg) = parts.next() {
                    *h.override_foreground.write().unwrap() = Some(pkg.to_string());
                    w.write_all(b"OK INJECT\n").await?;
                } else {
                    w.write_all(b"ERR INJECT usage: INJECT <package>\n").await?;
                }
            }
            "CLEAR_INJECT" => {
                *h.override_foreground.write().unwrap() = None;
                w.write_all(b"OK CLEAR_INJECT\n").await?;
            }
            "SET_PROFILE" => {
                match parts.next().map(|s| s.to_uppercase()).as_deref() {
                    Some("PERFORMANCE") => {
                        if let Err(e) = crate::core::profile::apply_performance() {
                            w.write_all(format!("ERR SET_PROFILE {e:?}\n").as_bytes()).await?;
                        } else {
                            w.write_all(b"OK SET_PROFILE PERFORMANCE\n").await?;
                        }
                    }
                    Some("BALANCE") => {
                        if let Err(e) = crate::core::profile::apply_balance(&h.balance_governor) {
                            w.write_all(format!("ERR SET_PROFILE {e:?}\n").as_bytes()).await?;
                        } else {
                            w.write_all(b"OK SET_PROFILE BALANCE\n").await?;
                        }
                    }
                    Some("POWERSAVE") => {
                        if let Err(e) = crate::core::profile::apply_powersave() {
                            w.write_all(format!("ERR SET_PROFILE {e:?}\n").as_bytes()).await?;
                        } else {
                            w.write_all(b"OK SET_PROFILE POWERSAVE\n").await?;
                        }
                    }
                    _ => {
                        w.write_all(b"ERR SET_PROFILE usage: SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE>\n").await?;
                    }
                }
            }
            _ => {                w.write_all(b"ERR UNKNOWN COMMAND\n").await?;
            }
        }
        line.clear();
    }
    Ok(())
}