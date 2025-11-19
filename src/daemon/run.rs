use anyhow::Result;
use std::{
    path::PathBuf,
    sync::{Arc, Mutex, RwLock},
    sync::atomic::AtomicBool,
    time::Duration,
};
use tokio::{signal, time};
use tracing::{debug, error, info, warn};
use crate::daemon::state::{CurrentState, LastState};
use crate::core::profile::ProfileMode;
use tracing_subscriber::{EnvFilter};
use notify::{Watcher, RecursiveMode, EventKind};

#[derive(Debug, Clone)]
pub struct DaemonConfig {
    pub poll_interval: Duration,
    pub config_path: PathBuf,
    pub log_debounce_ms: u128,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        Self {
            poll_interval: Duration::from_secs(2),
            config_path: PathBuf::from("/data/adb/.config/auriya/auriya.toml"),
            log_debounce_ms: 2000,
        }
    }
}

#[inline]
fn now_ms() -> u128 {
    use std::time::{SystemTime, UNIX_EPOCH, Duration};

    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|e| {
            tracing::warn!(
                target: "auriya::daemon",
                "System clock error: {}. Using fallback timestamp.",
                e
            );
            Duration::from_secs(0)
        })
        .as_millis()
}

#[inline]
fn should_log_change(last: &LastState, cfg: &DaemonConfig) -> bool {
    match last.last_log_ms {
        None => true,
        Some(t) => now_ms().saturating_sub(t) >= cfg.log_debounce_ms,
    }
}

#[inline]
fn bump_log(last: &mut LastState) {
    last.last_log_ms = Some(now_ms());
}

pub type ReloadHandle = tracing_subscriber::reload::Handle<
    tracing_subscriber::EnvFilter,
    tracing_subscriber::Registry,
>;

pub async fn run_with_config_and_logger(cfg: &DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: &DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya daemon...");
    let initial = crate::core::config::packages::PackageList::load_from_toml(&cfg.config_path)?;
    let balance_governor = initial.settings.default_governor.clone().unwrap_or_else(|| "schedutil".into());

    let shared_config: Arc<RwLock<_>> = Arc::new(RwLock::new(initial));
    let shared_current = Arc::new(RwLock::new(CurrentState::default()));
    let override_foreground: Arc<RwLock<Option<String>>> = Arc::new(RwLock::new(None));
    let last = Arc::new(Mutex::new(LastState::default()));

    let fas_controller = {
        let cfg_guard = shared_config.read().map_err(|_| anyhow::anyhow!("Config lock poisoned"))?;
        if cfg_guard.settings.fas_enabled {
            info!(target: "auriya::daemon", "FAS enabled (mode: {})", cfg_guard.settings.fas_mode);
            Some(Arc::new(Mutex::new(crate::daemon::fas::FasController::new())))
        } else {
            info!(target: "auriya::daemon", "FAS disabled");
            None
        }
    };

    info!(target: "auriya::daemon", "Setting up IPC socket...");

    let ipc_handles = crate::daemon::ipc::IpcHandles {
        enabled: Arc::new(AtomicBool::new(true)),
        shared_config: shared_config.clone(),
        override_foreground: override_foreground.clone(),
        reload_fn: Arc::new({
            let cfg_path = cfg.config_path.clone();
            let shared = shared_config.clone();
            move || {
                match crate::core::config::packages::PackageList::load_from_toml(&cfg_path) {
                    Ok(new_cfg) => {
                        if let Ok(mut g) = shared.write() {
                            let count = new_cfg.games.len();
                            *g = new_cfg;
                            Ok(count)
                        } else {
                            Err(anyhow::anyhow!("Config lock poisoned"))
                        }
                    }
                    Err(e) => Err(e),
                }
            }
        }),

        set_log_level: Arc::new({
            let handle = filter_handle.clone();
            move |lvl| {
                use crate::daemon::ipc::LogLevelCmd;

                let filter_str = match lvl {
                    LogLevelCmd::Debug => "debug",
                    LogLevelCmd::Info => "info",
                    LogLevelCmd::Warn => "warn",
                    LogLevelCmd::Error => "error",
                };

                match handle.reload(EnvFilter::new(filter_str)) {
                    Ok(_) => {
                        info!(
                            target: "auriya::ipc",
                            "Log level changed to {:?}",
                            lvl
                        );
                    }
                    Err(e) => {
                        error!(
                            target: "auriya::ipc",
                            "Failed to change log level: {}",
                            e
                        );
                    }
                }
            }
        }),
        current_state: shared_current.clone(),
        balance_governor: balance_governor.clone(),
    };

    tokio::spawn(async move {
        info!(target: "auriya::daemon", "Starting IPC socket listener...");
        match crate::daemon::ipc::start("/dev/socket/auriya.sock", ipc_handles).await {
            Ok(_) => info!(target: "auriya::daemon", "IPC listener stopped gracefully"),
            Err(e) => error!(target: "auriya::daemon", "IPC error: {:?}", e),
        }
    });

    tokio::time::sleep(tokio::time::Duration::from_millis(200)).await;
    info!(target: "auriya::daemon", "IPC socket should be ready at /dev/socket/auriya.sock");

    info!(target: "auriya::daemon", "Tick loop started (interval: {:?})", cfg.poll_interval);
    let mut tick = time::interval(cfg.poll_interval);
    tick.tick().await;
    let mut last_error: Option<(String, u128)> = None;
    let error_debounce_ms: u128 = 30_000;
    let (watch_tx, mut watch_rx) = tokio::sync::mpsc::channel::<()>(10);
    let config_path_for_watcher = cfg.config_path.clone();
    let shared_for_watcher = shared_config.clone();

    std::thread::spawn(move || {
        let tx = watch_tx;
        let shared = shared_for_watcher;
        let path = config_path_for_watcher.clone();
        let path_for_closure = config_path_for_watcher;

        let mut watcher = match notify::recommended_watcher(move |res: Result<notify::Event, notify::Error>| {
            if let Ok(event) = res {
                if matches!(event.kind, EventKind::Modify(_)) {
                    info!(target: "auriya::daemon", "Config file changed, reloading...");
                    let max_retries = 3;
                    let mut retry_count = 0;
                    let mut success = false;

                    while retry_count < max_retries && !success {
                        match crate::core::config::packages::PackageList::load_from_toml(&path_for_closure) {
                            Ok(new_cfg) => {
                                match shared.write() {
                                    Ok(mut g) => {
                                        let count = new_cfg.games.len();
                                        *g = new_cfg;
                                        info!(target: "auriya::daemon", "Config reloaded: {} games", count);
                                        success = true;
                                    }
                                    Err(_) => {
                                        error!(target: "auriya::daemon", "Failed to acquire config lock");
                                        break;
                                    }
                                }
                            }
                            Err(e) => {
                                retry_count += 1;
                                if retry_count < max_retries {
                                    warn!(
                                        target: "auriya::daemon",
                                        "Failed to reload config (attempt {}/{}): {:?}. Retrying in 2s...",
                                        retry_count,
                                        max_retries,
                                        e
                                    );
                                    std::thread::sleep(std::time::Duration::from_secs(2));
                                } else {
                                    error!(
                                        target: "auriya::daemon",
                                        "Failed to reload config after {} attempts: {:?}",
                                        max_retries,
                                        e
                                    );
                                }
                            }
                        }
                    }

                    let _ = tx.blocking_send(());
                }
            }
        }) {
            Ok(w) => w,
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to create file watcher: {}", e);
                return;
            }
        };

        if let Err(e) = watcher.watch(&path, RecursiveMode::NonRecursive) {
            error!(target: "auriya::daemon", "Failed to watch config file: {}", e);
            return;
        }

        info!(target: "auriya::daemon", "Config file watcher started");

        loop {
            std::thread::sleep(std::time::Duration::from_secs(3600));
        }
    });

    tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;

    loop {
        tokio::select! {
            _ = tick.tick() => {
                debug!(target: "auriya::daemon", "Tick");

                let snapshot = match shared_config.read() {
                    Ok(g) => g.clone(),
                    Err(_) => {
                        warn!(target: "auriya::daemon", "Config lock poisoned, skip tick");
                        continue;
                    }
                };

                let mut st = match last.lock() {
                    Ok(s) => s,
                    Err(_) => {
                        warn!(target: "auriya::daemon", "LastState lock poisoned, skip tick");
                        continue;
                    }
                };

                if let Err(e) = process_tick(
                    &snapshot,
                    &mut *st,
                    cfg,
                    &balance_governor,
                    &override_foreground,
                    &fas_controller,
                ).await {
                    // Debounced error logging
                    let err_msg = e.to_string();
                    let now = now_ms();

                    let should_log = match &last_error {
                        None => true,
                        Some((last_msg, last_time)) => {
                            err_msg != *last_msg || (now.saturating_sub(*last_time) >= error_debounce_ms)
                        }
                    };

                    if should_log {
                        error!(
                            target: "auriya::daemon",
                            "Tick error: {:?}",
                            e
                        );
                        last_error = Some((err_msg, now));
                    } else {
                        debug!(
                            target: "auriya::daemon",
                            "Tick error (suppressed): {:?}",
                            e
                        );
                    }
                } else if let Ok(mut cur) = shared_current.write() {
                    cur.pkg = st.pkg.clone();
                    cur.pid = st.pid;
                    cur.screen_awake = st.screen_awake.unwrap_or(false);
                    cur.battery_saver = st.battery_saver.unwrap_or(false);
                    cur.profile = st.profile_mode.unwrap_or(ProfileMode::Balance);
                }
            }
            Some(_) = watch_rx.recv() => {
                 debug!(target: "auriya::daemon", "Config reload notification received");
            }

            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Received Ctrl-C, shutting down...");
                break;
            }
        }
    }

    info!(target: "auriya::daemon", "Daemon stopped");
    Ok(())
}

pub async fn process_tick(
    config: &crate::core::config::packages::PackageList,
    last: &mut LastState,
    cfg: &DaemonConfig,
    balance_governor: &str,
    override_foreground: &Arc<RwLock<Option<String>>>,
    fas_controller: &Option<Arc<Mutex<crate::daemon::fas::FasController>>>,
) -> anyhow::Result<()> {
    use crate::core::profile;

    let power = crate::core::dumpsys::power::PowerState::fetch()?;
    let power_changed = last.screen_awake != Some(power.screen_awake)
        || last.battery_saver != Some(power.battery_saver);

    if !power.screen_awake || power.battery_saver {
        let target = ProfileMode::Powersave;
        if last.profile_mode != Some(target) {
            if let Err(e) = profile::apply_powersave() {
                error!(target: "auriya::profile", ?e, "Failed to apply POWERSAVE");
            } else {
                info!(target: "auriya::daemon", "Applied POWERSAVE (screen: {}, saver: {})", power.screen_awake, power.battery_saver);
                last.profile_mode = Some(target);
            }
        }
        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
        return Ok(());
    }

    if power_changed {
        info!(target: "auriya::daemon", "Screen ON & saver OFF");
        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
    }

    let mut pkg_opt: Option<String> = override_foreground.read().ok().and_then(|o| o.clone());
    if pkg_opt.is_none() {
        match crate::core::dumpsys::foreground::get_foreground_package()? {
            Some(p) => pkg_opt = Some(p),
            None => {
                if last.profile_mode != Some(ProfileMode::Balance) {
                    if let Err(e) = profile::apply_balance(balance_governor) {
                        error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
                    } else {
                        info!(target: "auriya::daemon", "Applied BALANCE (no foreground)");
                        last.profile_mode = Some(ProfileMode::Balance);
                    }
                }
                if last.pkg.is_some() || last.pid.is_some() {
                    if should_log_change(last, cfg) {
                        info!(target: "auriya::daemon", "No foreground app detected");
                        bump_log(last);
                    }
                    last.pkg = None;
                    last.pid = None;
                }
                return Ok(());
            }
        }
    }
    let pkg = pkg_opt.unwrap();

    if last.pkg.as_deref() == Some(pkg.as_str()) && last.pid.is_some() {
        if let Some(fas) = fas_controller {
            if config.get_packages().iter().any(|a| a == &pkg) {
                let game_cfg = config.get_game_config(&pkg);
                let governor = game_cfg.and_then(|c| c.cpu_governor.as_deref()).unwrap_or("performance");

                match run_fas_tick(config, fas, governor, balance_governor, &mut last.profile_mode) {
                    Ok(_) => {
                        debug!(target: "auriya::fas", "FAS tick completed");
                    }
                    Err(e) => {
                        warn!(target: "auriya::fas", "FAS tick error: {:?}", e);
                    }
                }
            }
        }
        debug!(target: "auriya::daemon", "Same app with known PID; skip profile reapply");
        return Ok(());
    }

    let allowed = config.get_packages();
    if allowed.iter().any(|a| a == &pkg) {
        match crate::core::dumpsys::activity::get_app_pid(&pkg)? {
            Some(pid) => {
                let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid != Some(pid);
                if changed && should_log_change(last, cfg) {
                    info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
                    bump_log(last);
                }

                let game_cfg = config.get_game_config(&pkg);
                let governor = game_cfg.and_then(|c| c.cpu_governor.as_deref()).unwrap_or("performance");
                let enable_dnd = game_cfg.and_then(|c| c.enable_dnd).unwrap_or(true);

                if last.profile_mode != Some(ProfileMode::Performance) {
                    if let Err(e) = profile::apply_performance_with_config(governor, enable_dnd) {
                        error!(target: "auriya::profile", ?e, "Failed to apply PERFORMANCE");
                    } else {
                        info!(target: "auriya::daemon", "Applied PERFORMANCE for {} (governor: {}, dnd: {})", pkg, governor, enable_dnd);
                        last.profile_mode = Some(ProfileMode::Performance);
                    }
                }

                last.pkg = Some(pkg);
                last.pid = Some(pid);
            }
            None => {
                if last.profile_mode != Some(ProfileMode::Balance) {
                    if let Err(e) = profile::apply_balance(balance_governor) {
                        error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
                    } else {
                        info!(target: "auriya::daemon", "Applied BALANCE (PID not found)");
                        last.profile_mode = Some(ProfileMode::Balance);
                    }
                }
                if last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some() {
                    if should_log_change(last, cfg) {
                        warn!(target: "auriya::daemon", "Foreground {} PID not found", pkg);
                        bump_log(last);
                    }
                }
                last.pkg = Some(pkg);
                last.pid = None;
            }
        }
    } else {
        if last.profile_mode != Some(ProfileMode::Balance) {
            if let Err(e) = profile::apply_balance(balance_governor) {
                error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
            } else {
                info!(target: "auriya::daemon", "Applied BALANCE (not whitelisted)");
                last.profile_mode = Some(ProfileMode::Balance);
            }
        }
        if last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some() {
            if should_log_change(last, cfg) {
                info!(target: "auriya::daemon", "Foreground {} (not whitelisted)", pkg);
                bump_log(last);
            }
        }
        last.pkg = Some(pkg);
        last.pid = None;
    }
    Ok(())
}

fn run_fas_tick(
    config: &crate::core::config::packages::PackageList,
    fas: &Arc<Mutex<crate::daemon::fas::FasController>>,
    game_governor: &str,
    balance_governor: &str,
    last_profile: &mut Option<ProfileMode>,
) -> anyhow::Result<bool> {
    use crate::core::{profile, scaling::ScalingAction};

    let margin = config.get_fas_margin(None);
    let thermal_thresh = config.get_fas_thermal();

    let mut fas_guard = fas.lock().map_err(|_| anyhow::anyhow!("FAS lock poisoned"))?;
    let action = fas_guard.tick(margin, thermal_thresh)?;

    match action {
        ScalingAction::Boost => {
            if *last_profile != Some(ProfileMode::Performance) {
                info!(target: "auriya::fas", "FAS decision: BOOST → applying PERFORMANCE");
                profile::apply_performance_with_config(game_governor, true)?;
                *last_profile = Some(ProfileMode::Performance);
            } else {
                debug!(target: "auriya::fas", "FAS decision: BOOST → already PERFORMANCE, skip");
            }
            Ok(true)
        }
        ScalingAction::Maintain => {
            debug!(target: "auriya::fas", "FAS decision: MAINTAIN → no change");
            Ok(true)
        }
        ScalingAction::Reduce => {
            if *last_profile != Some(ProfileMode::Balance) {
                info!(target: "auriya::fas", "FAS decision: REDUCE → applying BALANCE");
                profile::apply_balance(balance_governor)?;
                *last_profile = Some(ProfileMode::Balance);
            } else {
                debug!(target: "auriya::fas", "FAS decision: REDUCE → already BALANCE, skip");
            }
            Ok(true)
        }
    }
}
