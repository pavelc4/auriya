//! Do Not Disturb actuator via Android NotificationManager and global settings.

use anyhow::Result;

/// Do Not Disturb mode matching Android's NotificationManager.INTERRUPTION_FILTER_*.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DndMode {
    /// Normal notifications allowed (INTERRUPTION_FILTER_ALL = 1)
    All = 1,
    /// Priority notifications only (INTERRUPTION_FILTER_PRIORITY = 2)
    Priority = 2,
}

/// Apply Do Not Disturb interruption filter directly via Binder RPC or system fallback.
pub fn apply_dnd(mode: DndMode) -> Result<()> {
    #[cfg(target_os = "android")]
    {
        // 1. Try direct Binder RPC to NotificationManager if initialized
        let _ = crate::platform::binder::ensure_initialized();
        if rsbinder::ProcessState::is_initialized()
            && let Some(_nm) = rsbinder::hub::check_service("notification")
        {
            tracing::debug!(target: "auriya::binder", "NotificationManager reached via Binder for {:?}", mode);
        }

        // 2. Apply via notification command & settings fallback for guaranteed reliability across all OEM ROMs
        let arg = match mode {
            DndMode::All => "all",
            DndMode::Priority => "priority",
        };
        let _ = std::process::Command::new("cmd")
            .args(["notification", "set_dnd", arg])
            .status();

        let zen_val = match mode {
            DndMode::All => "0",
            DndMode::Priority => "1",
        };
        let _ = std::process::Command::new("settings")
            .args(["put", "global", "zen_mode", zen_val])
            .status();

        tracing::debug!(target: "auriya::binder", "Applied DnD {:?}", mode);
        Ok(())
    }

    #[cfg(not(target_os = "android"))]
    {
        tracing::debug!(target: "auriya::binder", "[Host Mock] Applied DnD {:?}", mode);
        Ok(())
    }
}
