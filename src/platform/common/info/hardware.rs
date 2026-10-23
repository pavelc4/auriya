use super::props::get_system_property;
use std::fs;
use std::path::Path;

#[derive(Debug, Clone)]
pub struct HardwareInfo {
    pub brand: String,
    pub codename: String,
    pub android_version: String,
    pub android_api: String,
    pub soc_board: String,
    pub cpu_topology: String,
    pub gpu_model: Option<String>,
    pub gpu_driver: Option<String>,
    pub vulkan_support: String,
    pub active_renderer: String,
    pub display_rates: Option<String>,
    pub root_manager: String,
    pub selinux: String,
    pub kernel: String,
}

pub fn collect_hardware(supported_rates: Option<String>) -> HardwareInfo {
    let brand = get_system_property("ro.product.manufacturer")
        .or_else(|| get_system_property("ro.product.brand"))
        .unwrap_or_else(|| "Unknown".to_string());

    let codename = get_system_property("ro.product.device")
        .or_else(|| get_system_property("ro.build.product"))
        .unwrap_or_else(|| "Unknown".to_string());

    let android_version =
        get_system_property("ro.build.version.release").unwrap_or_else(|| "Unknown".to_string());

    let android_api =
        get_system_property("ro.build.version.sdk").unwrap_or_else(|| "?".to_string());

    let soc_model = get_system_property("ro.soc.model")
        .or_else(|| get_system_property("ro.board.platform"))
        .or_else(|| {
            fs::read_to_string("/sys/devices/soc0/chip_name")
                .ok()
                .map(|s| s.trim().to_string())
        })
        .unwrap_or_else(|| "Unknown".to_string());

    let soc_vendor = get_system_property("ro.hardware")
        .or_else(|| get_system_property("ro.soc.manufacturer"))
        .unwrap_or_default();

    let soc_board = if !soc_vendor.is_empty() {
        format!("{soc_model} ({soc_vendor})")
    } else {
        soc_model
    };

    let cpu_topology = detect_cpu_topology();
    let (gpu_model, gpu_driver) = detect_gpu_and_driver();
    let vulkan_support = detect_vulkan_support();
    let active_renderer = detect_active_renderer();
    let root_manager = detect_root_manager();
    let selinux = detect_selinux();
    let kernel = detect_kernel_version();

    HardwareInfo {
        brand,
        codename,
        android_version,
        android_api,
        soc_board,
        cpu_topology,
        gpu_model,
        gpu_driver,
        vulkan_support,
        active_renderer,
        display_rates: supported_rates,
        root_manager,
        selinux,
        kernel,
    }
}

fn detect_cpu_topology() -> String {
    let cores = match crate::platform::cpu::topology::get_online_cores() {
        Ok(c) if !c.is_empty() => c,
        _ => return "8 cores (Unknown)".to_string(),
    };

    let total = cores.len();
    let (little_mask, big_mask, prime_mask) =
        crate::platform::cpu::topology::classify_cores(&cores);
    let little = little_mask.count_ones();
    let big = big_mask.count_ones();
    let prime = prime_mask.count_ones();

    if prime > 0 {
        format!("{total} cores ({little} Little, {big} Mid, {prime} Prime)")
    } else if little > 0 && big > 0 {
        format!("{total} cores ({little} Little, {big} Big)")
    } else {
        format!("{total} cores")
    }
}

fn detect_gpu_and_driver() -> (Option<String>, Option<String>) {
    let kgsl_model_path = "/sys/class/kgsl/kgsl-3d0/gpu_model";
    if Path::new(kgsl_model_path).exists() {
        let raw = fs::read_to_string(kgsl_model_path).unwrap_or_default();
        let trimmed = raw.trim();
        let model = if trimmed.starts_with("Adreno") {
            let s = trimmed.trim_end_matches("v1").trim_end_matches("v0");
            if s.len() > 6 && !s[6..].starts_with(' ') {
                format!("Adreno {}", &s[6..])
            } else {
                s.to_string()
            }
        } else if !trimmed.is_empty() {
            trimmed.to_string()
        } else {
            "Adreno".to_string()
        };

        let driver = detect_adreno_driver();
        return (Some(model), Some(driver));
    }

    // Check Mali
    if Path::new("/proc/gpufreq/gpufreq_opp_freq").exists()
        || Path::new("/sys/class/misc/mali0").exists()
    {
        return (Some("Mali".to_string()), Some("Mali Driver".to_string()));
    }

    (None, None)
}

fn detect_adreno_driver() -> String {
    let candidate_libs = [
        "/vendor/lib64/egl/libGLESv2_adreno.so",
        "/vendor/lib64/hw/vulkan.adreno.so",
    ];

    for lib in candidate_libs {
        if let Ok(bytes) = fs::read(lib)
            && let Some(ver) = extract_adreno_version(&bytes)
        {
            return format!("{ver} (kgsl)");
        }
    }

    if let Some(driver_prop) = get_system_property("ro.gfx.driver.1") {
        return format!("{driver_prop} (kgsl)");
    }

    "Qualcomm kgsl".to_string()
}

fn extract_adreno_version(bytes: &[u8]) -> Option<String> {
    let pattern = b"V@";
    let mut i = 0;
    while i + 8 < bytes.len() {
        if &bytes[i..i + 2] == pattern {
            let mut end = i + 2;
            while end < bytes.len()
                && end < i + 16
                && (bytes[end].is_ascii_digit() || bytes[end] == b'.')
            {
                end += 1;
            }
            if end > i + 2 {
                let ver = String::from_utf8_lossy(&bytes[i..end]).to_string();
                return Some(ver.to_lowercase());
            }
        }
        i += 1;
    }
    None
}

fn detect_vulkan_support() -> String {
    let versions = [
        (
            "android.hardware.vulkan.version-1_3.xml",
            "Supported (v1.3)",
        ),
        (
            "android.hardware.vulkan.version-1_2.xml",
            "Supported (v1.2)",
        ),
        (
            "android.hardware.vulkan.version-1_1.xml",
            "Supported (v1.1)",
        ),
        (
            "android.hardware.vulkan.version-1_0_3.xml",
            "Supported (v1.0)",
        ),
    ];

    for (xml, label) in versions {
        let p = format!("/vendor/etc/permissions/{xml}");
        if Path::new(&p).exists() {
            return label.to_string();
        }
    }

    if let Some(vk) = get_system_property("ro.hardware.vulkan") {
        return format!("Supported ({vk})");
    }

    if Path::new("/vendor/lib64/hw/vulkan.adreno.so").exists()
        || Path::new("/system/lib64/libvulkan.so").exists()
    {
        return "Supported".to_string();
    }

    "Unsupported".to_string()
}

fn detect_active_renderer() -> String {
    let hwui = get_system_property("debug.hwui.renderer")
        .or_else(|| get_system_property("ro.hwui.use_vulkan"));

    match hwui.as_deref() {
        Some("skiavk") | Some("vulkan") | Some("true") | Some("1") => "Vulkan (SkiaVK)".to_string(),
        _ => "OpenGL ES (GLES)".to_string(),
    }
}

fn detect_root_manager() -> String {
    if Path::new("/data/adb/ksu").exists() {
        "KernelSU".to_string()
    } else if Path::new("/data/adb/ap").exists() {
        "APatch".to_string()
    } else if Path::new("/data/adb/magisk").exists() {
        "Magisk".to_string()
    } else {
        "Rooted / Other".to_string()
    }
}

fn detect_selinux() -> String {
    match fs::read_to_string("/sys/fs/selinux/enforce").map(|s| s.trim().to_string()) {
        Ok(s) if s == "1" => "Enforcing".to_string(),
        Ok(s) if s == "0" => "Permissive".to_string(),
        _ => "Disabled".to_string(),
    }
}

fn detect_kernel_version() -> String {
    let mut buf: libc::utsname = unsafe { std::mem::zeroed() };
    if unsafe { libc::uname(&mut buf) } == 0 {
        let release = unsafe { std::ffi::CStr::from_ptr(buf.release.as_ptr()) }.to_string_lossy();
        let machine = unsafe { std::ffi::CStr::from_ptr(buf.machine.as_ptr()) }.to_string_lossy();
        format!("{release} ({machine})")
    } else {
        fs::read_to_string("/proc/version")
            .map(|s| s.lines().next().unwrap_or("Unknown").to_string())
            .unwrap_or_else(|_| "Unknown".to_string())
    }
}
