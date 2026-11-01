use serde::{Deserialize, Serialize};
use std::time::Duration;

/// A frame slower than `target_interval * JANK_FACTOR` counts as jank.
const JANK_FACTOR: f64 = 1.5;

fn round1(x: f64) -> f64 {
    (x * 10.0).round() / 10.0
}

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct FpsStats {
    /// Mean FPS over the window (`n / Σ frametime`).
    pub avg: f64,
    /// Fastest single frame (`1 / min frametime`), clamped to display refresh rate / target.
    pub peak: f64,
    /// FPS of the worst 1% of presented frames (P1 via empirical CDF).
    pub low_1pct: f64,
    /// FPS of the worst 0.1% of presented frames (P0.1 via empirical CDF) for micro-stutter tracking.
    #[serde(default)]
    pub low_01pct: f64,
    /// Population standard deviation of frametimes in milliseconds (sigma).
    #[serde(default)]
    pub std_dev_ms: f64,
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
            low_01pct: 0.0,
            std_dev_ms: 0.0,
            jank: 0,
            frames: 0,
        };
    }

    let sum: f64 = ft.iter().sum();
    let raw_avg = frames as f64 / sum; // Harmonic mean: n / Σ ft_i

    // Standard panel VSync targets on mobile displays
    let standard_targets = [30.0, 45.0, 60.0, 90.0, 120.0, 144.0];
    let candidate = if target_fps > 0 {
        Some(f64::from(target_fps))
    } else {
        standard_targets
            .iter()
            .copied()
            .find(|&t| (raw_avg - t).abs() <= 0.55)
    };
    let avg = match candidate {
        Some(target) if (raw_avg - target).abs() <= 0.55 => target,
        _ => raw_avg,
    };

    let min_ft = ft.iter().copied().fold(f64::INFINITY, f64::min);
    let raw_peak = if min_ft > 0.0 { 1.0 / min_ft } else { 0.0 };

    // Clamp peak to physical scanout ceiling (target refresh rate) to prevent compositor arrival artifacts (e.g. 123 FPS on a 120Hz display)
    let peak = match candidate {
        Some(target) if raw_peak > target && (raw_avg - target).abs() <= 5.0 => target,
        _ if target_fps > 0 => raw_peak.min(f64::from(target_fps)),
        _ => raw_peak,
    };

    // Sort frametimes descendingly (slowest frames first: ft[0] is slowest frame / lowest FPS)
    ft.sort_by(|a, b| b.partial_cmp(a).unwrap_or(std::cmp::Ordering::Equal));

    // True 1% Low (P1 via empirical CDF): index of the 1% boundary of slowest frames
    let idx_1pct = ((frames as f64 * 0.01).ceil() as usize)
        .saturating_sub(1)
        .min(frames - 1);
    let low_1pct = if ft[idx_1pct] > 0.0 {
        (1.0 / ft[idx_1pct]).min(avg)
    } else {
        0.0
    };

    // True 0.1% Low (P0.1 via empirical CDF): index of the 0.1% boundary of slowest frames
    let idx_01pct = ((frames as f64 * 0.001).ceil() as usize)
        .saturating_sub(1)
        .min(frames - 1);
    let low_01pct = if ft[idx_01pct] > 0.0 {
        (1.0 / ft[idx_01pct]).min(low_1pct)
    } else {
        0.0
    };

    // Frame Time Stability: Population standard deviation (sigma) of frametimes in milliseconds
    let mean_ft = sum / frames as f64;
    let variance: f64 = ft
        .iter()
        .map(|&s| {
            let diff = s - mean_ft;
            diff * diff
        })
        .sum::<f64>()
        / frames as f64;
    let std_dev_ms = variance.sqrt() * 1000.0;

    let target_interval = match (target_fps, candidate) {
        (fps, _) if fps > 0 => 1.0 / f64::from(fps),
        (_, Some(target)) => 1.0 / target,
        _ => 1.0 / 60.0,
    };
    let jank = ft
        .iter()
        .filter(|&&s| s > target_interval * JANK_FACTOR)
        .count() as u32;

    FpsStats {
        avg: round1(avg),
        peak: round1(peak),
        low_1pct: round1(low_1pct),
        low_01pct: round1(low_01pct),
        std_dev_ms: round2(std_dev_ms),
        jank,
        frames,
    }
}
