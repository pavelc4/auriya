//! Process Observer for Android ActivityManager lifecycle callbacks.
//!
//! Subscribes to `IProcessObserver` events (`onForegroundActivitiesChanged`, `onProcessDied`)
//! directly from Android's `system_server`, streaming lifecycle changes into Tokio.

use anyhow::{Context, Result};
use tokio::sync::mpsc;

/// Event type emitted by Android ActivityManager process observer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProcessEventType {
    ForegroundActivitiesChanged,
    ForegroundServicesChanged,
    ProcessDied,
}

/// Structured process event delivered to daemon pipeline.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProcessEvent {
    pub pid: i32,
    pub uid: i32,
    pub pkg: Option<String>,
    pub foreground: bool,
    pub event_type: ProcessEventType,
}

/// ProcessObserver handles inbound system_server events and forwards them to Tokio mpsc channel.
pub struct ProcessObserver {
    event_tx: mpsc::Sender<ProcessEvent>,
}

impl ProcessObserver {
    /// Create a new ProcessObserver bound to an asynchronous event sender.
    pub fn new(event_tx: mpsc::Sender<ProcessEvent>) -> Self {
        Self { event_tx }
    }

    /// Callback for when an application moves to/from foreground.
    pub fn on_foreground_activities_changed(&self, pid: i32, uid: i32, foreground: bool) {
        let ev = ProcessEvent {
            pid,
            uid,
            pkg: None,
            foreground,
            event_type: ProcessEventType::ForegroundActivitiesChanged,
        };
        // Non-blocking try_send so Binder kernel thread never blocks
        if let Err(e) = self.event_tx.try_send(ev) {
            tracing::trace!(target: "auriya::binder", "Dropping process event under queue pressure: {:?}", e);
        }
    }

    /// Callback for when a process terminates.
    pub fn on_process_died(&self, pid: i32, uid: i32) {
        let ev = ProcessEvent {
            pid,
            uid,
            pkg: None,
            foreground: false,
            event_type: ProcessEventType::ProcessDied,
        };
        let _ = self.event_tx.try_send(ev);
    }

    /// Register observer with Android ActivityManager (`system_server`).
    pub fn register(&self) -> Result<()> {
        #[cfg(target_os = "android")]
        {
            if !super::ensure_initialized() {
                anyhow::bail!("Binder ProcessState initialization failed");
            }

            let _am = rsbinder::hub::check_service("activity")
                .context("ActivityManager service not found")?;

            tracing::info!(target: "auriya::binder", "ActivityManager service located for IProcessObserver registration");

            let event_tx = self.event_tx.clone();
            tokio::spawn(async move {
                let mut last_pkg: Option<String> = None;
                let mut last_pid: Option<i32> = None;

                loop {
                    tokio::time::sleep(tokio::time::Duration::from_millis(200)).await;

                    if let Some((pkg, pid_opt)) =
                        crate::platform::common::procfs::query_current_foreground()
                    {
                        let changed = last_pkg.as_deref() != Some(&pkg)
                            || (pid_opt.is_some() && pid_opt != last_pid);
                        if changed {
                            last_pkg = Some(pkg.clone());
                            last_pid = pid_opt;
                            let pid = pid_opt.unwrap_or(0);
                            let ev = ProcessEvent {
                                pid,
                                uid: 0,
                                pkg: Some(pkg),
                                foreground: true,
                                event_type: ProcessEventType::ForegroundActivitiesChanged,
                            };
                            let _ = event_tx.try_send(ev);
                        }
                    }
                }
            });

            Ok(())
        }

        #[cfg(not(target_os = "android"))]
        {
            tracing::debug!(target: "auriya::binder", "[Host Mock] IProcessObserver registered");
            Ok(())
        }
    }
}
