use anyhow::Result;
use std::{path::PathBuf, sync::{Arc, RwLock}, time::Duration};
use tokio::{signal, time};
use tracing::{debug, error, info};
use crate::daemon::state::{CurrentState, LastState};


#[derive(Debug, Clone, Default)]
pub struct DaemonConfig {
    pub poll_interval: Duration,
    pub config_path: PathBuf,
    pub log_debounce_ms: u128,
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
fn bump_log(last: &mut LastState) { last.last_log_ms = Some(now_ms()); }

pub async fn run_with_config(cfg: &DaemonConfig) -> Result<()> {
    let initial = crate::core::config::packages::PackageList::load_from_toml(&cfg.config_path)?;
    let balance_governor = initial.settings.default_governor.clone().unwrap_or_else(|| "schedutil".into());
    let shared_config: Arc<RwLock<_>> = Arc::new(RwLock::new(initial));
    let shared_current = Arc::new(RwLock::new(CurrentState::default()));
    let last = Arc::new(std::sync::Mutex::new(LastState::default()));


    let mut tick = time::interval(cfg.poll_interval);
    tick.tick().await;
    loop {
        tokio::select! {
            _ = tick.tick() => {
                debug!(target: "auriya::daemon", "Tick");
                let snapshot = shared_config.read().ok().map(|g| g.clone());
                if snapshot.is_none() { continue; }
                let snapshot = snapshot.unwrap();

                if let Ok(mut st) = last.lock() {
                    if let Err(e) = process_tick(&snapshot, &mut *st, cfg, &balance_governor).await {
                        error!(target: "auriya::daemon", "Tick error: {:?}", e);
                    } else if let Ok(mut cur) = shared_current.write() {
                        cur.pkg = st.pkg.clone();
                        cur.pid = st.pid;
                        cur.screen_awake = st.screen_awake.unwrap_or(false);
                        cur.battery_saver = st.battery_saver.unwrap_or(false);
                    }
                }
            }
            _ = signal::ctrl_c() => { info!(target:"auriya::daemon","Shutdown"); break; }
        }
    }
    Ok(())
}

pub async fn process_tick(
    config: &crate::core::config::packages::PackageList,
    last: &mut LastState,
    cfg: &DaemonConfig,
    balance_governor: &str,
) -> anyhow::Result<()> {
    use tracing::{info, warn, error};
    use crate::core::profile::{self, ProfileMode};

    let power = crate::core::dumpsys::power::PowerState::fetch()?;
    let power_changed = last.screen_awake != Some(power.screen_awake)
        || last.battery_saver != Some(power.battery_saver);

    if !power.screen_awake || power.battery_saver {
        let target = ProfileMode::Powersave;
        if last.profile_mode != Some(target) {
            if let Err(e) = profile::apply_powersave() { error!(?e, "apply POWERSAVE failed"); }
            else { info!("Applied POWERSAVE"); last.profile_mode = Some(target); }
        }
        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
        return Ok(());
    }
    if power_changed {
        info!("Screen ON & saver OFF");
        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
    }

    let mut pkg_opt: Option<String> = None;
    if pkg_opt.is_none() {
        match crate::core::dumpsys::foreground::get_foreground_package()? {
            Some(p) => pkg_opt = Some(p),
            None => {
                if last.profile_mode != Some(ProfileMode::Balance) {
                    if let Err(e) = profile::apply_balance(balance_governor) { error!(?e, "apply BALANCE failed"); }
                    else { info!("Applied BALANCE (no foreground)"); last.profile_mode = Some(ProfileMode::Balance); }
                }
                if last.pkg.is_some() || last.pid.is_some() {
                    if should_log_change(last, cfg) { info!("No foreground app"); bump_log(last); }
                    last.pkg = None; last.pid = None;
                }
                return Ok(());
            }
        }
    }
    let pkg = pkg_opt.unwrap();

    if last.pkg.as_deref() == Some(pkg.as_str()) && last.pid.is_some() {
        return Ok(());
    }

    let allowed = config.get_packages();
    if allowed.iter().any(|a| a == &pkg) {
        match crate::core::dumpsys::activity::get_app_pid(&pkg)? {
            Some(pid) => {
                let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid != Some(pid);
                if changed && should_log_change(last, cfg) {
                    info!("Foreground {} PID={}", pkg, pid); bump_log(last);
                }

                let game_cfg = config.get_game_config(&pkg);
                let governor = game_cfg.and_then(|c| c.cpu_governor.as_deref()).unwrap_or("performance");
                let enable_dnd = game_cfg.and_then(|c| c.enable_dnd).unwrap_or(true);
                if last.profile_mode != Some(ProfileMode::Performance) {
                    if let Err(e) = profile::apply_performance_with_config(governor, enable_dnd) { error!(?e, "apply PERF failed"); }
                    else { info!("Applied PERFORMANCE for {} (governor: {}, dnd: {})", pkg, governor, enable_dnd);
                        last.profile_mode = Some(ProfileMode::Performance);
                    }
                }
                last.pkg = Some(pkg);
                last.pid = Some(pid);
            }
            None => {
                if last.profile_mode != Some(ProfileMode::Balance) {
                    if let Err(e) = profile::apply_balance(balance_governor) { error!(?e, "apply BALANCE failed"); }
                    else { info!("Applied BALANCE (pid not found)"); last.profile_mode = Some(ProfileMode::Balance); }
                }
                if last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some() {
                    if should_log_change(last, cfg) { warn!("Foreground {} PID not found", pkg); bump_log(last); }
                }
                last.pkg = Some(pkg);
                last.pid = None;
            }
        }
    } else {
        if last.profile_mode != Some(ProfileMode::Balance) {
            if let Err(e) = profile::apply_balance(balance_governor) { error!(?e, "apply BALANCE failed"); }
            else { info!("Applied BALANCE (not whitelisted)"); last.profile_mode = Some(ProfileMode::Balance); }
        }
        if last.pkg.as_deref() != Some(pkg.as_str()) || last.pid.is_some() {
            if should_log_change(last, cfg) { info!("Foreground {} (not whitelisted)", pkg); bump_log(last); }
        }
        last.pkg = Some(pkg);
        last.pid = None;
    }
    Ok(())
}
