use std::time::{Duration, Instant};

/// Coarse system CPU utilisation sampler from /proc/stat.
/// Returns a value in [0.0, 1.0], updated at most once per `interval`.
#[derive(Default)]
pub struct UtilSampler {
    last_sample: Option<Instant>,
    last_total: u64,
    last_idle: u64,
    cached: f64,
}

impl UtilSampler {
    pub fn new() -> Self {
        Self {
            last_sample: None,
            last_total: 0,
            last_idle: 0,
            cached: 0.0,
        }
    }

    pub fn sample(&mut self, interval: Duration) -> Option<f64> {
        let now = Instant::now();
        if let Some(last) = self.last_sample
            && now.duration_since(last) < interval
        {
            return Some(self.cached);
        }

        let stat = std::fs::read_to_string("/proc/stat").ok()?;
        let line = stat.lines().next()?;
        let mut fields = line.split_whitespace();
        if fields.next()? != "cpu" {
            return None;
        }
        let mut vals = [0u64; 8];
        for slot in &mut vals {
            *slot = fields.next().and_then(|s| s.parse().ok()).unwrap_or(0);
        }
        let idle = vals[3] + vals[4];
        let total: u64 = vals.iter().sum();

        let util = match self.last_sample {
            Some(_) => {
                let dt = total.saturating_sub(self.last_total);
                let di = idle.saturating_sub(self.last_idle);
                if dt == 0 {
                    self.cached
                } else {
                    1.0 - (di as f64 / dt as f64)
                }
            }
            None => 0.0,
        };

        self.last_sample = Some(now);
        self.last_total = total;
        self.last_idle = idle;
        self.cached = util.clamp(0.0, 1.0);
        Some(self.cached)
    }
}
