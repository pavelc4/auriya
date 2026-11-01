use serde::{Deserialize, Serialize};
use std::time::Duration;

/// Standard display panel VSync targets on mobile platforms.
pub const STANDARD_REFRESH_RATES: [f64; 6] = [30.0, 45.0, 60.0, 90.0, 120.0, 144.0];

/// A frame slower than `target_interval * JANK_FACTOR` counts as jank.
const JANK_FACTOR: f64 = 1.5;

#[inline(always)]
fn round1(x: f64) -> f64 {
    (x * 10.0).round() / 10.0
}

#[inline(always)]
fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct FpsStats {
    /// Mean FPS over the window (`n / Σ frametime`, harmonic mean).
    pub avg: f64,
    /// Fastest single frame (`1 / min frametime`), clamped to physical scanout ceiling.
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

impl FpsStats {
    #[inline]
    pub const fn empty() -> Self {
        Self {
            avg: 0.0,
            peak: 0.0,
            low_1pct: 0.0,
            low_01pct: 0.0,
            std_dev_ms: 0.0,
            jank: 0,
            frames: 0,
        }
    }
}

/// Zero-allocation, in-place FPS and frame-pacing statistical computation.
///
/// Modifies the provided `ft` (frametimes in seconds) slice in-place to sort for empirical CDF calculations.
/// Computes harmonic mean, P1 / P0.1 CDF percentiles, population standard deviation,
/// and clamps peak FPS to the physical display scanout ceiling.
pub fn fps_stats_compute_in_place(
    ft: &mut [f64],
    target_fps: u32,
    display_hz: Option<f64>,
) -> FpsStats {
    let frames = ft.len();
    if frames == 0 {
        return FpsStats::empty();
    }

    let sum: f64 = ft.iter().copied().sum();
    if sum <= 0.0 {
        return FpsStats::empty();
    }

    // Harmonic mean: n / Σ ft_i (strictly avoids arithmetic mean distortion on rates)
    let raw_avg = frames as f64 / sum;

    // Minimum frametime / fastest single frame
    let min_ft = ft.iter().copied().fold(f64::INFINITY, f64::min);
    let raw_peak = if min_ft > 0.0 { 1.0 / min_ft } else { 0.0 };

    // Population standard deviation (sigma) of frametimes in milliseconds
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

    // Resolve target cadence: user target, display mode, or standard panel rate
    let candidate_target = match target_fps {
        fps if fps > 0 => Some(f64::from(fps)),
        _ => match display_hz {
            Some(hz) if hz > 0.0 => Some(hz),
            _ => STANDARD_REFRESH_RATES
                .iter()
                .copied()
                .find(|&t| (raw_avg - t).abs() <= 1.25),
        },
    };

    // VSync target cadence snapping:
    // Snaps if within 0.55 FPS, or if within 1.25 FPS with low pacing variance (steady VSync lock
    // hovering near target e.g. 119.3 FPS on 120Hz display due to window edge truncation).
    let avg = match candidate_target {
        Some(target) if (raw_avg - target).abs() <= 0.55 => target,
        Some(target) if (raw_avg - target).abs() <= 1.25 && std_dev_ms <= 1.8 => target,
        _ => raw_avg,
    };

    // Physical scanout ceiling:
    // A display panel physically cannot scan out frames faster than its hardware refresh rate.
    // Clamping peak prevents queue buffer arrival jitter (e.g. 123 FPS on 120Hz panel).
    // Invariant: peak >= avg is strictly enforced.
    let physical_ceiling = match (candidate_target, display_hz) {
        (Some(target), _) if target >= raw_avg - 2.0 => target,
        (_, Some(hz)) if hz >= raw_avg - 2.0 => hz,
        _ => STANDARD_REFRESH_RATES
            .iter()
            .copied()
            .find(|&t| raw_avg <= t + 2.0)
            .unwrap_or(144.0),
    };

    let peak = raw_peak.min(physical_ceiling).max(avg);

    // In-place sort descendingly (ft[0] is slowest frame / lowest FPS)
    ft.sort_unstable_by(|a, b| b.partial_cmp(a).unwrap_or(std::cmp::Ordering::Equal));

    let target_interval = match candidate_target {
        Some(target) if target > 0.0 => 1.0 / target,
        _ => 1.0 / 60.0,
    };
    let jank = ft
        .iter()
        .filter(|&&s| s > target_interval * JANK_FACTOR)
        .count() as u32;

    // True 1% Low (P1 via empirical CDF): index of the 1% boundary of slowest frames
    let idx_1pct = ((frames as f64 * 0.01).ceil() as usize)
        .saturating_sub(1)
        .min(frames - 1);
    let mut low_1pct = if ft[idx_1pct] > 0.0 {
        (1.0 / ft[idx_1pct]).min(avg)
    } else {
        0.0
    };

    // True 0.1% Low (P0.1 via empirical CDF): index of the 0.1% boundary of slowest frames
    let idx_01pct = ((frames as f64 * 0.001).ceil() as usize)
        .saturating_sub(1)
        .min(frames - 1);
    let mut low_01pct = if ft[idx_01pct] > 0.0 {
        (1.0 / ft[idx_01pct]).min(low_1pct)
    } else {
        0.0
    };

    // Pristine pacing snap:
    // If no jank frames occurred, variance is pristine (sigma <= 1.0ms), and 1% low is within normal
    // sub-millisecond clock jitter of avg, snap to avg to prevent false flicker on locked gameplay.
    if jank == 0 && std_dev_ms <= 1.0 && (avg - low_1pct).abs() <= 3.5 {
        low_1pct = avg;
        low_01pct = avg;
    }

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

/// Compute windowed FPS stats from raw frametimes with explicit display refresh rate ceiling.
pub fn fps_stats_from_frametimes_with_display(
    frametimes: &[Duration],
    target_fps: u32,
    display_hz: Option<f64>,
) -> FpsStats {
    let mut ft: Vec<f64> = frametimes
        .iter()
        .map(Duration::as_secs_f64)
        .filter(|&s| s > 0.0)
        .collect();
    fps_stats_compute_in_place(&mut ft, target_fps, display_hz)
}

/// Compute windowed FPS stats from raw frametimes.
pub fn fps_stats_from_frametimes(frametimes: &[Duration], target_fps: u32) -> FpsStats {
    fps_stats_from_frametimes_with_display(frametimes, target_fps, None)
}
