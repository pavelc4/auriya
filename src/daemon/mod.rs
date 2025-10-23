use anyhow::{Context, Result};
use std::{
    path::PathBuf,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::{signal, time};
use tracing::{debug, error, info, warn};

#[derive(Debug, Default, Clone)]
struct LastState {
    pkg: Option<String>,
    pid: Option<i32>,
    screen_awake: Option<bool>,
    battery_saver: Option<bool>,
    last_log_ms: Option<u128>,
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
            config_path: PathBuf::from("packages.toml"),
            log_level: LogLevel::Info,
            log_debounce_ms: 3000,
        }
    }
}

pub async fn run_with_config(cfg: &DaemonConfig) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya PID-only daemon with config: {:?}", cfg);

    let packages_cfg = crate::core::config::packages::PackageList::load_from_toml(&cfg.config_path)
        .with_context(|| format!("Failed to load packages from {:?}", cfg.config_path))?;

    let packages = packages_cfg.packages;
    info!(target: "auriya::daemon", "Loaded {} packages from config", packages.len());

    let mut last = LastState::default();
    let mut tick = time::interval(cfg.poll_interval);
    tick.tick().await;

    let shutdown = async {
        if let Err(e) = signal::ctrl_c().await {
            error!(target: "auriya::daemon", "Failed to listen for ctrl-c: {e}");
        }
        info!(target: "auriya::daemon", "Shutdown signal received");
    };

    tokio::select! {
        _ = async {
            loop {
                tick.tick().await;
                debug!(target: "auriya::daemon", "Tick");

                if let Err(e) = process_tick(&packages, &mut last, cfg).await {
                    error!(target: "auriya::daemon", "Tick processing error: {e:?}");
                }
            }
        } => {}
        _ = shutdown => {
            info!(target: "auriya::daemon", "Exiting daemon");
        }
    }
    Ok(())
}

async fn process_tick(packages: &[String], last: &mut LastState, cfg: &DaemonConfig) -> Result<()> {
    let power =
        crate::core::dumpsys::power::PowerState::fetch().context("Failed to fetch power state")?;

    let power_changed = last.screen_awake != Some(power.screen_awake)
        || last.battery_saver != Some(power.battery_saver);

    if !power.screen_awake {
        if power_changed {
            info!(target: "auriya::daemon", "Screen off — skip");
        }
        last.screen_awake = Some(power.screen_awake);
        last.battery_saver = Some(power.battery_saver);
        return Ok(());
    }

    if power.battery_saver {
        if power_changed {
            info!(target: "auriya::daemon", "Battery saver ON — skip");
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
        Err(e) => {
            return Err(e).context("Failed to get foreground package");
        }
    };

    if last.pkg.as_deref() == Some(pkg.as_str()) && last.pid.is_some() {
        debug!(target: "auriya::daemon", "Same foreground app with known PID, skipping lookup");
        return Ok(());
    }

    if packages.iter().any(|allowed| allowed == &pkg) {
        match crate::core::dumpsys::activity::get_app_pid(&pkg) {
            Ok(Some(pid)) => {
                let changed = last.pkg.as_deref() != Some(pkg.as_str()) || last.pid != Some(pid);
                if changed && should_log_change(last, cfg) {
                    info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
                    update_last_log_time(last);
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
            Err(e) => {
                error!(target: "auriya::daemon", "PID check error for {}: {e:?}", pkg);
            }
        }
    } else {
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
