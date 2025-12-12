use crate::core::profile::ProfileMode;
use crate::core::tweaks::mtk;
use crate::daemon::state::{CurrentState, LastState};
use anyhow::Result;
use notify::{EventKind, RecursiveMode, Watcher};
use std::collections::HashSet;
use std::{
    sync::atomic::AtomicBool,
    sync::{Arc, Mutex, RwLock},
    time::Duration,
};
use tokio::{signal, time};
use tracing::{debug, error, info, warn};
use tracing_subscriber::EnvFilter;

fn update_current_profile_file(mode: ProfileMode) {
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

#[derive(Debug, Clone)]
pub struct DaemonConfig {
    pub poll_interval: Duration,
    pub settings: crate::core::config::Settings,
    pub gamelist: crate::core::config::GameList,
    pub log_debounce_ms: u128,
}

impl Default for DaemonConfig {
    fn default() -> Self {
        let settings =
            crate::core::config::Settings::load(crate::core::config::settings_path()).unwrap();
        let gamelist =
            crate::core::config::GameList::load(crate::core::config::gamelist_path()).unwrap();
        Self {
            poll_interval: Duration::from_secs(1),
            settings,
            gamelist,
            log_debounce_ms: 2000,
        }
    }
}

#[inline]
fn now_ms() -> u128 {
    use std::time::{Duration, SystemTime, UNIX_EPOCH};

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

pub type ReloadHandle =
    tracing_subscriber::reload::Handle<tracing_subscriber::EnvFilter, tracing_subscriber::Registry>;

pub struct Daemon {
    cfg: DaemonConfig,
    _shared_settings: Arc<RwLock<crate::core::config::Settings>>,
    shared_gamelist: Arc<RwLock<crate::core::config::GameList>>,
    shared_current: Arc<RwLock<CurrentState>>,
    override_foreground: Arc<RwLock<Option<String>>>,

    last: LastState,
    last_error: Option<(String, u128)>,
    error_debounce_ms: u128,

    fas_controller: Option<Arc<Mutex<crate::daemon::fas::FasController>>>,
    balance_governor: String,
    supported_modes: Vec<crate::core::display::DisplayMode>,
    refresh_rate_map: std::collections::HashMap<String, u32>,
    cached_whitelist: HashSet<String>,
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

        let fas_controller = if cfg.settings.fas.enabled {
            info!(target: "auriya::daemon", "FAS enabled");
            Some(Arc::new(Mutex::new(
                crate::daemon::fas::FasController::new(cfg.settings.fas.target_fps),
            )))
        } else {
            info!(target: "auriya::daemon", "FAS disabled");
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
            supported_modes,
            refresh_rate_map: std::collections::HashMap::new(),
            cached_whitelist,
        })
    }

    fn reload_settings(&mut self) {
        match crate::core::config::Settings::load(crate::core::config::settings_path()) {
            Ok(new_settings) => {
                info!(target: "auriya::daemon", "Settings reloaded. New default governor: {}", new_settings.cpu.default_governor);

                if self.balance_governor != new_settings.cpu.default_governor {
                    self.balance_governor = new_settings.cpu.default_governor.clone();

                    if self.last.profile_mode == Some(ProfileMode::Balance) {
                        info!(target: "auriya::daemon", "Applying new default governor immediately...");
                        if let Err(e) = crate::core::profile::apply_balance(&self.balance_governor)
                        {
                            error!(target: "auriya::profile", ?e, "Failed to apply new balance governor");
                        }
                    }
                }

                // Update FAS fps target if needed
                if let Some(fas) = &self.fas_controller
                    && let Ok(mut f) = fas.lock()
                    && f.get_target_fps() != new_settings.fas.target_fps
                {
                    info!(target: "auriya::daemon", "Updating Global Target FPS to {}", new_settings.fas.target_fps);
                    f.set_target_fps(new_settings.fas.target_fps);
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
            info!(target: "auriya::daemon", "Whitelist cache rebuilt: {} packages", self.cached_whitelist.len());
        }
    }

    pub async fn init_ipc(&self, filter_handle: ReloadHandle) {
        let fas_clone_for_ipc = self.fas_controller.clone();
        let set_fps = Arc::new(move |fps: u32| {
            if let Some(fas) = &fas_clone_for_ipc
                && let Ok(mut guard) = fas.lock()
            {
                guard.set_target_fps(fps);
            }
        });

        let fas_clone_for_get = self.fas_controller.clone();
        let get_fps = Arc::new(move || -> u32 {
            if let Some(fas) = &fas_clone_for_get
                && let Ok(guard) = fas.lock()
            {
                return guard.get_target_fps();
            }
            60 // Default if not available
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
                Ok(_) => info!(target: "auriya::ipc", "Log level changed to {:?}", lvl),
                Err(e) => {
                    error!(target: "auriya::ipc", "Failed to change log level: {}", e)
                }
            }
        });

        let current_state = self.shared_current.clone();
        let cfg = &self.cfg; // Borrow cfg for accessing settings

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
            info!(target: "auriya::daemon", "Starting IPC socket listener...");
            match crate::daemon::ipc::start("/dev/socket/auriya.sock", ipc_handles).await {
                Ok(_) => info!(target: "auriya::daemon", "IPC listener stopped gracefully"),
                Err(e) => error!(target: "auriya::daemon", "IPC error: {:?}", e),
            }
        });
    }

    pub fn init_watcher(&self) -> tokio::sync::mpsc::Receiver<String> {
        let (watch_tx, watch_rx) = tokio::sync::mpsc::channel::<String>(10);
        let gamelist_path = Arc::new(crate::core::config::gamelist_path());
        let settings_path = Arc::new(crate::core::config::settings_path());
        let shared_for_watcher = self.shared_gamelist.clone();

        std::thread::spawn(move || {
            let tx = watch_tx;
            let path = gamelist_path.clone();

            let mut watcher = match notify::recommended_watcher(
                move |res: Result<notify::Event, notify::Error>| {
                    if let Ok(event) = res
                        && matches!(event.kind, EventKind::Modify(_))
                    {
                        let path_str = event
                            .paths
                            .first()
                            .map(|p| p.to_string_lossy().to_string())
                            .unwrap_or_default();

                        if path_str.contains("settings.toml") {
                            let _ = tx.blocking_send("settings".to_string());
                            return;
                        }

                        info!(target: "auriya::daemon", "Gamelist file changed, reloading...");
                        let max_retries = 3;
                        let mut retry_count = 0;
                        let mut success = false;

                        while retry_count < max_retries && !success {
                            match crate::core::config::GameList::load(&*path) {
                                Ok(new_cfg) => match shared_for_watcher.write() {
                                    Ok(mut g) => {
                                        let count = new_cfg.game.len();
                                        *g = new_cfg;
                                        info!(target: "auriya::daemon", "Gamelist reloaded: {} games", count);
                                        success = true;
                                    }
                                    Err(_) => {
                                        error!(target: "auriya::daemon", "Failed to acquire gamelist lock");
                                        break;
                                    }
                                },
                                Err(e) => {
                                    retry_count += 1;
                                    if retry_count < max_retries {
                                        warn!(target: "auriya::daemon", "Failed reloading gamelist (attempt {}/{}): {:?}, retrying in 2s...", retry_count, max_retries, e);
                                        std::thread::sleep(std::time::Duration::from_secs(2));
                                    } else {
                                        error!(target: "auriya::daemon", "Failed to reload gamelist after {} attempts: {:?}", max_retries, e);
                                    }
                                }
                            }
                        }
                        let _ = tx.blocking_send("gamelist".to_string());
                    }
                },
            ) {
                Ok(w) => w,
                Err(e) => {
                    error!(target: "auriya::daemon", "Failed to create gamelist watcher: {}", e);
                    return;
                }
            };

            if let Err(e) = watcher.watch(&gamelist_path, RecursiveMode::NonRecursive) {
                error!(target: "auriya::daemon", "Failed to watch gamelist file: {}", e);
                return;
            }
            if let Err(e) = watcher.watch(&settings_path, RecursiveMode::NonRecursive) {
                error!(target: "auriya::daemon", "Failed to watch settings file: {}", e);
                return;
            }

            info!(target: "auriya::daemon", "Config file watchers started");
            loop {
                std::thread::sleep(std::time::Duration::from_secs(3600));
            }
        });

        watch_rx
    }

    pub async fn tick(&mut self) {
        debug!(target: "auriya::daemon", "Tick");
        mtk::fix_mediatek_ppm();

        let gamelist = match self.shared_gamelist.read() {
            Ok(g) => g.clone(),
            Err(_) => {
                warn!(target: "auriya::daemon", "Gamelist lock poisoned, skipping tick");
                return;
            }
        };
        let _settings = match self._shared_settings.read() {
            Ok(s) => s.clone(),
            Err(_) => {
                warn!(target: "auriya::daemon", "Settings lock poisoned, skipping tick");
                return;
            }
        };

        if let Err(e) = self.process_tick_logic(&gamelist).await {
            let err_msg = e.to_string();
            let now = now_ms();

            let should_log = match &self.last_error {
                None => true,
                Some((last_msg, last_time)) => {
                    err_msg != *last_msg
                        || (now.saturating_sub(*last_time) >= self.error_debounce_ms)
                }
            };

            if should_log {
                error!(target: "auriya::daemon", "Tick error: {:?}", e);
                self.last_error = Some((err_msg, now));
            } else {
                debug!(target: "auriya::daemon", "Tick error suppressed: {:?}", e);
            }
        } else if let Ok(mut cur) = self.shared_current.write() {
            cur.pkg = self.last.pkg.clone();
            cur.pid = self.last.pid;
            cur.screen_awake = self.last.screen_awake.unwrap_or(false);
            cur.battery_saver = self.last.battery_saver.unwrap_or(false);
            cur.profile = self.last.profile_mode.unwrap_or(ProfileMode::Balance);

            if self.last.profile_mode.is_some() && cur.profile != self.last.profile_mode.unwrap() {
                update_current_profile_file(cur.profile);
            } else if self.last.profile_mode.is_some() {
                update_current_profile_file(self.last.profile_mode.unwrap());
            }
        }
    }

    async fn process_tick_logic(&mut self, gamelist: &crate::core::config::GameList) -> Result<()> {
        use crate::core::profile;

        let power = crate::core::dumpsys::power::PowerState::fetch().await?;
        let power_changed = self.last.screen_awake != Some(power.screen_awake)
            || self.last.battery_saver != Some(power.battery_saver);

        if !power.screen_awake || power.battery_saver {
            let target = ProfileMode::Powersave;
            if self.last.profile_mode != Some(target) {
                if let Err(e) = profile::apply_powersave() {
                    error!(target: "auriya::profile", ?e, "Failed to apply POWERSAVE");
                } else {
                    info!(target: "auriya::daemon", "Applied POWERSAVE (screen: {}, saver: {})", power.screen_awake, power.battery_saver);
                    self.last.profile_mode = Some(target);
                }
            }
            self.last.screen_awake = Some(power.screen_awake);
            self.last.battery_saver = Some(power.battery_saver);
            return Ok(());
        }

        if power_changed {
            info!(target: "auriya::daemon", "Screen ON & saver OFF");
            self.last.screen_awake = Some(power.screen_awake);
            self.last.battery_saver = Some(power.battery_saver);
        }

        let mut pkg_opt: Option<String> =
            self.override_foreground.read().ok().and_then(|o| o.clone());
        if pkg_opt.is_none() {
            match crate::core::dumpsys::foreground::get_foreground_package().await? {
                Some(p) => pkg_opt = Some(p),
                None => {
                    if self.last.profile_mode != Some(ProfileMode::Balance) {
                        if let Err(e) = profile::apply_balance(&self.balance_governor) {
                            error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
                        } else {
                            info!(target: "auriya::daemon", "Applied BALANCE (no foreground)");
                            self.last.profile_mode = Some(ProfileMode::Balance);
                        }
                    }
                    if self.last.pkg.is_some() || self.last.pid.is_some() {
                        if should_log_change(&self.last, &self.cfg) {
                            info!(target: "auriya::daemon", "No foreground app detected");
                            bump_log(&mut self.last);
                        }
                        self.last.pkg = None;
                        self.last.pid = None;
                        let _ = crate::core::display::reset_refresh_rate().await;
                    }
                    return Ok(());
                }
            }
        }
        let pkg = pkg_opt.unwrap();

        if self.last.pkg.as_deref() == Some(pkg.as_str()) && self.last.pid.is_some() {
            let fas_clone = self.fas_controller.clone();
            if let Some(fas) = fas_clone
                && gamelist.game.iter().any(|a| a.package == pkg)
            {
                let game_cfg = gamelist.find(&pkg);
                let governor = game_cfg
                    .map(|c| &c.cpu_governor[..])
                    .unwrap_or("performance");

                if let Some(cfg) = game_cfg {
                    if let Some(fps) = cfg.target_fps {
                        if let Ok(mut f) = fas.lock() {
                            if f.get_target_fps() != fps {
                                f.set_target_fps(fps);
                            }
                        }
                    } else {
                        let global_fps = self.cfg.settings.fas.target_fps;
                        if let Ok(mut f) = fas.lock() {
                            if f.get_target_fps() != global_fps {
                                f.set_target_fps(global_fps);
                            }
                        }
                    }
                }

                match self.run_fas_tick(&fas, &pkg, governor).await {
                    Ok(_) => debug!(target: "auriya::fas", "FAS tick completed"),
                    Err(e) => warn!(target: "auriya::fas", "FAS tick error: {:?}", e),
                }
            }
            debug!(target: "auriya::daemon", "Same app with known PID; skip profile reapply");
            return Ok(());
        }

        if self.cached_whitelist.contains(&pkg) {
            match crate::core::dumpsys::activity::get_app_pid(&pkg).await? {
                Some(pid) => {
                    let changed = self.last.pkg.as_deref() != Some(pkg.as_str())
                        || self.last.pid != Some(pid);
                    if changed && should_log_change(&self.last, &self.cfg) {
                        info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
                        bump_log(&mut self.last);
                    }
                    if self.last.pkg.as_deref() != Some(pkg.as_str())
                        && let Some(last_pkg) = &self.last.pkg
                        && let Some(original_rate) = self.refresh_rate_map.remove(last_pkg)
                    {
                        info!(target: "auriya::display", "Restoring refresh rate for {}: {}Hz", last_pkg, original_rate);
                        if let Err(e) = crate::core::display::set_refresh_rate(original_rate).await
                        {
                            error!(target: "auriya::display", ?e, "Failed to restore refresh rate");
                        }
                    }

                    let game_cfg = gamelist.find(&pkg);
                    let governor = game_cfg
                        .map(|c| &c.cpu_governor[..])
                        .unwrap_or("performance");
                    let enable_dnd = game_cfg.map(|c| c.enable_dnd).unwrap_or(true);
                    let target_mode = game_cfg
                        .and_then(|c| c.mode.as_deref())
                        .map(|m| match m.to_lowercase().as_str() {
                            "powersave" => ProfileMode::Powersave,
                            "balance" => ProfileMode::Balance,
                            _ => ProfileMode::Performance,
                        })
                        .unwrap_or(ProfileMode::Performance);

                    if self.last.profile_mode != Some(target_mode) {
                        let res = match target_mode {
                            ProfileMode::Performance => profile::apply_performance_with_config(
                                governor,
                                enable_dnd,
                                Some(pid),
                            ),
                            ProfileMode::Balance => profile::apply_balance(&self.balance_governor),
                            ProfileMode::Powersave => profile::apply_powersave(),
                        };

                        if let Err(e) = res {
                            error!(target: "auriya::profile", ?e, "Failed to apply {:?}", target_mode);
                        } else {
                            info!(target: "auriya::daemon", "Applied {:?} for {} (governor: {}, dnd: {})", target_mode, pkg, governor, enable_dnd);
                            self.last.profile_mode = Some(target_mode);
                        }
                    }

                    if let Some(rr) = game_cfg.and_then(|c| c.refresh_rate) {
                        if !self.refresh_rate_map.contains_key(&pkg) {
                            match crate::core::display::get_refresh_rate().await {
                                Ok(current) => {
                                    self.refresh_rate_map.insert(pkg.clone(), current);
                                    debug!(target: "auriya::display", "Saved current rate for {}: {}Hz", pkg, current);
                                }
                                Err(e) => {
                                    warn!(target: "auriya::display", "Failed to read current refresh rate: {}", e)
                                }
                            }
                        }

                        let is_supported = self.supported_modes.iter().any(|m| {
                            (m.fps - rr as f32).abs() < 0.1 // Tolerance for float comparison
                        });

                        if is_supported {
                            if let Err(e) = crate::core::display::set_refresh_rate(rr).await {
                                error!(target: "auriya::display", ?e, "Failed to set refresh rate");
                            }
                        } else if self.supported_modes.is_empty() {
                            warn!(target: "auriya::display", "No supported modes cached, skipping refresh rate {}Hz for {}", rr, pkg);
                        } else {
                            warn!(target: "auriya::display", "Refresh rate {}Hz not supported by device, skipping for {}", rr, pkg);
                        }
                    } else if self.last.pkg.as_deref() != Some(pkg.as_str()) {
                    }

                    self.last.pkg = Some(pkg);
                    self.last.pid = Some(pid);
                    Ok(())
                }
                None => {
                    if self.last.pkg.as_deref() != Some(pkg.as_str())
                        && let Some(last_pkg) = &self.last.pkg
                        && let Some(original_rate) = self.refresh_rate_map.remove(last_pkg)
                    {
                        info!(target: "auriya::display", "Restoring refresh rate for {} (PID lost): {}Hz", last_pkg, original_rate);
                        let _ = crate::core::display::set_refresh_rate(original_rate).await;
                    }

                    if self.last.profile_mode != Some(ProfileMode::Balance) {
                        if let Err(e) = profile::apply_balance(&self.balance_governor) {
                            error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
                        } else {
                            info!(target: "auriya::daemon", "Applied BALANCE (PID not found)");
                            self.last.profile_mode = Some(ProfileMode::Balance);
                        }
                    }
                    if (self.last.pkg.as_deref() != Some(pkg.as_str()) || self.last.pid.is_some())
                        && should_log_change(&self.last, &self.cfg)
                    {
                        warn!(target: "auriya::daemon", "Foreground {} PID not found", pkg);
                        bump_log(&mut self.last);
                    }
                    self.last.pkg = Some(pkg);
                    self.last.pid = None;
                    Ok(())
                }
            }
        } else {
            if self.last.pkg.as_deref() != Some(pkg.as_str())
                && let Some(last_pkg) = &self.last.pkg
                && let Some(original_rate) = self.refresh_rate_map.remove(last_pkg)
            {
                info!(target: "auriya::display", "Restoring refresh rate for {} (Exited Game): {}Hz", last_pkg, original_rate);
                let _ = crate::core::display::set_refresh_rate(original_rate).await;
            }

            if self.last.profile_mode != Some(ProfileMode::Balance) {
                if let Err(e) = profile::apply_balance(&self.balance_governor) {
                    error!(target: "auriya::profile", ?e, "Failed to apply BALANCE");
                } else {
                    info!(target: "auriya::daemon", "Applied BALANCE (not whitelisted)");
                    self.last.profile_mode = Some(ProfileMode::Balance);
                }
            }
            if (self.last.pkg.as_deref() != Some(pkg.as_str()) || self.last.pid.is_some())
                && should_log_change(&self.last, &self.cfg)
            {
                info!(target: "auriya::daemon", "Foreground {} (not whitelisted)", pkg);
                bump_log(&mut self.last);
            }
            self.last.pkg = Some(pkg);
            self.last.pid = None;
            Ok(())
        }
    }

    async fn run_fas_tick(
        &mut self,
        fas: &Arc<Mutex<crate::daemon::fas::FasController>>,
        pkg: &str,
        game_governor: &str,
    ) -> Result<bool> {
        use crate::core::{profile, scaling::ScalingAction};

        let margin = 2.0;
        let thermal_thresh = 90.0;

        let mut fas_guard = fas
            .lock()
            .map_err(|_| anyhow::anyhow!("FAS lock poisoned"))?;

        fas_guard.set_package(pkg.to_string());
        let action = fas_guard.tick(margin, thermal_thresh).await?;

        match action {
            ScalingAction::Boost => {
                if self.last.profile_mode != Some(ProfileMode::Performance) {
                    info!(target: "auriya::fas", "FAS decision: BOOST → applying PERFORMANCE");
                    profile::apply_performance_with_config(game_governor, true, None)?;
                    self.last.profile_mode = Some(ProfileMode::Performance);
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
                if self.last.profile_mode != Some(ProfileMode::Balance) {
                    info!(target: "auriya::fas", "FAS decision: REDUCE → applying BALANCE");
                    profile::apply_balance(&self.balance_governor)?;
                    self.last.profile_mode = Some(ProfileMode::Balance);
                } else {
                    debug!(target: "auriya::fas", "FAS decision: REDUCE → already BALANCE, skip");
                }
                Ok(true)
            }
        }
    }
}

pub async fn run_with_config_and_logger(cfg: &DaemonConfig, reload: ReloadHandle) -> Result<()> {
    run_with_config(cfg, reload).await
}

pub async fn run_with_config(cfg: &DaemonConfig, filter_handle: ReloadHandle) -> Result<()> {
    info!(target: "auriya::daemon", "Starting Auriya daemon...");

    // Fetch supported modes on startup (async)
    let supported_modes = match crate::core::display::get_app_supported_modes().await {
        Ok(modes) => {
            info!(target: "auriya::daemon", "Cached {} supported display modes", modes.len());
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
    info!(target: "auriya::daemon", "IPC socket should be ready at /dev/socket/auriya.sock");

    let mut watch_rx = daemon.init_watcher();

    info!(target: "auriya::daemon", "Tick loop started (interval: {:?})", cfg.poll_interval);
    let mut tick_interval = time::interval(cfg.poll_interval);
    tick_interval.tick().await;

    loop {
        tokio::select! {
            _ = tick_interval.tick() => {
                daemon.tick().await;
            }
            Some(msg) = watch_rx.recv() => {
                if msg == "settings" {
                     daemon.reload_settings();
                } else {
                     daemon.rebuild_whitelist();
                     debug!(target: "auriya::daemon", "Gamelist reload notification received");
                }
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
