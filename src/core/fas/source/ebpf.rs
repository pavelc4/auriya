// eBPF backend for FrameSource — wraps `toru::FrameProbe`.
//
// `FrameProbe` is owned exclusively by a dedicated worker thread. All
// interaction goes through two channels:
//   - cmd_tx (this thread → worker): attach requests
//   - frame_tx (worker → daemon):    per-frame durations
// This avoids the mutex contention that occurs when both the tokio
// tick loop and the background recv loop fight over the same probe.

use anyhow::{Result, anyhow};
use std::sync::mpsc as std_mpsc;
use std::thread;
use std::time::Duration;
use tokio::sync::mpsc;
use toru::FrameProbe;

enum Cmd {
    Attach(i32, std_mpsc::Sender<Result<()>>),
}

pub struct EbpfHandle {
    cmd_tx: std_mpsc::Sender<Cmd>,
}

impl EbpfHandle {
    pub fn load(frame_tx: mpsc::Sender<Duration>) -> Result<Self> {
        let mut probe = FrameProbe::new().map_err(|e| anyhow!("Toru init: {e}"))?;

        let (cmd_tx, cmd_rx) = std_mpsc::channel::<Cmd>();

        thread::Builder::new()
            .name("auriya-fas-ebpf".into())
            .spawn(move || {
                let mut poll_count: u64 = 0;

                loop {
                    // Drain pending commands first so attach() callers don't
                    // wait for the next ring buffer event.
                    while let Ok(cmd) = cmd_rx.try_recv() {
                        match cmd {
                            Cmd::Attach(pid, reply) => {
                                poll_count = 0;
                                let res = probe
                                    .attach(pid)
                                    .map_err(|e| anyhow!("attach({pid}): {e}"));
                                let _ = reply.send(res);
                            }
                        }
                    }

                    // `recv_with_deadline` uses an mio poll with a deadline.
                    // A blocking variant would never return when no app is
                    // attached and we'd never check cmd_rx again, deadlocking
                    // attach calls.
                    match probe.recv_with_deadline(Duration::from_millis(50)) {
                        Some((pid, frametime)) => {
                            poll_count = 0;
                            tracing::debug!(
                                target: "auriya::fas",
                                "ebpf  | frame pid={pid} {:.3}ms",
                                frametime.as_secs_f64() * 1000.0
                            );
                            if frame_tx.blocking_send(frametime).is_err() {
                                return;
                            }
                        }
                        None => {
                            poll_count += 1;
                            if poll_count.is_multiple_of(2000) {
                                tracing::debug!(
                                    target: "auriya::fas",
                                    "ebpf  | worker alive, {} polls since last frame",
                                    poll_count
                                );
                            }
                        }
                    }
                    // None branch: no app attached yet, or poll timed out
                    // before a frame arrived. Loop back to check commands
                    // without sleeping — the poll deadline already throttles
                    // us.
                }
            })
            .map_err(|e| anyhow!("spawn ebpf worker: {e}"))?;

        Ok(Self { cmd_tx })
    }

    /// Attach the BPF program to a target app. Blocking with a 1s
    /// timeout — the worker drains commands between every poll, so
    /// in practice the reply comes back in milliseconds, but we cap
    /// it so a wedged worker can't deadlock the tick loop.
    pub fn attach(&self, pid: i32) -> Result<()> {
        let (reply_tx, reply_rx) = std_mpsc::channel();
        self.cmd_tx
            .send(Cmd::Attach(pid, reply_tx))
            .map_err(|_| anyhow!("Toru worker died"))?;
        match reply_rx.recv_timeout(Duration::from_secs(1)) {
            Ok(res) => res,
            Err(std_mpsc::RecvTimeoutError::Timeout) => {
                Err(anyhow!("attach({pid}) timed out after 1s"))
            }
            Err(std_mpsc::RecvTimeoutError::Disconnected) => {
                Err(anyhow!("Toru worker dropped reply channel"))
            }
        }
    }
}
