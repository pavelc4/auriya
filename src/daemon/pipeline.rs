use crate::core::config::gamelist::GameList;
use crate::core::pid_tracker::PidTracker;
use crate::core::profile::{self, ProfileMode};
use crate::core::scaling::ScalingAction;
use crate::daemon::engine::{Daemon, bump_log, should_log_change};
use anyhow::Result;
use std::sync::Arc;
use tracing::{debug, error, warn};

/// Subset of power state consumed by the daemon tick.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PowerSnapshot {
    pub screen_awake: bool,
    pub battery_saver: bool,
}

/// Pipeline input collected during the Ingest phase.
#[derive(Debug, Clone)]
pub struct PipelineInput {
    pub power: PowerSnapshot,
    pub foreground_pkg: Option<String>,
    pub focused_pid: Option<i32>,
    pub is_pid_valid: bool,
}

/// Decision determined during the Decide phase.
#[derive(Debug, PartialEq, Eq)]
pub enum PipelineDecision {
    Powersave,
    NoForeground,
    NonWhitelisted { pkg: String, reason: &'static str },
    SameGameFas { pkg: String },
    SameGameNoop,
    EnterGame { pkg: String, pid: i32 },
}

impl Daemon {
    /// Ingest phase: gather current system power, focused app, and PID validity.
    pub fn ingest(&mut self) -> PipelineInput {
        let (screen_awake, battery_saver) = self.status_cache.power_state();
        let power = PowerSnapshot {
            screen_awake,
            battery_saver,
        };

        let mut pkg_opt: Option<String> =
            self.override_foreground.read().ok().and_then(|o| o.clone());

        if pkg_opt.is_none() {
            pkg_opt = self.status_cache.focused_package();
        }

        let is_pid_valid = self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        let focused_pid = if let Some(ref pkg) = pkg_opt {
            self.status_cache.focused_pid().filter(|&p| {
                crate::core::procfs::is_pid_valid(p)
                    && crate::core::procfs::verify_pid_package(p, pkg)
            })
        } else {
            None
        };

        PipelineInput {
            power,
            foreground_pkg: pkg_opt,
            focused_pid,
            is_pid_valid,
        }
    }

    /// Decide phase: deterministic mapping from ingested input to policy decision.
    pub fn decide(&self, input: &PipelineInput, gamelist: &GameList) -> PipelineDecision {
        if !input.power.screen_awake || input.power.battery_saver {
            return PipelineDecision::Powersave;
        }

        let Some(ref pkg) = input.foreground_pkg else {
            return PipelineDecision::NoForeground;
        };

        if self.last.pkg.as_deref() == Some(pkg.as_str()) && input.is_pid_valid {
            let fas_enabled = self
                ._shared_settings
                .read()
                .map(|s| s.fas.enabled)
                .unwrap_or(true);

            if fas_enabled
                && self.fas_controller.is_some()
                && gamelist.game.iter().any(|a| a.package == *pkg)
            {
                return PipelineDecision::SameGameFas { pkg: pkg.clone() };
            }
            return PipelineDecision::SameGameNoop;
        }

        if !self.cached_whitelist.contains(pkg) {
            return PipelineDecision::NonWhitelisted {
                pkg: pkg.clone(),
                reason: "not whitelisted",
            };
        }

        match input.focused_pid {
            Some(pid) => PipelineDecision::EnterGame {
                pkg: pkg.clone(),
                pid,
            },
            None => PipelineDecision::NonWhitelisted {
                pkg: pkg.clone(),
                reason: "PID not found",
            },
        }
    }

    /// Apply phase: execute the decided state changes and hardware configurations.
    pub(crate) async fn apply(
        &mut self,
        decision: PipelineDecision,
        gamelist: &GameList,
    ) -> Result<()> {
        match decision {
            PipelineDecision::Powersave => self.apply_powersave_state(),
            PipelineDecision::NoForeground => self.handle_no_foreground().await,
            PipelineDecision::NonWhitelisted { pkg, reason } => {
                self.apply_balance_and_clear(&pkg, reason).await
            }
            PipelineDecision::SameGameFas { pkg } => self.run_fas_pipeline(&pkg, gamelist).await,
            PipelineDecision::SameGameNoop => {
                debug!(target: "auriya::daemon", "Same app with known PID; skip profile reapply");
                Ok(())
            }
            PipelineDecision::EnterGame { pkg, pid } => {
                self.apply_enter_game(&pkg, pid, gamelist).await
            }
        }
    }

    /// Execute the full ingest -> decide -> apply pipeline.
    pub async fn execute_pipeline(&mut self, gamelist: &GameList) -> Result<()> {
        let input = self.ingest();
        let decision = self.decide(&input, gamelist);
        self.apply(decision, gamelist).await
    }

    fn apply_powersave_state(&mut self) -> Result<()> {
        let (screen_awake, battery_saver) = self.status_cache.power_state();
        let target = ProfileMode::Powersave;
        if self.last.profile_mode != Some(target) {
            if let Err(e) = profile::apply_powersave() {
                error!(target: "auriya::profile", ?e, "Failed to apply POWERSAVE");
            } else {
                debug!(target: "auriya::daemon", "Applied POWERSAVE (screen: {}, saver: {})", screen_awake, battery_saver);
                self.last.profile_mode = Some(target);
            }
        }
        self.apply_ceiling_for_state(Some(crate::core::tweaks::ceiling::CeilingLevel::Low), None);
        self.ebpf_detach();
        self.sync_dnd(crate::core::cmd_writer::DndFilter::All);
        self.last.screen_awake = Some(screen_awake);
        self.last.battery_saver = Some(battery_saver);
        Ok(())
    }

    async fn handle_no_foreground(&mut self) -> Result<()> {
        if self.last.profile_mode != Some(self.default_mode) {
            let gov_guard = self.balance_governor.read();
            let default_gov = gov_guard
                .as_deref()
                .map(|s| s.as_str())
                .unwrap_or("schedutil");
            let res = match self.default_mode {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Fast => profile::apply_fast(),
                ProfileMode::Balance => profile::apply_balance(default_gov),
                ProfileMode::Powersave => profile::apply_powersave(),
            };

            if let Err(e) = res {
                error!(target: "auriya::profile", ?e, "Failed to apply {:?}", self.default_mode);
            } else {
                debug!(target: "auriya::daemon", "Applied {:?} (no foreground)", self.default_mode);
                self.last.profile_mode = Some(self.default_mode);
            }
        }

        self.apply_ceiling_for_state(None, None);
        self.ebpf_detach();
        if let Some(mut f) = self
            .fas_controller
            .as_ref()
            .and_then(|fas| fas.try_lock().ok())
        {
            f.reset();
        }
        self.fps_meter.clear();
        self.sync_dnd(crate::core::cmd_writer::DndFilter::All);

        if self.last.pkg.is_some() || self.last.pid.is_some() {
            let was_game = self
                .last
                .pkg
                .as_ref()
                .is_some_and(|p| self.cached_whitelist.contains(p));
            if was_game {
                let last_p = self.last.pkg.clone().unwrap_or_default();
                broadcast_intent("dev.auriya.app.ACTION_GAME_EXIT", &[("pkg", &last_p)]);
            }

            self.vendor_lock.unlock_all();

            if should_log_change(&self.last, &self.cfg) {
                debug!(target: "auriya::daemon", "No foreground app detected");
                bump_log(&mut self.last);
            }

            if self.applied_refresh_rate.is_some() {
                let last_pkg = self.last.pkg.as_deref().unwrap_or("?");
                debug!(target: "auriya::display", "Releasing game overrides for {} (no foreground)", last_pkg);
                if self.apply_refresh_rate_fallback(0) {
                    self.applied_refresh_rate = None;
                }
            }
            self.set_pid(None);
        }
        Ok(())
    }

    async fn apply_balance_and_clear(&mut self, pkg: &str, reason: &str) -> Result<()> {
        if self.last.pkg.as_deref() != Some(pkg) {
            self.vendor_lock.unlock_all();
            self.restore_thermal_default();

            if self.applied_refresh_rate.is_some() {
                debug!(target: "auriya::display", "Releasing game overrides for {} ({})", pkg, reason);
                if self.apply_refresh_rate_fallback(0) {
                    self.applied_refresh_rate = None;
                }
            }
        }

        if self.last.profile_mode != Some(self.default_mode) {
            let gov_guard = self.balance_governor.read();
            let default_gov = gov_guard
                .as_deref()
                .map(|s| s.as_str())
                .unwrap_or("schedutil");
            let res = match self.default_mode {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Fast => profile::apply_fast(),
                ProfileMode::Balance => profile::apply_balance(default_gov),
                ProfileMode::Powersave => profile::apply_powersave(),
            };

            if let Err(e) = res {
                error!(target: "auriya::profile", ?e, "Failed to apply {:?}", self.default_mode);
            } else {
                debug!(target: "auriya::daemon", "Applied {:?} ({})", self.default_mode, reason);
                self.last.profile_mode = Some(self.default_mode);
            }
        }

        self.apply_ceiling_for_state(None, None);
        self.ebpf_detach();
        if let Some(mut f) = self
            .fas_controller
            .as_ref()
            .and_then(|fas| fas.try_lock().ok())
        {
            f.reset();
        }
        self.fps_meter.clear();
        self.sync_dnd(crate::core::cmd_writer::DndFilter::All);
        let was_game = self
            .last
            .pkg
            .as_ref()
            .is_some_and(|p| self.cached_whitelist.contains(p));
        if was_game {
            let last_p = self.last.pkg.clone().unwrap_or_default();
            broadcast_intent("dev.auriya.app.ACTION_GAME_EXIT", &[("pkg", &last_p)]);
        }

        if self.last.pkg.as_deref() != Some(pkg) {
            debug!(target: "auriya::daemon", "Foreground: {} ({})", pkg, reason);
        }

        self.last.pkg = Some(pkg.to_string());
        self.set_pid(None);
        Ok(())
    }

    async fn apply_enter_game(&mut self, pkg: &str, pid: i32, gamelist: &GameList) -> Result<()> {
        let changed = self.last.pkg.as_deref() != Some(pkg) || self.last.pid != Some(pid);
        if changed && should_log_change(&self.last, &self.cfg) {
            debug!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
            bump_log(&mut self.last);
        }
        if self.last.pkg.as_deref() != Some(pkg)
            && let Some(last_pkg) = self.last.pkg.as_deref()
            && self.applied_refresh_rate.is_some()
        {
            debug!(target: "auriya::display", "Releasing refresh-rate override for {}", last_pkg);
            if self.apply_refresh_rate_fallback(0) {
                self.applied_refresh_rate = None;
            }
        }

        let global_dnd = self
            ._shared_settings
            .read()
            .map(|s| s.dnd.default_enable)
            .unwrap_or(true);
        let game_cfg = gamelist.find(pkg);
        let default_gov = self
            .balance_governor
            .read()
            .ok()
            .map(|g| g.clone())
            .unwrap_or_else(|| "schedutil".to_string());
        let governor = game_cfg
            .filter(|c| !c.cpu_governor.is_empty())
            .map(|c| c.cpu_governor.as_str())
            .unwrap_or(&default_gov);
        let enable_dnd = global_dnd && game_cfg.map(|c| c.enable_dnd).unwrap_or(true);
        let target_mode = game_cfg
            .and_then(|c| c.mode.as_deref())
            .map(|m| match m.to_lowercase().as_str() {
                "powersave" | "3" => ProfileMode::Powersave,
                "balance" | "2" => ProfileMode::Balance,
                "fast" | "fas" | "4" => ProfileMode::Fast,
                _ => ProfileMode::Performance,
            })
            .unwrap_or(ProfileMode::Performance);

        let entering_game = self.last.pkg.as_deref() != Some(pkg);
        if entering_game {
            self.vendor_lock.lock_all();
            self.apply_thermal_for_game(game_cfg);

            let mode_str = match target_mode {
                ProfileMode::Performance => "Performance",
                ProfileMode::Balance => "Balance",
                ProfileMode::Powersave => "Powersave",
                ProfileMode::Fast => "FAS",
            };
            let app_name = pkg.rsplit('.').next().unwrap_or(pkg);
            let msg = format!("Auriya: Applied {} Profile ({})", mode_str, app_name);
            broadcast_intent(
                "dev.auriya.app.ACTION_GAME_ENTER",
                &[("pkg", pkg), ("mode", mode_str), ("message", &msg)],
            );
        }

        if self.last.profile_mode != Some(target_mode) {
            let res = match target_mode {
                ProfileMode::Performance | ProfileMode::Fast => {
                    profile::apply_performance_with_config(governor, enable_dnd, Some(pid))
                }
                ProfileMode::Balance => profile::apply_balance(governor),
                ProfileMode::Powersave => profile::apply_powersave(),
            };

            if let Err(e) = res {
                error!(target: "auriya::profile", ?e, "Failed to apply {:?}", target_mode);
            } else {
                debug!(target: "auriya::daemon", "Applied {:?} for {} (governor: {}, dnd: {})", target_mode, pkg, governor, enable_dnd);
                self.last.profile_mode = Some(target_mode);
            }
        }

        let ceiling_level = game_cfg
            .and_then(|c| c.ceiling.as_deref())
            .and_then(|s| s.parse::<crate::core::tweaks::ceiling::CeilingLevel>().ok());
        self.apply_ceiling_for_state(ceiling_level, Some(pkg));

        let rr = game_cfg.and_then(|c| c.refresh_rate);
        let rr_changed = rr.is_some() && self.applied_refresh_rate != rr;

        if rr_changed && self.apply_refresh_rate_fallback(rr.unwrap_or(0)) {
            debug!(target: "auriya::display", "Requested refresh rate {}Hz for {}", rr.unwrap_or(0), pkg);
            self.applied_refresh_rate = rr;
        }

        self.ebpf_attach(pid);
        self.sync_dnd(if enable_dnd {
            crate::core::cmd_writer::DndFilter::Priority
        } else {
            crate::core::cmd_writer::DndFilter::All
        });
        self.last.pkg = Some(pkg.to_string());
        self.set_pid(Some(pid));
        Ok(())
    }

    async fn run_fas_pipeline(&mut self, pkg: &str, gamelist: &GameList) -> Result<()> {
        let Some(ref fas_arc) = self.fas_controller.clone() else {
            return Ok(());
        };
        let game_cfg = gamelist.find(pkg);
        let default_gov = self
            .balance_governor
            .read()
            .ok()
            .map(|g| g.clone())
            .unwrap_or_else(|| "schedutil".to_string());
        let governor = game_cfg
            .filter(|c| !c.cpu_governor.is_empty())
            .map(|c| c.cpu_governor.as_str())
            .unwrap_or(&default_gov);
        let global_dnd = self
            ._shared_settings
            .read()
            .map(|s| s.dnd.default_enable)
            .unwrap_or(true);
        let enable_dnd = global_dnd && game_cfg.map(|c| c.enable_dnd).unwrap_or(true);

        if let Some(cfg) = game_cfg
            && let Some(ref fps_cfg) = cfg.target_fps
        {
            let fps_key = fps_cfg
                .to_buffer_config()
                .values()
                .iter()
                .map(|v| v.to_string())
                .collect::<Vec<_>>()
                .join(",");
            if self.last_fps_config.as_deref() != Some(&fps_key) {
                let mut f = fas_arc.lock().await;
                f.set_target_fps_config(fps_cfg.to_buffer_config());
                self.last_fps_config = Some(fps_key);
            }
        }

        match self
            .run_fas_tick(fas_arc, pkg, governor, self.last.pid, enable_dnd)
            .await
        {
            Ok(_) => debug!(target: "auriya::fas", "FAS tick completed"),
            Err(e) => warn!(target: "auriya::fas", "FAS tick error: {:?}", e),
        }
        Ok(())
    }

    pub(crate) async fn run_fas_tick(
        &mut self,
        fas: &Arc<tokio::sync::Mutex<crate::core::fas::FasController>>,
        pkg: &str,
        game_governor: &str,
        pid: Option<i32>,
        enable_dnd: bool,
    ) -> Result<bool> {
        let action = {
            let mut fas_guard = fas.lock().await;
            fas_guard.set_package(pkg.to_string(), pid);
            fas_guard.tick().await?
        };

        let pid_opt = self.last.pid;
        match action {
            ScalingAction::BoostGpu => {
                self.apply_boost_if_needed("BOOST_GPU", profile::apply_gpu_boost)
            }
            ScalingAction::BoostCpu => self.apply_boost_if_needed("BOOST_CPU", || {
                profile::apply_cpu_boost(game_governor, pid_opt)
            }),
            ScalingAction::BoostBalanced => self.apply_boost_if_needed("BOOST_BAL", || {
                profile::apply_performance_with_config(game_governor, enable_dnd, None)
            }),
            ScalingAction::Maintain => {
                debug!(target: "auriya::fas", "FAS decision: MAINTAIN → no change");
                Ok(true)
            }
            ScalingAction::Reduce => self.apply_reduce_to_default(game_governor, enable_dnd),
        }
    }

    fn apply_boost_if_needed<F>(&mut self, label: &str, apply_fn: F) -> Result<bool>
    where
        F: FnOnce() -> Result<()>,
    {
        if self.last.profile_mode != Some(ProfileMode::Performance) {
            debug!(target: "auriya::fas", "FAS decision: {label} → applying boost");
            if let Err(e) = apply_fn() {
                error!(target: "auriya::fas", ?e, "Failed to apply {label}");
            }
            self.last.profile_mode = Some(ProfileMode::Performance);
        } else {
            debug!(target: "auriya::fas", "FAS decision: {label} → already boosted, skip");
        }
        Ok(true)
    }

    fn apply_reduce_to_default(&mut self, game_governor: &str, enable_dnd: bool) -> Result<bool> {
        if self.last.profile_mode != Some(self.default_mode) {
            let res = match self.default_mode {
                ProfileMode::Performance | ProfileMode::Fast => {
                    profile::apply_performance_with_config(game_governor, enable_dnd, None)
                }
                ProfileMode::Balance => profile::apply_balance_with_dnd(game_governor, enable_dnd),
                ProfileMode::Powersave => profile::apply_powersave_with_dnd(enable_dnd),
            };

            if let Err(e) = res {
                error!(target: "auriya::fas", ?e, "FAS decision: REDUCE → failed to apply {:?}", self.default_mode);
            } else {
                debug!(target: "auriya::fas", "FAS decision: REDUCE → applying {:?}", self.default_mode);
                self.last.profile_mode = Some(self.default_mode);
            }
        } else {
            debug!(target: "auriya::fas", "FAS decision: REDUCE → already {:?}, skip", self.default_mode);
        }
        Ok(true)
    }

    pub(crate) fn apply_ceiling_for_state(
        &mut self,
        game_override: Option<crate::core::tweaks::ceiling::CeilingLevel>,
        _pkg: Option<&str>,
    ) {
        let level = match game_override {
            Some(l) => l,
            None => self.ceiling_config.default,
        };

        if self.current_ceiling == Some(level) {
            return;
        }

        debug!(
            target: "auriya::ceiling",
            "Applying ceiling level: {} (was {:?})",
            level, self.current_ceiling
        );
        profile::apply_ceiling(&mut self.ceiling_controller, level, &self.ceiling_config);
        self.current_ceiling = Some(level);
    }

    fn apply_thermal_for_game(&mut self, game_cfg: Option<&crate::core::config::GameProfile>) {
        use crate::core::tweaks::thermal::{self, ThermalPreset};

        let thermal_enabled = self
            ._shared_settings
            .read()
            .map(|s| s.thermal.enabled)
            .unwrap_or(false);
        if !thermal_enabled {
            return;
        }

        let Some(preset_str) = game_cfg.and_then(|c| c.thermal_preset.as_deref()) else {
            return;
        };
        let Ok(preset) = preset_str.parse::<ThermalPreset>() else {
            warn!(target: "auriya::thermal", "Invalid thermal preset for game: {}", preset_str);
            return;
        };

        match thermal::set_preset(preset) {
            Ok(()) => {
                debug!(target: "auriya::thermal", "Applied preset {} for game", preset);
                self.last.thermal_applied = Some(preset);
            }
            Err(e) => error!(target: "auriya::thermal", ?e, "Failed to apply thermal preset"),
        }
    }

    fn restore_thermal_default(&mut self) {
        use crate::core::tweaks::thermal;

        if self.last.thermal_applied.is_none() {
            return;
        }
        self.last.thermal_applied = None;

        let configured = self
            ._shared_settings
            .read()
            .ok()
            .and_then(|s| s.thermal.default);
        let Some(value) = configured.or(self.last.thermal_default) else {
            return;
        };

        if let Err(e) = thermal::set_value(value) {
            warn!(target: "auriya::thermal", ?e, "Failed to restore thermal value");
        } else {
            debug!(target: "auriya::thermal", "Restored thermal sconfig={value}");
        }
    }

    fn ebpf_attach(&mut self, pid: i32) {
        let Some(listener) = self.ebpf.as_ref() else {
            return;
        };
        if self.attached_ebpf_pid == Some(pid) {
            return;
        }
        if let Some(prev) = self.attached_ebpf_pid {
            let _ = listener.detach(prev);
        }
        match listener.attach(pid) {
            Ok(_) => {
                self.attached_ebpf_pid = Some(pid);
                debug!(target: "auriya::ebpf", "Attached frame probe to {pid}");
            }
            Err(e) => warn!(target: "auriya::ebpf", "attach({pid}): {e}"),
        }
    }

    pub(crate) fn ebpf_detach(&mut self) {
        let Some(listener) = self.ebpf.as_ref() else {
            return;
        };
        if let Some(pid) = self.attached_ebpf_pid.take() {
            match listener.detach(pid) {
                Ok(_) => debug!(target: "auriya::ebpf", "Detached frame probe from {pid}"),
                Err(e) => warn!(target: "auriya::ebpf", "detach({pid}): {e}"),
            }
        }
    }

    fn sync_dnd(&mut self, desired: crate::core::cmd_writer::DndFilter) {
        if self.last_dnd == Some(desired) {
            return;
        }
        crate::core::profile::request_dnd(desired);
        self.last_dnd = Some(desired);
        debug!(target: "auriya::daemon", "DnD → {:?}", desired);
    }
}

pub(crate) fn broadcast_intent(action: &str, extras: &[(&str, &str)]) {
    let mut cmd_str = format!("am broadcast -a {action} -f 0x01000020 --include-stopped-packages");
    for (k, v) in extras {
        let escaped_v = v.replace('\"', "\\\"");
        cmd_str.push_str(&format!(" --es {k} \"{escaped_v}\""));
    }
    let _ = std::process::Command::new("su")
        .args(["2000", "-c", &cmd_str])
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn();
}
