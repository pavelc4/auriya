# Changelog

## v1.0.2

### New Features

**Configurable Default Mode:**
- Added `default_mode` field to `settings.toml` for global profile preset
- Supports Balance/Performance/Powersave as default when no game is running
- Auto-reload default mode when `settings.toml` changes
- Global Preset dropdown in WebUI Settings
- Handles missing `[daemon]` section with serde defaults

**Internationalization (i18n):**
- Full i18n support for WebUI with English and Indonesian translations
- Localized all UI strings, dashboard messages, and settings
- Dynamic language switching without restart

**Adaptive Polling:**
- Implemented adaptive polling mechanism for reduced CPU overhead
- CPU usage reduced from 0.3% to 0.1% (67% reduction) during idle
- Removed fixed `poll_interval` from `DaemonConfig`

---

### WebUI Improvements

- Fixed per-game settings persistence and display
- Fixed `target_fps` display using `Array.isArray()` for serde plain JSON
- Added reactive statement to update UI when store changes
- Backend now serializes target_fps as plain number/array

---

### Refactoring

**Module Reorganization:**
- Split `run.rs` into `tick.rs`, `watcher.rs`, and `config.rs` modules
  - `run.rs`: 770 → 330 lines
  - New: `tick.rs` (342 lines), `watcher.rs` (91 lines), `config.rs` (22 lines)
- Split `ipc.rs` into `ipc/commands.rs`, `ipc/handlers.rs`, `ipc/server.rs`
- Merged duplicate balance handling into `apply_balance_and_clear()`

**Code Quality:**
- Migrated `FasController` from `std::sync::Mutex` to `tokio::sync::Mutex` for async-aware locking
- Removed unused `log`/`android_logger` dependencies, use `tracing` only
- Simplified conditionals throughout codebase

---

### Bug Fixes

**Backend (`tick.rs`):**
- Use global governor fallback instead of hardcoded 'performance'
- Balance mode now respects per-game `cpu_governor` setting
- Clone governor string to avoid borrow checker conflict

**Frontend (`GameSettings.svelte`):**
- Fix `target_fps` display for serde plain JSON serialization
- Backend serializes as plain number/array instead of enum `{ Single, Array }`

---

### CI/CD & Dependencies

**Dependabot Auto-Updates:**
- Added Dependabot configuration for automated dependency updates
- Bumped `kernelsu-alt` from 2.1.2 to 3.0.0
- Bumped `serde_json` from 1.0.145 to 1.0.147
- Bumped `toml` from 0.9.8 to 0.9.10+spec-1.1.0
- Bumped `tracing` from 0.1.43 to 0.1.44
- Bumped `actions/checkout` from 4 to 6
- Bumped `actions/upload-artifact` from 4 to 6
- Bumped `softprops/action-gh-release` from 1 to 2

**GitHub Actions:**
- Use raw GitHub URL for changelog in `update.json` generation
- Switch changelog URL from rendered webpage to raw content

---

### Documentation

- Updated README.md with project details and features
- Added supported root managers, resources, and license information

---

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
