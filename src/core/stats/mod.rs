// Live performance-stats DTOs + FPS-stat math for the IPC `GET_STATS` API.
//
// Design (see plan): stats are computed *on request*, not accumulated per tick.
// The FPS numbers derive from the per-frame deque already held by the FAS
// `FrameBuffer` (~5 s window); telemetry/battery are point snapshots. When no
// client polls, this code never runs.
//
// The DTOs are grouped by *concept*, one group per UI card, and are the stable
// JSON contract the Kotlin UI parses. Battery *temperature* lives in the thermal
// card (it is a temperature); battery charge/power lives in the battery card.
//
// This module is in `core` and must not depend on `daemon` — the builder takes
// core snapshot types, and the IPC handler feeds them in from `CurrentState`.

use crate::core::profile::ProfileMode;
use crate::core::telemetry::battery::BatterySnapshot;
use crate::core::telemetry::cpu::{ClusterType, CpuSnapshot};
use crate::core::telemetry::gpu::GpuSnapshot;
use crate::core::telemetry::thermal::ThermalSnapshot;
use serde::Serialize;
use std::time::Duration;

/// A frame slower than `target_interval * JANK_FACTOR` counts as jank.
const JANK_FACTOR: f64 = 1.5;

fn round1(x: f64) -> f64 {
    (x * 10.0).round() / 10.0
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct FpsStats {
    /// Mean FPS over the window (`n / Σ frametime`).
    pub avg: f64,
    /// Fastest single frame (`1 / min frametime`).
    pub peak: f64,
    /// FPS of the worst 1% of presented time — the gamer-standard stutter metric.
    pub low_1pct: f64,
    /// Frames slower than `target * JANK_FACTOR`.
    pub jank: u32,
    /// Sample count the stats were computed from.
    pub frames: usize,
}

/// Compute windowed FPS stats from raw frametimes. Pure — the runnable check.
///
/// Zero/degenerate durations are filtered; an empty input yields all-zero stats
/// (the caller treats "no frames" as `fps: null` upstream, but this stays total).
pub fn fps_stats_from_frametimes(frametimes: &[Duration], target_fps: u32) -> FpsStats {
    let mut ft: Vec<f64> = frametimes
        .iter()
        .map(Duration::as_secs_f64)
        .filter(|&s| s > 0.0)
        .collect();
    let frames = ft.len();
    if frames == 0 {
        return FpsStats {
            avg: 0.0,
            peak: 0.0,
            low_1pct: 0.0,
            jank: 0,
            frames: 0,
        };
    }

    let sum: f64 = ft.iter().sum();
    let avg = frames as f64 / sum; // 1 / mean_frametime

    let min_ft = ft.iter().copied().fold(f64::INFINITY, f64::min);
    let peak = if min_ft > 0.0 { 1.0 / min_ft } else { 0.0 };

    // Time-weighted 1% low: the smallest set of the largest deltas whose
    // summed *time* reaches 1% of the window's total presented time. A single
    // long stutter is weighted by how long it was actually on screen, not by
    // its count share, so a clean frame is never dragged into the bucket.
    let total: f64 = ft.iter().sum();
    let threshold = total * 0.01;
    ft.sort_by(|a, b| b.partial_cmp(a).unwrap_or(std::cmp::Ordering::Equal));
    let mut acc = 0.0;
    let mut worst = 0usize;
    for (i, &s) in ft.iter().enumerate() {
        acc += s;
        worst = i + 1;
        if acc >= threshold {
            break;
        }
    }
    let low_1pct = match acc {
        a if a > 0.0 => worst as f64 / a,
        _ => 0.0,
    };

    let target_interval = if target_fps > 0 {
        1.0 / f64::from(target_fps)
    } else {
        1.0 / 60.0
    };
    let jank = ft
        .iter()
        .filter(|&&s| s > target_interval * JANK_FACTOR)
        .count() as u32;

    FpsStats {
        avg: round1(avg),
        peak: round1(peak),
        low_1pct: round1(low_1pct),
        jank,
        frames,
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct ThermalCard {
    pub cpu_c: Option<f32>,
    pub gpu_c: Option<f32>,
    pub battery_c: Option<f32>,
}

#[derive(Debug, Clone, Serialize)]
pub struct BatteryCard {
    pub pct: Option<u8>,
    pub current_ma: Option<i32>,
    pub voltage_v: Option<f32>,
    pub status: Option<String>,
    pub health: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct CoreDto {
    pub id: usize,
    pub khz: u64,
    pub gov: String,
    pub cluster: String,
    pub online: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct CpuCard {
    pub load_pct: f32,
    pub cores: Vec<CoreDto>,
}

#[derive(Debug, Clone, Serialize)]
pub struct GpuCard {
    pub mhz: Option<u64>,
    pub load_pct: Option<u32>,
    pub vendor: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SessionCard {
    pub pkg: Option<String>,
    pub profile: String,
    pub active: bool,
}

#[derive(Debug, Clone, Serialize)]
pub struct StatsSnapshot {
    pub fps: Option<FpsStats>,
    pub thermal: ThermalCard,
    pub battery: BatteryCard,
    pub cpu: Option<CpuCard>,
    pub gpu: Option<GpuCard>,
    pub session: SessionCard,
}

fn cluster_str(c: &ClusterType) -> &'static str {
    match c {
        ClusterType::Little => "Little",
        ClusterType::Big => "Big",
        ClusterType::Prime => "Prime",
    }
}

impl StatsSnapshot {
    /// Assemble the response from the pieces the daemon already has in
    /// `CurrentState` plus a fresh battery read. All inputs are `core` types so
    /// this module stays free of any `daemon` dependency.
    #[allow(clippy::too_many_arguments)]
    pub fn build(
        fps: Option<FpsStats>,
        cpu: Option<&CpuSnapshot>,
        gpu: Option<&GpuSnapshot>,
        thermal: Option<&ThermalSnapshot>,
        battery: &BatterySnapshot,
        pkg: Option<&str>,
        profile: ProfileMode,
        active: bool,
    ) -> Self {
        StatsSnapshot {
            fps,
            thermal: ThermalCard {
                cpu_c: thermal.and_then(|t| t.cpu_temp_c),
                gpu_c: thermal.and_then(|t| t.gpu_temp_c),
                battery_c: battery.temp_c,
            },
            battery: BatteryCard {
                pct: battery.pct,
                current_ma: battery.current_ma,
                voltage_v: battery.voltage_v,
                status: battery.status.clone(),
                health: battery.health.clone(),
            },
            cpu: cpu.map(|c| CpuCard {
                load_pct: c.load_pct,
                cores: c
                    .cores
                    .iter()
                    .map(|core| CoreDto {
                        id: core.core_id,
                        khz: core.cur_freq_khz,
                        gov: core.governor.clone(),
                        cluster: cluster_str(&core.cluster).to_string(),
                        online: core.online,
                    })
                    .collect(),
            }),
            gpu: gpu.map(|g| GpuCard {
                mhz: g.cur_freq_mhz,
                load_pct: g.load_pct,
                vendor: g.vendor.clone(),
            }),
            session: SessionCard {
                pkg: pkg.map(str::to_string),
                profile: profile.to_string().to_lowercase(),
                active,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ms(m: f64) -> Duration {
        Duration::from_secs_f64(m / 1000.0)
    }

    #[test]
    fn steady_60fps_with_spikes() {
        // 297 clean 16.6ms frames (~60fps) + 3 × 40ms stalls.
        let mut ft: Vec<Duration> = std::iter::repeat_n(ms(16.6), 297).collect();
        ft.extend(std::iter::repeat_n(ms(40.0), 3));

        let s = fps_stats_from_frametimes(&ft, 60);

        assert_eq!(s.frames, 300);
        assert!((s.avg - 60.0).abs() < 2.0, "avg ~60, got {}", s.avg);
        assert!(s.peak >= 60.0, "peak >= 60, got {}", s.peak);
        // 1% low is dragged down by the 40ms stalls (~25fps), well below avg.
        assert!(
            s.low_1pct < 40.0,
            "low_1pct should reflect stalls, got {}",
            s.low_1pct
        );
        // 40ms > 16.6ms*1.5 (=25ms) → the 3 stalls are jank.
        assert_eq!(s.jank, 3, "exactly the 3 stalls are jank");
    }

    #[test]
    fn empty_is_all_zero_not_panic() {
        let s = fps_stats_from_frametimes(&[], 60);
        assert_eq!(s.frames, 0);
        assert_eq!(s.avg, 0.0);
    }

    #[test]
    fn zero_durations_filtered() {
        let ft = vec![ms(16.6), Duration::ZERO, ms(16.6)];
        let s = fps_stats_from_frametimes(&ft, 60);
        assert_eq!(s.frames, 2);
    }
}
