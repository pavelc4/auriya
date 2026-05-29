// FrameSource — per-frame timestamps for the FAS controller.
//
// Backend: `toru::FrameProbe` (uprobe on libgui's `Surface::queueBuffer`,
// BPF ring buffer). FAS requires a working eBPF probe; there is no
// dumpsys fallback. When the probe cannot load (very old kernel, locked-
// down SELinux, missing root), `FrameSource::new()` returns an error and
// FAS is disabled at the controller level.
//
// API:
//   - new()                  — load probe, fail if unavailable
//   - attach(package, pid)   — switch the probe to a new app
//   - drain_frame_times()    — drain every per-frame delta produced
//                              since the last call, ordered oldest → newest

use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc;

mod ebpf;

const FRAME_CHANNEL_CAPACITY: usize = 4096;

pub struct FrameSource {
    package: String,
    handle: ebpf::EbpfHandle,
    rx: mpsc::Receiver<Duration>,
}

impl FrameSource {
    pub fn new() -> Result<Self> {
        let (tx, rx) = mpsc::channel(FRAME_CHANNEL_CAPACITY);

        let handle = ebpf::EbpfHandle::load(tx)?;
        tracing::info!(
            target: "auriya::fas",
            "FAS    | Frame source: eBPF via Toru (uprobe on libgui)"
        );

        Ok(Self {
            package: String::new(),
            handle,
            rx,
        })
    }

    pub async fn attach(&mut self, package: &str, pid: i32) -> Result<()> {
        self.package = package.to_string();

        self.handle.attach(pid).inspect_err(|e| {
            tracing::warn!(
                target: "auriya::fas",
                "Toru attach({pid}) failed for {package}: {e}"
            );
        })?;

        tracing::debug!(
            target: "auriya::fas",
            "Toru attached to pid {pid} ({})",
            package
        );
        Ok(())
    }

    /// Drain every per-frame delta produced since the last call.
    pub async fn drain_frame_times(&mut self) -> Vec<Duration> {
        let mut out = Vec::new();
        loop {
            match self.rx.try_recv() {
                Ok(dt) => {
                    // Drop absurd gaps (> 500ms) — these usually mean the app
                    // was backgrounded, not a real frame.
                    if dt < Duration::from_millis(500) {
                        out.push(dt);
                    }
                }
                Err(mpsc::error::TryRecvError::Empty) => break,
                Err(mpsc::error::TryRecvError::Disconnected) => {
                    tracing::error!(
                        target: "auriya::fas",
                        "Toru reader disconnected; FAS will stop receiving frames"
                    );
                    break;
                }
            }
        }

        if !out.is_empty() {
            tracing::debug!(
                target: "auriya::fas",
                "drained {} frame deltas (first {:.2}ms last {:.2}ms)",
                out.len(),
                out.first().unwrap().as_secs_f64() * 1000.0,
                out.last().unwrap().as_secs_f64() * 1000.0
            );
        }
        out
    }
}
