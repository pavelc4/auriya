use anyhow::{Context, Result};
use notify::{Config, Event, EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::{path::PathBuf, sync::mpsc, thread, time::Duration};
use tokio::sync::mpsc as other_mpsc;

use std::time::SystemTime;
use tokio::{signal, time};
use tracing::{debug, error, info, warn};
type SharedConfig = Arc<RwLock<crate::core::config::packages::PackageList>>;

use std::time::UNIX_EPOCH;
use tracing_subscriber::fmt::time::SystemTime as OtherSystemTime;
pub mod ipc;

#[derive(Debug, Default, Clone)]
struct LastState {
    pkg: Option<String>,
    pid: Option<i32>,
    screen_awake: Option<bool>,
    battery_saver: Option<bool>,
    last_log_ms: Option<u128>,
    profile_mode: Option<crate::core::profile::ProfileMode>,
}

#[derive(Debug, Default, Clone)]
pub struct CurrentState {
    pub pkg: Option<String>,
    pub pid: Option<i32>,
    pub screen_awake: bool,
    pub battery_saver: bool,
}

#[derive(Debug, Clone)]
pub struct DaemonConfig {
    pub poll_interval: Duration,
    pub config_path: PathBuf,
    pub log_level: LogLevel,
    pub log_debounce_ms: u128,
}

#[derive(Debug, Clone)]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        Self {
            poll_interval: Duration::from_secs(5),
            config_path: PathBuf::from("Packages.toml"),
            log_level: LogLevel::Info,
            log_debounce_ms: 500,
        }
    }
}

fn start_packages_watcher(
    path: PathBuf,
    shared: SharedConfig,
    on_reload: impl Fn(usize) + Send + 'static,
) -> notify::Result<RecommendedWatcher> {
    let (tx, rx) = mpsc::channel();
    let mut watcher = RecommendedWatcher::new(tx, Config::default())?;
    watcher.watch(&path, RecursiveMode::NonRecursive)?;

    thread::spawn(move || {
        let mut last = std::time::Instant::now();
        while let Ok(event) = rx.recv() {
            if let Ok(Event {
                kind: EventKind::Modify(_),
                ..
            }) = event
            {
                let now = std::time::Instant::now();
                if now.duration_since(last) < Duration::from_millis(300) {
                    continue;
                }
                last = now;
                match crate::core::config::packages::PackageList::load_from_toml(&path) {
                    Ok(new_cfg) => {
                        let count = new_cfg.games.len();
                        let mut guard = shared.write().unwrap();
                        *guard = new_cfg;
                        on_reload(count);
                    }
                    Err(e) => {
                        tracing::warn!(target: "auriya::daemon", "Config reload failed: {e:?}");
                    }
                }
            }
        }
    });

    Ok(watcher)
}

pub async fn run_with_config(cfg: &DaemonConfig) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya daemon with config: {:?}", cfg);

    let initial = crate::core::config::packages::PackageList::load_from_toml(&cfg.config_path)
        .with_context(|| format!("Failed to load packages from {:?}", cfg.config_path))?;

    let balance_governor = initial.settings.default_governor
        .clone()
        .unwrap_or_else(|| "schedutil".to_string());
    info!(target: "auriya::daemon", "Balance mode will use governor: {}", balance_governor);

    let shared_config: SharedConfig = Arc::new(RwLock::new(initial.clone()));
    info!(target: "auriya::daemon", "Loaded {} games from config", initial.games.len());

    let last = Arc::new(Mutex::new(LastState::default()));

    let sp_clone = shared_config.clone();
    let last_clone = last.clone();
    let _watch = start_packages_watcher(
        cfg.config_path.clone(),
        shared_config.clone(),
        move |n| {
            tracing::info!(target: "auriya::daemon", "Config modified -> reloaded {} games", n);
            if let Ok(mut st) = last_clone.lock() {
                st.pkg = None;
                st.pid = None;
                st.last_log_ms = None;
            }
        }
    )?;

    let enabled = Arc::new(AtomicBool::new(true));
    let override_foreground = Arc::new(RwLock::new(None::<String>));

    let path_reload = cfg.config_path.clone();
    let sp_reload = shared_config.clone();
    let reload_fn = Arc::new(move || {
        let loaded = crate::core::config::packages::PackageList::load_from_toml(&path_reload)?;
        let count = loaded.games.len();
        let mut guard = sp_reload.write().unwrap();
        *guard = loaded;
        Ok(count)
    });

    let set_log_level = Arc::new(|_lvl: crate::daemon::ipc::LogLevelCmd| {
        tracing::info!(target: "auriya::daemon", "SET_LOG accepted (no-op)");
    });

    let shared_current = Arc::new(RwLock::new(CurrentState::default()));

    let shared_packages_for_ipc = {
        let config = shared_config.read().unwrap();
        Arc::new(RwLock::new(config.get_packages()))
    };

    let ipc_handles = crate::daemon::ipc::IpcHandles {
        enabled: enabled.clone(),
        shared_packages: shared_packages_for_ipc,
        override_foreground: override_foreground.clone(),
        reload_fn: reload_fn.clone(),
        set_log_level,
        current_state: shared_current.clone(),
        balance_governor: balance_governor.clone(),
    };

    let ipc_path = PathBuf::from("/dev/socket/auriya");
    tokio::spawn(async move {
        if let Err(e) = crate::daemon::ipc::start_ipc_socket(ipc_path, ipc_handles).await {
            tracing::error!(target: "auriya::daemon", "IPC server error: {:?}", e);
        }
    });

    let mut tick = time::interval(cfg.poll_interval);
    tick.tick().await;

    let shutdown = async {
        if let Err(e) = signal::ctrl_c().await {
            error!(target: "auriya::daemon", "Failed to listen for ctrl-c: {e}");
        }
        info!(target: "auriya::daemon", "Shutdown signal received");
    };

    let gov_for_tick = balance_governor.clone();
    tokio::select! {
        _ = async {
            loop {
                tick.tick().await;
                debug!(target: "auriya::daemon", "Tick");

                let config_snapshot = {
                    let guard = sp_clone.read().unwrap();
                    guard.clone()
                };

                if let Ok(mut st) = last.lock() {
                    if let Err(e) = process_tick(&config_snapshot, &mut *st, cfg, &gov_for_tick).await {
                        error!(target: "auriya::daemon", "Tick processing error: {e:?}");
                    }
                }
            }
        } => {}
        _ = shutdown => {
            info!(target: "auriya::daemon", "Exiting daemon");
        }
    }
    Ok(())
}

async fn process_tick(
    config: &crate::core::config::packages::PackageList,
    last: &mut LastState,
    cfg: &DaemonConfig,
    balance_governor: &str,
) -> Result<()> {
    use crate::core::profile::{self, ProfileMode};

    let power = crate::core::dumpsys::power::PowerState::fetch()
        .context("Failed to fetch power state")?;

    let power_changed = last.screen_awake != Some(power.screen_awake)
        || last.battery_saver != Some(power.battery_saver);

    if !power.screen_awake || power.battery_saver {
        let target_mode = ProfileMode::Powersave;
        if last.profile_mode != Some(target_mode) {
            if let Err(e) = profile::apply_powersave() {
                error!(target: "auriya::daemon", "Failed to apply profile: {e:?}");
            } else {
                info!(target: "auriya::daemon", "Applied POWERSAVE (screen off / saver on)");
                last.profile_mode = Some(target_mode);
            }
        }

        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
        return Ok(());
    }

    if power_changed {
        info!(target: "auriya::daemon", "Screen on + saver OFF — proceed");
    }
    last.screen_awake = Some(power.screen_awake);
    last.battery_saver = Some(power.battery_saver);

    let pkg = match crate::core::dumpsys::foreground::get_foreground_package() {
        Ok(Some(pkg)) => pkg,
        Ok(None) => {
            let target_mode = ProfileMode::Balance;
            if last.profile_mode != Some(target_mode) {
                if let Err(e) = profile::apply_balance(balance_governor) {
                    error!(target: "auriya::daemon", "Failed to apply profile: {e:?}");
                } else {
                    info!(target: "auriya::daemon", "Applied BALANCE (no foreground app)");
                    last.profile_mode = Some(target_mode);
                }
            }

            if last.pkg.is_some() || last.pid.is_some() {
                if should_log_change(last, cfg) {
                    info!(target: "auriya::daemon", "No foreground app detected");
                    update_last_log_time(last);
                }
                last.pkg = None;
                last.pid = None;
            }
            return Ok(());
        }
        Err(e) => return Err(e).context("Failed to get foreground package"),
    };

    if last.pkg.as_deref() == Some(pkg.as_str()) && last.pid.is_some() {
        debug!(target: "auriya::daemon", "Same foreground app with known PID, skipping lookup");
        return Ok(());
    }

    let packages = config.get_packages();
    if packages.iter().any(|allowed| allowed == &pkg) {
        match crate::core::dumpsys::activity::get_app_pid(&pkg) {
            Ok(Some(pid)) => {
                let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid != Some(pid);
                if changed && should_log_change(last, cfg) {
                    info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
                    update_last_log_time(last);
                }

                let target_mode = ProfileMode::Performance;
                if last.profile_mode != Some(target_mode) {
                    let game_config = config.get_game_config(&pkg);
                    let governor = game_config
                        .and_then(|c| c.cpu_governor.as_deref())
                        .unwrap_or("performance");
                    let enable_dnd = game_config
                        .and_then(|c| c.enable_dnd)
                        .unwrap_or(true);

                    if let Err(e) = profile::apply_performance_with_config(governor, enable_dnd) {
                        error!(target: "auriya::daemon", "Failed to apply PERFORMANCE: {e:?}");
                    } else {
                        info!(
                            target: "auriya::daemon",
                            "Applied PERFORMANCE for {} (governor: {}, dnd: {})",
                            pkg, governor, enable_dnd
                        );
                        last.profile_mode = Some(target_mode);
                    }
                }

                last.pkg = Some(pkg);
                last.pid = Some(pid);
            }
            Ok(None) => {
                let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some();
                if changed && should_log_change(last, cfg) {
                    warn!(target: "auriya::daemon", "Foreground {} PID not found", pkg);
                    update_last_log_time(last);
                }
                last.pkg = Some(pkg);
                last.pid = None;
            }
            Err(e) => error!(target: "auriya::daemon", "PID check error for {}: {e:?}", pkg),
        }
    } else {
        let target_mode = ProfileMode::Balance;
        if last.profile_mode != Some(target_mode) {
            if let Err(e) = profile::apply_balance(balance_governor) {
                error!(target: "auriya::daemon", "Failed to apply BALANCE: {e:?}");
            } else {
                debug!(target: "auriya::daemon", "Applied BALANCE (non-whitelisted)");
                last.profile_mode = Some(target_mode);
            }
        }

        let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some();
        if changed && should_log_change(last, cfg) {
            debug!(target: "auriya::daemon", "Foreground {} (not in whitelist) — ignored", pkg);
            update_last_log_time(last);
        }
        last.pkg = Some(pkg);
        last.pid = None;
    }

    Ok(())
}

fn should_log_change(last: &LastState, cfg: &DaemonConfig) -> bool {
    let now_ms = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis();

    match last.last_log_ms {
        Some(last_ms) => now_ms - last_ms >= cfg.log_debounce_ms,
        None => true,
    }
}

fn update_last_log_time(last: &mut LastState) {
    last.last_log_ms = Some(
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis(),
    );
}

pub async fn run_pid_only_with_path(path_str: &str) -> Result<()> {
    let cfg = DaemonConfig {
        config_path: PathBuf::from(path_str),
        ..Default::default()
    };
    run_with_config(&cfg).await
}

pub async fn run_pid_only() -> Result<()> {
    run_with_config(&DaemonConfig::default()).await
}

impl DaemonConfig {
    pub async fn run(&self) -> Result<()> {
        run_with_config(self).await
    }
}
