pub mod formatter;
pub mod telemetry;
pub mod throttle;
pub mod writer;

use core::fmt::Write;
use core::str;
use std::sync::Arc;
use tracing::field::{Field, Visit};
use tracing::{Event, Subscriber};
use tracing_subscriber::Layer;
use tracing_subscriber::layer::Context;
use tracing_subscriber::registry::LookupSpan;

pub use formatter::{LogFlag, StackWriter, format_dmesg_entry, get_boottime_secs};
pub use telemetry::{DaemonResourceStats, DaemonResourceTelemetry};
pub use throttle::{StateChangeLogger, WindowThrottle};
pub use writer::LogWriter;

struct MessageVisitor<'a, const N: usize> {
    writer: &'a mut StackWriter<N>,
}

impl<'a, const N: usize> Visit for MessageVisitor<'a, N> {
    fn record_debug(&mut self, field: &Field, value: &dyn core::fmt::Debug) {
        if field.name() == "message" {
            let _ = write!(self.writer, "{:?}", value);
        } else {
            let _ = write!(self.writer, " {}={:?}", field.name(), value);
        }
    }

    fn record_str(&mut self, field: &Field, value: &str) {
        if field.name() == "message" {
            let _ = write!(self.writer, "{}", value);
        } else {
            let _ = write!(self.writer, " {}={}", field.name(), value);
        }
    }
}

/// Tracing subscriber layer that emits zero-allocation dmesg-style log entries.
pub struct DmesgLayer {
    writer: Arc<LogWriter>,
}

impl DmesgLayer {
    pub fn new(writer: Arc<LogWriter>) -> Self {
        Self { writer }
    }
}

impl<S> Layer<S> for DmesgLayer
where
    S: Subscriber + for<'a> LookupSpan<'a>,
{
    fn on_event(&self, event: &Event<'_>, _ctx: Context<'_, S>) {
        let uptime = get_boottime_secs();
        let flag = LogFlag::from_tracing_level(event.metadata().level(), event.metadata().target());

        let mut msg_buf = StackWriter::<512>::new();
        let mut visitor = MessageVisitor {
            writer: &mut msg_buf,
        };
        event.record(&mut visitor);

        let msg_str = str::from_utf8(msg_buf.as_bytes()).unwrap_or("");
        let mut out_buf = StackWriter::<512>::new();
        let _ = writeln!(
            out_buf,
            "[{:>12.6}] auriya: {} | {}",
            uptime,
            flag.as_padded_str(),
            msg_str
        );

        self.writer.write_bytes(out_buf.as_bytes());
    }
}

#[macro_export]
macro_rules! klog_status {
    ($($arg:tt)*) => {
        $crate::tracing::info!(target: "status", $($arg)*);
    };
}

#[macro_export]
macro_rules! klog_code {
    ($($arg:tt)*) => {
        $crate::tracing::info!(target: "code", $($arg)*);
    };
}

#[macro_export]
macro_rules! klog_pass {
    ($($arg:tt)*) => {
        $crate::tracing::info!(target: "pass", $($arg)*);
    };
}

#[macro_export]
macro_rules! klog_warn {
    ($($arg:tt)*) => {
        $crate::tracing::warn!($($arg)*);
    };
}

#[macro_export]
macro_rules! klog_fail {
    ($($arg:tt)*) => {
        $crate::tracing::error!($($arg)*);
    };
}

#[macro_export]
macro_rules! klog_debug {
    ($($arg:tt)*) => {
        $crate::tracing::debug!($($arg)*);
    };
}
