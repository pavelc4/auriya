use std::fs;
use std::path::Path;

use tracing::debug;

const GPU_PATHS: &[(&str, &str, &str, &str)] = &[
    (
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/available_frequencies",
        "kgsl",
    ),
    (
        "/sys/devices/platform/*gpu*/cur_freq",
        "",
        "/sys/devices/platform/*gpu*/available_frequencies",
        "devfreq",
    ),
    (
        "/sys/kernel/gpu/gpu_clock",
        "/sys/kernel/gpu/gpu_busy",
        "",
        "mali",
    ),
    ("/sys/kernel/ged/gpu/gpu_cur_freq", "", "", "mtk"),
];

#[derive(Debug, Clone)]
pub struct GpuSnapshot {
    pub vendor: Option<String>,
    pub cur_freq_mhz: Option<u64>,
    pub max_freq_mhz: Option<u64>,
    pub min_freq_mhz: Option<u64>,
    pub load_pct: Option<u32>,
    pub available_freqs: Vec<u64>,
}

#[derive(Default)]
pub struct GpuCollector {
    pub(crate) vendor: Option<String>,
    freq_path: Option<String>,
    load_path: Option<String>,
    max_freq_path: Option<String>,
    min_freq_path: Option<String>,
}

impl GpuCollector {
    pub fn new() -> Self {
        let mut c = Self::default();
        c.detect_paths();
        c
    }

    pub fn has_vendor(&self) -> bool {
        self.vendor.is_some()
    }

    fn detect_paths(&mut self) {
        for (freq_p, load_p, _avail_p, vendor) in GPU_PATHS {
            let expanded = expand_glob(freq_p);
            if Path::new(&expanded).exists() {
                self.freq_path = Some(expanded.clone());
                self.vendor = Some(vendor.to_string());
                let load_path = if load_p.is_empty() {
                    expanded
                        .replace("gpuclk", "gpu_busy_percentage")
                        .replace("gpu_clock", "gpu_busy")
                } else {
                    expand_glob(load_p)
                };
                if Path::new(&load_path).exists() {
                    self.load_path = Some(load_path.clone());
                }
                let max_path = "/sys/class/kgsl/kgsl-3d0/max_gpuclk".to_string();
                if Path::new(&max_path).exists() {
                    self.max_freq_path = Some(max_path);
                }
                let min_path = "/sys/class/kgsl/kgsl-3d0/min_gpuclk".to_string();
                if Path::new(&min_path).exists() {
                    self.min_freq_path = Some(min_path);
                }
                debug!(target: "auriya::telemetry", "GPU vendor: {} freq={}, load={}", vendor, expanded, load_path);
                return;
            }
        }
        debug!(target: "auriya::telemetry", "No GPU sysfs found");
    }

    pub fn snapshot(&self) -> GpuSnapshot {
        let cur_freq_mhz = self
            .freq_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| s.split_whitespace().next()?.parse::<u64>().ok())
            .map(|hz| {
                if hz > 1_000_000 {
                    hz / 1_000_000
                } else if hz > 1_000 {
                    hz / 1_000
                } else {
                    hz
                }
            });

        let max_freq_mhz = self
            .max_freq_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| s.trim().parse::<u64>().ok())
            .map(|hz| hz / 1_000_000);

        let min_freq_mhz = self
            .min_freq_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| s.trim().parse::<u64>().ok())
            .map(|hz| hz / 1_000_000);

        let load_pct = self
            .load_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| s.trim().parse::<u32>().ok());

        GpuSnapshot {
            vendor: self.vendor.clone(),
            cur_freq_mhz,
            max_freq_mhz,
            min_freq_mhz,
            load_pct,
            available_freqs: Vec::new(),
        }
    }
}

fn expand_glob(pattern: &str) -> String {
    if pattern.contains('*') {
        let parent = Path::new(pattern).parent().unwrap_or(Path::new("/"));
        let prefix = pattern
            .trim_end_matches('*')
            .chars()
            .rev()
            .skip_while(|&c| c != '/')
            .collect::<String>()
            .chars()
            .rev()
            .collect::<String>();
        if let Ok(entries) = fs::read_dir(parent) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with(&prefix) {
                    return entry.path().to_string_lossy().to_string();
                }
            }
        }
    }
    pattern.to_string()
}
