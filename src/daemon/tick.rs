use crate::core::pid_tracker::PidTracker;
use crate::core::profile::{self, ProfileMode};
use crate::daemon::run::{
    COMPANION_HEALTH_CHECK_TICKS, Daemon, bump_log, now_ms, should_log_change,
    update_current_profile_file,
};
use anyhow::Result;
use std::sync::Arc;
use tracing::{debug, error, warn};

/// Local view of the relevant subset of `SystemStatus`. Defined here
/// rather than reusing a shared struct because the tick loop only ever
/// cares about these two booleans and we want to keep the conversion
/// from `Option<bool>` defaults colocated with the consumer.
struct PowerSnapshot {
    screen_awake: bool,
    battery_saver: bool,
}

impl Daemon {
    pub async fn tick(&mut self) {
        self.tick_count = self.tick_count.wrapping_add(1);
        debug!(target: "auriya::daemon", "Tick #{}", self.tick_count);

        // Periodic companion health check (spaced by COMPANION_HEALTH_CHECK_TICKS).
        if self.tick_count.is_multiple_of(COMPANION_HEALTH_CHECK_TICKS) {
            self.check_companion_health();
        }

        // Cheap `Arc` refcount bump instead of deep-copying the whole
        // GameList every tick — the guard cannot be held across the
        // `.await` in `process_tick_logic`, hence the clone.
        let gamelist = match self.shared_gamelist.read() {
            Ok(g) => Arc::clone(&g),
            Err(_) => {
                warn!(target: "auriya::daemon", "Gamelist lock poisoned, skipping tick");
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
        } else {
            let telemetry = self.telemetry_hub.snapshot(&self.ceiling_controller.layout);

            if let Ok(mut cur) = self.shared_current.write() {
                cur.pkg = self.last.pkg.clone();
                cur.pid = self.last.pid;
                cur.screen_awake = self.last.screen_awake.unwrap_or(false);
                cur.battery_saver = self.last.battery_saver.unwrap_or(false);
                cur.profile = self.last.profile_mode.unwrap_or(self.default_mode);
                cur.companion_alive = self.companion_alive;
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

    async fn process_tick_logic(&mut self, gamelist: &crate::core::config::GameList) -> Result<()> {
        use crate::core::profile;

        let (screen_awake, battery_saver) = self.status_cache.power_state();
        let power = PowerSnapshot {
            screen_awake,
            battery_saver,
        };

        let power_changed = self.last.screen_awake != Some(power.screen_awake)
            || self.last.battery_saver != Some(power.battery_saver);

        if !power.screen_awake || power.battery_saver {
            let target = ProfileMode::Powersave;
            if self.last.profile_mode != Some(target) {
                if let Err(e) = profile::apply_powersave() {
                    error!(target: "auriya::profile", ?e, "Failed to apply POWERSAVE");
                } else {
                    debug!(target: "auriya::daemon", "Applied POWERSAVE (screen: {}, saver: {})", power.screen_awake, power.battery_saver);
                    self.last.profile_mode = Some(target);
                }
            }
            self.apply_ceiling_for_state(
                Some(crate::core::tweaks::ceiling::CeilingLevel::Low),
                None,
            );
            // Screen off / battery saver: stop draining frames entirely.
            self.ebpf_detach();
            self.last.screen_awake = Some(power.screen_awake);
            self.last.battery_saver = Some(power.battery_saver);
            return Ok(());
        }

        if power_changed {
            debug!(target: "auriya::daemon", "Screen ON & saver OFF");
            self.last.screen_awake = Some(power.screen_awake);
            self.last.battery_saver = Some(power.battery_saver);
        }

        let mut pkg_opt: Option<String> =
            self.override_foreground.read().ok().and_then(|o| o.clone());

        if pkg_opt.is_none() {
            match self.status_cache.focused_package() {
                Some(p) => pkg_opt = Some(p),
                None => {
                    self.handle_no_foreground().await;
                    return Ok(());
                }
            }
        }
        let pkg = pkg_opt.unwrap();

        let pid_still_valid = self.pid_tracker.as_ref().is_some_and(PidTracker::is_alive);

        if self.last.pkg.as_deref() == Some(pkg.as_str()) && pid_still_valid {
            let fas_clone = self.fas_controller.clone();
            if let Some(fas) = fas_clone
                && gamelist.game.iter().any(|a| a.package == pkg)
            {
                let game_cfg = gamelist.find(&pkg);
                let governor = game_cfg
                    .map(|c| c.cpu_governor.clone())
                    .unwrap_or_else(|| self.balance_governor.clone());
                let enable_dnd = game_cfg.map(|c| c.enable_dnd).unwrap_or(true);

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
                        let mut f = fas.lock().await;
                        f.set_target_fps_config(fps_cfg.to_buffer_config());
                        self.last_fps_config = Some(fps_key);
                    }
                }

                match self
                    .run_fas_tick(&fas, &pkg, &governor, self.last.pid, enable_dnd)
                    .await
                {
                    Ok(_) => debug!(target: "auriya::fas", "FAS tick completed"),
                    Err(e) => warn!(target: "auriya::fas", "FAS tick error: {:?}", e),
                }
            }
            debug!(target: "auriya::daemon", "Same app with known PID; skip profile reapply");
            return Ok(());
        }

        if self.cached_whitelist.contains(&pkg) {
            self.handle_whitelisted_app(&pkg, gamelist).await
        } else {
            self.apply_balance_and_clear(&pkg, "not whitelisted").await
        }
    }

    async fn handle_whitelisted_app(
        &mut self,
        pkg: &str,
        gamelist: &crate::core::config::GameList,
    ) -> Result<()> {
        use crate::core::profile;

        let cached_pid = self.status_cache.focused_pid().filter(|&p| {
            crate::core::dumpsys::activity::is_pid_valid(p)
                && crate::core::dumpsys::activity::verify_pid_package(p, pkg)
        });
        match cached_pid {
            Some(pid) => {
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

                let game_cfg = gamelist.find(pkg);
                let governor = game_cfg
                    .map(|c| &c.cpu_governor[..])
                    .unwrap_or(&self.balance_governor);
                let enable_dnd = game_cfg.map(|c| c.enable_dnd).unwrap_or(true);
                let target_mode = game_cfg
                    .and_then(|c| c.mode.as_deref())
                    .map(|m| match m.to_lowercase().as_str() {
                        "powersave" => ProfileMode::Powersave,
                        "balance" => ProfileMode::Balance,
                        _ => ProfileMode::Performance,
                    })
                    .unwrap_or(ProfileMode::Performance);

                let entering_game = self.last.pkg.as_deref() != Some(pkg);
                if entering_game {
                    self.vendor_lock.lock_all();
                }

                if self.last.profile_mode != Some(target_mode) {
                    let res = match target_mode {
                        ProfileMode::Performance => {
                            profile::apply_performance_with_config(governor, enable_dnd, Some(pid))
                        }
                        ProfileMode::Balance => profile::apply_balance(
                            game_cfg
                                .filter(|c| !c.cpu_governor.is_empty())
                                .map(|c| &c.cpu_governor[..])
                                .unwrap_or(&self.balance_governor),
                        ),
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
                self.last.pkg = Some(pkg.to_string());
                self.set_pid(Some(pid));
                Ok(())
            }
            None => self.apply_balance_and_clear(pkg, "PID not found").await,
        }
    }

    async fn apply_balance_and_clear(&mut self, pkg: &str, reason: &str) -> Result<()> {
        use crate::core::profile;

        if self.last.pkg.as_deref() != Some(pkg) {
            self.vendor_lock.unlock_all();

            if self.applied_refresh_rate.is_some() {
                debug!(target: "auriya::display", "Releasing game overrides for {} ({})", pkg, reason);
                if self.apply_refresh_rate_fallback(0) {
                    self.applied_refresh_rate = None;
                }
            }
        }

        if self.last.profile_mode != Some(self.default_mode) {
            let res = match self.default_mode {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Balance => profile::apply_balance(&self.balance_governor),
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

        if self.last.pkg.as_deref() != Some(pkg) {
            debug!(target: "auriya::daemon", "Foreground: {} ({})", pkg, reason);
        }

        self.last.pkg = Some(pkg.to_string());
        self.set_pid(None);
        Ok(())
    }

    /// Attach the eBPF frame probe to a (validated) game PID. Only called
    /// for whitelisted games — the worker stays idle for every other app.
    /// No-op when already tracking this PID.
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

    /// Detach the eBPF frame probe so the worker thread stops draining
    /// frames (drops to ~0% CPU) once no game is in the foreground.
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

    async fn handle_no_foreground(&mut self) {
        use crate::core::profile;

        if self.last.profile_mode != Some(self.default_mode) {
            let res = match self.default_mode {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Balance => profile::apply_balance(&self.balance_governor),
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

        if self.last.pkg.is_some() || self.last.pid.is_some() {
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
    }

    pub(crate) async fn run_fas_tick(
        &mut self,
        fas: &Arc<tokio::sync::Mutex<crate::daemon::fas::FasController>>,
        pkg: &str,
        game_governor: &str,
        pid: Option<i32>,
        enable_dnd: bool,
    ) -> Result<bool> {
        use crate::core::{profile, scaling::ScalingAction};

        let thermal_thresh = 90.0;

        let action = {
            let mut fas_guard = fas.lock().await;
            fas_guard.set_package(pkg.to_string(), pid);
            fas_guard.tick(thermal_thresh).await?
        };

        match action {
            ScalingAction::BoostGpu => {
                debug!(target: "auriya::fas", "FAS decision: BOOST_GPU → GPU-only boost");
                if let Err(e) = profile::apply_gpu_boost() {
                    error!(target: "auriya::fas", ?e, "Failed to apply GPU boost");
                }
                self.last.profile_mode = Some(ProfileMode::Performance);
                Ok(true)
            }
            ScalingAction::BoostCpu => {
                debug!(target: "auriya::fas", "FAS decision: BOOST_CPU → CPU-only boost");
                if let Err(e) = profile::apply_cpu_boost(game_governor, self.last.pid) {
                    error!(target: "auriya::fas", ?e, "Failed to apply CPU boost");
                }
                self.last.profile_mode = Some(ProfileMode::Performance);
                Ok(true)
            }
            ScalingAction::BoostBalanced => {
                if self.last.profile_mode != Some(ProfileMode::Performance) {
                    debug!(target: "auriya::fas", "FAS decision: BOOST_BAL → full PERFORMANCE");
                    profile::apply_performance_with_config(game_governor, enable_dnd, None)?;
                    self.last.profile_mode = Some(ProfileMode::Performance);
                } else {
                    debug!(target: "auriya::fas", "FAS decision: BOOST_BAL → already PERFORMANCE, skip");
                }
                Ok(true)
            }
            ScalingAction::Maintain => {
                debug!(target: "auriya::fas", "FAS decision: MAINTAIN → no change");
                Ok(true)
            }
            ScalingAction::Reduce => {
                if self.last.profile_mode != Some(self.default_mode) {
                    let res = match self.default_mode {
                        ProfileMode::Performance => {
                            profile::apply_performance_with_config(game_governor, enable_dnd, None)
                        }
                        ProfileMode::Balance => {
                            profile::apply_balance_with_dnd(game_governor, enable_dnd)
                        }
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
        }
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
}
