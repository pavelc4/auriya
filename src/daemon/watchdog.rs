use crate::core::system_status::SystemStatusCache;
use std::time::{Duration, Instant};
use tracing::{error, info, warn};

/// Companion health check interval (in ticks). At 1s tick interval,
/// this checks companion health roughly every 10 seconds.
pub const COMPANION_HEALTH_CHECK_TICKS: u64 = 10;

/// Maximum time since the last status file update before we assume
/// the companion process is dead and attempt to restart it.
pub const COMPANION_STALE_TIMEOUT: Duration = Duration::from_secs(10);

/// Minimum interval between companion restart attempts.
pub const COMPANION_RESTART_COOLDOWN_SECS: u64 = 30;

/// Companion process name (matches `--nice-name` in service.sh).
pub const COMPANION_PROC: &str = "AuriyaSysMon";

/// Companion APK path (matches `COMPANION_APK` in service.sh).
pub const COMPANION_APK: &str = "/data/adb/modules/auriya/system/etc/auriya/service.apk";

/// Watchdog responsible for monitoring companion health and managing restarts.
pub struct CompanionWatchdog {
    pub alive: bool,
    pub last_restart: Option<Instant>,
}

impl Default for CompanionWatchdog {
    fn default() -> Self {
        Self::new()
    }
}

impl CompanionWatchdog {
    pub fn new() -> Self {
        Self {
            alive: true,
            last_restart: None,
        }
    }

    /// Check companion health via the status file timestamp.
    pub fn check_health(&mut self, status_cache: &SystemStatusCache) {
        let elapsed = status_cache.elapsed_since_last_event();
        let alive = elapsed < COMPANION_STALE_TIMEOUT;
        let was_alive = self.alive;

        self.alive = alive;
        crate::core::profile::set_companion_alive(alive);

        match (was_alive, alive) {
            (true, false) => {
                warn!(
                    target: "auriya::companion",
                    "Companion appears dead (no status update for {:.1}s). Restarting...",
                    elapsed.as_secs_f64()
                );
                self.restart_companion();
            }
            (false, false) => {
                let cooldown = Duration::from_secs(COMPANION_RESTART_COOLDOWN_SECS);
                match self.last_restart {
                    Some(last) if last.elapsed() < cooldown => {
                        // Cooldown active, skip
                    }
                    _ => {
                        warn!(
                            target: "auriya::companion",
                            "Companion still dead (no update for {:.1}s), retrying restart...",
                            elapsed.as_secs_f64()
                        );
                        self.restart_companion();
                    }
                }
            }
            (false, true) => {
                info!(target: "auriya::companion", "Companion is alive again");
            }
            (true, true) => {}
        }
    }

    /// React to the companion's liveness lock being released.
    pub fn on_companion_died(&mut self) {
        if !self.alive {
            return;
        }
        warn!(
            target: "auriya::companion",
            "Companion liveness lock released — service died. Restarting..."
        );
        self.alive = false;
        crate::core::profile::set_companion_alive(false);
        self.restart_companion();
    }

    /// Spawn a fresh companion process.
    pub fn restart_companion(&mut self) {
        self.last_restart = Some(Instant::now());

        let _ = std::process::Command::new("killall")
            .args(["-q", "-TERM", COMPANION_PROC])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();
        std::thread::sleep(Duration::from_millis(500));
        let _ = std::process::Command::new("killall")
            .args(["-q", "-KILL", COMPANION_PROC])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();

        let companion_log = "/data/adb/auriya/companion.log";
        let cmd = format!(
            "nohup app_process -Djava.class.path={apk} \
             /system/bin --nice-name={proc} \
             dev.auriya.service.Main >> {log} 2>&1 &",
            apk = COMPANION_APK,
            proc = COMPANION_PROC,
            log = companion_log,
        );

        match std::process::Command::new("sh").args(["-c", &cmd]).spawn() {
            Ok(_) => info!(target: "auriya::companion", "Companion restart spawned"),
            Err(e) => error!(target: "auriya::companion", "Failed to spawn companion restart: {e}"),
        }
    }
}
