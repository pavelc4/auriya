use std::path::PathBuf;
use std::sync::OnceLock;

pub struct CpuPaths {
    pub governors_cpu: Vec<PathBuf>,
    pub governors_policy: Vec<PathBuf>,
    pub online: Vec<PathBuf>,
}

impl CpuPaths {
    fn scan() -> Self {
        let mut governors_cpu = Vec::with_capacity(16);
        let mut governors_policy = Vec::with_capacity(8);
        let mut online = Vec::with_capacity(8);

        for i in 0..16 {
            let path = PathBuf::from(format!(
                "/sys/devices/system/cpu/cpu{}/cpufreq/scaling_governor",
                i
            ));
            if path.exists() {
                governors_cpu.push(path);
            }
        }

        for i in 0..8 {
            let path = PathBuf::from(format!(
                "/sys/devices/system/cpu/cpufreq/policy{}/scaling_governor",
                i
            ));
            if path.exists() {
                governors_policy.push(path);
            }
        }

        for i in 1..16 {
            let path = PathBuf::from(format!("/sys/devices/system/cpu/cpu{}/online", i));
            if path.exists() {
                online.push(path);
            }
        }

        tracing::debug!(
            "Cached sysfs paths: {} cpu governors, {} policy governors, {} online",
            governors_cpu.len(),
            governors_policy.len(),
            online.len()
        );

        Self {
            governors_cpu,
            governors_policy,
            online,
        }
    }
}

static CPU_PATHS: OnceLock<CpuPaths> = OnceLock::new();
static AVAILABLE_GOVERNORS: OnceLock<Vec<String>> = OnceLock::new();

#[inline]
pub fn cpu_paths() -> &'static CpuPaths {
    CPU_PATHS.get_or_init(CpuPaths::scan)
}

#[inline]
pub fn available_governors() -> &'static [String] {
    AVAILABLE_GOVERNORS.get_or_init(|| {
        let candidates = [
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors",
            "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors",
        ];
        for path in candidates {
            if let Ok(content) = std::fs::read_to_string(path) {
                let list: Vec<String> = content
                    .split_whitespace()
                    .filter(|s| !s.is_empty())
                    .map(|s| s.to_string())
                    .collect();
                if !list.is_empty() {
                    return list;
                }
            }
        }
        vec![
            "performance".to_string(),
            "schedutil".to_string(),
            "powersave".to_string(),
        ]
    })
}

#[inline]
pub fn resolve_governor<'a>(requested: &'a str, fallback: &'a str) -> &'a str {
    let available = available_governors();
    let candidate = [requested, fallback]
        .into_iter()
        .find(|&c| available.iter().any(|g| g == c));

    match candidate {
        Some(gov) => gov,
        None => match available.first() {
            Some(first) => first.as_str(),
            None => fallback,
        },
    }
}

pub fn set_governor_cached(governor: &str) {
    let target = resolve_governor(governor, "schedutil");
    let paths = cpu_paths();

    for path in &paths.governors_cpu {
        let _ = std::fs::write(path, target);
    }

    for path in &paths.governors_policy {
        let _ = std::fs::write(path, target);
    }
}

pub fn online_all_cores_cached() {
    let paths = cpu_paths();

    for path in &paths.online {
        let _ = std::fs::write(path, "1");
    }
}
pub struct SnapdragonPaths {
    pub kgsl: Option<PathBuf>,
    pub memlat_settings: Option<PathBuf>,
    pub orig_min_pwrlevel: Option<String>,
    pub orig_max_pwrlevel: Option<String>,
    pub orig_idle_timer: Option<String>,
    pub orig_memlat_sample_ms: Option<String>,
}

impl SnapdragonPaths {
    fn scan() -> Self {
        let kgsl_candidates = [
            "/sys/devices/platform/soc/3d00000.qcom,kgsl-3d0/kgsl/kgsl-3d0",
            "/sys/devices/platform/soc/5000000.qcom,kgsl-3d0/kgsl/kgsl-3d0",
            "/sys/class/kgsl/kgsl-3d0",
        ];

        let kgsl = kgsl_candidates
            .iter()
            .map(PathBuf::from)
            .find(|p| p.exists());

        let memlat_settings = {
            let path = PathBuf::from("/sys/devices/system/cpu/bus_dcvs/memlat_settings");
            if path.exists() { Some(path) } else { None }
        };

        let (orig_min_pwrlevel, orig_max_pwrlevel, orig_idle_timer) =
            if let Some(ref kgsl_path) = kgsl {
                let min_pwr = std::fs::read_to_string(kgsl_path.join("min_pwrlevel"))
                    .ok()
                    .map(|s| s.trim().to_string());
                let max_pwr = std::fs::read_to_string(kgsl_path.join("max_pwrlevel"))
                    .ok()
                    .map(|s| s.trim().to_string());
                let idle_t = std::fs::read_to_string(kgsl_path.join("idle_timer"))
                    .ok()
                    .map(|s| s.trim().to_string());

                // In Qualcomm KGSL, power levels are numbered 0 (highest clock/power) to
                // num_pwrlevels - 1 (lowest clock/power).
                // min_pwrlevel is the minimum performance level (lowest clock allowed),
                // so setting it to num_pwrlevels - 1 unlocks the full dynamic scaling range
                // (e.g. 20 MHz on KonaBess/Adreno or whatever the lowest OPP is).
                let safe_min_pwr = std::fs::read_to_string(kgsl_path.join("num_pwrlevels"))
                    .ok()
                    .and_then(|n| n.trim().parse::<u32>().ok())
                    .filter(|&n| n > 0)
                    .map(|n| (n - 1).to_string())
                    .or_else(|| match min_pwr {
                        Some(s) if s != "0" && !s.is_empty() => Some(s),
                        _ => std::fs::read_to_string(kgsl_path.join("default_pwrlevel"))
                            .ok()
                            .map(|s| s.trim().to_string())
                            .or_else(|| Some("8".to_string())),
                    });

                let safe_idle_timer = match idle_t {
                    Some(s) if s.parse::<u32>().is_ok_and(|t| t > 0 && t <= 500) => Some(s),
                    _ => Some("80".to_string()),
                };

                (
                    safe_min_pwr,
                    max_pwr.or_else(|| Some("0".to_string())),
                    safe_idle_timer,
                )
            } else {
                (None, None, None)
            };

        let orig_memlat_sample_ms = memlat_settings.as_ref().and_then(|p| {
            std::fs::read_to_string(p.join("sample_ms"))
                .ok()
                .map(|s| s.trim().to_string())
        });

        if kgsl.is_some() || memlat_settings.is_some() {
            tracing::debug!(
                "Cached Snapdragon paths: kgsl={}, memlat={}, orig_pwrlevel={:?}/{:?}, orig_idle={:?}, orig_memlat={:?}",
                kgsl.is_some(),
                memlat_settings.is_some(),
                orig_min_pwrlevel,
                orig_max_pwrlevel,
                orig_idle_timer,
                orig_memlat_sample_ms,
            );
        }

        Self {
            kgsl,
            memlat_settings,
            orig_min_pwrlevel,
            orig_max_pwrlevel,
            orig_idle_timer,
            orig_memlat_sample_ms,
        }
    }
}

static SNAPDRAGON_PATHS: OnceLock<SnapdragonPaths> = OnceLock::new();

#[inline]
pub fn snapdragon_paths() -> &'static SnapdragonPaths {
    SNAPDRAGON_PATHS.get_or_init(SnapdragonPaths::scan)
}
