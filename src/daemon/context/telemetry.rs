use super::DaemonContext;
use crate::core::stats::StatsSnapshot;
use crate::daemon::model::state::CurrentState;
use std::sync::atomic::Ordering;

impl DaemonContext {
    /// Format foreground PID response.
    pub fn get_pid(&self) -> String {
        match self.current_state.read() {
            Ok(ref st) => match (&st.pkg, st.pid) {
                (Some(p), Some(id)) => format!("PKG={p} PID={id}\n"),
                (Some(p), None) => format!("PKG={p} PID=None\n"),
                _ => "PKG=None PID=None\n".into(),
            },
            Err(_) => "PKG=None PID=None\n".into(),
        }
    }

    /// Format daemon status report with telemetry.
    pub fn status(&self) -> String {
        let enabled = self.enabled.load(Ordering::Acquire);
        let n = match self.shared_config.read() {
            Ok(ref c) => c.game.len(),
            Err(_) => 0,
        };
        let ov = match self.override_foreground.read() {
            Ok(ref o) => o.as_deref().map(str::to_string),
            Err(_) => None,
        };
        let log_level = match self.current_log_level.read() {
            Ok(ref l) => format!("{l:?}"),
            Err(_) => "Unknown".to_string(),
        };

        let binder = if crate::platform::binder::BinderClient::is_healthy() {
            "OPERATIONAL"
        } else {
            "UNAVAILABLE"
        };

        let telemetry = match self.current_state.read() {
            Ok(ref st) => format_telemetry(st),
            Err(_) => String::new(),
        };

        format!(
            "ENABLED={enabled} BINDER={binder} PACKAGES={n} OVERRIDE={ov:?} LOG_LEVEL={log_level}\n{telemetry}"
        )
    }

    /// Build full stats snapshot serialized as JSON.
    pub async fn build_stats_json(&self) -> String {
        let is_monitored = self
            .current_state
            .read()
            .ok()
            .map(|s| s.pkg.is_some())
            .unwrap_or(false);
        let ebpf_fps = match is_monitored {
            true => self.get_fps_stats().await.filter(|f| f.frames > 0),
            false => None,
        };
        let bat = crate::core::telemetry::battery::snapshot();
        let uptime_secs = self.start_time.elapsed().as_secs();

        let display_hz = crate::platform::binder::get_active_display_refresh_rate();
        let snap = {
            let st_guard = self.current_state.read().ok();
            let st = st_guard.as_deref();
            let fps = match (ebpf_fps, is_monitored) {
                (Some(mut f), _) => {
                    if display_hz > 0.0 {
                        f.avg = f.avg.min(display_hz);
                        f.peak = f.peak.min(display_hz).max(f.avg);
                        f.low_1pct = f.low_1pct.min(f.avg);
                        f.low_01pct = f.low_01pct.min(f.low_1pct);
                    }
                    Some(f)
                }
                (None, true) => {
                    let fallback_val = st.and_then(|s| s.fps).or_else(|| {
                        crate::core::fps::SysfsFpsReader::new()
                            .read()
                            .map(|r| r.fps)
                    });
                    match fallback_val {
                        Some(val) => {
                            let clamped = match display_hz {
                                hz if hz > 0.0 => val.min(hz),
                                _ => val,
                            };
                            Some(crate::core::stats::FpsStats {
                                avg: clamped,
                                low_1pct: clamped,
                                low_01pct: clamped,
                                peak: clamped,
                                std_dev_ms: 0.0,
                                jank: 0,
                                frames: 1,
                            })
                        }
                        None => None,
                    }
                }
                (None, false) => None,
            };

            StatsSnapshot::build(
                fps,
                st.and_then(|s| s.cpu_telemetry.as_ref()),
                st.and_then(|s| s.gpu_telemetry.as_ref()),
                st.and_then(|s| s.thermal_telemetry.as_ref()),
                bat,
                st.and_then(|s| s.pkg.as_deref()),
                st.map(|s| s.profile).unwrap_or_default(),
                is_monitored,
                st.map(|s| s.game_session).unwrap_or(false),
                uptime_secs,
            )
        };

        let response = crate::core::stats::ApiResponse::ok(snap);
        serde_json::to_string(&response)
            .map(|json| format!("{json}\n"))
            .unwrap_or_else(|e| format!("ERR JSON {e:?}\n"))
    }
}

pub fn format_telemetry(st: &CurrentState) -> String {
    let mut out = String::new();
    if let Some(fps) = st.fps {
        let display_hz = crate::platform::binder::get_active_display_refresh_rate();
        let clamped = match display_hz {
            hz if hz > 0.0 && (fps - hz).abs() <= 0.75 => hz,
            hz if hz > 0.0 => fps.min(hz),
            _ => fps,
        };
        let source = match st.fps_source {
            Some(crate::core::fps::FpsSource::Ebpf) => "ebpf",
            Some(crate::core::fps::FpsSource::Sysfs) => "sysfs",
            None => "?",
        };
        out.push_str(&format!("FPS={clamped:.1} SOURCE={source}\n"));
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
