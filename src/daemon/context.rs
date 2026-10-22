use crate::core::config::gamelist::{GameList, GameProfile, GameProfileUpdate};
use crate::core::display::DisplayMode;
use crate::core::profile::ProfileMode;
use crate::core::stats::{FpsStats, StatsSnapshot};
use crate::daemon::ipc::types::LogLevelCmd;
use crate::daemon::run::ReloadHandle;
use crate::daemon::state::CurrentState;
use anyhow::{Result, anyhow};
use std::collections::BTreeSet;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use tracing::{debug, error};
use tracing_subscriber::EnvFilter;

/// Central daemon execution and state context.
///
/// Encapsulates shared state, hardware capabilities, and IPC command handlers
/// replacing fragmented handle bags and boxed callbacks.
pub struct DaemonContext {
    pub enabled: Arc<AtomicBool>,
    pub shared_config: Arc<RwLock<Arc<GameList>>>,
    pub override_foreground: Arc<RwLock<Option<String>>>,
    pub current_state: Arc<RwLock<CurrentState>>,
    pub balance_governor: Arc<RwLock<String>>,
    pub dnd_default: bool,
    pub current_log_level: Arc<RwLock<LogLevelCmd>>,
    pub supported_modes: Arc<Vec<DisplayMode>>,
    pub profile_lock: Arc<Mutex<()>>,
    pub fas_controller: Option<Arc<tokio::sync::Mutex<crate::core::fas::FasController>>>,
    pub reload_handle: Option<ReloadHandle>,
}

impl DaemonContext {
    /// Reload gamelist from disk into shared config.
    pub fn reload_gamelist(&self) -> Result<usize> {
        let new_cfg = crate::core::config::GameList::load(crate::core::config::gamelist_path())?;
        let mut g = self
            .shared_config
            .write()
            .map_err(|_| anyhow!("Gamelist lock poisoned"))?;
        let count = new_cfg.game.len();
        *g = Arc::new(new_cfg);
        Ok(count)
    }

    /// Process reload command: reloads gamelist and balance governor.
    pub fn reload(&self) -> String {
        let gl_result = self.reload_gamelist();
        if let (Ok(new_settings), Ok(mut g)) = (
            crate::core::config::Settings::load(crate::core::config::settings_path()),
            self.balance_governor.write(),
        ) {
            *g = new_settings.cpu.default_governor;
        }
        match gl_result {
            Ok(n) => format!("OK RELOADED {n}\n"),
            Err(e) => format!("ERR RELOAD {e:?}\n"),
        }
    }

    /// Update runtime logging level filter.
    pub fn set_log_level(&self, lvl: LogLevelCmd) {
        let filter_str = match lvl {
            LogLevelCmd::Trace => "trace",
            LogLevelCmd::Debug => "debug",
            LogLevelCmd::Info => "info",
            LogLevelCmd::Warn => "warn",
            LogLevelCmd::Error => "error",
        };
        if let Ok(mut l) = self.current_log_level.write() {
            *l = lvl;
        }
        if let Some(ref handle) = self.reload_handle {
            match handle.reload(EnvFilter::new(filter_str)) {
                Ok(_) => debug!(target: "auriya::ipc", "Log level changed to {:?}", lvl),
                Err(e) => error!(target: "auriya::ipc", "Failed to change log level: {}", e),
            }
        }
    }

    /// Set target FPS on FAS controller if active.
    pub async fn set_fps(&self, fps: u32) {
        if let Some(fas) = &self.fas_controller {
            let mut guard = fas.lock().await;
            guard.set_target_fps(fps);
        }
    }

    /// Get current target FPS from FAS controller (or 60 default).
    pub async fn get_fps(&self) -> u32 {
        if let Some(fas) = &self.fas_controller {
            let guard = fas.lock().await;
            return guard.get_target_fps();
        }
        60
    }

    /// Retrieve FPS window stats from FAS controller.
    pub async fn get_fps_stats(&self) -> Option<FpsStats> {
        match &self.fas_controller {
            Some(fas) => Some(fas.lock().await.fps_stats()),
            None => None,
        }
    }

    /// Format foreground PID response.
    pub fn get_pid(&self) -> String {
        let st = self
            .current_state
            .read()
            .ok()
            .map(|g| g.clone())
            .unwrap_or_default();
        match (st.pkg, st.pid) {
            (Some(p), Some(id)) => format!("PKG={p} PID={id}\n"),
            (Some(p), None) => format!("PKG={p} PID=None\n"),
            _ => "PKG=None PID=None\n".into(),
        }
    }

    /// Format daemon status report with telemetry.
    pub fn status(&self) -> String {
        let enabled = self.enabled.load(Ordering::Acquire);
        let n = self
            .shared_config
            .read()
            .ok()
            .map(|c| c.game.len())
            .unwrap_or(0);
        let ov = self.override_foreground.read().ok().and_then(|o| o.clone());
        let log_level = match self.current_log_level.read() {
            Ok(l) => format!("{l:?}"),
            Err(_) => "Unknown".to_string(),
        };

        let telemetry = self
            .current_state
            .read()
            .ok()
            .map(|st| format_telemetry(&st))
            .unwrap_or_default();

        format!("ENABLED={enabled} PACKAGES={n} OVERRIDE={ov:?} LOG_LEVEL={log_level}\n{telemetry}")
    }

    /// Apply profile mode with concurrency protection.
    pub fn set_profile(&self, mode: ProfileMode) -> Result<String> {
        let _profile_guard = self
            .profile_lock
            .lock()
            .map_err(|_| anyhow!("profile lock poisoned"))?;
        let gov_guard = self.balance_governor.read();
        let balance_gov = gov_guard
            .as_deref()
            .map(|s| s.as_str())
            .unwrap_or("schedutil");

        let r = match mode {
            ProfileMode::Performance => crate::core::profile::apply_performance(),
            ProfileMode::Balance => crate::core::profile::apply_balance(balance_gov),
            ProfileMode::Powersave => crate::core::profile::apply_powersave(),
            ProfileMode::Fast => crate::core::profile::apply_fast(),
        };
        match r {
            Ok(_) => Ok(format!("OK SET_PROFILE {mode:?}\n")),
            Err(e) => Ok(format!("ERR SET_PROFILE {e:?}\n")),
        }
    }

    /// Add a new game package to gamelist.
    pub fn add_game(&self, pkg: &str) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let profile = GameProfile {
            package: pkg.to_string(),
            cpu_governor: "performance".to_string(),
            enable_dnd: self.dnd_default,
            target_fps: None,
            refresh_rate: None,
            mode: Some("performance".to_string()),
            ceiling: None,
            thermal_preset: None,
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.add(profile) {
            return format!("ERR ADD_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK ADD_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Remove a game package from gamelist.
    pub fn remove_game(&self, pkg: &str) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.remove(pkg) {
            return format!("ERR REMOVE_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK REMOVE_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Update game package settings in gamelist.
    pub fn update_game(&self, pkg: &str, upd: GameProfileUpdate) -> String {
        let Ok(mut gl) = self.shared_config.write() else {
            return "ERR lock poisoned\n".into();
        };

        let g = Arc::make_mut(&mut gl);
        if let Err(e) = g.update(pkg, upd) {
            return format!("ERR UPDATE_GAME {e:?}\n");
        }
        match g.save(crate::core::config::gamelist_path()) {
            Ok(_) => format!("OK UPDATE_GAME {pkg}\n"),
            Err(e) => format!("ERR SAVE_GAMELIST {e:?}\n"),
        }
    }

    /// Get current gamelist serialized as JSON.
    pub fn get_gamelist(&self) -> String {
        let Ok(gl) = self.shared_config.read() else {
            return "ERR lock poisoned\n".into();
        };
        serde_json::to_string(&gl.game)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR GET_GAMELIST {e:?}\n"))
    }

    /// Get current measured and target FPS formatted response.
    pub async fn get_fps_response(&self) -> String {
        let target = self.get_fps().await;
        let is_gaming = self
            .current_state
            .read()
            .ok()
            .map(|s| s.game_session)
            .unwrap_or(false);
        let measured = if is_gaming {
            self.current_state.read().ok().and_then(|s| s.fps)
        } else {
            None
        };
        match measured {
            Some(m) => format!("FPS={m:.1} TARGET={target}\n"),
            None => format!("FPS=0 TARGET={target}\n"),
        }
    }

    /// Get display supported refresh rates formatted as JSON array.
    pub fn get_supported_rates(&self) -> String {
        let rates: Vec<u32> = self
            .supported_modes
            .iter()
            .map(|m| m.fps.round() as u32)
            .collect::<BTreeSet<_>>()
            .into_iter()
            .collect();

        serde_json::to_string(&rates)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR JSON {e:?}\n"))
    }

    /// Build full stats snapshot serialized as JSON.
    pub async fn build_stats_json(&self) -> String {
        let is_gaming = self
            .current_state
            .read()
            .ok()
            .map(|s| s.game_session)
            .unwrap_or(false);
        let fps = if is_gaming {
            self.get_fps_stats().await.filter(|f| f.frames > 0)
        } else {
            None
        };
        let bat = crate::core::telemetry::battery::snapshot();

        let snap = {
            let st = self.current_state.read().ok();
            let st = st.as_deref();
            StatsSnapshot::build(
                fps,
                st.and_then(|s| s.cpu_telemetry.as_ref()),
                st.and_then(|s| s.gpu_telemetry.as_ref()),
                st.and_then(|s| s.thermal_telemetry.as_ref()),
                &bat,
                st.and_then(|s| s.pkg.as_deref()),
                st.map(|s| s.profile).unwrap_or_default(),
                st.map(|s| s.game_session).unwrap_or(false),
            )
        };

        serde_json::to_string(&snap)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR JSON {e:?}\n"))
    }
}

fn format_telemetry(st: &CurrentState) -> String {
    let mut out = String::new();
    if let Some(fps) = st.fps {
        let source = match st.fps_source {
            Some(crate::core::fps_meter::FpsSource::Ebpf) => "ebpf",
            Some(crate::core::fps_meter::FpsSource::Sysfs) => "sysfs",
            None => "?",
        };
        out.push_str(&format!("FPS={fps:.1} SOURCE={source}\n"));
    }
    if let Some(ref cpu) = st.cpu_telemetry {
        out.push_str(&format!(
            "CPU_CORES={} CPU_LOAD={:.0}\n",
            cpu.cores.len(),
            cpu.load_pct
        ));
        for core in &cpu.cores {
            out.push_str(&format!(
                "CORE_{id}={id} online={online} freq={freq} governor={gov} cluster={cluster:?}\n",
                id = core.core_id,
                online = core.online,
                freq = core.cur_freq_khz,
                gov = core.governor,
                cluster = core.cluster,
            ));
        }
    }
    if let Some(ref gpu) = st.gpu_telemetry {
        out.push_str(&format!(
            "GPU_FREQ={} GPU_LOAD={} GPU_VENDOR={:?}\n",
            gpu.cur_freq_mhz.unwrap_or(0),
            gpu.load_pct.unwrap_or(0),
            gpu.vendor,
        ));
    }
    if let Some(ref thermal) = st.thermal_telemetry {
        let bat_c = crate::core::telemetry::battery::snapshot().temp_c;
        out.push_str(&format!(
            "TEMP_CPU={} TEMP_GPU={} TEMP_BAT={}\n",
            thermal
                .cpu_temp_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
            thermal
                .gpu_temp_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
            bat_c
                .map(|v| format!("{v:.1}"))
                .unwrap_or_else(|| "N/A".into()),
        ));
    }
    out
}
