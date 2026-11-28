use std::fs;
use std::path::{Path, PathBuf};

use tracing::debug;

const DEVFREQ_LOAD_CANDIDATES: &[&str] = &[
    "interface/current_utilization",
    "gpu_busy",
    "load",
    "utilization",
];

#[derive(Debug, Clone)]
pub struct GpuSnapshot {
    pub vendor: Option<&'static str>,
    pub cur_freq_mhz: Option<u64>,
    pub load_pct: Option<u32>,
}

#[derive(Default)]
pub struct GpuCollector {
    pub(crate) vendor: Option<&'static str>,
    freq_path: Option<String>,
    load_path: Option<String>,
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
        self.detect_paths_in(
            Path::new("/sys/class/kgsl/kgsl-3d0"),
            Path::new("/sys/class/devfreq"),
            Path::new("/sys/devices/platform"),
            Path::new("/sys/kernel/gpu/gpu_clock"),
            Path::new("/sys/kernel/ged/gpu/gpu_cur_freq"),
        );
    }

    /// Discover GPU frequency and utilization sysfs paths under the given roots.
    pub fn detect_paths_in(
        &mut self,
        kgsl_dir: &Path,
        devfreq_dir: &Path,
        platform_dir: &Path,
        mali_kernel_freq: &Path,
        mtk_ged_freq: &Path,
    ) {
        let kgsl_freq = kgsl_dir.join("gpuclk");
        if kgsl_freq.exists() {
            let load = kgsl_dir.join("gpu_busy_percentage");
            self.set_detected("kgsl", kgsl_freq, load.exists().then_some(load));
            return;
        }

        if let Some((vendor, freq, load)) = detect_devfreq_gpu(devfreq_dir, platform_dir) {
            self.set_detected(vendor, freq, load);
            return;
        }

        if mali_kernel_freq.exists() {
            let load = mali_kernel_freq
                .parent()
                .map(|p| p.join("gpu_busy"))
                .filter(|p| p.exists());
            self.set_detected("mali", mali_kernel_freq.to_path_buf(), load);
            return;
        }

        if mtk_ged_freq.exists() {
            self.set_detected("mtk", mtk_ged_freq.to_path_buf(), None);
            return;
        }

        debug!(
            target: "auriya::telemetry",
            "No GPU sysfs found (kgsl={}, devfreq={}, platform={})",
            kgsl_dir.display(),
            devfreq_dir.display(),
            platform_dir.display()
        );
    }

    fn set_detected(&mut self, vendor: &'static str, freq: PathBuf, load: Option<PathBuf>) {
        self.load_path = load.map(|p| p.to_string_lossy().into_owned());
        self.freq_path = Some(freq.to_string_lossy().into_owned());
        self.vendor = Some(vendor);
        debug!(
            target: "auriya::telemetry",
            "GPU vendor: {} freq={:?}, load={:?}",
            vendor, self.freq_path, self.load_path
        );
    }

    pub fn snapshot(&self) -> GpuSnapshot {
        let cur_freq_mhz = self
            .freq_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| s.split_whitespace().next()?.parse::<u64>().ok())
            .map(normalize_freq_to_mhz);

        let load_pct = self
            .load_path
            .as_ref()
            .and_then(|p| fs::read_to_string(p).ok())
            .and_then(|s| parse_gpu_load_pct(&s));

        GpuSnapshot {
            vendor: self.vendor,
            cur_freq_mhz,
            load_pct,
        }
    }
}

/// Convert raw GPU clock (Hz, kHz, or MHz) to MHz.
/// Devfreq nodes like Exynos sgpu report kHz up to ~1,306,000, while kgsl reports Hz (>= 10M).
#[inline]
fn normalize_freq_to_mhz(raw: u64) -> u64 {
    match raw {
        h if h >= 10_000_000 => h / 1_000_000,
        h if h >= 10_000 => h / 1_000,
        h => h,
    }
}

fn parse_gpu_load_pct(text: &str) -> Option<u32> {
    let parts: Vec<&str> = text.split_whitespace().collect();
    match parts.as_slice() {
        [pct, "%", ..] => pct.parse::<u32>().ok().map(|v| v.min(100)),
        [busy, total, ..] => match (busy.parse::<u64>(), total.parse::<u64>()) {
            (Ok(b), Ok(t)) => (b * 100).checked_div(t).map(|v| v.min(100) as u32),
            _ => busy
                .trim_end_matches('%')
                .parse::<u32>()
                .ok()
                .map(|v| v.min(100)),
        },
        [single] => single
            .trim_end_matches('%')
            .parse::<u32>()
            .ok()
            .map(|v| v.min(100)),
        [] => None,
    }
}

fn is_gpu_devfreq_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    (lower.contains("sgpu") || lower.contains("mali") || lower.contains("gpu"))
        && !lower.contains("kgsl")
}

fn devfreq_vendor_label(name: &str) -> &'static str {
    match name.to_ascii_lowercase().contains("sgpu") {
        true => "sgpu",
        false => "devfreq",
    }
}

fn find_devfreq_load(dir: &Path) -> Option<PathBuf> {
    DEVFREQ_LOAD_CANDIDATES
        .iter()
        .map(|rel| dir.join(rel))
        .find(|p| p.exists())
}

fn probe_devfreq_gpu_dir(
    dir: &Path,
    vendor: &'static str,
) -> Option<(&'static str, PathBuf, Option<PathBuf>)> {
    let freq = dir.join("cur_freq");
    freq.exists()
        .then(|| (vendor, freq, find_devfreq_load(dir)))
}

fn detect_devfreq_gpu(
    devfreq_dir: &Path,
    platform_dir: &Path,
) -> Option<(&'static str, PathBuf, Option<PathBuf>)> {
    if let Ok(entries) = fs::read_dir(devfreq_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if is_gpu_devfreq_name(&name)
                && let Some(found) =
                    probe_devfreq_gpu_dir(&entry.path(), devfreq_vendor_label(&name))
            {
                return Some(found);
            }
        }
    }

    let Ok(entries) = fs::read_dir(platform_dir) else {
        return None;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !is_gpu_devfreq_name(&name) {
            continue;
        }
        let vendor = devfreq_vendor_label(&name);
        let base = entry.path();
        if let Some(found) = probe_devfreq_gpu_dir(&base, vendor) {
            return Some(found);
        }
        if let Ok(df_entries) = fs::read_dir(base.join("devfreq")) {
            for sub in df_entries.flatten() {
                if let Some(found) = probe_devfreq_gpu_dir(&sub.path(), vendor) {
                    return Some(found);
                }
            }
        }
    }

    None
}
