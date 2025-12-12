use anyhow::Result;
use std::fs;
use std::path::Path;
use tracing::{debug, info};

pub fn apply_performance_sched() -> Result<()> {
    let stune_base = "/dev/stune/top-app";
    if Path::new(stune_base).exists() {
        let _ = fs::write(format!("{}/schedtune.prefer_idle", stune_base), "1");

        let _ = fs::write(format!("{}/schedtune.boost", stune_base), "1");
        debug!("Schedtune: prefer_idle=1, boost=1");
    }

    let sched_features = "/sys/kernel/debug/sched_features";
    if Path::new(sched_features).exists() {
        let _ = fs::write(sched_features, "NEXT_BUDDY");

        let _ = fs::write(sched_features, "NO_TTWU_QUEUE");
        debug!("Sched features: NEXT_BUDDY, NO_TTWU_QUEUE");
    }

    let sched_lib_name = "/proc/sys/kernel/sched_lib_name";
    if Path::new(sched_lib_name).exists() {
        let libs =
            "libunity.so,libil2cpp.so,libmain.so,libUE4.so,libminecraftpe.so,libgodot_android.so";
        let _ = fs::write(sched_lib_name, libs);
        let _ = fs::write("/proc/sys/kernel/sched_lib_mask_force", "255");
        debug!("Sched lib mask set for game libraries");
    }

    let split_lock = "/proc/sys/kernel/split_lock_mitigate";
    if Path::new(split_lock).exists() {
        let _ = fs::write(split_lock, "0");
    }

    info!("Performance scheduler tweaks applied");
    Ok(())
}

pub fn apply_balance_sched() -> Result<()> {
    let stune_base = "/dev/stune/top-app";
    if Path::new(stune_base).exists() {
        let _ = fs::write(format!("{}/schedtune.prefer_idle", stune_base), "0");

        let _ = fs::write(format!("{}/schedtune.boost", stune_base), "1");
        debug!("Schedtune: prefer_idle=0, boost=1");
    }

    let sched_features = "/sys/kernel/debug/sched_features";
    if Path::new(sched_features).exists() {
        let _ = fs::write(sched_features, "NEXT_BUDDY");
        let _ = fs::write(sched_features, "TTWU_QUEUE");
        debug!("Sched features: NEXT_BUDDY, TTWU_QUEUE (balanced)");
    }

    let split_lock = "/proc/sys/kernel/split_lock_mitigate";
    if Path::new(split_lock).exists() {
        let _ = fs::write(split_lock, "1");
    }

    info!("Balanced scheduler tweaks applied");
    Ok(())
}
