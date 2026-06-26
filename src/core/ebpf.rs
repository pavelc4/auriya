// Shared broadcast of per-frame deltas from eBPF (kala `FrameProbe`).
//
// Owns the worker thread. Frame times are broadcast to all subscribers
// via `tokio::sync::broadcast`. Both FAS and the FPS meter subscribe
// independently.
//
// API:
//   - new()         — load probe, spawn worker, fail if unavailable
//   - attach(pid)   — switch the probe to a target process
//   - subscribe()   — get a new broadcast receiver

use anyhow::{Result, anyhow};
use kala::FrameProbe;
use std::sync::mpsc as std_mpsc;
use std::thread;
use std::time::Duration;
use tokio::sync::broadcast;

enum Cmd {
    Attach(i32, std_mpsc::Sender<Result<()>>),
}

pub struct EbpfFrameStream {
    cmd_tx: std_mpsc::Sender<Cmd>,
    rx: broadcast::Receiver<Duration>,
}

impl EbpfFrameStream {
    /// Load the eBPF probe and spawn the worker thread.
    /// Failures (old kernel, no BTF, SELinux deny) propagate to the caller.
    pub fn new() -> Result<Self> {
        let mut probe = FrameProbe::new().map_err(|e| anyhow!("kala init: {e}"))?;

        let (cmd_tx, cmd_rx) = std_mpsc::channel::<Cmd>();
        let (frame_tx, rx) = broadcast::channel(4096);

        thread::Builder::new()
            .name("auriya-ebpf".into())
            .spawn(move || {
                let mut poll_count: u64 = 0;

                loop {
                    while let Ok(cmd) = cmd_rx.try_recv() {
                        match cmd {
                            Cmd::Attach(pid, reply) => {
                                poll_count = 0;
                                let res =
                                    probe.attach(pid).map_err(|e| anyhow!("attach({pid}): {e}"));
                                let _ = reply.send(res);
                            }
                        }
                    }

                    match probe.recv_with_deadline(Duration::from_millis(50)) {
                        Some((_pid, frametime)) => {
                            poll_count = 0;
                            tracing::debug!(
                                target: "auriya::ebpf",
                                "frame {:.3}ms",
                                frametime.as_secs_f64() * 1000.0
                            );
                            let _ = frame_tx.send(frametime);
                        }
                        None => {
                            poll_count += 1;
                            if poll_count.is_multiple_of(2000) {
                                tracing::debug!(
                                    target: "auriya::ebpf",
                                    "worker alive, {} polls since last frame",
                                    poll_count
                                );
                            }
                        }
                    }
                }
            })
            .map_err(|e| anyhow!("spawn ebpf worker: {e}"))?;

        Ok(Self { cmd_tx, rx })
    }

    /// Subscribe to the frame broadcast stream.
    /// Each subscriber receives every frame delta independently.
    pub fn subscribe(&self) -> broadcast::Receiver<Duration> {
        self.rx.resubscribe()
    }

    /// Attach the BPF program to a target process.
    /// Blocking with a 1s timeout.
    pub fn attach(&self, pid: i32) -> Result<()> {
        let (reply_tx, reply_rx) = std_mpsc::channel();
        self.cmd_tx
            .send(Cmd::Attach(pid, reply_tx))
            .map_err(|_| anyhow!("eBPF worker died"))?;
        match reply_rx.recv_timeout(Duration::from_secs(1)) {
            Ok(res) => res,
            Err(std_mpsc::RecvTimeoutError::Timeout) => {
                Err(anyhow!("attach({pid}) timed out after 1s"))
            }
            Err(std_mpsc::RecvTimeoutError::Disconnected) => {
                Err(anyhow!("eBPF worker dropped reply channel"))
            }
        }
    }
}
