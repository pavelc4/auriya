// FrameSource — per-frame timestamps for the FAS controller.
//
// Consumes frame deltas from the shared `core::ebpf::EbpfFrameStream`
// broadcast channel. Multiple consumers (FAS, FpsMeter) subscribe.
//
// API:
//   - new(rx)                — receive frames from an existing broadcast
//   - attach(package, pid)   — switch the probe to a new app
//   - drain_frame_times()    — drain every per-frame delta produced
//                              since the last call, ordered oldest → newest

use super::buffer::FrameBuffer;
use anyhow::Result;
use std::time::Duration;
use tokio::sync::broadcast;

pub struct FrameSource {
    package: String,
    rx: broadcast::Receiver<Duration>,
}

impl FrameSource {
    pub fn new(rx: broadcast::Receiver<Duration>) -> Self {
        tracing::info!(
            target: "auriya::fas",
            "FAS    | Frame source: eBPF broadcast (shared with FpsMeter)"
        );
        Self {
            package: String::new(),
            rx,
        }
    }

    /// The actual eBPF attach is handled by `EbpfFrameStream::attach`.
    /// This method records the package name for bookkeeping.
    pub fn attach(&mut self, package: &str, _pid: i32) -> Result<()> {
        if self.package != package {
            self.package.clear();
            self.package.push_str(package);
        }
        Ok(())
    }

    pub fn drain_into_buffer(&mut self, buffer: &mut FrameBuffer) -> usize {
        let mut count = 0;
        loop {
            match self.rx.try_recv() {
                Ok(dt) => {
                    if dt >= Duration::from_millis(3) && dt < Duration::from_millis(500) {
                        buffer.push(dt);
                        count += 1;
                    }
                }
                Err(broadcast::error::TryRecvError::Empty) => break,
                Err(broadcast::error::TryRecvError::Closed) => {
                    tracing::error!(
                        target: "auriya::fas",
                        "eBPF broadcast closed; FAS frame consumption terminated"
                    );
                    break;
                }
                Err(broadcast::error::TryRecvError::Lagged(n)) => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "eBPF broadcast lagged by {n} frames"
                    );
                    continue;
                }
            }
        }
        count
    }

    /// Drain every per-frame delta produced since the last call into an owned vector.
    pub fn drain_frame_times(&mut self) -> Vec<Duration> {
        let mut out = Vec::new();
        loop {
            match self.rx.try_recv() {
                Ok(dt) if dt >= Duration::from_millis(3) && dt < Duration::from_millis(500) => {
                    out.push(dt);
                }
                Ok(_) => {}
                Err(broadcast::error::TryRecvError::Empty) => break,
                Err(broadcast::error::TryRecvError::Closed) => {
                    tracing::error!(
                        target: "auriya::fas",
                        "eBPF broadcast closed; FAS will stop receiving frames"
                    );
                    break;
                }
                Err(broadcast::error::TryRecvError::Lagged(n)) => {
                    tracing::debug!(
                        target: "auriya::fas",
                        "eBPF broadcast lagged by {n} frames"
                    );
                    continue;
                }
            }
        }
        out
    }
}
