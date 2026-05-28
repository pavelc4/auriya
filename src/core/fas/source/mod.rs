// FrameSource — per-frame timestamps for the FAS controller.
//
// Primary backend: `toru::FrameProbe` (uprobe on libgui's
// `Surface::queueBuffer`, BPF ring buffer). Falls back to dumpsys
// SurfaceFlinger when the eBPF program cannot load (very old kernel,
// locked-down SELinux, missing root).
//
// API:
//   - attach(package, pid)   — switch the probe / dumpsys layer to a
//                              new app
//   - drain_frame_times()    — drain every per-frame delta produced
//                              since the last call, ordered oldest → newest
//   - get_frame_time()       — back-compat helper, returns only the freshest

use crate::core::dumpsys::surfaceflinger::SurfaceFlinger;
use anyhow::Result;
use std::time::Duration;
use tokio::sync::mpsc;

mod ebpf;

const FRAME_CHANNEL_CAPACITY: usize = 4096;

pub struct FrameSource {
    package: String,
    layer_name: Option<String>,
    backend: Backend,
    rx: Option<mpsc::Receiver<Duration>>,
}

enum Backend {
    Ebpf(ebpf::EbpfHandle),
    Dumpsys,
}

impl Default for FrameSource {
    fn default() -> Self {
        Self::new()
    }
}

impl FrameSource {
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel(FRAME_CHANNEL_CAPACITY);

        let backend = match ebpf::EbpfHandle::load(tx) {
            Ok(handle) => {
                tracing::info!(
                    target: "auriya::fas",
                    "FAS    | Frame source: eBPF via Toru (uprobe on libgui)"
                );
                Backend::Ebpf(handle)
            }
            Err(e) => {
                tracing::warn!(
                    target: "auriya::fas",
                    "FAS    | eBPF load failed ({}); falling back to dumpsys SurfaceFlinger",
                    e
                );
                Backend::Dumpsys
            }
        };

        Self {
            package: String::new(),
            layer_name: None,
            backend,
            rx: Some(rx),
        }
    }

    pub async fn attach(&mut self, package: &str, pid: i32) -> Result<()> {
        self.package = package.to_string();
        self.layer_name = None;

        match &self.backend {
            Backend::Ebpf(h) => {
                if let Err(e) = h.attach(pid) {
                    tracing::warn!(
                        target: "auriya::fas",
                        "Toru attach({pid}) failed: {e}; falling back to dumpsys"
                    );
                    self.backend = Backend::Dumpsys;
                    if let Ok(Some(layer)) = SurfaceFlinger::find_layer(package).await {
                        self.layer_name = Some(layer);
                    }
                } else {
                    tracing::debug!(
                        target: "auriya::fas",
                        "Toru attached to pid {pid} ({})",
                        package
                    );
                }
            }
            Backend::Dumpsys => {
                if let Ok(Some(layer)) = SurfaceFlinger::find_layer(package).await {
                    tracing::debug!(target: "auriya::fas", "Found layer: {}", layer);
                    self.layer_name = Some(layer);
                }
            }
        }
        Ok(())
    }

    /// Drain every per-frame delta produced since the last call.
    pub async fn drain_frame_times(&mut self) -> Vec<Duration> {
        match &self.backend {
            Backend::Ebpf(_) => self.drain_ebpf(),
            Backend::Dumpsys => self
                .next_dumpsys_frametime()
                .await
                .ok()
                .flatten()
                .into_iter()
                .collect(),
        }
    }

    /// Back-compat shim: return the most recent delta only.
    pub async fn get_frame_time(&mut self) -> Result<Option<Duration>> {
        let mut frames = self.drain_frame_times().await;
        Ok(frames.pop())
    }

    fn drain_ebpf(&mut self) -> Vec<Duration> {
        let Some(rx) = self.rx.as_mut() else {
            return Vec::new();
        };

        let mut out = Vec::new();
        loop {
            match rx.try_recv() {
                Ok(dt) => {
                    // Drop absurd gaps (> 500ms) — these usually mean the app
                    // was backgrounded, not a real frame.
                    if dt < Duration::from_millis(500) {
                        out.push(dt);
                    }
                }
                Err(mpsc::error::TryRecvError::Empty) => break,
                Err(mpsc::error::TryRecvError::Disconnected) => {
                    tracing::warn!(
                        target: "auriya::fas",
                        "Toru reader disconnected; switching to dumpsys"
                    );
                    self.backend = Backend::Dumpsys;
                    self.rx = None;
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

    async fn next_dumpsys_frametime(&mut self) -> Result<Option<Duration>> {
        if self.layer_name.is_none()
            && !self.package.is_empty()
            && let Ok(Some(layer)) = SurfaceFlinger::find_layer(&self.package).await
        {
            self.layer_name = Some(layer);
        }

        let Some(layer) = &self.layer_name else {
            return Ok(None);
        };

        match SurfaceFlinger::get_frame_time(layer).await {
            Ok(ft) if ft > 0.0 => Ok(Some(Duration::from_secs_f32(ft / 1000.0))),
            Ok(_) => Ok(None),
            Err(e) => {
                tracing::debug!(target: "auriya::fas", "dumpsys error: {:?}", e);
                self.layer_name = None;
                Ok(None)
            }
        }
    }
}
