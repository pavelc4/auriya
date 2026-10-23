use crate::core::config::gamelist::GameList;
use crate::core::profile::{self, ProfileMode};
use crate::core::scaling::ScalingAction;
use crate::daemon::engine::Daemon;
use anyhow::Result;
use std::sync::Arc;
use tracing::{debug, error, warn};

impl Daemon {
    pub(crate) async fn run_fas_pipeline(&mut self, pkg: &str, gamelist: &GameList) -> Result<()> {
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
}
