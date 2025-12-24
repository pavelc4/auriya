use crate::core::profile::ProfileMode;
use crate::core::tweaks::vendor::mtk;
use crate::daemon::run::{
    Daemon, bump_log, now_ms, should_log_change, update_current_profile_file,
};
use anyhow::Result;
use std::sync::Arc;
use tracing::{debug, error, info, warn};

impl Daemon {
    pub async fn tick(&mut self) {
        self.tick_count = self.tick_count.wrapping_add(1);
        debug!(target: "auriya::daemon", "Tick #{}", self.tick_count);
        mtk::fix_mediatek_ppm();

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

        let power = if self.tick_count.is_multiple_of(5) || self.tick_count == 1 {
            crate::core::dumpsys::power::PowerState::fetch().await?
        } else {
            crate::core::dumpsys::power::PowerState {
                screen_awake: self.last.screen_awake.unwrap_or(true),
                battery_saver: self.last.battery_saver.unwrap_or(false),
            }
        };

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
            let fetch_foreground = self.tick_count.is_multiple_of(2) || self.tick_count == 1;

            if fetch_foreground {
                match crate::core::dumpsys::foreground::get_foreground_package().await? {
                    Some(p) => pkg_opt = Some(p),
                    None => {
                        self.handle_no_foreground().await;
                        return Ok(());
                    }
                }
            } else if let Some(last_pkg) = &self.last.pkg {
                pkg_opt = Some(last_pkg.clone());
            } else {
                return Ok(());
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

                if let Some(cfg) = game_cfg
                    && let Some(ref fps_cfg) = cfg.target_fps
                {
                    let mut f = fas.lock().await;
                    f.set_target_fps_config(fps_cfg.to_buffer_config());
                }

                match self
                    .run_fas_tick(&fas, &pkg, &governor, self.last.pid)
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

        match crate::core::dumpsys::activity::get_app_pid(pkg).await? {
            Some(pid) => {
                let changed = self.last.pkg.as_deref() != Some(pkg) || self.last.pid != Some(pid);
                if changed && should_log_change(&self.last, &self.cfg) {
                    info!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
                    bump_log(&mut self.last);
                }
                if self.last.pkg.as_deref() != Some(pkg)
                    && let Some(last_pkg) = &self.last.pkg
                    && let Some(original_rate) = self.refresh_rate_map.remove(last_pkg)
                {
                    info!(target: "auriya::display", "Restoring refresh rate for {}: {}Hz", last_pkg, original_rate);
                    if let Err(e) = crate::core::display::set_refresh_rate(original_rate).await {
                        error!(target: "auriya::display", ?e, "Failed to restore refresh rate");
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
                        info!(target: "auriya::daemon", "Applied {:?} for {} (governor: {}, dnd: {})", target_mode, pkg, governor, enable_dnd);
                        self.last.profile_mode = Some(target_mode);
                    }
                }

                if let Some(rr) = game_cfg.and_then(|c| c.refresh_rate) {
                    if !self.refresh_rate_map.contains_key(pkg) {
                        match crate::core::display::get_refresh_rate().await {
                            Ok(current) => {
                                self.refresh_rate_map.insert(pkg.to_string(), current);
                                debug!(target: "auriya::display", "Saved current rate for {}: {}Hz", pkg, current);
                            }
                            Err(e) => {
                                warn!(target: "auriya::display", "Failed to read current refresh rate: {}", e)
                            }
                        }
                    }

                    let is_supported = self
                        .supported_modes
                        .iter()
                        .any(|m| (m.fps - rr as f32).abs() < 0.1);

                    if is_supported {
                        if let Err(e) = crate::core::display::set_refresh_rate(rr).await {
                            error!(target: "auriya::display", ?e, "Failed to set refresh rate");
                        }
                    } else if self.supported_modes.is_empty() {
                        warn!(target: "auriya::display", "No supported modes cached, skipping refresh rate {}Hz for {}", rr, pkg);
                    } else {
                        warn!(target: "auriya::display", "Refresh rate {}Hz not supported by device, skipping for {}", rr, pkg);
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

        if self.last.pkg.as_deref() != Some(pkg)
            && let Some(last_pkg) = &self.last.pkg
            && let Some(original_rate) = self.refresh_rate_map.remove(last_pkg)
        {
            info!(target: "auriya::display", "Restoring refresh rate for {}: {}Hz ({})", last_pkg, original_rate, reason);
            let _ = crate::core::display::set_refresh_rate(original_rate).await;
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
                info!(target: "auriya::daemon", "Applied {:?} ({})", self.default_mode, reason);
                self.last.profile_mode = Some(self.default_mode);
            }
        }

        if (self.last.pkg.as_deref() != Some(pkg) || self.last.pid.is_some())
            && should_log_change(&self.last, &self.cfg)
        {
            info!(target: "auriya::daemon", "Foreground {} ({})", pkg, reason);
            bump_log(&mut self.last);
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
                info!(target: "auriya::daemon", "Applied {:?} (no foreground)", self.default_mode);
                self.last.profile_mode = Some(self.default_mode);
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
    }

    pub(crate) async fn run_fas_tick(
        &mut self,
        fas: &Arc<tokio::sync::Mutex<crate::daemon::fas::FasController>>,
        pkg: &str,
        game_governor: &str,
        pid: Option<i32>,
    ) -> Result<bool> {
        use crate::core::{profile, scaling::ScalingAction};

        let thermal_thresh = 90.0;

        let action = {
            let mut fas_guard = fas.lock().await;
            fas_guard.set_package(pkg.to_string(), pid);
            fas_guard.tick(thermal_thresh).await?
        };

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
                if self.last.profile_mode != Some(self.default_mode) {
                    let res = match self.default_mode {
                        ProfileMode::Performance => profile::apply_performance(),
                        ProfileMode::Balance => profile::apply_balance(&self.balance_governor),
                        ProfileMode::Powersave => profile::apply_powersave(),
                    };

                    if let Err(e) = res {
                        error!(target: "auriya::fas", ?e, "FAS decision: REDUCE → failed to apply {:?}", self.default_mode);
                    } else {
                        info!(target: "auriya::fas", "FAS decision: REDUCE → applying {:?}", self.default_mode);
                        self.last.profile_mode = Some(self.default_mode);
                    }
                } else {
                    debug!(target: "auriya::fas", "FAS decision: REDUCE → already {:?}, skip", self.default_mode);
                }
                Ok(true)
            }
        }
    }
}
