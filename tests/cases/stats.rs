use auriya::core::stats::fps_stats_from_frametimes;
use std::time::Duration;

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
