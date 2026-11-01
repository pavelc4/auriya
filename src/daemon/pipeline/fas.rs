use crate::core::config::gamelist::GameList;
use crate::core::profile::{self, ProfileMode};
use crate::core::scaling::ScalingAction;
use crate::daemon::engine::Daemon;
use anyhow::Result;
use std::sync::Arc;
use tracing::{debug, error, warn};

impl Daemon {
    pub(crate) async fn run_fas_pipeline(
        &mut self,
        pkg: &str,
        pids: &[i32],
        gamelist: &GameList,
    ) -> Result<()> {
        let Some(fas_arc) = self.fas_controller.as_ref().cloned() else {
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
        let is_game = self.cached_whitelist.contains(pkg);
        let enable_dnd = is_game && global_dnd && game_cfg.map(|c| c.enable_dnd).unwrap_or(true);

        if self.status_cache.is_floating() {
            if self.last_fps_config.as_deref() != Some("floating_30") {
                let mut f = fas_arc.lock().await;
                f.set_target_fps_config(crate::core::fas::buffer::TargetFps::Single(30));
                self.last_fps_config = Some("floating_30".to_string());
                debug!(target: "auriya::fas", "Floating/freeform window detected for {}, capping FAS to 30 FPS", pkg);
            }
        } else if let Some(cfg) = game_cfg
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
        } else if self.last_fps_config.as_deref() != Some("auto_multi") {
            let mut f = fas_arc.lock().await;
            let rates = crate::platform::binder::query_binder_supported_rates();
            f.set_target_fps_config(crate::core::fas::buffer::TargetFps::Array(rates));
            self.last_fps_config = Some("auto_multi".to_string());
        }

        let active_default = self.default_mode();
        let current_profile = self.last.profile_mode.unwrap_or(active_default);
        let tuning = self
            ._shared_settings
            .read()
            .ok()
            .map(|s| crate::core::fas::FasTuning::for_mode(&s, current_profile));
        if let Some(tuning) = tuning {
            let mut f = fas_arc.lock().await;
            f.set_tuning(tuning);
        }

        match (pids.is_empty(), self.last.pid) {
            (false, _) => self.sync_ebpf_pids(pids),
            (true, Some(ref pid)) => self.sync_ebpf_pids(std::slice::from_ref(pid)),
            (true, None) => {}
        }

        match self
            .run_fas_tick(&fas_arc, pkg, governor, self.last.pid, enable_dnd)
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
            fas_guard.set_package(pkg, pid);
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
        let active_default = self.default_mode();
        if self.last.profile_mode != Some(active_default) {
            let res = match active_default {
                ProfileMode::Performance | ProfileMode::Fast => {
                    profile::apply_performance_with_config(game_governor, enable_dnd, None)
                }
                ProfileMode::Balance => profile::apply_balance_with_dnd(game_governor, enable_dnd),
                ProfileMode::Powersave => profile::apply_powersave_with_dnd(enable_dnd),
            };

            if let Err(e) = res {
                error!(target: "auriya::fas", ?e, "FAS decision: REDUCE → failed to apply {:?}", active_default);
            } else {
                debug!(target: "auriya::fas", "FAS decision: REDUCE → applying {:?}", active_default);
                self.last.profile_mode = Some(active_default);
            }
        } else {
            debug!(target: "auriya::fas", "FAS decision: REDUCE → already {:?}, skip", active_default);
        }
        Ok(true)
    }
}
