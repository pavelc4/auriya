use tracing::debug;

pub use crate::platform::binder::DndMode as DndFilter;

/// Request the daemon to apply the given DnD filter via Native Binder IPC.
#[inline]
pub fn request_dnd(filter: DndFilter) {
    if let Err(e) = crate::platform::binder::apply_dnd(filter) {
        tracing::warn!(
            target: "auriya::profile",
            "Failed to apply DnD {:?}: {}",
            filter,
            e
        );
    } else {
        debug!(target: "auriya::profile", "Requested DnD filter {:?}", filter);
    }
}
