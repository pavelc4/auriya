# Changelog

## v1.0.0 (Initial Release)

**New Features:**
*   **WebUI:**
    *   Initial release of WebUI for module configuration and system monitoring.
    *   **Game Management:** Configure game packages and settings directly via WebUI.
    *   Redesigned main page and home view.
    *   Enhanced game list UI with GitHub-style badges and Material 3 design elements.
*   **Core Daemon Logic:**
    *   Implemented **Power Gating** & **Foreground Detection** via activities.
    *   Added **PID Priority** management based on `VisibleActivityProcess`.
    *   Implemented **Live Package Config Reloading** for dynamic updates.
    *   Added **Time-based Log Debounce** to reduce spam.
    *   Secure IPC socket permissions (0o660).
*   **Performance & Tweaks:**
    *   Introduced `tweaks` module for CPU, Memory, and **Adreno GPU** optimizations (auto-detect).
    *   Added **Adaptive LMK** (Low Memory Killer) tuning for low/mid-RAM devices.
    *   Added **Battery Saver Awareness**.
*   **Configuration:**
    *   Added **`gamelist.toml`** for managing game profiles.
    *   Added **`settings.toml`** for global daemon and FAS configuration.
    *   Initial support for Clash Royale in game list.

**Refactoring & Improvements:**
*   **Architecture:**
    *   Extracted daemon run logic and state into separate modules (`state.rs`, `run.rs`).
    *   Encapsulated daemon state in `Daemon` struct.
    *   Consolidated IPC server, commands, and types.
*   **Code Quality:**
    *   Simplified conditional logic and removed unused code.
    *   Applied consistent formatting and import ordering.

**Fixes & Build:**
*   Configured Cargo for **Android Release Builds**.
*   Improved module installation and daemon management.
*   Updated default config paths.
