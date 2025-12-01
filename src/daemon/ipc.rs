use crate::core::config::gamelist::GameList;
use crate::daemon::state::CurrentState;
use anyhow::Result;
use log::{error, info};
use std::os::unix::fs::PermissionsExt;
use std::sync::atomic::{AtomicBool, Ordering};
use std::{
    path::Path,
    str::FromStr,
    sync::{Arc, RwLock},
};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::{UnixListener, UnixStream};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogLevelCmd {
    Debug,
    Info,
    Warn,
    Error,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProfileMode {
    Performance,
    Balance,
    Powersave,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Command {
    Help,
    Status,
    Enable,
    Disable,
    Reload,
    SetLog(LogLevelCmd),
    Inject(String),
    ClearInject,
    GetPid,
    Ping,
    Quit,
    SetProfile(ProfileMode),
    AddGame(String),
    RemoveGame(String),
    ListPackages,
    GetGameList,
    UpdateGame(String, Option<String>, Option<bool>),
}

impl FromStr for Command {
    type Err = &'static str;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let parts: Vec<&str> = s.split_whitespace().collect();
        match parts.as_slice() {
            ["HELP"] | ["?"] => Ok(Command::Help),
            ["STATUS"] => Ok(Command::Status),
            ["ENABLE"] => Ok(Command::Enable),
            ["DISABLE"] => Ok(Command::Disable),
            ["RELOAD"] => Ok(Command::Reload),
            ["GETPID"] | ["GET_PID"] => Ok(Command::GetPid),
            ["PING"] => Ok(Command::Ping),
            ["QUIT"] => Ok(Command::Quit),
            ["LIST_PACKAGES"] | ["LISTPACKAGES"] => Ok(Command::ListPackages),
            ["GET_GAMELIST"] | ["GETGAMELIST"] => Ok(Command::GetGameList),

            ["SETLOG", level] | ["SET_LOG", level] => match level.to_uppercase().as_str() {
                "DEBUG" => Ok(Command::SetLog(LogLevelCmd::Debug)),
                "INFO" => Ok(Command::SetLog(LogLevelCmd::Info)),
                "WARN" => Ok(Command::SetLog(LogLevelCmd::Warn)),
                "ERROR" => Ok(Command::SetLog(LogLevelCmd::Error)),
                _ => Err("usage: SETLOG <DEBUG|INFO|WARN|ERROR>"),
            },

            ["INJECT", pkg] => Ok(Command::Inject(pkg.to_string())),
            ["CLEAR_INJECT"] | ["CLEARINJECT"] => Ok(Command::ClearInject),

            ["SET_PROFILE", mode] | ["SETPROFILE", mode] => match mode.to_uppercase().as_str() {
                "PERFORMANCE" => Ok(Command::SetProfile(ProfileMode::Performance)),
                "BALANCE" => Ok(Command::SetProfile(ProfileMode::Balance)),
                "POWERSAVE" => Ok(Command::SetProfile(ProfileMode::Powersave)),
                _ => Err("usage: SETPROFILE <PERFORMANCE|BALANCE|POWERSAVE>"),
            },

            ["ADD_GAME", pkg] | ["ADDGAME", pkg] => Ok(Command::AddGame(pkg.to_string())),
            ["REMOVE_GAME", pkg] | ["REMOVEGAME", pkg] => Ok(Command::RemoveGame(pkg.to_string())),
            ["UPDATE_GAME", pkg, rest @ ..] | ["UPDATEGAME", pkg, rest @ ..] => {
                let mut governor = None;
                let mut dnd = None;

                for arg in rest {
                    if let Some(gov) = arg.strip_prefix("gov=") {
                        governor = Some(gov.to_string());
                    } else if let Some(dnd_val) = arg.strip_prefix("dnd=") {
                        dnd = Some(dnd_val.parse::<bool>().unwrap_or(true));
                    }
                }

                Ok(Command::UpdateGame(pkg.to_string(), governor, dnd))
            }

            _ => Err("unknown command (try HELP)"),
        }
    }
}

pub struct IpcHandles {
    pub enabled: Arc<AtomicBool>,
    pub shared_config: Arc<RwLock<GameList>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub reload_fn: Arc<dyn Fn() -> anyhow::Result<usize> + Send + Sync>,
    pub set_log_level: Arc<dyn Fn(LogLevelCmd) + Send + Sync>,
    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: String,
}

const HELP: &str = "CMDS:
        - HELP | ?
        - STATUS
        - ENABLE | DISABLE
        - RELOAD
        - SETLOG <DEBUG|INFO|WARN|ERROR>
        - INJECT <pkg>
        - CLEAR_INJECT
        - GETPID
        - PING
        - QUIT
        - SET_PROFILE <PERFORMANCE|BALANCE|POWERSAVE>
        - ADD_GAME <pkg>
        - REMOVE_GAME <pkg>
 ";

pub async fn start<P: AsRef<Path>>(path: P, h: IpcHandles) -> Result<()> {
    let path_ref = path.as_ref();
    let _ = std::fs::remove_file(path_ref);
    let listener = UnixListener::bind(path_ref)?;
    let _ = std::fs::set_permissions(path_ref, std::fs::Permissions::from_mode(0o666));
    tracing::info!(target: "auriya::daemon", "IPC listening at {:?}", path_ref);

    loop {
        let (stream, _) = listener.accept().await?;
        let hc = IpcHandles {
            enabled: h.enabled.clone(),
            shared_config: h.shared_config.clone(),
            override_foreground: h.override_foreground.clone(),
            reload_fn: h.reload_fn.clone(),
            set_log_level: h.set_log_level.clone(),
            current_state: h.current_state.clone(),
            balance_governor: h.balance_governor.clone(),
        };
        tokio::spawn(async move {
            if let Err(e) = handle_client(stream, hc).await {
                tracing::warn!(target: "auriya::daemon", "client error: {:?}", e);
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
        let s = line.trim();
        if s.len() > 256 {
            w.write_all(b"ERR input too long\n").await?;
            line.clear();
            continue;
        }
        let resp = match s.parse::<Command>() {
            Ok(Command::Help) => HELP.to_string(),
            Ok(Command::Ping) => "PONG\n".into(),
            Ok(Command::Quit) => {
                w.write_all(b"BYE\n").await?;
                break;
            }
            Ok(Command::GetPid) => {
                let st = h
                    .current_state
                    .read()
                    .ok()
                    .map(|g| g.clone())
                    .unwrap_or_default();
                match (st.pkg, st.pid) {
                    (Some(p), Some(id)) => format!("PKG={} PID={}\n", p, id),
                    (Some(p), None) => format!("PKG={} PID=None\n", p),
                    _ => "PKG=None PID=None\n".into(),
                }
            }
            Ok(Command::Status) => {
                let enabled = h.enabled.load(Ordering::Acquire);
                let n = h
                    .shared_config
                    .read()
                    .ok()
                    .map(|c| c.game.len())
                    .unwrap_or(0);
                let ov = h.override_foreground.read().ok().and_then(|o| o.clone());
                format!("ENABLED={} PACKAGES={} OVERRIDE={:?}\n", enabled, n, ov)
            }
            Ok(Command::Enable) => {
                h.enabled.store(true, Ordering::Release);
                "OK ENABLED\n".into()
            }
            Ok(Command::Disable) => {
                h.enabled.store(false, Ordering::Release);
                "OK DISABLED\n".into()
            }
            Ok(Command::Reload) => match (h.reload_fn)() {
                Ok(n) => format!("OK RELOADED {}\n", n),
                Err(e) => format!("ERR RELOAD {:?}\n", e),
            },
            Ok(Command::SetLog(lvl)) => {
                (h.set_log_level)(lvl);
                "OK SET_LOG\n".into()
            }
            Ok(Command::Inject(pkg)) => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = Some(pkg);
                }
                "OK INJECT\n".into()
            }
            Ok(Command::ClearInject) => {
                if let Ok(mut g) = h.override_foreground.write() {
                    *g = None;
                }
                "OK CLEAR_INJECT\n".into()
            }
            Ok(Command::SetProfile(mode)) => {
                use crate::core::profile;
                let r = match mode {
                    ProfileMode::Performance => profile::apply_performance(),
                    ProfileMode::Balance => profile::apply_balance(&h.balance_governor),
                    ProfileMode::Powersave => profile::apply_powersave(),
                };
                match r {
                    Ok(_) => format!("OK SET_PROFILE {:?}\n", mode),
                    Err(e) => format!("ERR SET_PROFILE {:?}\n", e),
                }
            }
            Ok(Command::AddGame(pkg)) => {
                use crate::core::config::gamelist::GameProfile;
                let mut gl = h.shared_config.write().unwrap();
                let profile = GameProfile {
                    package: pkg.clone(),
                    cpu_governor: "performance".to_string(),
                    enable_dnd: true,
                };
                match gl.add(profile) {
                    Ok(_) => {
                        if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                            format!("ERR SAVE_GAMELIST {:?}\n", e)
                        } else {
                            format!("OK ADD_GAME {}\n", pkg)
                        }
                    }
                    Err(e) => format!("ERR ADD_GAME {:?}\n", e),
                }
            }
            Ok(Command::RemoveGame(pkg)) => {
                let mut gl = h.shared_config.write().unwrap();
                match gl.remove(&pkg) {
                    Ok(_) => {
                        if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                            format!("ERR SAVE_GAMELIST {:?}\n", e)
                        } else {
                            format!("OK REMOVE_GAME {}\n", pkg)
                        }
                    }
                    Err(e) => format!("ERR REMOVE_GAME {:?}\n", e),
                }
            }
            Ok(Command::ListPackages) => {
                use tokio::process::Command as TokioCommand;
                info!(target: "auriya::ipc", "Executing ListPackages...");
                match TokioCommand::new("pm")
                    .arg("list")
                    .arg("packages")
                    .output()
                    .await
                {
                    Ok(output) => {
                        let stdout = String::from_utf8_lossy(&output.stdout);
                        info!(target: "auriya::ipc", "ListPackages success, len: {}", stdout.len());
                        // Return raw output, client will parse
                        format!("{}\n", stdout)
                    }
                    Err(e) => {
                        error!(target: "auriya::ipc", "ListPackages failed: {:?}", e);
                        format!("ERR LIST_PACKAGES {:?}\n", e)
                    }
                }
            }

            Ok(Command::GetGameList) => {
                let gl = h.shared_config.read().unwrap();
                match serde_json::to_string(&gl.game) {
                    Ok(json) => format!("{}\n", json),
                    Err(e) => format!("ERR GET_GAMELIST {:?}\n", e),
                }
            }
            Ok(Command::UpdateGame(pkg, gov, dnd)) => {
                let mut gl = h.shared_config.write().unwrap();
                match gl.update(&pkg, gov, dnd) {
                    Ok(_) => {
                        if let Err(e) = gl.save(crate::core::config::gamelist_path()) {
                            format!("ERR SAVE_GAMELIST {:?}\n", e)
                        } else {
                            format!("OK UPDATE_GAME {}\n", pkg)
                        }
                    }
                    Err(e) => format!("ERR UPDATE_GAME {:?}\n", e),
                }
            }
            Err(e) => format!("ERR {}\n", e),
        };
        if !resp.is_empty() {
            w.write_all(resp.as_bytes()).await?;
        }
        line.clear();
    }
    Ok(())
}
