use crate::core::profile::ProfileMode;
use crate::daemon::state::{CurrentState, LastState};
use anyhow::Result;
use std::collections::HashSet;
use std::{
    sync::atomic::AtomicBool,
    sync::{Arc, RwLock},
    time::Duration,
};
use tokio::{signal, time};
use tracing::{debug, error, info};
use tracing_subscriber::EnvFilter;

const INGAME_INTERVAL_MS: u64 = 500;
const NORMAL_INTERVAL_MS: u64 = 5000;
const SCREEN_OFF_INTERVAL_MS: u64 = 10000;

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

    pub(crate) fas_controller: Option<Arc<tokio::sync::Mutex<crate::daemon::fas::FasController>>>,
    pub(crate) balance_governor: String,
    pub(crate) default_mode: ProfileMode,
    pub(crate) supported_modes: Vec<crate::core::display::DisplayMode>,
    pub(crate) refresh_rate_map: std::collections::HashMap<String, u32>,
    pub(crate) cached_whitelist: HashSet<String>,
}

impl Daemon {
    pub fn new(
        cfg: DaemonConfig,
        supported_modes: Vec<crate::core::display::DisplayMode>,
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

        let fas_controller = if cfg.settings.fas.enabled {
            debug!(target: "auriya::daemon", "FAS enabled");
            Some(Arc::new(tokio::sync::Mutex::new(
                crate::daemon::fas::FasController::with_target_fps(60), // Default 60, per-game overrides
            )))
        } else {
            debug!(target: "auriya::daemon", "FAS disabled");
            None
        };

        let cached_whitelist: HashSet<String> = cfg
            .gamelist
            .game
            .iter()
            .map(|g| g.package.clone())
            .collect();

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
            refresh_rate_map: std::collections::HashMap::new(),
            cached_whitelist,
            tick_count: 0,
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
        if let Ok(gl) = self.shared_gamelist.read() {
            self.cached_whitelist = gl.game.iter().map(|g| g.package.clone()).collect();
            self.last.pkg = None;
            self.last.pid = None;
            debug!(target: "auriya::daemon", "Whitelist cache rebuilt: {} packages (forcing re-detect)", self.cached_whitelist.len());
        }
    }

    pub async fn init_ipc(&self, filter_handle: ReloadHandle) {
        let fas_clone_for_ipc = self.fas_controller.clone();
        let set_fps = Arc::new(move |fps: u32| {
            if let Some(fas) = &fas_clone_for_ipc {
                let mut guard = fas.blocking_lock();
                guard.set_target_fps(fps);
            }
        });

        let fas_clone_for_get = self.fas_controller.clone();
        let get_fps = Arc::new(move || -> u32 {
            if let Some(fas) = &fas_clone_for_get {
                let guard = fas.blocking_lock();
                return guard.get_target_fps();
            }
            60
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
            supported_modes: Arc::new(self.supported_modes.clone()),
        };

        tokio::spawn(async move {
            debug!(target: "auriya::daemon", "Starting IPC socket listener...");
            match crate::daemon::ipc::start("/dev/socket/auriya.sock", ipc_handles).await {
                Ok(_) => info!(target: "auriya::daemon", "IPC    | Listener stopped"),
                Err(e) => error!(target: "auriya::daemon", "IPC    | Error: {:?}", e),
            }
        });
    }
}

pub async fn run_with_config_and_logger(cfg: &DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: &DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
    let supported_modes = match crate::core::display::get_app_supported_modes().await {
        Ok(modes) => {
            debug!(target: "auriya::daemon", "Cached {} supported display modes", modes.len());
            modes
        }
        Err(e) => {
            error!(target: "auriya::daemon", "Failed to cache supported modes: {}", e);
            Vec::new()
        }
    };

    let mut daemon = Daemon::new(cfg.clone(), supported_modes)?;

    daemon.init_ipc(filter_handle).await;

    tokio::time::sleep(time::Duration::from_millis(200)).await;
    debug!(target: "auriya::daemon", "IPC socket ready at /dev/socket/auriya.sock");

    let mut watch_rx = crate::daemon::watcher::start_config_watcher(daemon.shared_gamelist.clone());

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
            Some(msg) = watch_rx.recv() => {
                if msg == "settings" {
                     daemon.reload_settings();
                } else {
                     daemon.rebuild_whitelist();
                     debug!(target: "auriya::daemon", "Gamelist reload notification received, triggering instant tick");
                     daemon.tick().await;
                }
            }
            _ = signal::ctrl_c() => {
                info!(target: "auriya::daemon", "Daemon | Received Ctrl-C, shutting down");
                break;
            }
        }
    }
    info!(target: "auriya::daemon", "Daemon | Stopped");
    Ok(())
}
