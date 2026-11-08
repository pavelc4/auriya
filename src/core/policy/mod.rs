//! Pure policy evaluation engine.
//!
//! Evaluates system observations (screen power, battery saver, focused app)
//! against operational rules (whitelists, FAS capability) to determine the next
//! state transition (Powersave, EnterGame, SameAppFas, etc.).
//!
//! Zero-allocation: uses borrowed slices and string references.
//! Pure: contains no I/O, tokio, Binder, or sysfs dependencies.

/// Subset of power state consumed by the policy tick.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PowerSnapshot {
    pub screen_awake: bool,
    pub battery_saver: bool,
}

/// System observation snapshot at a discrete tick.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SystemSnapshot<'a> {
    pub power: PowerSnapshot,
    pub foreground_pkg: Option<&'a str>,
    pub focused_pid: Option<i32>,
    pub package_pids: &'a [i32],
    pub is_pid_valid: bool,
    pub is_floating: bool,
}

/// Operational parameters and historical context supplied by the daemon.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PolicyContext<'p> {
    pub last_pkg: Option<&'p str>,
    pub is_whitelisted: bool,
    pub fas_enabled: bool,
    pub fas_available: bool,
}

/// Operational action determined by the policy engine.
#[derive(Debug, PartialEq, Eq)]
pub enum PolicyDecision<'a> {
    Powersave,
    NoForeground,
    NonWhitelisted {
        pkg: &'a str,
        pid: Option<i32>,
        pids: &'a [i32],
        reason: &'static str,
    },
    SameAppFas {
        pkg: &'a str,
        pids: &'a [i32],
    },
    SameAppMonitor {
        pkg: &'a str,
    },
    EnterGame {
        pkg: &'a str,
        pid: i32,
        pids: &'a [i32],
    },
}

pub struct PolicyEngine;

impl PolicyEngine {
    #[inline]
    pub fn evaluate<'a>(
        snapshot: &SystemSnapshot<'a>,
        ctx: &PolicyContext<'_>,
    ) -> PolicyDecision<'a> {
        // 1. Screen off is an unconditional powersave transition.
        if !snapshot.power.screen_awake {
            return PolicyDecision::Powersave;
        }

        let Some(pkg) = snapshot.foreground_pkg else {
            return PolicyDecision::NoForeground;
        };

        // 2. Battery saver: apply powersave unless a whitelisted game is actively running.
        if snapshot.power.battery_saver && !ctx.is_whitelisted {
            return PolicyDecision::Powersave;
        }

        let is_same_pkg = ctx.last_pkg == Some(pkg);

        match (is_same_pkg, snapshot.is_pid_valid, ctx.is_whitelisted) {
            (true, true, true) => match ctx.fas_enabled && ctx.fas_available {
                true => PolicyDecision::SameAppFas {
                    pkg,
                    pids: snapshot.package_pids,
                },
                false => PolicyDecision::SameAppMonitor { pkg },
            },
            (true, true, false) => PolicyDecision::SameAppMonitor { pkg },
            (false, _, false) => PolicyDecision::NonWhitelisted {
                pkg,
                pid: snapshot.focused_pid,
                pids: snapshot.package_pids,
                reason: "not whitelisted",
            },
            (false, _, true) => match snapshot.focused_pid {
                Some(pid) => PolicyDecision::EnterGame {
                    pkg,
                    pid,
                    pids: snapshot.package_pids,
                },
                None => PolicyDecision::NonWhitelisted {
                    pkg,
                    pid: None,
                    pids: snapshot.package_pids,
                    reason: "PID not found",
                },
            },
            (true, false, false) => PolicyDecision::NonWhitelisted {
                pkg,
                pid: snapshot.focused_pid,
                pids: snapshot.package_pids,
                reason: "process dead",
            },
            (true, false, true) => match snapshot.focused_pid {
                Some(pid) => PolicyDecision::EnterGame {
                    pkg,
                    pid,
                    pids: snapshot.package_pids,
                },
                None => PolicyDecision::NonWhitelisted {
                    pkg,
                    pid: None,
                    pids: snapshot.package_pids,
                    reason: "PID not found",
                },
            },
        }
    }
}
