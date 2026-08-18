# Architecture Overview

```text
┌─────────────────┐
│ Android Manager │
└────────┬────────┘
         │
         │ commands and state
         ▼
┌───────────────────┐
│ Companion Service │
└────────┬──────────┘
         │
         │ local IPC
         ▼
┌───────────────────┐
│    Rust Daemon    │
└────────┬──────────┘
         │
         ├─► Process / game detection ──┐
         │                              │
         └─► FPS meter ─────────────────┴─► ┌───────────────────┐
                                            │ Profile scheduler │
                                            └─────────┬─────────┘
                                                      │
                                                      ▼
                                            ┌───────────────────┐
                                            │ System tweak layer│
                                            └─────────┬─────────┘
                                                      │
                                                      ▼
                                            ┌───────────────────┐
                                            │  /proc and /sys   │
                                            └───────────────────┘

```

The Android manager owns user interaction and presentation. The companion service bridges Android lifecycle constraints. The Rust daemon owns long-running observation, scheduling, telemetry, and system writes.

The control CLI in `src/ctl.rs` provides a second entry point for querying or controlling the daemon without the Compose UI.
