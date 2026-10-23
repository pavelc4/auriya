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
