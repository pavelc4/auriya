use crate::daemon::model::event::{DaemonEvent, EventSender};
use notify::{RecursiveMode, Watcher};
use std::path::PathBuf;
use tracing::{debug, error, warn};

pub const MODULE_PATH: &str = "/data/adb/modules/auriya";

/// Watch the module directory for a staged update.
///
/// Magisk/KernelSU drops an empty `update` file inside the module path
/// when a new version is flashed; the running daemon must stop gracefully
/// so the replacement binary takes over on the next boot. On a host where
/// the module path does not exist (development), the watch simply fails to
/// arm and the thread exits — the daemon keeps running normally.
fn perform_module_removal_cleanup() {
    warn!(target: "auriya::daemon", "Module removal detected, immediately uninstalling manager app and cleaning up");
    let _ = std::process::Command::new("/system/bin/sh")
        .arg("-c")
        .arg("export PATH=\"/system/bin:/system/xbin:$PATH\"; \
              am force-stop dev.auriya.app 2>/dev/null; \
              am force-stop dev.auriya.app.debug 2>/dev/null; \
              pm uninstall dev.auriya.app 2>/dev/null || pm uninstall --user 0 dev.auriya.app 2>/dev/null || cmd package uninstall dev.auriya.app 2>/dev/null; \
              pm uninstall dev.auriya.app.debug 2>/dev/null || pm uninstall --user 0 dev.auriya.app.debug 2>/dev/null || cmd package uninstall dev.auriya.app.debug 2>/dev/null; \
              rm -f /dev/socket/auriya.sock; \
              rm -rf /data/adb/.config/auriya /data/adb/auriya; \
              rm -f /data/adb/ksu/bin/auriya /data/adb/ap/bin/auriya /data/adb/ksu/bin/auriyactl /data/adb/ap/bin/auriyactl; \
              rm -f /data/local/tmp/auriya*")
        .status();
    std::process::exit(0);
}

pub fn start_module_update_watcher(event_tx: EventSender) {
    let modpath = PathBuf::from(MODULE_PATH);

    std::thread::spawn(move || {
        let tx = event_tx;
        let mut watcher = match notify::recommended_watcher(
            move |res: Result<notify::Event, notify::Error>| {
                let Ok(event) = res else {
                    return;
                };
                let is_remove = event
                    .paths
                    .iter()
                    .any(|p| p.file_name().is_some_and(|n| n == "remove"));
                if is_remove {
                    std::thread::spawn(perform_module_removal_cleanup);
                    return;
                }

                let is_update = event
                    .paths
                    .iter()
                    .any(|p| p.file_name().is_some_and(|n| n == "update"));
                if is_update {
                    warn!(target: "auriya::daemon", "Module update staged, requesting graceful stop");
                    let _ = tx.blocking_send(DaemonEvent::ModuleUpdate);
                }
            },
        ) {
            Ok(w) => w,
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to create module update watcher: {e}");
                return;
            }
        };

        if let Err(e) = watcher.watch(&modpath, RecursiveMode::NonRecursive) {
            warn!(
                target: "auriya::daemon",
                "Module update watch unavailable ({}): {e}",
                modpath.display()
            );
        }

        debug!(target: "auriya::daemon", "Module update watcher started ({})", modpath.display());
        loop {
            std::thread::sleep(std::time::Duration::from_secs(2));
            if modpath.join("remove").exists() {
                perform_module_removal_cleanup();
            }
        }
    });
}
