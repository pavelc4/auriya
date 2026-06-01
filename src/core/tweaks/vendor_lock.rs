use std::collections::HashMap;
use std::ffi::CString;
use std::fs::{self, set_permissions};
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::ptr;

use libc::{mount, umount2, MS_BIND, MS_REC, MNT_DETACH};
use tracing::{debug, warn};

const VENDOR_PATHS: &[(&str, &str)] = &[
    ("/sys/module/mtk_fpsgo/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr_policy/parameters/perfmgr_enable", "0"),
    ("/sys/module/perfmgr_mtk/parameters/perfmgr_enable", "0"),
    ("/sys/module/migt/parameters/glk_fbreak_enable", "0"),
    ("/sys/module/migt/parameters/glk_disable", "1"),
    ("/proc/game_opt/disable_cpufreq_limit", "1"),
];

fn do_mount_bind(src: &str, dest: &str) -> bool {
    let src_c = CString::new(src).unwrap();
    let dest_c = CString::new(dest).unwrap();
    unsafe {
        umount2(dest_c.as_ptr(), MNT_DETACH);
        mount(
            src_c.as_ptr().cast(),
            dest_c.as_ptr().cast(),
            ptr::null(),
            MS_BIND | MS_REC,
            ptr::null(),
        ) == 0
    }
}

fn do_unmount(path: &str) -> bool {
    let p = CString::new(path).unwrap();
    unsafe { umount2(p.as_ptr(), MNT_DETACH) == 0 }
}

#[derive(Default)]
pub struct VendorLock {
    saved: HashMap<String, String>,
    locked: bool,
}

impl VendorLock {
    pub fn new() -> Self {
        Self {
            saved: HashMap::new(),
            locked: false,
        }
    }

    pub fn lock_all(&mut self) {
        if self.locked {
            return;
        }

        for (path, value) in VENDOR_PATHS {
            if !Path::new(path).exists() {
                continue;
            }

            let prev = fs::read_to_string(path).ok();
            let mount_path = format!("/cache/.auriya_{}", path.replace('/', "_"));

            let _ = fs::write(path, value);
            let _ = set_permissions(path, PermissionsExt::from_mode(0o444));
            let _ = fs::write(&mount_path, value);
            if do_mount_bind(&mount_path, path) {
                if let Some(saved_val) = prev {
                    self.saved.insert(path.to_string(), saved_val);
                }
                debug!(target: "auriya::vendor_lock", "Locked {} → {}", path, value);
            } else {
                warn!(target: "auriya::vendor_lock", "Failed to mount-bind {}", path);
                let _ = set_permissions(path, PermissionsExt::from_mode(0o644));
            }
        }

        self.locked = true;
    }

    pub fn unlock_all(&mut self) {
        if !self.locked {
            return;
        }

        for (path, _) in VENDOR_PATHS {
            if !Path::new(path).exists() {
                continue;
            }

            let _ = set_permissions(path, PermissionsExt::from_mode(0o644));
            do_unmount(path);

            if let Some(saved_val) = self.saved.remove(*path) {
                let _ = fs::write(path, saved_val.trim());
                debug!(target: "auriya::vendor_lock", "Unlocked {}, restored", path);
            }
        }

        self.locked = false;
    }
}
