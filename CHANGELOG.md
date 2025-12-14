# Changelog

## v1.0.1 

### New Features

**Frame-Aware Scheduling (FAS) Lite:**
- Implemented FAS Lite with PID controller for adaptive frame rate management
- Added FrameMonitor for real-time frame detection via SurfaceFlinger
- Per-game target FPS configuration with auto-detect support (60/90/120)
- `target_fps` array support via IPC for comma-separated FPS targets

**Per-Game Refresh Rate:**
- Added per-game display refresh rate override
- Stateful refresh rate handling with automatic restoration on game exit
- Exposed supported refresh rates via IPC for WebUI

**Per-Game Profile Mode:**
- Configurable profile mode per game (Performance/Balance/Powersave)

**Daemon Self-Restart:**
- Implemented daemon restart via IPC with `setsid` orphan pattern
- WebUI integration for restart functionality

**Hot-Reload Settings:**
- Settings file watcher for dynamic governor updates without restart

---

###  WebUI Improvements

**Svelte Migration:**
- Complete migration from vanilla JS to Svelte framework
- Custom notification system with Ferris mascot icons
- `unplugin-icons` for declarative icon management

**UI/UX Enhancements:**
- Redesigned home section with device architecture display
- Overhauled game settings UI with dedicated view and enhanced controls
- Theme toggle button with dynamic icon (previously checkbox)
- GitHub-style pagination for game list
- Global CPU governor selection UI
- Debug mode toggle
- Updated social links and footer styling

---

### Performance Optimizations

**Daemon Loop:**
- Replaced shell commands with libc syscalls (`taskset`/`renice`)
- Single-pass foreground parsing with PID validation
- Cached whitelist as `HashSet` for O(1) lookup
- Staggered power/foreground checks to reduce system load
- Instant profile apply on gamelist change

**Memory & CPU:**
- Added swappiness fallback chain for HyperOS
- `chmod` fallback for HyperOS sysfs restrictions

---

### Refactoring

**Module Reorganization:**
- Extracted SoC detection to dedicated module
- Separated Snapdragon and MediaTek tweaks into vendor modules
- Consolidated system tweaks module
- Enhanced GPU tweaks for Adreno 
- Removed regex dependency (manual display mode parsing)
- Flattened nested conditionals throughout codebase

**Code Quality:**
- Replaced `unwrap()` with safe lock handling
- Removed unused legacy FAS code
- Localized logs to English
- Use direct struct initialization in `PowerState`
- Replace modulo checks with `is_multiple_of()`
- Remove redundant closures
- Introduce `GameProfileUpdate` struct for cleaner API
- Remove unused `is_plugged_in` and `battery_saver_sticky` fields

---

###  Bug Fixes

- Fixed package truncation and taskset compatibility
- Fixed missing "Enable Optimization" toggle in Game Settings
- Fixed IPC JSON parsing robustness (strip handshake header)
- Fixed wildcard paths for Global Governor shell commands
- Removed unused `get_supported_refresh_rates` function

---

###  Build & CI

- Initial GitHub Actions build workflow
- Cargo-ndk with explicit `--platform` flag
- Centralized release profile configuration

---

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
