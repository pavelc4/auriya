use crate::core::fps_meter::FpsMeter;
use crate::core::profile::ProfileMode;
use crate::core::system_status::watcher::COMPANION_STALE_TIMEOUT;
use crate::core::system_status::{STATUS_FILE, SystemStatusCache};
use crate::core::telemetry::TelemetryHub;
use crate::core::tweaks::vendor::{detect, mtk};
use crate::daemon::event::{self, DaemonEvent, EventSender};
use crate::daemon::state::{CurrentState, LastState};
use anyhow::Result;
use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, RwLock};
use std::time::Duration;
use tokio::{signal, time};
use tracing::{debug, error, info, warn};
use tracing_subscriber::EnvFilter;

type AsyncFpsCallback = Arc<
    dyn Fn(u32) -> std::pin::Pin<Box<dyn std::future::Future<Output = ()> + Send + Sync>>
        + Send
        + Sync,
>;
type AsyncGetFpsCallback = Arc<
    dyn Fn() -> std::pin::Pin<Box<dyn std::future::Future<Output = u32> + Send + Sync>>
        + Send
        + Sync,
>;

const INGAME_INTERVAL_MS: u64 = 500;
const NORMAL_INTERVAL_MS: u64 = 5000;
const SCREEN_OFF_INTERVAL_MS: u64 = 10000;

/// How often to re-check whether the companion process is still alive.
/// The daemon checks `elapsed_since_last_event()` against
/// `COMPANION_STALE_TIMEOUT` and relaunches the service if it exceeds
/// the threshold.
/// At 500ms tick (in-game), 24 ticks = 12s. At 5s tick (idle), 24 ticks = 120s.
pub(crate) const COMPANION_HEALTH_CHECK_TICKS: u64 = 24;

/// Minimum interval between companion restart attempts.
const COMPANION_RESTART_COOLDOWN_SECS: u64 = 30;

/// Companion process name (matches `--nice-name` in service.sh).
const COMPANION_PROC: &str = "AuriyaSysMon";
/// Companion APK path (matches `COMPANION_APK` in service.sh).
const COMPANION_APK: &str = "/data/adb/modules/auriya/system/etc/auriya/service.apk";

pub(crate) fn update_current_profile_file(mode: ProfileMode) {
    let val = match mode {
        ProfileMode::Performance => "1",
        ProfileMode::Balance => "2",
        ProfileMode::Powersave => "3",
    };

    let config_path = crate::core::config::CONFIG_DIR;
    let profile_file = format!("{}/current_profile", config_path);

    let _ = std::fs::create_dir_all(config_path);

    if let Err(e) = std::fs::write(&profile_file, val) {
        error!(target: "auriya::daemon", "Failed to update current_profile: {}", e);
    } else {
        debug!(target: "auriya::daemon", "Updated current_profile to {} ({})", val, mode);
    }
}

pub use crate::daemon::config::DaemonConfig;

#[inline]
pub(crate) fn now_ms() -> u128 {
    use std::time::{Duration, SystemTime, UNIX_EPOCH};

    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_else(|_e| {
            tracing::warn!(
                target: "auriya::daemon",
                "Daemon | System clock error (using fallback)"
            );
            Duration::from_secs(0)
        })
        .as_millis()
}

#[inline]
pub(crate) fn should_log_change(last: &LastState, cfg: &DaemonConfig) -> bool {
    match last.last_log_ms {
        None => true,
        Some(t) => now_ms().saturating_sub(t) >= cfg.log_debounce_ms,
    }
}

#[inline]
pub(crate) fn bump_log(last: &mut LastState) {
    last.last_log_ms = Some(now_ms());
}

pub type ReloadHandle =
    tracing_subscriber::reload::Handle<tracing_subscriber::EnvFilter, tracing_subscriber::Registry>;

pub struct Daemon {
    pub(crate) cfg: DaemonConfig,
    pub(crate) _shared_settings: Arc<RwLock<crate::core::config::Settings>>,
    pub(crate) shared_gamelist: Arc<RwLock<crate::core::config::GameList>>,
    pub(crate) shared_current: Arc<RwLock<CurrentState>>,
    pub(crate) override_foreground: Arc<RwLock<Option<String>>>,

    pub(crate) last: LastState,
    pub(crate) last_error: Option<(String, u128)>,
    pub(crate) error_debounce_ms: u128,
    pub(crate) tick_count: u64,
    pub(crate) companion_alive: bool,
    /// `Instant` of the last companion restart attempt, used to
    /// throttle retries to at most one per `COMPANION_RESTART_COOLDOWN_SECS`.
    companion_restart_cooldown: Option<std::time::Instant>,

    pub(crate) fas_controller: Option<Arc<tokio::sync::Mutex<crate::daemon::fas::FasController>>>,
    pub(crate) balance_governor: String,
    pub(crate) default_mode: ProfileMode,
    pub(crate) supported_modes: Arc<Vec<crate::core::display::DisplayMode>>,
    /// Currently-active refresh-rate override (Hz) we've asked the
    /// companion to apply. `None` means we have not pushed a custom rate
    /// since the daemon started or since the last release.
    pub(crate) applied_refresh_rate: Option<u32>,
    pub(crate) cached_whitelist: HashSet<String>,
    pub(crate) status_cache: SystemStatusCache,
    pub(crate) vendor_lock: crate::core::tweaks::vendor_lock::VendorLock,
    /// Cached FPS config string to avoid resetting FAS state every tick.
    pub(crate) last_fps_config: Option<String>,
    pub(crate) pid_tracker: Option<crate::core::pid_tracker::PidTracker>,
    pub(crate) ceiling_controller: crate::core::tweaks::ceiling::CeilingController,
    pub(crate) ceiling_config: crate::core::tweaks::ceiling::CeilingConfig,
    pub(crate) current_ceiling: Option<crate::core::tweaks::ceiling::CeilingLevel>,
    pub(crate) telemetry_hub: TelemetryHub,
    pub(crate) fps_meter: FpsMeter,
    pub(crate) ebpf: Option<crate::core::ebpf::EbpfFrameStream>,
    /// Producer side of the out-of-band event channel. Cloned to the
    /// background threads (PID tracker, companion lock watcher) so they
    /// can wake the tick loop instantly.
    pub(crate) event_tx: EventSender,
}

impl Daemon {
    pub fn new(
        cfg: DaemonConfig,
        supported_modes: Arc<Vec<crate::core::display::DisplayMode>>,
        status_cache: SystemStatusCache,
        event_tx: EventSender,
    ) -> Result<Self> {
        let shared_settings = Arc::new(RwLock::new(cfg.settings.clone()));
        let shared_gamelist = Arc::new(RwLock::new(cfg.gamelist.clone()));
        let shared_current = Arc::new(RwLock::new(CurrentState::default()));
        let override_foreground = Arc::new(RwLock::new(None));

        let balance_governor = cfg.settings.cpu.default_governor.clone();
        let default_mode = cfg
            .settings
            .daemon
            .default_mode
            .parse::<ProfileMode>()
            .unwrap_or(ProfileMode::Balance);
        debug!(target: "auriya::daemon", "Default mode: {:?}", default_mode);

        let (fas_controller, fps_meter, ebpf) = {
            let e = match crate::core::ebpf::EbpfFrameStream::new() {
                Ok(e) => {
                    debug!(target: "auriya::daemon", "eBPF frame stream ready");
                    Some(e)
                }
                Err(err) => {
                    tracing::warn!(
                        target: "auriya::daemon",
                        "eBPF frame stream unavailable: {err:#}. FPS=sysfs-only, FAS disabled."
                    );
                    None
                }
            };

            let ebpf_rx = e.as_ref().map(|e| e.subscribe());
            let fps = FpsMeter::new(ebpf_rx);

            let fas = if cfg.settings.fas.enabled {
                if let Some(ref listener) = e {
                    debug!(target: "auriya::daemon", "FAS enabled with eBPF");
                    let rx = listener.subscribe();
                    Some(Arc::new(tokio::sync::Mutex::new(
                        crate::daemon::fas::FasController::with_target_fps(rx, 60),
                    )))
                } else {
                    tracing::warn!(
                        target: "auriya::daemon",
                        "FAS    | requested but eBPF unavailable. Continuing without FAS."
                    );
                    None
                }
            } else {
                debug!(target: "auriya::daemon", "FAS disabled");
                None
            };

            (fas, fps, e)
        };

        let cached_whitelist: HashSet<String> = cfg
            .gamelist
            .game
            .iter()
            .map(|g| g.package.clone())
            .collect();

        let ceiling_config = {
            let c = &cfg.settings.ceiling;
            crate::core::tweaks::ceiling::CeilingConfig {
                default: c
                    .default
                    .parse::<crate::core::tweaks::ceiling::CeilingLevel>()
                    .unwrap_or(crate::core::tweaks::ceiling::CeilingLevel::Balance),
                low_freq_little_khz: c.low_freq_little_khz,
                low_freq_big_khz: c.low_freq_big_khz,
            }
        };

        let core_layout = crate::core::tweaks::ceiling::CoreLayout::detect();

        Ok(Self {
            cfg,
            _shared_settings: shared_settings,
            shared_gamelist,
            shared_current,
            override_foreground,
            last: LastState::default(),
            last_error: None,
            error_debounce_ms: 30_000,
            fas_controller,
            balance_governor,
            default_mode,
            supported_modes,
            applied_refresh_rate: None,
            cached_whitelist,
            tick_count: 0,
            companion_alive: true,
            companion_restart_cooldown: None,
            status_cache,
            vendor_lock: crate::core::tweaks::vendor_lock::VendorLock::new(),
            last_fps_config: None,
            pid_tracker: None,
            ceiling_controller: crate::core::tweaks::ceiling::CeilingController::new(),
            ceiling_config,
            current_ceiling: None,
            telemetry_hub: TelemetryHub::new(&core_layout),
            fps_meter,
            ebpf,
            event_tx,
        })
    }
    #[inline]
    pub fn is_in_game_session(&self) -> bool {
        self.last
            .pkg
            .as_ref()
            .map(|pkg| self.cached_whitelist.contains(pkg))
            .unwrap_or(false)
            && self.last.pid.is_some()
    }

    pub(crate) fn set_pid(&mut self, pid: Option<i32>) {
        self.last.pid = pid;
        self.pid_tracker = pid.map(|p| {
            crate::core::pid_tracker::PidTracker::spawn(p, self.event_tx.clone())
        });
    }

    #[inline]
    pub fn is_suspended(&self) -> bool {
        !self.last.screen_awake.unwrap_or(true) || self.last.battery_saver.unwrap_or(false)
    }

    fn reload_settings(&mut self) {
        match crate::core::config::Settings::load(crate::core::config::settings_path()) {
            Ok(new_settings) => {
                if self.balance_governor != new_settings.cpu.default_governor {
                    self.balance_governor = new_settings.cpu.default_governor.clone();
                    debug!(target: "auriya::daemon", "Settings reloaded. New default governor: {}", self.balance_governor);

                    if self.last.profile_mode == Some(ProfileMode::Balance) {
                        debug!(target: "auriya::daemon", "Applying new default governor immediately...");
                        if let Err(e) = crate::core::profile::apply_balance(&self.balance_governor)
                        {
                            error!(target: "auriya::profile", ?e, "Failed to apply new balance governor");
                        }
                    }
                }

                let new_default_mode = new_settings
                    .daemon
                    .default_mode
                    .parse::<ProfileMode>()
                    .unwrap_or(ProfileMode::Balance);

                if self.default_mode != new_default_mode {
                    debug!(target: "auriya::daemon", "Settings reloaded. New default mode: {:?} → {:?}", self.default_mode, new_default_mode);
                    self.default_mode = new_default_mode;
                }
            }
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to reload settings: {:?}", e);
            }
        }
    }

    fn rebuild_whitelist(&mut self) {
        let whitelist: Option<HashSet<String>> = self
            .shared_gamelist
            .read()
            .ok()
            .map(|gl| gl.game.iter().map(|g| g.package.clone()).collect());
        if let Some(list) = whitelist {
            self.cached_whitelist = list;
            self.last.pkg = None;
            self.set_pid(None);
            debug!(target: "auriya::daemon", "Whitelist cache rebuilt: {} packages (forcing re-detect)", self.cached_whitelist.len());
        }
    }

    pub async fn init_ipc(&self, filter_handle: ReloadHandle) {
        let fas_clone_for_ipc = self.fas_controller.clone();
        let set_fps: AsyncFpsCallback = Arc::new(move |fps: u32| {
            let fas = fas_clone_for_ipc.clone();
            Box::pin(async move {
                if let Some(fas) = &fas {
                    let mut guard = fas.lock().await;
                    guard.set_target_fps(fps);
                }
            })
        });

        let fas_clone_for_get = self.fas_controller.clone();
        let get_fps: AsyncGetFpsCallback = Arc::new(move || {
            let fas = fas_clone_for_get.clone();
            Box::pin(async move {
                if let Some(fas) = &fas {
                    let guard = fas.lock().await;
                    return guard.get_target_fps();
                }
                60
            })
        });

        let shared_cfg = self.shared_gamelist.clone();
        let reload_fn = Arc::new(move || {
            match crate::core::config::GameList::load(crate::core::config::gamelist_path()) {
                Ok(new_cfg) => {
                    if let Ok(mut g) = shared_cfg.write() {
                        let count = new_cfg.game.len();
                        *g = new_cfg;
                        Ok(count)
                    } else {
                        Err(anyhow::anyhow!("Gamelist lock poisoned"))
                    }
                }
                Err(e) => Err(e),
            }
        });

        let current_log_level = Arc::new(RwLock::new(crate::daemon::ipc::LogLevelCmd::Info));
        let log_level_clone = current_log_level.clone();

        let handle = filter_handle.clone();
        let set_log_level = Arc::new(move |lvl| {
            use crate::daemon::ipc::LogLevelCmd;
            let filter_str = match lvl {
                LogLevelCmd::Debug => "debug",
                LogLevelCmd::Info => "info",
                LogLevelCmd::Warn => "warn",
                LogLevelCmd::Error => "error",
            };
            if let Ok(mut l) = log_level_clone.write() {
                *l = lvl;
            }
            match handle.reload(EnvFilter::new(filter_str)) {
                Ok(_) => debug!(target: "auriya::ipc", "Log level changed to {:?}", lvl),
                Err(e) => {
                    error!(target: "auriya::ipc", "Failed to change log level: {}", e)
                }
            }
        });

        let current_state = self.shared_current.clone();
        let cfg = &self.cfg;

        let ipc_handles = crate::daemon::ipc::IpcHandles {
            enabled: Arc::new(AtomicBool::new(true)),
            shared_config: self.shared_gamelist.clone(),
            override_foreground: self.override_foreground.clone(),
            reload_fn,
            set_log_level,
            set_fps,
            get_fps,
            current_state: current_state.clone(),
            balance_governor: cfg.settings.cpu.default_governor.clone(),
            current_log_level,
            supported_modes: self.supported_modes.clone(),
        };

        tokio::spawn(async move {
            debug!(target: "auriya::daemon", "Starting IPC socket listener...");
            match crate::daemon::ipc::start("/dev/socket/auriya.sock", ipc_handles).await {
                Ok(_) => info!(target: "auriya::daemon", "IPC    | Listener stopped"),
                Err(e) => error!(target: "auriya::daemon", "IPC    | Error: {:?}", e),
            }
        });
    }

    /// Check whether the Android companion service is still alive by
    /// inspecting how long since the last status file update. If it
    /// has been longer than [`COMPANION_STALE_TIMEOUT`] we assume the
    /// process was killed and try to restart it.
    ///
    /// Called every [`COMPANION_HEALTH_CHECK_TICKS`] ticks.
    pub(crate) fn check_companion_health(&mut self) {
        let elapsed = self.status_cache.elapsed_since_last_event();

        let alive = elapsed < COMPANION_STALE_TIMEOUT;
        let was_alive = self.companion_alive;

        self.companion_alive = alive;
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
                // Still dead. Try again if enough time has passed.
                let cooldown = Duration::from_secs(COMPANION_RESTART_COOLDOWN_SECS);
                match self.companion_restart_cooldown {
                    Some(last) if last.elapsed() < cooldown => {
                        // Too soon, skip.
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
            (true, true) => {
                // All good, nothing to do.
            }
        }
    }

    /// Apply refresh rate via the fallback path (`settings put system`)
    /// when the companion is not available. `0` restores default.
    /// Returns `true` on success.
    pub(crate) fn apply_refresh_rate_fallback(&self, hz: u32) -> bool {
        let ok = if !self.companion_alive {
            debug!(target: "auriya::companion", "RR fallback: setting {hz}Hz");
            let r1 = std::process::Command::new("settings")
                .args(["put", "system", "min_refresh_rate", &hz.to_string()])
                .status()
                .map(|s| s.success())
                .unwrap_or(false);
            let r2 = std::process::Command::new("settings")
                .args(["put", "system", "peak_refresh_rate", &hz.to_string()])
                .status()
                .map(|s| s.success())
                .unwrap_or(false);
            r1 && r2
        } else {
            crate::core::cmd_writer::shared()
                .write_refresh_rate(hz)
                .is_ok()
        };
        if !ok {
            error!(target: "auriya::companion", "Failed to apply refresh rate {hz}Hz");
        }
        ok
    }

    /// Spawn a new companion process by launching `app_process` with
    /// the companion APK. This is a lighter alternative to running the
    /// full `service.sh` (which would also kill-and-restart the daemon).
    fn restart_companion(&mut self) {
        self.companion_restart_cooldown = Some(std::time::Instant::now());

        // First, kill any existing companion process gracefully.
        let _ = std::process::Command::new("killall")
            .args(["-TERM", COMPANION_PROC])
            .status();
        std::thread::sleep(Duration::from_millis(500));
        let _ = std::process::Command::new("killall")
            .args(["-KILL", COMPANION_PROC])
            .status();

        // Now launch a fresh companion process via the shell, same
        // pattern as service.sh but without touching the daemon.
        let companion_log = "/data/adb/auriya/companion.log";
        let cmd = format!(
            "nohup app_process -Djava.class.path={apk} \
             /system/bin --nice-name={proc} \
             dev.auriya.service.Main >> {log} 2>&1 &",
            apk = COMPANION_APK,
            proc = COMPANION_PROC,
            log = companion_log,
        );
        let child = std::process::Command::new("sh").args(["-c", &cmd]).spawn();

        match child {
            Ok(_) => info!(target: "auriya::companion", "Companion restart spawned"),
            Err(e) => error!(
                target: "auriya::companion",
                "Failed to spawn companion restart: {e}"
            ),
        }
    }

    /// React to the companion's liveness lock being released — the
    /// service process died. Mark it dead and relaunch it. The restart
    /// is rate-limited by [`COMPANION_RESTART_COOLDOWN_SECS`] inside
    /// [`Self::restart_companion`].
    pub(crate) fn on_companion_died(&mut self) {
        if !self.companion_alive {
            // Already known dead (e.g. the staleness check beat us to it).
            return;
        }
        warn!(
            target: "auriya::companion",
            "Companion liveness lock released — service died. Restarting..."
        );
        self.companion_alive = false;
        crate::core::profile::set_companion_alive(false);
        self.restart_companion();
    }

    /// Release every host-state override the daemon owns before exiting,
    /// so a graceful stop (Ctrl-C or a staged module update) does not
    /// leave mount-binds or offlined cores behind. The `CeilingController`
    /// also restores on `Drop`, but doing it explicitly keeps the ordering
    /// deterministic and covers `VendorLock`, which has no `Drop`.
    pub(crate) fn shutdown_cleanup(&mut self) {
        debug!(target: "auriya::daemon", "Releasing overrides for graceful shutdown");
        self.vendor_lock.unlock_all();
        self.ceiling_controller.restore();
        self.ceiling_controller.online_all();
    }
}

pub async fn run_with_config_and_logger(cfg: &DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: &DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
    let supported_modes = match crate::core::display::get_app_supported_modes().await {
        Ok(modes) => {
            debug!(target: "auriya::daemon", "Cached {} supported display modes", modes.len());
            Arc::new(modes)
        }
        Err(e) => {
            error!(target: "auriya::daemon", "Failed to cache supported modes: {}", e);
            Arc::new(Vec::new())
        }
    };

    // Companion service is required: it produces the status file the
    // daemon now reads instead of polling dumpsys. Refuse to start
    // without it so the user gets a clear error instead of silent
    // misbehaviour.
    let status_path = PathBuf::from(STATUS_FILE);
    let wait_timeout = Duration::from_secs(10);
    if let Err(e) =
        crate::core::system_status::watcher::await_status_file(&status_path, wait_timeout)
    {
        error!(
            target: "auriya::daemon",
            "Daemon | {e}"
        );
        return Err(e);
    }

    let (status_cache, mut status_rx) =
        crate::core::system_status::watcher::start_status_watcher(status_path)?;

    let (event_tx, mut event_rx) = event::channel();

    let mut daemon = Daemon::new(cfg.clone(), supported_modes, status_cache, event_tx)?;

    if detect::is_mediatek() {
        debug!(target: "auriya::daemon", "MTK device detected, applying PPM fix...");
        tokio::task::spawn_blocking(|| {
            mtk::fix_mediatek_ppm();
        })
        .await
        .ok();
        debug!(target: "auriya::daemon", "MTK PPM fix applied");
    }

    daemon.init_ipc(filter_handle).await;

    tokio::time::sleep(time::Duration::from_millis(200)).await;
    debug!(target: "auriya::daemon", "IPC socket ready at /dev/socket/auriya.sock");

    let mut watch_rx = crate::daemon::watcher::start_config_watcher(daemon.shared_gamelist.clone());

    crate::daemon::watcher::start_module_update_watcher(daemon.event_tx.clone());

    debug!(target: "auriya::daemon", "Tick loop started (adaptive: {}ms idle, {}ms gaming)", NORMAL_INTERVAL_MS, INGAME_INTERVAL_MS);

    daemon.tick().await;

    loop {
        let sleep_ms = if daemon.is_suspended() {
            SCREEN_OFF_INTERVAL_MS
        } else if daemon.is_in_game_session() {
            INGAME_INTERVAL_MS
        } else {
            NORMAL_INTERVAL_MS
        };

        tokio::select! {
            _ = time::sleep(Duration::from_millis(sleep_ms)) => {
                daemon.tick().await;
            }
            Some(_) = status_rx.recv() => {
                debug!(target: "auriya::daemon", "Status update received, triggering instant tick");
                daemon.tick().await;
            }
            Some(msg) = watch_rx.recv() => {
                if msg == "settings" {
                     daemon.reload_settings();
                } else {
                     daemon.rebuild_whitelist();
                     debug!(target: "auriya::daemon", "Gamelist reload notification received, triggering instant tick");
                     daemon.tick().await;
                }
            }
            Some(ev) = event_rx.recv() => {
                match ev {
                    DaemonEvent::PidExited(pid) => {
                        debug!(target: "auriya::daemon", "Tracked PID {} exited, triggering instant tick", pid);
                        daemon.tick().await;
                    }
                    DaemonEvent::CompanionDied => {
                        daemon.on_companion_died();
                    }
                    DaemonEvent::ModuleUpdate => {
                        info!(target: "auriya::daemon", "Daemon | Module update staged, stopping gracefully");
                        daemon.shutdown_cleanup();
                        break;
                    }
                }
            }
            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Daemon | Received Ctrl-C, shutting down");
                daemon.shutdown_cleanup();
                break;
            }
        }
    }
    info!(target: "auriya::daemon", "Daemon | Stopped");
    Ok(())
}
