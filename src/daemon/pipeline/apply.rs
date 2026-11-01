use super::PipelineDecision;
use crate::core::config::gamelist::GameList;
use crate::core::profile::{self, ProfileMode};
use crate::daemon::engine::{Daemon, bump_log, should_log_change};
use anyhow::Result;
use tracing::{debug, error, warn};

impl Daemon {
    /// Apply phase: execute the decided state changes and hardware configurations.
    pub async fn apply(
        &mut self,
        decision: PipelineDecision<'_>,
        gamelist: &GameList,
    ) -> Result<()> {
        match decision {
            PipelineDecision::Powersave => self.apply_powersave_state().await,
            PipelineDecision::NoForeground => self.handle_no_foreground().await,
            PipelineDecision::NonWhitelisted {
                pkg,
                pid,
                pids,
                reason,
            } => self.apply_balance_and_clear(pkg, pid, pids, reason).await,
            PipelineDecision::SameAppFas { pkg, pids } => {
                self.run_fas_pipeline(pkg, pids, gamelist).await
            }
            PipelineDecision::SameAppMonitor { pkg } => self.monitor_app_frames(pkg).await,
            PipelineDecision::EnterGame { pkg, pid, pids } => {
                self.apply_enter_game(pkg, pid, pids, gamelist).await
            }
        }
    }

    async fn apply_powersave_state(&mut self) -> Result<()> {
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
        self.apply_ceiling_for_state(Some(crate::platform::cpu::ceiling::CeilingLevel::Low), None);
        self.ebpf_detach_all().await;
        if let Some(mut f) = self
            .fas_controller
            .as_ref()
            .and_then(|fas| fas.try_lock().ok())
        {
            f.reset();
        }
        self.last_fps_config = None;
        self.fps_meter.clear();
        self.set_pid(None);
        self.sync_dnd(crate::platform::binder::DndMode::All);
        let _ = crate::platform::binder::set_wifi_low_latency_lock(false);
        let _ = crate::platform::binder::set_statusbar_lock(false);
        let _ = crate::platform::binder::set_high_touch_polling_rate(false);
        let _ = crate::platform::binder::thaw_display_rotation(0);
        if self.applied_refresh_rate != Some(60) && self.apply_refresh_rate(60) {
            self.applied_refresh_rate = Some(60);
        }
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::ExpensiveRendering,
            false,
        );
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::SustainedPerformance,
            false,
        );
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::DeviceIdle,
            true,
        );
        self.last.screen_awake = Some(screen_awake);
        self.last.battery_saver = Some(battery_saver);
        Ok(())
    }

    async fn handle_no_foreground(&mut self) -> Result<()> {
        let active_default = self.default_mode();
        if self.last.profile_mode != Some(active_default) {
            let gov_guard = self.balance_governor.read();
            let default_gov = gov_guard
                .as_deref()
                .map(|s| s.as_str())
                .unwrap_or("schedutil");
            let res = match active_default {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Fast => profile::apply_fast(),
                ProfileMode::Balance => profile::apply_balance(default_gov),
                ProfileMode::Powersave => profile::apply_powersave(),
            };

            if let Err(e) = res {
                error!(target: "auriya::profile", ?e, "Failed to apply {:?}", active_default);
            } else {
                debug!(target: "auriya::daemon", "Applied {:?} (no foreground)", active_default);
                self.last.profile_mode = Some(active_default);
            }
        }

        self.apply_ceiling_for_state(None, None);
        self.ebpf_detach_all().await;
        if let Some(mut f) = self
            .fas_controller
            .as_ref()
            .and_then(|fas| fas.try_lock().ok())
        {
            f.reset();
        }
        self.last_fps_config = None;
        self.fps_meter.clear();
        self.sync_dnd(crate::platform::binder::DndMode::All);
        let _ = crate::platform::binder::set_wifi_low_latency_lock(false);
        let _ = crate::platform::binder::set_statusbar_lock(false);
        let _ = crate::platform::binder::set_high_touch_polling_rate(false);
        let _ = crate::platform::binder::thaw_display_rotation(0);
        if self.applied_refresh_rate.is_some() && self.apply_refresh_rate(0) {
            self.applied_refresh_rate = None;
        }
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::ExpensiveRendering,
            false,
        );
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::SustainedPerformance,
            false,
        );
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::DeviceIdle,
            false,
        );
        if self.last.pkg.is_some() || self.last.pid.is_some() {
            let was_game = self
                .last
                .pkg
                .as_ref()
                .is_some_and(|p| self.cached_whitelist.contains(p));
            if was_game {
                let last_p = self.last.pkg.as_deref().unwrap_or("");
                broadcast_intent("dev.auriya.app.ACTION_GAME_EXIT", &[("pkg", last_p)]);
            }

            self.vendor_lock.unlock_all();

            if should_log_change(&self.last, self.log_debounce_ms) {
                debug!(target: "auriya::daemon", "No foreground app detected");
                bump_log(&mut self.last);
            }

            if self.applied_refresh_rate.is_some() {
                let last_pkg = self.last.pkg.as_deref().unwrap_or("?");
                debug!(target: "auriya::display", "Releasing game overrides for {} (no foreground)", last_pkg);
                if self.apply_refresh_rate(0) {
                    self.applied_refresh_rate = None;
                }
            }
            self.set_pid(None);
        }
        Ok(())
    }

    async fn apply_balance_and_clear(
        &mut self,
        pkg: &str,
        pid: Option<i32>,
        pids: &[i32],
        reason: &str,
    ) -> Result<()> {
        if self.last.pkg.as_deref() != Some(pkg) {
            self.vendor_lock.unlock_all();
            self.restore_thermal_default();
            self.last_fps_config = None;

            if self.applied_refresh_rate.is_some() {
                debug!(target: "auriya::display", "Releasing game overrides for {} ({})", pkg, reason);
                if self.apply_refresh_rate(0) {
                    self.applied_refresh_rate = None;
                }
            }
            self.sync_dnd(crate::platform::binder::DndMode::All);
            let _ = crate::platform::binder::set_wifi_low_latency_lock(false);
            let _ = crate::platform::binder::set_statusbar_lock(false);
            let _ = crate::platform::binder::set_high_touch_polling_rate(false);
            let _ = crate::platform::binder::thaw_display_rotation(0);
            let _ = crate::platform::binder::set_power_mode(
                crate::platform::binder::PowerMode::ExpensiveRendering,
                false,
            );
            let _ = crate::platform::binder::set_power_mode(
                crate::platform::binder::PowerMode::SustainedPerformance,
                false,
            );
            let _ = crate::platform::binder::set_power_mode(
                crate::platform::binder::PowerMode::DeviceIdle,
                false,
            );

            let was_game = self
                .last
                .pkg
                .as_ref()
                .is_some_and(|p| self.cached_whitelist.contains(p));
            if was_game {
                let last_p = self.last.pkg.as_deref().unwrap_or("");
                broadcast_intent("dev.auriya.app.ACTION_GAME_EXIT", &[("pkg", last_p)]);
            }

            debug!(target: "auriya::daemon", "Foreground: {} ({})", pkg, reason);
        }

        let active_default = self.default_mode();
        if self.last.profile_mode != Some(active_default) {
            let gov_guard = self.balance_governor.read();
            let default_gov = gov_guard
                .as_deref()
                .map(|s| s.as_str())
                .unwrap_or("schedutil");
            let res = match active_default {
                ProfileMode::Performance => profile::apply_performance(),
                ProfileMode::Fast => profile::apply_fast(),
                ProfileMode::Balance => profile::apply_balance(default_gov),
                ProfileMode::Powersave => profile::apply_powersave(),
            };

            if let Err(e) = res {
                error!(target: "auriya::profile", ?e, "Failed to apply {:?}", active_default);
            } else {
                debug!(target: "auriya::daemon", "Applied {:?} ({})", active_default, reason);
                self.last.profile_mode = Some(active_default);
            }
        }

        self.apply_ceiling_for_state(None, None);
        let fallback_pid = [pid.unwrap_or(0)];
        let effective_pids: &[i32] = match (pids.is_empty(), pid) {
            (false, _) => pids,
            (true, Some(_)) => &fallback_pid[..],
            (true, None) => &[],
        };

        match effective_pids {
            [first, ..] => {
                self.sync_ebpf_pids(effective_pids).await;
                let primary_pid = Some(*first);
                self.set_pid(primary_pid);
                if let Some(mut f) = self
                    .fas_controller
                    .as_ref()
                    .and_then(|fas| fas.try_lock().ok())
                {
                    f.set_package(pkg, primary_pid);
                    let active_hz =
                        crate::platform::binder::get_active_display_refresh_rate() as u32;
                    let rates = crate::platform::binder::query_binder_supported_rates();
                    let filtered_rates: Vec<u32> = rates
                        .into_iter()
                        .filter(|&r| active_hz == 0 || r <= active_hz)
                        .collect();
                    let effective_rates = match filtered_rates.as_slice() {
                        [] => match active_hz {
                            hz if hz > 0 => vec![hz],
                            _ => vec![60],
                        },
                        _ => filtered_rates,
                    };
                    f.set_target_fps_config(crate::core::fas::buffer::TargetFps::Array(
                        effective_rates,
                    ));
                }
            }
            [] => {
                self.ebpf_detach_all().await;
                if let Some(mut f) = self
                    .fas_controller
                    .as_ref()
                    .and_then(|fas| fas.try_lock().ok())
                {
                    f.reset();
                }
                self.last_fps_config = None;
                self.fps_meter.clear();
                self.set_pid(None);
            }
        }

        self.last.pkg = Some(pkg.to_string());
        Ok(())
    }

    async fn monitor_app_frames(&mut self, _pkg: &str) -> Result<()> {
        let fallback_fps = self.fps_meter.read().map(|r| r.fps);
        if let Some(mut f) = self
            .fas_controller
            .as_ref()
            .and_then(|fas| fas.try_lock().ok())
        {
            f.drain_frames_with_fallback(fallback_fps);
        }
        Ok(())
    }

    async fn apply_enter_game(
        &mut self,
        pkg: &str,
        pid: i32,
        pids: &[i32],
        gamelist: &GameList,
    ) -> Result<()> {
        let changed = self.last.pkg.as_deref() != Some(pkg) || self.last.pid != Some(pid);
        if changed && should_log_change(&self.last, self.log_debounce_ms) {
            debug!(target: "auriya::daemon", "Foreground {} PID={}", pkg, pid);
            bump_log(&mut self.last);
        }
        if self.applied_refresh_rate == Some(60) {
            debug!(target: "auriya::display", "Releasing powersave 60Hz override before game entry");
            if self.apply_refresh_rate(0) {
                self.applied_refresh_rate = None;
            }
        }
        if self.last.pkg.as_deref() != Some(pkg)
            && let Some(last_pkg) = self.last.pkg.as_deref()
            && self.applied_refresh_rate.is_some()
        {
            debug!(target: "auriya::display", "Releasing refresh-rate override for {}", last_pkg);
            if self.apply_refresh_rate(0) {
                self.applied_refresh_rate = None;
            }
        }

        let global_dnd = self
            ._shared_settings
            .read()
            .map(|s| s.dnd.default_enable)
            .unwrap_or(true);
        let game_cfg = gamelist.find(pkg);
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

        let primary_pid = match pids.first().copied() {
            Some(p) => Some(p),
            None if pid > 0 && crate::platform::common::procfs::verify_pid_package(pid, pkg) => {
                Some(pid)
            }
            _ => None,
        };

        let entering_game = self.last.pkg.as_deref() != Some(pkg);
        if entering_game {
            self.last_fps_config = None;
            self.vendor_lock.lock_all();
            self.apply_thermal_for_game(game_cfg);
            if let Some(mut f) = self
                .fas_controller
                .as_ref()
                .and_then(|fas| fas.try_lock().ok())
            {
                f.set_package(pkg, primary_pid);
                if let Some(cfg) = game_cfg
                    && let Some(ref fps_cfg) = cfg.target_fps
                {
                    let buf_cfg = fps_cfg.to_buffer_config();
                    f.set_target_fps_config(buf_cfg);
                }
            }

            let mode_str = match target_mode {
                ProfileMode::Performance => "Performance",
                ProfileMode::Balance => "Balance",
                ProfileMode::Powersave => "Powersave",
                ProfileMode::Fast => "FAS",
            };
            let app_name = pkg.rsplit('.').next().unwrap_or(pkg);
            let msg = format!("Auriya: Applied {} Profile ({})", mode_str, app_name);
            let _ = crate::platform::binder::show_toast(&msg);
            broadcast_intent(
                "dev.auriya.app.ACTION_GAME_ENTER",
                &[("pkg", pkg), ("mode", mode_str), ("message", &msg)],
            );
        }

        if self.last.profile_mode != Some(target_mode) {
            let gov_guard = self.balance_governor.read().ok();
            let default_gov = gov_guard
                .as_deref()
                .map(|s| s.as_str())
                .unwrap_or("schedutil");
            let governor = game_cfg
                .filter(|c| !c.cpu_governor.is_empty())
                .map(|c| c.cpu_governor.as_str())
                .unwrap_or(default_gov);
            let res = match target_mode {
                ProfileMode::Performance | ProfileMode::Fast => {
                    profile::apply_performance_with_config(governor, enable_dnd, primary_pid)
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

        let ceiling_level = game_cfg.and_then(|c| c.ceiling.as_deref()).and_then(|s| {
            s.parse::<crate::platform::cpu::ceiling::CeilingLevel>()
                .ok()
        });
        self.apply_ceiling_for_state(ceiling_level, Some(pkg));

        let rr = game_cfg.and_then(|c| c.refresh_rate);
        let rr_changed = rr.is_some() && self.applied_refresh_rate != rr;

        if rr_changed && self.apply_refresh_rate(rr.unwrap_or(0)) {
            debug!(target: "auriya::display", "Requested refresh rate {}Hz for {}", rr.unwrap_or(0), pkg);
            self.applied_refresh_rate = rr;
        }

        if !pids.is_empty() {
            self.sync_ebpf_pids(pids).await;
        } else if let Some(p) = primary_pid {
            self.sync_ebpf_pids(std::slice::from_ref(&p)).await;
        } else {
            self.ebpf_detach_all().await;
        }
        self.sync_dnd(if enable_dnd {
            crate::platform::binder::DndMode::Priority
        } else {
            crate::platform::binder::DndMode::All
        });

        let wifi_lock = game_cfg.map(|c| c.wifi_lock).unwrap_or(true);
        let statusbar_lock = game_cfg.map(|c| c.statusbar_lock).unwrap_or(true);
        let touch_boost = game_cfg.map(|c| c.touch_boost).unwrap_or(true);
        let power_hal = game_cfg.map(|c| c.power_hal).unwrap_or(true);
        let silence_vibrator = game_cfg.map(|c| c.silence_vibrator).unwrap_or(false);
        let freeze_rotation = game_cfg.map(|c| c.freeze_rotation).unwrap_or(false);

        let _ = crate::platform::binder::set_wifi_low_latency_lock(wifi_lock);
        let _ = crate::platform::binder::set_statusbar_lock(statusbar_lock);
        let _ = crate::platform::binder::set_high_touch_polling_rate(touch_boost);
        if power_hal {
            let _ = crate::platform::binder::set_power_mode(
                crate::platform::binder::PowerMode::ExpensiveRendering,
                true,
            );
            let _ = crate::platform::binder::set_power_mode(
                crate::platform::binder::PowerMode::SustainedPerformance,
                true,
            );
        }
        let _ = crate::platform::binder::set_power_mode(
            crate::platform::binder::PowerMode::DeviceIdle,
            false,
        );
        if silence_vibrator {
            let _ = crate::platform::binder::silence_vibrator();
        }
        if freeze_rotation {
            let _ = crate::platform::binder::freeze_display_rotation(0, -1);
        }

        self.last.pkg = Some(pkg.to_string());
        self.set_pid(primary_pid);
        Ok(())
    }

    pub(crate) fn apply_ceiling_for_state(
        &mut self,
        game_override: Option<crate::platform::cpu::ceiling::CeilingLevel>,
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
        use crate::platform::thermal::sconfig::{self as thermal, ThermalPreset};

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
        use crate::platform::thermal::sconfig as thermal;

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

    pub(crate) async fn sync_ebpf_pids(&mut self, target_pids: &[i32]) {
        let Some(listener) = self.ebpf.as_ref() else {
            return;
        };

        // Detach PIDs no longer in target
        let to_detach: Vec<i32> = self
            .attached_ebpf_pids
            .iter()
            .copied()
            .filter(|p| !target_pids.contains(p))
            .collect();
        for pid in to_detach {
            match listener.detach(pid).await {
                Ok(_) => debug!(target: "auriya::ebpf", "Detached frame probe from {pid}"),
                Err(e) => warn!(target: "auriya::ebpf", "detach({pid}): {e}"),
            }
            self.attached_ebpf_pids.retain(|&p| p != pid);
        }

        // Attach new target PIDs
        for &pid in target_pids {
            if !self.attached_ebpf_pids.contains(&pid) {
                match listener.attach(pid).await {
                    Ok(_) => {
                        self.attached_ebpf_pids.push(pid);
                        debug!(target: "auriya::ebpf", "Attached frame probe to {pid}");
                    }
                    Err(e) => warn!(target: "auriya::ebpf", "attach({pid}): {e}"),
                }
            }
        }
    }

    pub(crate) async fn ebpf_detach_all(&mut self) {
        let Some(listener) = self.ebpf.as_ref() else {
            return;
        };
        for pid in self.attached_ebpf_pids.drain(..) {
            match listener.detach(pid).await {
                Ok(_) => debug!(target: "auriya::ebpf", "Detached frame probe from {pid}"),
                Err(e) => warn!(target: "auriya::ebpf", "detach({pid}): {e}"),
            }
        }
    }

    fn sync_dnd(&mut self, desired: crate::platform::binder::DndMode) {
        if self.last_dnd == Some(desired) {
            return;
        }
        if let Err(e) = crate::platform::binder::apply_dnd(desired) {
            tracing::warn!(target: "auriya::binder", "Failed to apply DnD {:?}: {}", desired, e);
        }
        self.last_dnd = Some(desired);
        debug!(target: "auriya::daemon", "DnD → {:?}", desired);
    }
}

pub(crate) fn broadcast_intent(action: &str, extras: &[(&str, &str)]) {
    if let Err(e) = crate::platform::binder::broadcast_intent(action, extras) {
        tracing::debug!(target: "auriya::daemon", "Failed to broadcast intent via Binder: {:?}", e);
    }
}
