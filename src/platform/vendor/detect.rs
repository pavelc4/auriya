use anyhow::Result;
use std::process::Command;
use std::sync::OnceLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SocType {
    Snapdragon,
    MediaTek,
    Exynos,
    Unisoc,
    Tensor,
    Unknown,
}

impl std::fmt::Display for SocType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SocType::Snapdragon => write!(f, "Snapdragon"),
            SocType::MediaTek => write!(f, "MediaTek"),
            SocType::Exynos => write!(f, "Exynos"),
            SocType::Unisoc => write!(f, "Unisoc"),
            SocType::Tensor => write!(f, "Tensor"),
            SocType::Unknown => write!(f, "Unknown"),
        }
    }
}

static SOC_CACHE: OnceLock<SocType> = OnceLock::new();

pub fn detect_soc() -> SocType {
    *SOC_CACHE.get_or_init(detect_soc_internal)
}

#[inline]
pub fn is_mediatek() -> bool {
    detect_soc() == SocType::MediaTek
}
/// Classify SoC vendor from Android `ro.board.platform` and `ro.hardware` properties.
pub fn classify_soc_from_props(platform: Option<&str>, hardware: Option<&str>) -> SocType {
    if let Some(platform) = platform {
        let p = platform.to_lowercase();
        let platform_patterns = [
            (&["mt", "k6"][..], SocType::MediaTek),
            (&["sm", "sdm", "msm", "apq"][..], SocType::Snapdragon),
            (&["exynos", "s5e", "erd"][..], SocType::Exynos),
            (&["ud710", "ums"][..], SocType::Unisoc),
            (&["gs"][..], SocType::Tensor),
        ];

        for (prefixes, soc_type) in platform_patterns {
            if prefixes.iter().any(|prefix| p.starts_with(prefix)) {
                return soc_type;
            }
        }
    }

    if let Some(hardware) = hardware {
        let h = hardware.to_lowercase();
        let hardware_patterns = [
            ("mt", SocType::MediaTek),
            ("qcom", SocType::Snapdragon),
            ("exynos", SocType::Exynos),
            ("samsung", SocType::Exynos),
            ("s5e", SocType::Exynos),
        ];

        for (substring, soc_type) in hardware_patterns {
            if h.contains(substring) {
                return soc_type;
            }
        }
    }

    SocType::Unknown
}

fn has_platform_sgpu(platform_dir: &std::path::Path) -> bool {
    let Ok(entries) = std::fs::read_dir(platform_dir) else {
        return false;
    };
    entries
        .flatten()
        .any(|e| e.file_name().to_string_lossy().contains(".sgpu"))
}

fn detect_soc_internal() -> SocType {
    let platform = get_prop("ro.board.platform").ok();
    let hardware = get_prop("ro.hardware").ok();
    let from_props = classify_soc_from_props(platform.as_deref(), hardware.as_deref());
    if from_props != SocType::Unknown {
        tracing::debug!(
            target: "auriya::platform",
            "SoC detected from props: {:?} (platform={:?}, hardware={:?})",
            from_props,
            platform,
            hardware
        );
        return from_props;
    }

    let soc = match () {
        _ if std::path::Path::new("/proc/ppm").exists() => SocType::MediaTek,
        _ if std::path::Path::new("/sys/class/kgsl/kgsl-3d0").exists() => SocType::Snapdragon,
        _ if std::path::Path::new("/sys/devices/platform/exynos-gpu-profiler").exists()
            || has_platform_sgpu(std::path::Path::new("/sys/devices/platform")) =>
        {
            SocType::Exynos
        }
        _ => SocType::Unknown,
    };

    tracing::debug!(
        target: "auriya::platform",
        "SoC detected from sysfs fallback: {:?} (platform={:?}, hardware={:?})",
        soc,
        platform,
        hardware
    );
    soc
}

fn get_prop(key: &str) -> Result<String> {
    let output = Command::new("getprop").arg(key).output()?;
    let val = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if val.is_empty() {
        anyhow::bail!("Property empty");
    }
    Ok(val)
}
