use anyhow::Result;
use std::{path::PathBuf, time::Duration};
use tokio::{signal, time};
use tracing::{error, info};

pub async fn run_pid_only_with_path(path_str: &str) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya PID-only daemon...");
    let pkg_path = PathBuf::from(path_str);
    let packages_cfg = crate::core::config::packages::PackageList::load_from_toml(&pkg_path)?;
    let packages = packages_cfg.packages;

    let mut tick = time::interval(Duration::from_secs(5));
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
                info!(target: "auriya::daemon", "Tick");

                match crate::core::dumpsys::foreground::get_foreground_package() {
                    Ok(Some(pkg)) => {
                        if packages.iter().any(|p| p == &pkg) {
                            match crate::core::dumpsys::activity::get_app_pid(&pkg) {
                                Ok(Some(pid)) => info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid),
                                Ok(None)      => info!(target: "auriya::daemon", "Foreground {} PID not found", pkg),
                                Err(e)        => error!(target: "auriya::daemon", "PID check error for {}: {e:?}", pkg),
                            }
                        } else {
                            info!(target: "auriya::daemon", "Foreground {} (not in whitelist) — ignored", pkg);
                        }
                    }
                    Ok(None) => info!(target: "auriya::daemon", "No foreground app detected"),
                    Err(e)   => error!(target: "auriya::daemon", "Foreground detect error: {e:?}"),
                }
            }
        } => {}
        _ = shutdown => {
            info!(target: "auriya::daemon", "Exiting daemon");
        }
    }
    Ok(())
}

pub async fn run_pid_only() -> Result<()> {
    run_pid_only_with_path("packages.toml").await
}
