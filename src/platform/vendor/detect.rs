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
fn detect_soc_internal() -> SocType {
    if let Ok(platform) = get_prop("ro.board.platform") {
        let p = platform.to_lowercase();
        let platform_patterns = [
            (&["mt", "k6"][..], SocType::MediaTek),
            (&["sm", "sdm", "msm", "apq"], SocType::Snapdragon),
            (&["exynos"], SocType::Exynos),
            (&["ud710", "ums"], SocType::Unisoc),
            (&["gs"], SocType::Tensor),
        ];

        for (prefixes, soc_type) in platform_patterns {
            if prefixes.iter().any(|prefix| p.starts_with(prefix)) {
                return soc_type;
            }
        }
    }

    if let Ok(hardware) = get_prop("ro.hardware") {
        let h = hardware.to_lowercase();
        let hardware_patterns = [
            ("mt", SocType::MediaTek),
            ("qcom", SocType::Snapdragon),
            ("exynos", SocType::Exynos),
            ("samsung", SocType::Exynos),
        ];

        for (substring, soc_type) in hardware_patterns {
            if h.contains(substring) {
                return soc_type;
            }
        }
    }

    if std::path::Path::new("/proc/ppm").exists() {
        return SocType::MediaTek;
    }
    if std::path::Path::new("/sys/class/kgsl/kgsl-3d0").exists() {
        return SocType::Snapdragon;
    }

    SocType::Unknown
}

fn get_prop(key: &str) -> Result<String> {
    let output = Command::new("getprop").arg(key).output()?;
    let val = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if val.is_empty() {
        anyhow::bail!("Property empty");
    }
    Ok(val)
}
