use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};

pub struct PidTracker {
    pid: i32,
    pidfd: Option<OwnedFd>,
}

impl PidTracker {
    pub fn new(pid: i32) -> Self {
        let pidfd = Self::open_pidfd(pid);
        Self { pid, pidfd }
    }

    fn open_pidfd(pid: i32) -> Option<OwnedFd> {
        if pid <= 0 {
            return None;
        }
        #[cfg(target_arch = "aarch64")]
        const NR: i64 = 434;
        #[cfg(target_arch = "x86_64")]
        const NR: i64 = 439;
        #[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
        const NR: i64 = 434;

        let ret = unsafe { libc::syscall(NR, pid as libc::pid_t, 0) };
        if ret < 0 {
            return None;
        }
        Some(unsafe { OwnedFd::from_raw_fd(ret as RawFd) })
    }

    pub fn is_alive(&self) -> bool {
        match self.pidfd.as_ref() {
            Some(fd) => Self::pidfd_is_alive(fd),
            None => Self::fallback_is_alive(self.pid),
        }
    }

    fn pidfd_is_alive(fd: &OwnedFd) -> bool {
        let mut pfd = libc::pollfd {
            fd: fd.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        };
        let ret = unsafe { libc::poll(&mut pfd, 1, 0) };
        if ret < 0 {
            return true;
        }
        (pfd.revents & libc::POLLIN) == 0
    }

    fn fallback_is_alive(pid: i32) -> bool {
        std::path::Path::new(&format!("/proc/{pid}")).exists()
    }
}
