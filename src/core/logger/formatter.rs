use core::fmt::{self, Write};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogFlag {
    Status,
    Code,
    Pass,
    Warn,
    Fail,
    Debug,
}

impl LogFlag {
    #[inline(always)]
    pub fn as_padded_str(&self) -> &'static str {
        match self {
            Self::Status => "STATUS",
            Self::Code => "CODE  ",
            Self::Pass => "PASS  ",
            Self::Warn => "WARN  ",
            Self::Fail => "FAIL  ",
            Self::Debug => "DEBUG ",
        }
    }

    #[inline(always)]
    pub fn from_tracing_level(level: &tracing::Level, target: &str) -> Self {
        if target.eq_ignore_ascii_case("status") {
            return Self::Status;
        }
        if target.eq_ignore_ascii_case("code") {
            return Self::Code;
        }
        if target.eq_ignore_ascii_case("pass") {
            return Self::Pass;
        }

        match *level {
            tracing::Level::ERROR => Self::Fail,
            tracing::Level::WARN => Self::Warn,
            tracing::Level::INFO => Self::Status,
            tracing::Level::DEBUG | tracing::Level::TRACE => Self::Debug,
        }
    }
}

/// Zero-allocation stack-allocated byte buffer implementing `core::fmt::Write`.
pub struct StackWriter<const N: usize> {
    buf: [u8; N],
    cursor: usize,
}

impl<const N: usize> StackWriter<N> {
    pub const fn new() -> Self {
        Self {
            buf: [0u8; N],
            cursor: 0,
        }
    }

    #[inline(always)]
    pub fn as_bytes(&self) -> &[u8] {
        &self.buf[..self.cursor]
    }

    #[inline(always)]
    pub fn clear(&mut self) {
        self.cursor = 0;
    }
}

impl<const N: usize> Default for StackWriter<N> {
    fn default() -> Self {
        Self::new()
    }
}

impl<const N: usize> Write for StackWriter<N> {
    fn write_str(&mut self, s: &str) -> fmt::Result {
        let bytes = s.as_bytes();
        let rem = self.buf.len().saturating_sub(self.cursor);
        let amt = bytes.len().min(rem);
        self.buf[self.cursor..self.cursor + amt].copy_from_slice(&bytes[..amt]);
        self.cursor += amt;
        Ok(())
    }
}

/// Retrieve kernel boottime uptime in fractional seconds with microsecond resolution.
#[inline]
pub fn get_boottime_secs() -> f64 {
    let mut ts = libc::timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    // SAFETY: Valid pointer to stack-allocated timespec structure.
    let res = unsafe { libc::clock_gettime(libc::CLOCK_BOOTTIME, &mut ts) };
    if res == 0 {
        ts.tv_sec as f64 + (ts.tv_nsec as f64 / 1_000_000_000.0)
    } else {
        0.0
    }
}

/// Format a single dmesg-style log entry into the provided `StackWriter`.
pub fn format_dmesg_entry<const N: usize>(
    writer: &mut StackWriter<N>,
    uptime: f64,
    flag: LogFlag,
    args: fmt::Arguments<'_>,
) -> fmt::Result {
    writeln!(
        writer,
        "[{:>12.6}] auriya: {} | {}",
        uptime,
        flag.as_padded_str(),
        args
    )
}
