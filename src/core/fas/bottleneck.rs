use std::time::Duration;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BottleneckType {
    Gpu,
    Cpu,
    Balanced,
    Unknown,
}

pub struct BottleneckDetector {
    cv_threshold: f64,
    debounce_frames: u32,
    current: BottleneckType,
    counter: u32,
}

impl BottleneckDetector {
    pub fn new(cv_threshold: f64, debounce_frames: u32) -> Self {
        Self {
            cv_threshold,
            debounce_frames,
            current: BottleneckType::Unknown,
            counter: 0,
        }
    }

    pub fn classify(
        &mut self,
        frametimes: &[Duration],
        target_fps: u32,
    ) -> BottleneckType {
        let n = frametimes.len();
        if n < 3 {
            return BottleneckType::Unknown;
        }

        let target_interval = Duration::from_secs_f64(1.0 / f64::from(target_fps));

        let mean_ns: f64 =
            frametimes.iter().map(|ft| ft.as_nanos() as f64).sum::<f64>() / n as f64;

        if mean_ns <= target_interval.as_nanos() as f64 {
            return self.debounce(BottleneckType::Balanced);
        }

        let variance = frametimes
            .iter()
            .map(|ft| {
                let diff = ft.as_nanos() as f64 - mean_ns;
                diff * diff
            })
            .sum::<f64>()
            / n as f64;

        let cv = variance.sqrt() / mean_ns;

        let raw = if cv < self.cv_threshold {
            BottleneckType::Gpu
        } else {
            BottleneckType::Cpu
        };

        self.debounce(raw)
    }

    fn debounce(&mut self, raw: BottleneckType) -> BottleneckType {
        if self.current == BottleneckType::Unknown {
            self.current = raw;
            self.counter = 0;
            return self.current;
        }

        if raw == self.current {
            self.counter = 0;
            return self.current;
        }

        self.counter += 1;
        if self.counter >= self.debounce_frames {
            self.current = raw;
            self.counter = 0;
        }

        self.current
    }

    pub fn reset(&mut self) {
        self.current = BottleneckType::Unknown;
        self.counter = 0;
    }
}

#[cfg(test)]
#[allow(clippy::manual_repeat_n)]
mod tests {
    use super::*;

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
}
