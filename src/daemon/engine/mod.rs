pub mod r#loop;
pub mod status_file;

use crate::core::fps::FpsMeter;
use crate::core::profile::ProfileMode;
use crate::core::system_status::SystemStatusCache;
use crate::core::telemetry::TelemetryHub;
use crate::daemon::companion::watchdog::CompanionWatchdog;
pub use crate::daemon::model::config::DaemonConfig;
use crate::daemon::model::event::EventSender;
use crate::daemon::model::state::{CurrentState, LastState};
use anyhow::Result;
use std::collections::HashSet;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, RwLock};
use tracing::{debug, error, info, warn};

pub use r#loop::{
    INGAME_INTERVAL_MS, SCREEN_OFF_INTERVAL_MS, run_with_config, run_with_config_and_logger,
};
pub use status_file::{STATUS_FILE, update_current_profile_file};
pub(crate) use status_file::{bump_log, now_ms, should_log_change};

pub type ReloadHandle =
    tracing_subscriber::reload::Handle<tracing_subscriber::EnvFilter, tracing_subscriber::Registry>;

pub struct Daemon {
    pub(crate) cfg: DaemonConfig,
    pub(crate) _shared_settings: Arc<RwLock<crate::core::config::Settings>>,
    pub(crate) shared_gamelist: Arc<RwLock<Arc<crate::core::config::GameList>>>,
    pub(crate) shared_current: Arc<RwLock<CurrentState>>,
    pub(crate) override_foreground: Arc<RwLock<Option<String>>>,

    pub(crate) last: LastState,
    pub(crate) last_error: Option<(String, u128)>,
    pub(crate) error_debounce_ms: u128,
    pub(crate) tick_count: u64,
    pub(crate) watchdog: CompanionWatchdog,

    pub(crate) fas_controller: Option<Arc<tokio::sync::Mutex<crate::core::fas::FasController>>>,
    pub(crate) balance_governor: Arc<RwLock<String>>,
    pub(crate) default_mode: ProfileMode,
    pub(crate) normal_interval_ms: u64,
    pub(crate) supported_modes: Arc<Vec<crate::platform::display::DisplayMode>>,
    pub(crate) applied_refresh_rate: Option<u32>,
    pub(crate) cached_whitelist: HashSet<String>,
    pub(crate) status_cache: SystemStatusCache,
    pub(crate) vendor_lock: crate::platform::vendor::VendorLock,
    pub(crate) last_fps_config: Option<String>,
    pub(crate) pid_tracker: Option<crate::core::pid::PidTracker>,
    pub(crate) ceiling_controller: crate::platform::cpu::ceiling::CeilingController,
    pub(crate) ceiling_config: crate::platform::cpu::ceiling::CeilingConfig,
    pub(crate) current_ceiling: Option<crate::platform::cpu::ceiling::CeilingLevel>,
    pub(crate) telemetry_hub: TelemetryHub,
    pub(crate) fps_meter: FpsMeter,
    pub(crate) ebpf: Option<crate::core::ebpf::EbpfFrameStream>,
    pub(crate) attached_ebpf_pid: Option<i32>,
    pub(crate) last_dnd: Option<crate::platform::android_cmd::DndFilter>,
    pub(crate) event_tx: EventSender,
}

impl Daemon {
    pub fn new(
        cfg: DaemonConfig,
        supported_modes: Arc<Vec<crate::platform::display::DisplayMode>>,
        status_cache: SystemStatusCache,
        event_tx: EventSender,
    ) -> Result<Self> {
        let shared_settings = Arc::new(RwLock::new(cfg.settings.clone()));
        let shared_gamelist = Arc::new(RwLock::new(Arc::new(cfg.gamelist.clone())));
        let shared_current = Arc::new(RwLock::new(CurrentState::default()));
        let override_foreground = Arc::new(RwLock::new(None));

        let balance_governor = Arc::new(RwLock::new(cfg.settings.cpu.default_governor.clone()));
        let default_mode = cfg
            .settings
            .daemon
            .default_mode
            .parse::<ProfileMode>()
            .unwrap_or(ProfileMode::Balance);
        debug!(target: "auriya::daemon", "Default mode: {:?}", default_mode);

        let (fas_controller, fps_meter, ebpf) = {
            let e = match crate::core::ebpf::EbpfFrameStream::new(cfg.settings.fas.poll_interval_ms)
            {
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
                    let tuning = crate::core::fas::FasTuning::from_settings(&cfg.settings);
                    debug!(target: "auriya::daemon", "FAS enabled with eBPF (target_fps={}, mode={})", cfg.settings.fas.target_fps, cfg.settings.fas.default_mode);
                    let rx = listener.subscribe();
                    Some(Arc::new(tokio::sync::Mutex::new(
                        crate::core::fas::FasController::new(
                            rx,
                            crate::core::fas::buffer::TargetFps::Single(
                                cfg.settings.fas.target_fps,
                            ),
                            tuning,
                        ),
                    )))
                } else {
                    tracing::warn!(
                        target: "auriya::daemon",
                        "FAS requested but eBPF unavailable. Continuing without FAS."
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
            crate::platform::cpu::ceiling::CeilingConfig {
                default: c
                    .default
                    .parse::<crate::platform::cpu::ceiling::CeilingLevel>()
                    .unwrap_or(crate::platform::cpu::ceiling::CeilingLevel::Balance),
                low_freq_little_khz: c.low_freq_little_khz,
                low_freq_big_khz: c.low_freq_big_khz,
            }
        };

        let core_layout = crate::platform::cpu::ceiling::CoreLayout::detect();
        let normal_interval_ms = cfg.settings.daemon.check_interval_ms.max(100);

        let thermal_default = if cfg.settings.thermal.default.is_some() {
            None
        } else {
            let stock = crate::platform::thermal::sconfig::load_or_capture_rom_default(
                &crate::core::config::thermal_default_path(),
            );
            if let Some(stock) = stock {
                debug!(target: "auriya::thermal", "Stock thermal sconfig: {}", stock);
            }
            stock
        };

        Ok(Self {
            cfg,
            _shared_settings: shared_settings,
            shared_gamelist,
            shared_current,
            override_foreground,
            last: LastState {
                thermal_default,
                ..LastState::default()
            },
            last_error: None,
            error_debounce_ms: 30_000,
            fas_controller,
            balance_governor,
            default_mode,
            normal_interval_ms,
            supported_modes,
            applied_refresh_rate: None,
            cached_whitelist,
            tick_count: 0,
            watchdog: CompanionWatchdog::new(),
            status_cache,
            vendor_lock: crate::platform::vendor::VendorLock::new(),
            last_fps_config: None,
            pid_tracker: None,
            ceiling_controller: crate::platform::cpu::ceiling::CeilingController::new(),
            ceiling_config,
            current_ceiling: None,
            telemetry_hub: TelemetryHub::new(&core_layout),
            fps_meter,
            ebpf,
            attached_ebpf_pid: None,
            last_dnd: None,
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

    #[inline]
    pub fn is_suspended(&self) -> bool {
        !self.last.screen_awake.unwrap_or(true) || self.last.battery_saver.unwrap_or(false)
    }

    pub(crate) fn set_pid(&mut self, pid: Option<i32>) {
        self.last.pid = pid;
        self.pid_tracker =
            pid.map(|p| crate::core::pid::PidTracker::spawn(p, self.event_tx.clone()));
    }

    pub(crate) fn reload_settings(&mut self) {
        match crate::core::config::Settings::load(crate::core::config::settings_path()) {
            Ok(new_settings) => {
                let mut gov_changed = false;
                if let Ok(mut g) = self.balance_governor.write()
                    && *g != new_settings.cpu.default_governor
                {
                    *g = new_settings.cpu.default_governor.clone();
                    gov_changed = true;
                }

                if gov_changed {
                    let new_gov = &new_settings.cpu.default_governor;
                    debug!(target: "auriya::daemon", "Settings reloaded. New default governor: {}", new_gov);

                    if !self.is_in_game_session()
                        && (self.last.profile_mode.is_none()
                            || self.last.profile_mode == Some(ProfileMode::Balance))
                    {
                        debug!(target: "auriya::daemon", "Applying new default governor immediately...");
                        if let Err(e) = crate::core::profile::apply_balance(new_gov) {
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
                    if !self.is_in_game_session() {
                        self.last.profile_mode = None;
                    }
                }

                let new_interval = new_settings.daemon.check_interval_ms.max(100);
                if self.normal_interval_ms != new_interval {
                    debug!(target: "auriya::daemon", "Settings reloaded. Normal tick interval: {}ms → {}ms", self.normal_interval_ms, new_interval);
                    self.normal_interval_ms = new_interval;
                }

                if let Some(ref fas_arc) = self.fas_controller {
                    let tuning = crate::core::fas::FasTuning::from_settings(&new_settings);
                    if let Ok(mut f) = fas_arc.try_lock() {
                        f.set_tuning(tuning);
                        debug!(target: "auriya::daemon", "Settings reloaded. Updated FAS tuning parameters.");
                    }
                }

                if let Ok(mut shared) = self._shared_settings.write() {
                    *shared = new_settings;
                    debug!(target: "auriya::daemon", "Settings reloaded. Shared settings refreshed.");
                }
            }
            Err(e) => {
                error!(target: "auriya::daemon", "Failed to reload settings: {:?}", e);
            }
        }
    }

    pub(crate) fn rebuild_whitelist(&mut self) {
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
        let current_log_level = Arc::new(RwLock::new(crate::daemon::ipc::LogLevelCmd::Info));
        let ctx = Arc::new(crate::daemon::context::DaemonContext {
            enabled: Arc::new(AtomicBool::new(true)),
            shared_config: self.shared_gamelist.clone(),
            override_foreground: self.override_foreground.clone(),
            current_state: self.shared_current.clone(),
            balance_governor: self.balance_governor.clone(),
            dnd_default: self.cfg.settings.dnd.default_enable,
            current_log_level,
            supported_modes: self.supported_modes.clone(),
            profile_lock: Arc::new(std::sync::Mutex::new(())),
            fas_controller: self.fas_controller.clone(),
            reload_handle: Some(filter_handle),
        });

        tokio::spawn(async move {
            debug!(target: "auriya::daemon", "Starting IPC socket listener...");
            match crate::daemon::ipc::start("/dev/socket/auriya.sock", ctx).await {
                Ok(_) => info!(target: "auriya::daemon", "IPC    | Listener stopped"),
                Err(e) => error!(target: "auriya::daemon", "IPC    | Error: {:?}", e),
            }
        });
    }

    pub(crate) fn apply_refresh_rate_fallback(&self, hz: u32) -> bool {
        let ok = if !self.watchdog.alive {
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
            crate::platform::android_cmd::shared()
                .write_refresh_rate(hz)
                .is_ok()
        };
        if !ok {
            error!(target: "auriya::companion", "Failed to apply refresh rate {hz}Hz");
        }
        ok
    }

    pub(crate) fn shutdown_cleanup(&mut self) {
        debug!(target: "auriya::daemon", "Releasing overrides for graceful shutdown");
        self.vendor_lock.unlock_all();
        self.ceiling_controller.restore();
        self.ceiling_controller.online_all();
    }

    pub async fn tick(&mut self) {
        self.tick_count = self.tick_count.wrapping_add(1);
        debug!(target: "auriya::daemon", "Tick #{}", self.tick_count);

        if self
            .tick_count
            .is_multiple_of(crate::daemon::companion::watchdog::COMPANION_HEALTH_CHECK_TICKS)
        {
            self.watchdog.check_health(&self.status_cache);
        }

        let gamelist = match self.shared_gamelist.read() {
            Ok(g) => Arc::clone(&g),
            Err(_) => {
                warn!(target: "auriya::daemon", "Gamelist lock poisoned, skipping tick");
                return;
            }
        };

        if let Err(e) = self.execute_pipeline(&gamelist).await {
            self.handle_tick_error(e);
        } else {
            self.publish_telemetry();
        }
    }

    fn handle_tick_error(&mut self, e: anyhow::Error) {
        let err_msg = e.to_string();
        let now = now_ms();

        let should_log = match &self.last_error {
            None => true,
            Some((last_msg, last_time)) => {
                err_msg != *last_msg || (now.saturating_sub(*last_time) >= self.error_debounce_ms)
            }
        };

        if should_log {
            error!(target: "auriya::daemon", "Tick error: {:?}", e);
            self.last_error = Some((err_msg, now));
        } else {
            debug!(target: "auriya::daemon", "Tick error suppressed: {:?}", e);
        }
    }

    fn publish_telemetry(&mut self) {
        let telemetry = self.telemetry_hub.snapshot(&self.ceiling_controller.layout);
        let game_session = self.is_in_game_session();

        if let Ok(mut cur) = self.shared_current.write() {
            cur.pkg = self.last.pkg.clone();
            cur.pid = self.last.pid;
            cur.game_session = game_session;
            cur.screen_awake = self.last.screen_awake.unwrap_or(false);
            cur.battery_saver = self.last.battery_saver.unwrap_or(false);
            cur.profile = self.last.profile_mode.unwrap_or(self.default_mode);
            cur.companion_alive = self.watchdog.alive;
            cur.cpu_telemetry = telemetry.cpu;
            cur.gpu_telemetry = telemetry.gpu;
            cur.thermal_telemetry = telemetry.thermal;

            match self.fps_meter.read() {
                Some(reading) => {
                    cur.fps = Some(reading.fps);
                    cur.fps_source = Some(reading.source);
                }
                None => {
                    cur.fps = None;
                    cur.fps_source = None;
                }
            }
        }

        if let Some(mode) = self.last.profile_mode {
            update_current_profile_file(mode);
        }
    }
}
