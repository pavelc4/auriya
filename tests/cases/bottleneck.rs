#![allow(clippy::manual_repeat_n)]

use auriya::core::fas::bottleneck::{BottleneckDetector, BottleneckType};
use std::time::Duration;

fn ft_ns(ns: u64) -> Duration {
    Duration::from_nanos(ns)
}

#[test]
fn consistent_slow_frames_detected_as_gpu() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let target = 60;
    let interval_ns = 1_000_000_000 / 60;

    let frametimes: Vec<_> = std::iter::repeat(ft_ns(interval_ns + 5_000_000))
        .take(30)
        .collect();

    let bt = det.classify(&frametimes, target);
    assert_eq!(bt, BottleneckType::Gpu);
}

#[test]
fn spiky_frametimes_detected_as_cpu() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let target = 60;
    let interval_ns = 1_000_000_000 / 60;

    let mut frametimes = Vec::with_capacity(30);
    for i in 0..30 {
        let jitter = if i % 3 == 0 { 30_000_000 } else { 1_000_000 };
        frametimes.push(ft_ns(interval_ns + jitter));
    }

    let bt = det.classify(&frametimes, target);
    assert_eq!(bt, BottleneckType::Cpu);
}

#[test]
fn fast_frames_are_balanced() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let target = 60;
    let interval_ns = 1_000_000_000 / 60;

    let frametimes: Vec<_> = std::iter::repeat(ft_ns(interval_ns - 2_000_000))
        .take(30)
        .collect();

    let bt = det.classify(&frametimes, target);
    assert_eq!(bt, BottleneckType::Balanced);
}

#[test]
fn debounce_prevents_premature_switch() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let target = 60;
    let interval_ns = 1_000_000_000 / 60;

    let fast: Vec<_> = std::iter::repeat(ft_ns(interval_ns - 2_000_000))
        .take(30)
        .collect();
    assert_eq!(
        det.classify(&fast, target),
        BottleneckType::Balanced,
        "initial classification goes straight through",
    );

    let slow: Vec<_> = std::iter::repeat(ft_ns(interval_ns + 10_000_000))
        .take(30)
        .collect();
    assert_eq!(
        det.classify(&slow, target),
        BottleneckType::Balanced,
        "first slow call still debouncing",
    );
    assert_eq!(
        det.classify(&slow, target),
        BottleneckType::Balanced,
        "second slow call still debouncing",
    );
    assert_eq!(
        det.classify(&slow, target),
        BottleneckType::Gpu,
        "third slow call passes debounce threshold",
    );
}

#[test]
fn too_few_frames_returns_unknown() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let frametimes = vec![ft_ns(16_000_000), ft_ns(17_000_000)];
    assert_eq!(det.classify(&frametimes, 60), BottleneckType::Unknown);
}

#[test]
fn reset_clears_state() {
    let mut det = BottleneckDetector::new(0.15, 3);
    let target = 60;
    let interval_ns = 1_000_000_000 / 60;

    let slow: Vec<_> = std::iter::repeat(ft_ns(interval_ns + 10_000_000))
        .take(30)
        .collect();
    det.classify(&slow, target);
    det.classify(&slow, target);
    det.classify(&slow, target);
    assert_eq!(det.current, BottleneckType::Gpu);

    det.reset();
    assert_eq!(det.current, BottleneckType::Unknown);
    assert_eq!(det.counter, 0);

    let fast: Vec<_> = std::iter::repeat(ft_ns(interval_ns - 2_000_000))
        .take(30)
        .collect();
    assert_eq!(
        det.classify(&fast, target),
        BottleneckType::Balanced,
        "after reset, first classify goes through immediately",
    );
}
