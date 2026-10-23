use crate::daemon::model::event::{DaemonEvent, EventSender};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::JoinHandle;

pub struct PidTracker {
    pid: i32,
    pidfd: Option<Arc<OwnedFd>>,
    stop: Arc<AtomicBool>,
    wakeup: Option<Arc<OwnedFd>>,
    handle: Option<JoinHandle<()>>,
}

impl PidTracker {
    /// Begin tracking `pid`. Spawns a watcher thread that fires
    /// [`DaemonEvent::PidExited`] on `event_tx` when the process exits.
    pub fn spawn(pid: i32, event_tx: EventSender) -> Self {
        let pidfd = Self::open_pidfd(pid).map(Arc::new);
        let stop = Arc::new(AtomicBool::new(false));
        let wakeup = Self::make_eventfd().map(Arc::new);

        let handle = match wakeup.clone() {
            Some(wfd) => {
                let stop_t = stop.clone();
                let pidfd_t = pidfd.clone();
                std::thread::Builder::new()
                    .name("auriya-pidtrack".into())
                    .spawn(move || track_loop(pid, pidfd_t, wfd, stop_t, event_tx))
                    .ok()
            }
            None => None,
        };

        Self {
            pid,
            pidfd,
            stop,
            wakeup,
            handle,
        }
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

    fn make_eventfd() -> Option<OwnedFd> {
        let fd = unsafe { libc::eventfd(0, libc::EFD_CLOEXEC | libc::EFD_NONBLOCK) };
        if fd < 0 {
            return None;
        }
        Some(unsafe { OwnedFd::from_raw_fd(fd) })
    }

    /// Non-blocking liveness probe used by the tick loop's fast path.
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
        proc_exists(pid)
    }
}

impl Drop for PidTracker {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Release);
        if let Some(w) = self.wakeup.as_ref() {
            let val: u64 = 1;
            unsafe {
                libc::write(
                    w.as_raw_fd(),
                    std::ptr::addr_of!(val).cast(),
                    std::mem::size_of::<u64>(),
                );
            }
        }
        if let Some(h) = self.handle.take() {
            let _ = h.join();
        }
    }
}

fn proc_exists(pid: i32) -> bool {
    std::path::Path::new(&format!("/proc/{pid}")).exists()
}

fn track_loop(
    pid: i32,
    pidfd: Option<Arc<OwnedFd>>,
    wakeup: Arc<OwnedFd>,
    stop: Arc<AtomicBool>,
    event_tx: EventSender,
) {
    let exited = match pidfd {
        Some(fd) => wait_pidfd(fd.as_raw_fd(), wakeup.as_raw_fd(), &stop),
        None => wait_proc_poll(pid, wakeup.as_raw_fd(), &stop),
    };

    if exited && !stop.load(Ordering::Acquire) {
        let _ = event_tx.try_send(DaemonEvent::PidExited(pid));
    }
}

fn wait_pidfd(pidfd: RawFd, wakeup: RawFd, stop: &AtomicBool) -> bool {
    loop {
        let mut pfds = [
            libc::pollfd {
                fd: pidfd,
                events: libc::POLLIN,
                revents: 0,
            },
            libc::pollfd {
                fd: wakeup,
                events: libc::POLLIN,
                revents: 0,
            },
        ];
        let ret = unsafe { libc::poll(pfds.as_mut_ptr(), 2, -1) };
        if stop.load(Ordering::Acquire) {
            return false;
        }
        if ret < 0 {
            if is_eintr() {
                continue;
            }
            return false;
        }
        if (pfds[1].revents & libc::POLLIN) != 0 {
            return false;
        }
        if (pfds[0].revents & libc::POLLIN) != 0 {
            return true;
        }
    }
}

fn wait_proc_poll(pid: i32, wakeup: RawFd, stop: &AtomicBool) -> bool {
    const POLL_INTERVAL_MS: libc::c_int = 150;
    loop {
        let mut pfd = libc::pollfd {
            fd: wakeup,
            events: libc::POLLIN,
            revents: 0,
        };
        let ret = unsafe { libc::poll(&mut pfd, 1, POLL_INTERVAL_MS) };
        if stop.load(Ordering::Acquire) {
            return false;
        }
        if ret > 0 && (pfd.revents & libc::POLLIN) != 0 {
            return false;
        }
        if !proc_exists(pid) {
            return true;
        }
    }
}

fn is_eintr() -> bool {
    std::io::Error::last_os_error().raw_os_error() == Some(libc::EINTR)
}
