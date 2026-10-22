use std::time::Duration;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BottleneckType {
    Gpu,
    Cpu,
    Balanced,
    Unknown,
}

pub struct BottleneckDetector {
    pub cv_threshold: f64,
    pub debounce_frames: u32,
    pub current: BottleneckType,
    pub counter: u32,
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

    pub fn classify(&mut self, frametimes: &[Duration], target_fps: u32) -> BottleneckType {
        let n = frametimes.len();
        if n < 3 {
            return BottleneckType::Unknown;
        }

        let target_interval = Duration::from_secs_f64(1.0 / f64::from(target_fps));

        let mean_ns: f64 = frametimes
            .iter()
            .map(|ft| ft.as_nanos() as f64)
            .sum::<f64>()
            / n as f64;

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
