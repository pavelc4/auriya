use crate::core::profile::ProfileMode;
use crate::daemon::run::{
    Daemon, bump_log, now_ms, should_log_change, update_current_profile_file,
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

        let gamelist = match self.shared_gamelist.read() {
            Ok(g) => g.clone(),
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
        } else if let Ok(mut cur) = self.shared_current.write() {
            cur.pkg = self.last.pkg.clone();
            cur.pid = self.last.pid;
            cur.screen_awake = self.last.screen_awake.unwrap_or(false);
            cur.battery_saver = self.last.battery_saver.unwrap_or(false);
            cur.profile = self.last.profile_mode.unwrap_or(self.default_mode);

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

        let pid_still_valid = self
            .last
            .pid
            .is_some_and(crate::core::dumpsys::activity::is_pid_valid);

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
                let block_notif = game_cfg.map(|c| c.block_notifications).unwrap_or(false);

                if let Some(cfg) = game_cfg
                    && let Some(ref fps_cfg) = cfg.target_fps
                {
                    let fps_key = fps_cfg.to_buffer_config().values()
                        .iter().map(|v| v.to_string()).collect::<Vec<_>>().join(",");
                    if self.last_fps_config.as_deref() != Some(&fps_key) {
                        let mut f = fas.lock().await;
                        f.set_target_fps_config(fps_cfg.to_buffer_config());
                        self.last_fps_config = Some(fps_key);
                    }
                }

                match self
                    .run_fas_tick(&fas, &pkg, &governor, self.last.pid, enable_dnd, block_notif)
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
                    if let Err(e) = crate::core::cmd_writer::shared().write_refresh_rate(0) {
                        error!(target: "auriya::display", ?e, "Failed to release refresh-rate override");
                    } else {
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

                let rr = game_cfg.and_then(|c| c.refresh_rate);
                let want_lock = game_cfg.map(|c| c.lock_rotation).unwrap_or(false);
                let block_notif = game_cfg.map(|c| c.block_notifications).unwrap_or(false);
                let rr_changed = rr.is_some() && self.applied_refresh_rate != rr;
                let lock_changed = self.applied_lock_rotation != Some(want_lock);
                let notif_changed = block_notif != self.applied_block_notifications;

                let dnd_override: Option<crate::core::cmd_writer::DndFilter> = if block_notif {
                    Some(crate::core::cmd_writer::DndFilter::None)
                } else if notif_changed {
                    // Was previously blocking, now unblocked — write All to restore
                    Some(crate::core::cmd_writer::DndFilter::All)
                } else {
                    None
                };

                if rr_changed || lock_changed || notif_changed {
                    let want_rr = rr.unwrap_or(0);
                    let cmd_rr = if rr_changed { Some(want_rr) } else { None };
                    let cmd_lock = if lock_changed { Some(want_lock) } else { None };

                    if let Err(e) = crate::core::cmd_writer::shared().write_fields(
                        dnd_override,
                        cmd_rr,
                        cmd_lock,
                    ) {
                        error!(target: "auriya::display", ?e, "Failed to batch-write game settings for {}", pkg);
                    } else {
                        if rr_changed {
                            debug!(target: "auriya::display", "Requested refresh rate {}Hz for {}", want_rr, pkg);
                            self.applied_refresh_rate = rr;
                        }
                        if lock_changed {
                            debug!(target: "auriya::display", "Requested lock_rotation={} for {}", want_lock, pkg);
                            self.applied_lock_rotation = Some(want_lock);
                        }
                        if notif_changed {
                            debug!(target: "auriya::display", "{} block_notifications for {}", if block_notif { "Enabled" } else { "Disabled" }, pkg);
                            self.applied_block_notifications = block_notif;
                        }
                    }
                }

                self.last.pkg = Some(pkg.to_string());
                self.last.pid = Some(pid);
                Ok(())
            }
            None => self.apply_balance_and_clear(pkg, "PID not found").await,
        }
    }

    async fn apply_balance_and_clear(&mut self, pkg: &str, reason: &str) -> Result<()> {
        use crate::core::profile;

        if self.last.pkg.as_deref() != Some(pkg) {
            self.vendor_lock.unlock_all();

            let needs_release = self.applied_refresh_rate.is_some()
                || self.applied_lock_rotation == Some(true)
                || self.applied_block_notifications;

            if needs_release {
                let rr = if self.applied_refresh_rate.is_some() {
                    Some(0u32)
                } else {
                    None
                };
                let lock = if self.applied_lock_rotation == Some(true) {
                    Some(false)
                } else {
                    None
                };
                let dnd = if self.applied_block_notifications {
                    Some(crate::core::cmd_writer::DndFilter::All)
                } else {
                    None
                };

                let last_pkg = self.last.pkg.as_deref().unwrap_or(pkg);
                debug!(target: "auriya::display", "Releasing game overrides for {} ({})", last_pkg, reason);
                if let Err(e) = crate::core::cmd_writer::shared().write_fields(dnd, rr, lock) {
                    error!(target: "auriya::display", ?e, "Failed to release game overrides ({})", reason);
                } else {
                    self.applied_refresh_rate = None;
                    self.applied_lock_rotation = None;
                    self.applied_block_notifications = false;
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

        if self.last.pkg.as_deref() != Some(pkg) {
            debug!(target: "auriya::daemon", "Foreground: {} ({})", pkg, reason);
        }

        self.last.pkg = Some(pkg.to_string());
        self.last.pid = None;
        Ok(())
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

        if self.last.pkg.is_some() || self.last.pid.is_some() {
            self.vendor_lock.unlock_all();

            if should_log_change(&self.last, &self.cfg) {
                debug!(target: "auriya::daemon", "No foreground app detected");
                bump_log(&mut self.last);
            }
            let needs_release = self.applied_refresh_rate.is_some()
                || self.applied_lock_rotation == Some(true)
                || self.applied_block_notifications;

            if needs_release {
                let rr = if self.applied_refresh_rate.is_some() {
                    Some(0u32)
                } else {
                    None
                };
                let lock = if self.applied_lock_rotation == Some(true) {
                    Some(false)
                } else {
                    None
                };
                let dnd = if self.applied_block_notifications {
                    Some(crate::core::cmd_writer::DndFilter::All)
                } else {
                    None
                };

                let last_pkg = self.last.pkg.as_deref().unwrap_or("?");
                debug!(target: "auriya::display", "Releasing game overrides for {} (no foreground)", last_pkg);
                if let Err(e) = crate::core::cmd_writer::shared().write_fields(dnd, rr, lock) {
                    error!(target: "auriya::display", ?e, "Failed to release game overrides (no foreground)");
                } else {
                    self.applied_refresh_rate = None;
                    self.applied_lock_rotation = None;
                    self.applied_block_notifications = false;
                }
            }
            self.last.pid = None;
        }
    }

    /// Push `DndFilter::None` when the active game has `block_notifications`
    /// enabled, overriding whatever the profile just set (e.g. Priority-only).
    /// Also tracks applied state so exit paths can undo it.
    fn apply_block_notifications_if_needed(&mut self, block_notif: bool) {
        if block_notif && !self.applied_block_notifications {
            let _ = crate::core::cmd_writer::shared().write_dnd(
                crate::core::cmd_writer::DndFilter::None,
            );
            self.applied_block_notifications = true;
        } else if !block_notif && self.applied_block_notifications {
            let _ = crate::core::cmd_writer::shared().write_dnd(
                crate::core::cmd_writer::DndFilter::All,
            );
            self.applied_block_notifications = false;
        }
    }

    pub(crate) async fn run_fas_tick(
        &mut self,
        fas: &Arc<tokio::sync::Mutex<crate::daemon::fas::FasController>>,
        pkg: &str,
        game_governor: &str,
        pid: Option<i32>,
        enable_dnd: bool,
        block_notif: bool,
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
                    self.apply_block_notifications_if_needed(block_notif);
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
                        self.apply_block_notifications_if_needed(block_notif);
                    }
                } else {
                    debug!(target: "auriya::fas", "FAS decision: REDUCE → already {:?}, skip", self.default_mode);
                }
                Ok(true)
            }
        }
    }
}
