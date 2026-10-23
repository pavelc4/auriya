use super::DaemonContext;
use crate::core::stats::StatsSnapshot;
use crate::daemon::model::state::CurrentState;
use std::sync::atomic::Ordering;

impl DaemonContext {
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

pub fn format_telemetry(st: &CurrentState) -> String {
    let mut out = String::new();
    if let Some(fps) = st.fps {
        let source = match st.fps_source {
            Some(crate::core::fps::FpsSource::Ebpf) => "ebpf",
            Some(crate::core::fps::FpsSource::Sysfs) => "sysfs",
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
