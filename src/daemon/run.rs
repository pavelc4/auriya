use anyhow::Result;
use std::{
    path::PathBuf,
    sync::{Arc, Mutex, RwLock},
    time::Duration,
};
use tokio::{signal, time};
use tracing::{debug, error, info, warn};
use crate::daemon::state::{CurrentState, LastState};
use crate::core::profile::ProfileMode;


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
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis()
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


pub async fn run_with_config_and_logger(cfg: &DaemonConfig, _reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg).await
}

pub async fn run_with_config(cfg: &DaemonConfig) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya daemon...");
    let initial = crate::core::config::packages::PackageList::load_from_toml(&cfg.config_path)?;
    let balance_governor = initial.settings.default_governor.clone().unwrap_or_else(|| "schedutil".into());

    let shared_config: Arc<RwLock<_>> = Arc::new(RwLock::new(initial));
    let shared_current = Arc::new(RwLock::new(CurrentState::default()));
    let override_foreground: Arc<RwLock<Option<String>>> = Arc::new(RwLock::new(None));
    let last = Arc::new(Mutex::new(LastState::default()));

    info!(target: "auriya::daemon", "Tick loop started (interval: {:?})", cfg.poll_interval);
    let mut tick = time::interval(cfg.poll_interval);
    tick.tick().await;

    loop {
        tokio::select! {
            _ = tick.tick() => {
                debug!(target: "auriya::daemon", "Tick");

                let snapshot = match shared_config.read() {
                    Ok(g) => g.clone(),
                    Err(_) => { warn!(target: "auriya::daemon", "Config lock poisoned, skip tick"); continue; }
                };

                let mut st = match last.lock() {
                    Ok(s) => s,
                    Err(_) => { warn!(target: "auriya::daemon", "LastState lock poisoned, skip tick"); continue; }
                };

                if let Err(e) = process_tick(&snapshot, &mut *st, cfg, &balance_governor, &override_foreground).await {
                    error!(target: "auriya::daemon", "Tick error: {:?}", e);
                } else if let Ok(mut cur) = shared_current.write() {
                    cur.pkg = st.pkg.clone();
                    cur.pid = st.pid;
                    cur.screen_awake = st.screen_awake.unwrap_or(false);
                    cur.battery_saver = st.battery_saver.unwrap_or(false);
                    cur.profile = st.profile_mode.unwrap_or(ProfileMode::Balance);
                }
            }
            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Shutdown");
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
                    if should_log_change(last, cfg) { info!(target: "auriya::daemon", "No foreground app detected"); bump_log(last); }
                    last.pkg = None; last.pid = None;
                }
                return Ok(());
            }
        }
    }
    let pkg = pkg_opt.unwrap();

    if last.pkg.as_deref() == Some(pkg.as_str()) && last.pid.is_some() {
        debug!(target: "auriya::daemon", "Same app with known PID; skip");
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
                    if should_log_change(last, cfg) { warn!(target: "auriya::daemon", "Foreground {} PID not found", pkg); bump_log(last); }
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
            if should_log_change(last, cfg) { info!(target: "auriya::daemon", "Foreground {} (not whitelisted)", pkg); bump_log(last); }
        }
        last.pkg = Some(pkg);
        last.pid = None;
    }
    Ok(())
}
