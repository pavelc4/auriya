use anyhow::Result;
use std::process::Command;

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

pub fn detect_soc() -> SocType {
    // 1. Check ro.board.platform
    if let Ok(platform) = get_prop("ro.board.platform") {
        let p = platform.to_lowercase();
        if p.starts_with("mt") || p.starts_with("k6") {
            return SocType::MediaTek;
        } else if p.starts_with("sm")
            || p.starts_with("sdm")
            || p.starts_with("msm")
            || p.starts_with("apq")
        {
            return SocType::Snapdragon;
        } else if p.starts_with("exynos") {
            return SocType::Exynos;
        } else if p.starts_with("ud710") || p.starts_with("ums") {
            return SocType::Unisoc;
        } else if p.starts_with("gs") {
            return SocType::Tensor;
        }
    }

    if let Ok(hardware) = get_prop("ro.hardware") {
        let h = hardware.to_lowercase();
        if h.contains("mt") {
            return SocType::MediaTek;
        } else if h.contains("qcom") {
            return SocType::Snapdragon;
        } else if h.contains("exynos") {
            return SocType::Exynos;
        } else if h.contains("samsung") {
            return SocType::Exynos;
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
