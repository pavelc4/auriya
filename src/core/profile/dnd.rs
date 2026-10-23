use crate::platform::android_cmd::{self as cmd_writer, DndFilter};
use std::sync::atomic::{AtomicBool, Ordering};
use tracing::debug;

/// Set by the daemon each tick to indicate whether the companion service
/// is believed to be alive. When `false`, `request_dnd` falls back to
/// `settings put global zen_mode` directly instead of going through the
/// companion's cmd_writer path.
static COMPANION_ALIVE: AtomicBool = AtomicBool::new(true);

/// Update the global companion-alive flag. Called by the daemon tick
/// after its health check.
pub fn set_companion_alive(alive: bool) {
    COMPANION_ALIVE.store(alive, Ordering::Release);
}

/// Request the companion service to apply the given DnD filter. The
/// daemon cannot call NotificationManager itself — it would need to be
/// an app rather than a root binary — so we hand the work off through
/// the cmd file. When the companion is known dead we fall back to a
/// `settings put global zen_mode` call directly (less polished — no status
/// bar icon — but still functional).
/// Driven by the daemon's game-session lifecycle (see `Daemon::sync_dnd`),
/// not by profile transitions — DnD must toggle on game enter/exit even
/// when the CPU/GPU profile itself does not change.
#[inline]
pub fn request_dnd(filter: DndFilter) {
    if COMPANION_ALIVE.load(Ordering::Acquire) {
        if let Err(e) = cmd_writer::shared().write_dnd(filter) {
            tracing::warn!(
                target: "auriya::profile",
                "Failed to write DnD {:?} to cmd file: {}",
                filter,
                e
            );
        } else {
            debug!(target: "auriya::profile", "Requested DnD filter {:?}", filter);
        }
    } else {
        let (zen_val, dnd_arg) = match filter {
            DndFilter::All => ("0", "all"),
            DndFilter::Priority => ("1", "priority"),
        };
        debug!(
            target: "auriya::profile",
            "DnD fallback (companion dead): zen_mode={zen_val}"
        );
        let _ = std::process::Command::new("su")
            .args(["2000", "-c", &format!("cmd notification set_dnd {dnd_arg}")])
            .status();
        let _ = std::process::Command::new("settings")
            .args(["put", "global", "zen_mode", zen_val])
            .status();
    }
}
