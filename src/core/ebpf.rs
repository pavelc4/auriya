// Shared broadcast of per-frame deltas from eBPF (kala `FrameProbe`).
//
// Owns the worker thread. Frame times are broadcast to all subscribers
// via `tokio::sync::broadcast`. Both FAS and the FPS meter subscribe
// independently.
//
// The worker only polls for frames while at least one PID is attached.
// When nothing is attached (no game in the foreground) it blocks on the
// command channel, so the thread costs zero CPU while idle instead of
// draining frames from whatever happens to be on screen.
//
// API:
//   - new()         — load probe, spawn worker, fail if unavailable
//   - attach(pid)   — start tracking a target process
//   - detach(pid)   — stop tracking a target process
//   - subscribe()   — get a new broadcast receiver

use anyhow::{Result, anyhow};
use kala::FrameProbe;
use std::sync::mpsc as std_mpsc;
use std::thread;
use std::time::Duration;
use tokio::sync::broadcast;

enum Cmd {
    Attach(i32, std_mpsc::Sender<Result<()>>),
    Detach(i32, std_mpsc::Sender<Result<bool>>),
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
                loop {
                    // Nothing attached → block until a command arrives. This
                    // is what keeps the thread at ~0% CPU when no game is
                    // being tracked, instead of polling frames from whatever
                    // app is currently on screen.
                    if probe.attached() == 0 {
                        match cmd_rx.recv() {
                            Ok(cmd) => handle_cmd(&mut probe, cmd),
                            Err(_) => return, // all senders dropped
                        }
                        continue;
                    }

                    // Attached → drain pending commands without blocking,
                    // then poll for the next frame.
                    loop {
                        match cmd_rx.try_recv() {
                            Ok(cmd) => handle_cmd(&mut probe, cmd),
                            Err(std_mpsc::TryRecvError::Empty) => break,
                            Err(std_mpsc::TryRecvError::Disconnected) => return,
                        }
                    }

                    if let Some((_pid, frametime)) =
                        probe.recv_with_deadline(Duration::from_millis(50))
                    {
                        let _ = frame_tx.send(frametime);
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

    /// Detach the BPF program from a target process so the worker stops
    /// draining its frames. Blocking with a 1s timeout.
    pub fn detach(&self, pid: i32) -> Result<bool> {
        let (reply_tx, reply_rx) = std_mpsc::channel();
        self.cmd_tx
            .send(Cmd::Detach(pid, reply_tx))
            .map_err(|_| anyhow!("eBPF worker died"))?;
        match reply_rx.recv_timeout(Duration::from_secs(1)) {
            Ok(res) => res,
            Err(std_mpsc::RecvTimeoutError::Timeout) => {
                Err(anyhow!("detach({pid}) timed out after 1s"))
            }
            Err(std_mpsc::RecvTimeoutError::Disconnected) => {
                Err(anyhow!("eBPF worker dropped reply channel"))
            }
        }
    }
}

fn handle_cmd(probe: &mut FrameProbe, cmd: Cmd) {
    match cmd {
        Cmd::Attach(pid, reply) => {
            let res = probe.attach(pid).map_err(|e| anyhow!("attach({pid}): {e}"));
            let _ = reply.send(res);
        }
        Cmd::Detach(pid, reply) => {
            let res = probe.detach(pid).map_err(|e| anyhow!("detach({pid}): {e}"));
            let _ = reply.send(res);
        }
    }
}
